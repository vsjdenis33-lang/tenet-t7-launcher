package com.dash.headunit.navi

import android.content.Context
import android.util.Log
import com.dash.headunit.navi.cluster.Guidance
import com.dash.headunit.navi.cluster.GuidanceHub
import com.dash.headunit.settings.AppSettings

/**
 * Автоматический перенос навигатора на приборку по маршруту:
 * маршрут начался ([Guidance.onRoute]) — навигатор уезжает на приборку;
 * маршрут закончился — возвращается на основной экран.
 *
 * Работает от [GuidanceHub] (уведомления навигатора + спец. возможности).
 * Инициализируется из MainActivity и NaviAccessibilityService — событие
 * обрабатывается и когда дашборд не на переднем плане (Android 9
 * разрешает фоновый startActivity).
 */
object ClusterNavAuto {

    private const val TAG = "ClusterNavAuto"

    @Volatile private var appContext: Context? = null
    @Volatile private var registered = false

    /** Прошлое состояние ведения: реагируем только на смену. */
    @Volatile private var lastOnRoute = false

    fun init(context: Context) {
        if (registered) return
        registered = true
        appContext = context.applicationContext
        GuidanceHub.addListener { g -> onGuidance(g) }
    }

    private fun onGuidance(g: Guidance?) {
        val ctx = appContext ?: return
        val route = g?.onRoute == true
        if (!AppSettings.autoClusterNav(ctx)) {
            // Авто выключено: только помним состояние, ничего не переносим.
            lastOnRoute = route
            return
        }
        if (route == lastOnRoute) return
        lastOnRoute = route
        when {
            route && !ClusterNavLauncher.navOnCluster -> {
                val err = ClusterNavLauncher.launchToCluster(ctx)
                if (err == null) Log.i(TAG, "маршрут начат — навигатор на приборке")
                else Log.w(TAG, "перенос на приборку не удался")
            }
            !route && ClusterNavLauncher.navOnCluster -> {
                val err = ClusterNavLauncher.launchToMain(ctx)
                if (err == null) Log.i(TAG, "маршрут закончен — навигатор на основном экране")
                else Log.w(TAG, "возврат на экран не удался")
            }
        }
    }
}
