package com.dash.headunit.widgets

import android.widget.TextView
import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import com.dash.headunit.R
import com.dash.headunit.navi.ClusterNavLauncher
import com.dash.headunit.theme.DashTheme

/**
 * Виджет «На приборку»: одна кнопка-переключатель.
 * Тап — навигатор уезжает на приборку или возвращается на экран
 * ([ClusterNavLauncher]). Подпись показывает, куда навигатор
 * перенесён последней нашей командой.
 */
class ClusterLaunchWidgetView(
    context: Context,
    private val theme: DashTheme = DashTheme.DARK
) : LinearLayout(context) {

    private val iconView = ImageView(context)
    private val stateView = TextView(context)

    private val watcher: () -> Unit = { render() }

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER

        iconView.apply {
            setImageResource(R.drawable.ic_cluster)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }
        addView(iconView, LayoutParams(dp(34), dp(34)))

        stateView.apply {
            gravity = Gravity.CENTER
            maxLines = 2
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        }
        addView(
            stateView,
            LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
        )

        setOnClickListener { move() }
        render()
    }

    private fun dp(v: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    private fun move() {
        val err = ClusterNavLauncher.toggle(context)
        val msg = err
            ?: if (ClusterNavLauncher.navOnCluster) R.string.msg_moved_to_cluster
            else R.string.msg_moved_to_main
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        render()
    }

    private fun render() {
        val onCluster = ClusterNavLauncher.navOnCluster
        stateView.text = context.getString(
            if (onCluster) R.string.cluster_state_cluster else R.string.cluster_state_main
        )
        stateView.setTextColor(if (onCluster) theme.accent else theme.secondaryText)
        iconView.setColorFilter(if (onCluster) theme.accent else theme.primaryText)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ClusterNavLauncher.watch(watcher)
    }

    override fun onDetachedFromWindow() {
        ClusterNavLauncher.unwatch(watcher)
        super.onDetachedFromWindow()
    }
}
