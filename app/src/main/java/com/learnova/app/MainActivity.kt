package com.learnova.app

import android.os.Bundle
import android.graphics.*
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.sin

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(LearnovaView())
    }

    private inner class LearnovaView : View(this) {

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var running = false
        private var frame = 0L

        init {
            isClickable = true

            setOnClickListener {
                running = !running
                invalidate()
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val w = width.toFloat()
            val h = height.toFloat()

            frame++

            // Sky
            canvas.drawColor(Color.rgb(220, 242, 255))

            paint.style = Paint.Style.FILL

            // Ground
            paint.color = Color.rgb(150, 205, 170)
            canvas.drawRect(0f, h * 0.45f, w, h, paint)

            // Mountains
            val mountain = Path()

            mountain.moveTo(0f, h * 0.55f)
            mountain.lineTo(w * 0.18f, h * 0.30f)
            mountain.lineTo(w * 0.35f, h * 0.52f)
            mountain.lineTo(w * 0.55f, h * 0.25f)
            mountain.lineTo(w * 0.78f, h * 0.52f)
            mountain.lineTo(w, h * 0.32f)
            mountain.lineTo(w, h * 0.60f)
            mountain.lineTo(0f, h * 0.60f)
            mountain.close()

            paint.color = Color.rgb(100, 155, 135)
            canvas.drawPath(mountain, paint)

            // River
            val river = Path()

            river.moveTo(w * 0.05f, h)
            river.lineTo(w * 0.35f, h * 0.52f)
            river.lineTo(w * 0.55f, h * 0.52f)
            river.lineTo(w * 0.90f, h)
            river.close()

            paint.color = Color.rgb(80, 175, 220)
            canvas.drawPath(river, paint)

            // Road
            val road = Path()

            road.moveTo(w * 0.43f, h * 0.53f)
            road.lineTo(w * 0.57f, h * 0.53f)
            road.lineTo(w * 0.92f, h)
            road.lineTo(w * 0.08f, h)
            road.close()

            paint.color = Color.rgb(55, 60, 65)
            canvas.drawPath(road, paint)

            // Road markings
            paint.color = Color.WHITE
            paint.strokeWidth = 6f

            val offset =
                if (running) ((frame * 7) % 100).toFloat()
                else 0f

            var y = h * 0.58f + offset

            while (y < h) {

                val t =
                    ((y - h * 0.53f) / (h * 0.47f))
                        .coerceIn(0f, 1f)

                val halfWidth = 4f + t * 18f

                canvas.drawLine(
                    w / 2f - halfWidth,
                    y,
                    w / 2f + halfWidth,
                    y,
                    paint
                )

                y += 75f + t * 45f
            }

            // Car movement
            val bob =
                if (running)
                    sin(frame / 7.0).toFloat() * 3f
                else 0f

            val cx = w / 2f
            val cy = h * 0.78f + bob

            // Car body
            paint.color = Color.rgb(20, 130, 75)

            val body = RectF(
                cx - 105f,
                cy - 38f,
                cx + 105f,
                cy + 35f
            )

            canvas.drawRoundRect(
                body,
                25f,
                25f,
                paint
            )

            // Car window
            paint.color = Color.rgb(175, 225, 240)

            val window = RectF(
                cx - 55f,
                cy - 70f,
                cx + 55f,
                cy - 18f
            )

            canvas.drawRoundRect(
                window,
                22f,
                22f,
                paint
            )

            // Wheels
            paint.color = Color.rgb(30, 30, 30)

            canvas.drawCircle(
                cx - 68f,
                cy + 35f,
                20f,
                paint
            )

            canvas.drawCircle(
                cx + 68f,
                cy + 35f,
                20f,
                paint
            )

            // Title
            paint.color = Color.WHITE
            paint.textAlign = Paint.Align.CENTER
            paint.typeface =
                Typeface.create(
                    "sans-serif",
                    Typeface.BOLD
                )

            paint.textSize = 34f

            canvas.drawText(
                if (running)
                    "TOUCH TO PAUSE"
                else
                    "TOUCH TO START",
                cx,
                h * 0.13f,
                paint
            )

            // Subtitle
            paint.textSize = 22f

            paint.typeface =
                Typeface.create(
                    "sans-serif",
                    Typeface.NORMAL
                )

            canvas.drawText(
                "Learn • Play • Discover",
                cx,
                h * 0.18f,
                paint
            )

            // Continue animation
            if (running) {
                postInvalidateOnAnimation()
            }
        }
    }
}
