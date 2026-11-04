package com.dash.headunit.widgets

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.dash.headunit.R
import com.dash.headunit.navi.ClusterNavLauncher
import com.dash.headunit.theme.DashTheme

/**
 * Плитка «Открыть навигатор»: одним касанием поднимает Навигатор
 * на основной экран. Работает и без машины — просто запускает
 * приложение Навигатора.
 */
class ClusterLaunchNavWidgetView(
    context: Context,
    @Suppress("UNUSED_PARAMETER") wTheme: DashTheme
) : FrameLayout(context) {

    init {
        setBackgroundResource(R.drawable.widget_card)
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        val icon = ImageView(context).apply {
            setImageResource(R.drawable.ic_nav)
            val s = (48 * resources.displayMetrics.density).toInt()
            layoutParams = LayoutParams(s, s)
        }
        val label = TextView(context).apply {
            text = context.getString(R.string.widget_open_nav)
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#EDEDED"))
            textSize = 14f
        }
        box.addView(icon)
        box.addView(label)
        addView(box, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        setOnClickListener {
            try {
                ClusterNavLauncher.launchToMain(context)
            } catch (e: Exception) {
                // Навигатор недоступен — тихо выходим, плитка безопасна.
            }
        }
    }
}
