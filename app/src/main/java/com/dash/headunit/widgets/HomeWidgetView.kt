package com.dash.headunit.widgets

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.dash.headunit.R
import com.dash.headunit.theme.DashTheme
import com.dash.headunit.theme.ThemeCard

/**
 * Плитка «Домой / На работу»: крупная стрелка навигации и две пилюли.
 * Адрес — координаты «55.75, 37.61» (маршрут в Навигаторе) или текст
 * («Москва, Тверская 1» — поиск по карте). Без часов и погоды.
 */
class HomeWidgetView(
    context: Context,
    private val wTheme: DashTheme,
    home: String,
    work: String
) : FrameLayout(context) {

    companion object {
        const val OPT_HOME = "home_addr"
        const val OPT_WORK = "work_addr"
        private const val COORD = "-?\\d+(\\.\\d+)?\\s*,\\s*-?\\d+(\\.\\d+)?"

        fun launch(context: Context, addr: String) {
            val a = addr.trim()
            try {
                val uri = if (a.isEmpty()) {
                    "yandexnavi://"
                } else if (Regex(COORD).find(a) != null) {
                    val pair = Regex(COORD).find(a)!!.value.split(",")
                        .map { it.trim().toDouble() }
                    "yandexnavi://build_route_on_map?lat_to=" + pair[0] + "&lon_to=" + pair[1]
                } else {
                    "yandexnavi://map_search?text=" + Uri.encode(a)
                }
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (e: Exception) {
                // Навигатора нет — тихо выходим, плитка безопасна.
            }
        }
    }

    init {
        val bg = ThemeCard.widget(context, wTheme)
        if (bg != null) background = bg
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        val icon = ImageView(context).apply {
            setImageResource(R.drawable.ic_nav)
            setColorFilter(wTheme.accent)
            val s = (56 * resources.displayMetrics.density).toInt()
            layoutParams = LayoutParams(s, s)
        }
        box.addView(icon)
        box.addView(
            android.view.View(context),
            LinearLayout.LayoutParams(1, (12 * resources.displayMetrics.density).toInt())
        )
        box.addView(button(R.string.nav_home_label, R.drawable.ic_home) { launch(context, home) })
        box.addView(button(R.string.nav_work_label, R.drawable.ic_work) { launch(context, work) })
        addView(box, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    private fun button(labelRes: Int, iconRes: Int, onClick: () -> Unit): TextView {
        val pad = (10 * resources.displayMetrics.density).toInt()
        return TextView(context).apply {
            text = context.getString(labelRes)
            textSize = 15f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            compoundDrawablePadding = pad / 2
            setCompoundDrawablesRelativeWithIntrinsicBounds(iconRes, 0, 0, 0)
            background = ThemeCard.pill(context, wTheme)
            setPadding(pad * 2, pad / 2 + 2, pad * 2, pad / 2 + 2)
            elevation = 4f
            setOnClickListener { onClick() }
        }
    }
}
