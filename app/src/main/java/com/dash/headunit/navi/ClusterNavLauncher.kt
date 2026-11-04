package com.dash.headunit.navi

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.Display
import com.dash.headunit.R
import com.dash.headunit.widgets.NavApps

/**
 * Перенос навигатора между основным экраном и приборкой.
 *
 * Механика проверена на живом ГУ (T7, DesaySV): Яндекс Навигатор
 * открывается на дисплее приборки (Экран HDMI, displayId 1) через
 * ActivityOptions.setLaunchDisplayId — так же, как это делает NavPP.
 *
 * Важно: активность карты ru.yandex.yandexmaps.app.MapActivity живёт
 * в пакете ru.yandex.yandexnavi (проверено dumpsys activity activities:
 * cmp=ru.yandex.yandexnavi/ru.yandex.yandexmaps.app.MapActivity).
 * Прямой запуск по компоненту надёжнее geo-интента: geo резолвится по
 * системе, и на части сборок система может открыть Карты вместо Навигатора.
 *
 * Состояние navOnCluster — наше собственное наблюдение: что мы сами
 * недавно делали (в т.ч. ClusterNavAuto). Если навигатор переносят
 * руками через NavPP, состояние не меняется — виджет покажет
 * последнюю нашу команду.
 */
object ClusterNavLauncher {

    /** Активность карты: пакет ru.yandex.yandexnavi, класс из модуля yandexmaps. */
    const val YANDEX_NAVI_PKG = "ru.yandex.yandexnavi"
    const val MAP_ACTIVITY = "ru.yandex.yandexmaps.app.MapActivity"
    const val YANDEX_MAPS_PKG = "ru.yandex.yandexmaps"

    /** Последний перенос: true — навигатор на приборке (по нашей команде). */
    @Volatile
    var navOnCluster: Boolean = false
        private set

    private val watchers = mutableListOf<() -> Unit>()
    private val main = Handler(Looper.getMainLooper())

    /** Подписка на смену состояния (для виджета); сразу же присылает текущее. */
    fun watch(w: () -> Unit) {
        synchronized(watchers) { watchers.add(w) }
        w()
    }

    fun unwatch(w: () -> Unit) {
        synchronized(watchers) { watchers.remove(w) }
    }

    private fun notifyWatchers() {
        val copy = synchronized(watchers) { watchers.toList() }
        main.post { copy.forEach { it() } }
    }

    /**
     * Запуск навигатора на приборку. null — успех, иначе id строки ошибки.
     */
    fun launchToCluster(context: Context): Int? = launch(context, toCluster = true)

    /** Возврат навигатора на основной экран (displayId 0). */
    fun launchToMain(context: Context): Int? = launch(context, toCluster = false)

    /** Переключение «экран ↔ приборка» (виджет, клавиша руля). */
    fun toggle(context: Context): Int? = launch(context, toCluster = !navOnCluster)

    private fun launch(context: Context, toCluster: Boolean): Int? {
        val displayId = if (toCluster) {
            // На этих ГУ приборка — первый неосновной дисплей (Экран HDMI, id 1).
            NavApps.getClusterDisplayId(context) ?: return R.string.msg_no_cluster
        } else {
            Display.DEFAULT_DISPLAY
        }
        val intent = intentFor(context) ?: return R.string.msg_no_nav
        val options = ActivityOptions.makeBasic()
        try {
            options.launchDisplayId = displayId
            context.startActivity(intent, options.toBundle())
        } catch (e: Exception) {
            return R.string.msg_no_nav
        }
        navOnCluster = toCluster
        notifyWatchers()
        return null
    }

    /**
     * Интент для навигатора: Яндекс — прямой компонент MapActivity,
     * остальные — geo-интент с пакетом.
     */
    private fun intentFor(context: Context): Intent? {
        val pkg = NavApps.NAV_APPS.firstOrNull { isInstalled(context, it.second) }?.second
            ?: return null
        return if (pkg == YANDEX_NAVI_PKG || pkg == YANDEX_MAPS_PKG) {
            Intent(Intent.ACTION_VIEW).apply {
                component = ComponentName(YANDEX_NAVI_PKG, MAP_ACTIVITY)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0")).apply {
                setPackage(pkg)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    private fun isInstalled(context: Context, pkg: String): Boolean = try {
        context.packageManager.getPackageInfo(pkg, 0)
        true
    } catch (e: Exception) {
        false
    }
}
