package com.dash.headunit.weather

import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import org.json.JSONArray
import org.json.JSONObject

/**
 * Источники погоды, доступные из России без VPN. Все отдают общую
 * модель WeatherData с WMO-кодами; перебор делает WeatherRepository:
 * Яндекс.Погода (по ключу) -> wttr.in -> met.no -> Open-Meteo -> OWM.
 */
object WeatherRf {

    private const val CONNECT_TIMEOUT = 6_000
    private const val READ_TIMEOUT = 6_000

    /** GET с таймаутами 6 с; ошибки HTTP превращаем в исключение. */
    fun get(url: String, headers: Map<String, String> = emptyMap()): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = CONNECT_TIMEOUT
            conn.readTimeout = READ_TIMEOUT
            conn.requestMethod = "GET"
            for ((k, v) in headers) conn.setRequestProperty(k, v)
            val okHttp = conn.responseCode in 200..299
            val stream: InputStream? = if (okHttp) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (!okHttp) throw IOException("HTTP ${conn.responseCode}: ${body.take(200)}")
            return body
        } finally {
            conn.disconnect()
        }
    }

    /** Состояние Яндекса -> WMO-код (для общих иконок погоды). */
    fun yandexToWmo(condition: String): Int = when (condition) {
        "clear" -> 0
        "partly-cloudy" -> 2
        "cloudy", "overcast" -> 3
        "drizzle" -> 51
        "light-rain" -> 61
        "rain", "moderate-rain" -> 63
        "heavy-rain", "continuous-heavy-rain" -> 65
        "showers" -> 80
        "wet-snow" -> 68
        "light-snow" -> 71
        "snow" -> 73
        "snow-showers" -> 75
        "thunderstorm", "thunderstorm-with-rain" -> 95
        "hail", "thunderstorm-with-hail" -> 99
        else -> 3
    }

    /** Символ met.no (symbol_code) -> WMO-код. */
    fun metNoToWmo(symbol: String): Int {
        val s = symbol.substringBefore('_') // срезаем суффиксы _day/_night/_polartwilight
        return when (s) {
            "clearsky" -> 0
            "fair" -> 1
            "partlycloudy" -> 2
            "cloudy" -> 3
            "fog" -> 45
            "sleet", "lightsleet", "heavysleet" -> 68
            "lightrain" -> 51
            "rain" -> 63
            "heavyrain" -> 65
            "lightrainshowers", "rainshowers" -> 80
            "heavyrainshowers" -> 81
            "lightsnow" -> 71
            "snow" -> 73
            "heavysnow" -> 75
            "lightsnowshowers", "snowshowers" -> 85
            "heavysnowshowers" -> 86
            "thunder", "thunderstorm" -> 95
            else -> 3
        }
    }
}

/** Агрегатор суточных значений met.no. */
private class DayAgg(val date: String, var max: Double, var min: Double, var code: Int)

