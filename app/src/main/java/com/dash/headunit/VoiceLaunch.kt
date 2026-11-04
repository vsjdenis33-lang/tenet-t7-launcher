package com.dash.headunit

import android.content.Context
import android.content.Intent
import com.dash.headunit.navi.cluster.NaviAccessibilityService

/**
 * Вторая клавиша руля: «голос». Сначала пробуем «тапнуть» микрофон в
 * открытом Навигаторе — голосовой ввод на дороге удобнее. Не вышло
 * (Навигатор закрыт, узел не найден) — открываем Алису: приложение
 * «Яндекс» с голосовым помощником, package ru.yandex.searchplugin.
 */
object VoiceLaunch {
    fun launch(context: Context) {
        val tapped = try {
            NaviAccessibilityService.instance?.tapMic() == true
        } catch (e: Exception) {
            false
        }
        if (tapped) return
        val intent = context.packageManager.getLaunchIntentForPackage("ru.yandex.searchplugin")
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}
