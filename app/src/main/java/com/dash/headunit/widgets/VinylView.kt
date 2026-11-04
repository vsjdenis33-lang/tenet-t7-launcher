package com.dash.headunit.widgets

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.View

/**
 * Виниловая пластинка в стиле штатной оболочки: чёрный диск с канавками,
 * в центре — обложка альбома («этикетка»), по краям — мягкий блик.
 * Пока музыка играет, пластинка плавно вращается.
 */
class VinylView(context: Context) : View(context) {

    private var label: Bitmap? = null
    private var labelShader: BitmapShader? = null
    private var labelMatrix = Matrix()

    private var playing = false
    private var angle = 0f

    /** Базовый диск: радиальный градиент имитирует глубину канавок. */
    private val disc = Paint(Paint.ANTI_ALIAS_FLAG)

    /** Канавки: тонкие светлые окружности с малой прозрачностью. */
    private val groove = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    /** Блики по краям диска (как отражение света на виниле). */
    private val sheen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    /** Этикетка без обложки — спокойный тёмный круг. */
    private val plainLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1D222A.toInt()
    }

    /** Ободок вокруг этикетки. */
    private val labelRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0x33000000
    }

    /** Центральное отверстие. */
    private val hole = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF05060A.toInt()
    }

    private val rect = RectF()

    /** Задать обложку (null — просто винил без этикетки). */
    fun setArt(b: Bitmap?) {
        if (b === label) return
        label = b
        labelShader = b?.let { BitmapShader(it, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
        invalidate()
    }

    /** Играет ли музыка: вращение запускается/останавливается плавно. */
    fun setPlaying(p: Boolean) {
        playing = p
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val r = minOf(w, h) / 2f
        if (r <= 0f) return

        // Корпус пластинки.
        disc.shader = RadialGradient(
            cx, cy, r,
            intArrayOf(
                0xFF191D23.toInt(),
                0xFF111419.toInt(),
                0xFF232932.toInt(),
                0xFF0A0C10.toInt()
            ),
            floatArrayOf(0.52f, 0.64f, 0.70f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, r, disc)
        disc.shader = null

        // Канавки: равномерные тонкие окружности.
        groove.color = 0x12FFFFFF
        groove.strokeWidth = maxOf(1f, r / 140f)
        var rr = r * 0.66f
        while (rr < r * 0.97f) {
            rect.set(cx - rr, cy - rr, cx + rr, cy + rr)
            canvas.drawOval(rect, groove)
            rr += r * 0.045f
        }

        // Блики: две дуги, как отражение света.
        sheen.color = 0x20FFFFFF
        sheen.strokeWidth = r * 0.085f
        rect.set(cx - r * 0.84f, cy - r * 0.84f, cx + r * 0.84f, cy + r * 0.84f)
        canvas.drawArc(rect, 152f, 30f, false, sheen)
        canvas.drawArc(rect, 332f, 30f, false, sheen)

        // Вращаемая этикетка с обложкой.
        canvas.save()
        canvas.rotate(angle, cx, cy)
        val lr = r * 0.60f
        val shader = labelShader
        if (shader != null) {
            val bm = label!!
            val side = lr * 2f
            val scale = side / minOf(bm.width, bm.height).toFloat()
            labelMatrix.reset()
            labelMatrix.setScale(scale, scale)
            labelMatrix.postTranslate(
                cx - bm.width * scale / 2f,
                cy - bm.height * scale / 2f
            )
            shader.setLocalMatrix(labelMatrix)
            disc.shader = shader
            canvas.drawCircle(cx, cy, lr, disc)
            disc.shader = null
        } else {
            canvas.drawCircle(cx, cy, lr, plainLabel)
        }
        labelRing.strokeWidth = maxOf(1.5f, r / 90f)
        canvas.drawCircle(cx, cy, lr, labelRing)
        // Отверстие и «грибок» тонарма.
        canvas.drawCircle(cx, cy, r * 0.045f, hole)
        canvas.restore()

        if (playing && isAttachedToWindow && isShown) {
            angle = (angle + 1.6f) % 360f
            postInvalidateOnAnimation()
        }
    }
}