/** Яндекс.Погода API v2: нужен бесплатный ключ (yandexweather.key). */
object YandexWeatherClient {
    fun fetchForecast(
        latitude: Double,
        longitude: Double,
        key: String,
        hoursCount: Int,
        daysCount: Int,
        isFallbackLocation: Boolean
    ): WeatherData {
        val url = "https://api.weather.yandex.ru/v2/forecast" +
            "?lat=$latitude&lon=$longitude&lang=ru_RU&limit=${daysCount.coerceIn(1, 7)}"
        val json = JSONObject(WeatherRf.get(url, mapOf("X-Yandex-Weather-Key" to key)))
        val fact = json.getJSONObject("fact")
        val nowTemp = if (fact.has("temp")) fact.getDouble("temp") else null
        val nowCode = WeatherRf.yandexToWmo(fact.optString("condition", ""))
        val now = Calendar.getInstance()
        val forecasts = json.optJSONArray("forecasts") ?: JSONArray()
        val hours = ArrayList<HourlyPoint>()
        for (d in 0 until forecasts.length()) {
            val hrs = forecasts.getJSONObject(d).optJSONArray("hours") ?: continue
            for (h in 0 until hrs.length()) {
                if (hours.size >= hoursCount) break
                val hour = hrs.getJSONObject(h)
                val hourVal = hour.optInt("hour", -1)
                if (d == 0 && hourVal <= now.get(Calendar.HOUR_OF_DAY)) continue
                hours.add(
                    HourlyPoint(
                        label = String.format(Locale.getDefault(), "%02d:00", hourVal),
                        temperatureC = hour.optDouble("temp", 0.0),
                        weatherCode = WeatherRf.yandexToWmo(hour.optString("condition", ""))
                    )
                )
            }
        }
        val days = ArrayList<DailyPoint>()
        for (d in 0 until minOf(daysCount, forecasts.length())) {
            val day = forecasts.getJSONObject(d)
            val parts = day.getJSONObject("parts")
            days.add(
                DailyPoint(
                    dateIso = day.optString("date", ""),
                    maxC = parts.getJSONObject("day").optDouble("temp", 0.0),
                    minC = parts.getJSONObject("night").optDouble("temp", 0.0),
                    weatherCode = WeatherRf.yandexToWmo(
                        parts.getJSONObject("day").optString("condition", "")
                    )
                )
            )
        }
        return WeatherData(
            latitude = latitude,
            longitude = longitude,
            city = null,
            nowTempC = nowTemp,
            nowCode = nowCode,
            hours = hours,
            days = days,
            fetchedAtMillis = System.currentTimeMillis(),
            isFallbackLocation = isFallbackLocation,
            source = WeatherData.SOURCE_YANDEX
        )
    }
}

/** wttr.in (дублёр wttr.is): без ключа, WMO-коды из коробки, 3 дня. */
object WttrClient {
    fun fetchForecast(
        latitude: Double,
        longitude: Double,
        hoursCount: Int,
        daysCount: Int,
        isFallbackLocation: Boolean
    ): WeatherData {
        val body = try {
            WeatherRf.get("https://wttr.in/$latitude,$longitude?format=j1")
        } catch (e: Exception) {
            // Основной домен недоступен — пробуем зеркало wttr.is.
            WeatherRf.get("https://wttr.is/$latitude,$longitude?format=j1")
        }
        val json = JSONObject(body)
        val cur = json.getJSONArray("current_condition").getJSONObject(0)
        val nowTemp = cur.optString("temp_C").toDoubleOrNull()
        val nowCode = cur.optInt("weatherCode", 3)
        var city: String? = null
        val areas = json.optJSONArray("nearest_area")
        if (areas != null && areas.length() > 0) {
            val names = areas.getJSONObject(0).optJSONArray("areaName")
            if (names != null && names.length() > 0) city = names.getJSONObject(0).optString("value")
        }
        val now = Calendar.getInstance()
        val weather = json.optJSONArray("weather") ?: JSONArray()
        val hours = ArrayList<HourlyPoint>()
        outer@ for (d in 0 until minOf(daysCount, weather.length())) {
            val hrs = weather.getJSONObject(d).optJSONArray("hourly") ?: continue
            for (h in 0 until hrs.length()) {
                val hour = hrs.getJSONObject(h)
                val timeVal = hour.optString("time", "0").toInt() / 100
                if (d == 0 && timeVal <= now.get(Calendar.HOUR_OF_DAY)) continue
                if (hours.size >= hoursCount) break@outer
                hours.add(
                    HourlyPoint(
                        label = String.format(Locale.getDefault(), "%02d:00", timeVal),
                        temperatureC = hour.optString("tempC", "0").toDoubleOrNull() ?: 0.0,
                        weatherCode = hour.optInt("weatherCode", 3)
                    )
                )
            }
        }
        val days = ArrayList<DailyPoint>()
        for (d in 0 until minOf(daysCount, weather.length())) {
            val day = weather.getJSONObject(d)
            days.add(
                DailyPoint(
                    dateIso = day.optString("date", ""),
                    maxC = day.optString("maxtempC", "0").toDoubleOrNull() ?: 0.0,
                    minC = day.optString("mintempC", "0").toDoubleOrNull() ?: 0.0,
                    weatherCode = day.optInt("weatherCode", 3)
                )
            )
        }
        return WeatherData(
            latitude = latitude,
            longitude = longitude,
            city = city,
            nowTempC = nowTemp,
            nowCode = nowCode,
            hours = hours,
            days = days,
            fetchedAtMillis = System.currentTimeMillis(),
            isFallbackLocation = isFallbackLocation,
            source = WeatherData.SOURCE_WTTR
        )
    }
}

/** met.no (Yr): без ключа, нужен только User-Agent. */
object MetNoClient {
    private val UA = "TenetT7Launcher/1.0 android-headunit-weather"

    fun fetchForecast(
        latitude: Double,
        longitude: Double,
        hoursCount: Int,
        daysCount: Int,
        isFallbackLocation: Boolean
    ): WeatherData {
        val url = "https://api.met.no/weatherapi/locationforecast/2.0/compact" +
            "?lat=$latitude&lon=$longitude"
        val json = JSONObject(WeatherRf.get(url, mapOf("User-Agent" to UA)))
        val ts = json.getJSONObject("properties").getJSONArray("timeseries")
        if (ts.length() == 0) throw IOException("met.no: пустой прогноз")
        val isoFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        isoFmt.timeZone = TimeZone.getTimeZone("UTC")
        val hourFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        fun detailsOf(point: JSONObject) =
            point.getJSONObject("data").getJSONObject("instant").getJSONObject("details")
        fun symbolOf(point: JSONObject): String {
            val next = point.getJSONObject("data").optJSONObject("next_1_hours") ?: return ""
            return next.getJSONObject("summary").optString("symbol_code", "")
        }
        fun timeOf(point: String): Calendar {
            val cal = Calendar.getInstance()
            cal.time = isoFmt.parse(point) ?: throw IOException("met.no: битое время")
            return cal
        }

        val first = ts.getJSONObject(0)
        val nowTemp = detailsOf(first).optDouble("air_temperature", Double.NaN)
            .let { if (it.isNaN()) null else it }
        val nowCode = WeatherRf.metNoToWmo(symbolOf(first))

        val hours = ArrayList<HourlyPoint>()
        for (i in 1 until ts.length()) {
            if (hours.size >= hoursCount) break
            val point = ts.getJSONObject(i)
            val cal = timeOf(point.optString("time", ""))
            val sym = symbolOf(point)
            hours.add(
                HourlyPoint(
                    label = hourFmt.format(cal.time),
                    temperatureC = detailsOf(point).optDouble("air_temperature", 0.0),
                    weatherCode = if (sym.isBlank()) 3 else WeatherRf.metNoToWmo(sym)
                )
            )
        }
        val byDay = LinkedHashMap<String, DayAgg>()
        for (i in 0 until ts.length()) {
            val point = ts.getJSONObject(i)
            val cal = timeOf(point.optString("time", ""))
            val key = dayFmt.format(cal.time)
            val t = detailsOf(point).optDouble("air_temperature", 0.0)
            val agg = byDay.getOrPut(key) { DayAgg(key, t, t, 3) }
            if (t > agg.max) agg.max = t
            if (t < agg.min) agg.min = t
            val sym = symbolOf(point)
            if (agg.code == 3 && sym.isNotBlank()) agg.code = WeatherRf.metNoToWmo(sym)
        }
        val days = byDay.values.take(daysCount).map { DailyPoint(it.date, it.max, it.min, it.code) }
        return WeatherData(
            latitude = latitude,
            longitude = longitude,
            city = null,
            nowTempC = nowTemp,
            nowCode = nowCode,
            hours = hours,
            days = days,
            fetchedAtMillis = System.currentTimeMillis(),
            isFallbackLocation = isFallbackLocation,
            source = WeatherData.SOURCE_METNO
        )
    }
}
