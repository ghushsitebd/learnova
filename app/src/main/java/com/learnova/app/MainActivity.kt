package com.learnova.app

import android.graphics.*
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.sin

class MainActivity : AppCompatActivity() {

    private lateinit var gameView: LearnovaGameView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        gameView = LearnovaGameView()
        setContentView(gameView)
    }

    private inner class LearnovaGameView : View(this@MainActivity) {

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val roadPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        private var driving = false
        private var time = 0L
        private var level = 1

        // Learning system
        private val questions = arrayOf(
            "A",
            "B",
            "C",
            "1 + 1 = 2",
            "বাংলা: অ",
            "Arabic: ا"
        )

        private var questionIndex = 0

        init {
            isClickable = true

            textPaint.typeface = Typeface.create(
                "sans-serif",
                Typeface.BOLD
            )
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {

            when (event.actionMasked) {

                MotionEvent.ACTION_DOWN -> {
                    driving = true
                    invalidate()
                    postInvalidateOnAnimation()
                    return true
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    driving = false
                    invalidate()
                    return true
                }
            }

            return true
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val w = width.toFloat()
            val h = height.toFloat()

            if (w <= 0f || h <= 0f) return

            if (driving) {
                time++
            }

            drawSky(canvas, w, h)
            drawSun(canvas, w, h)
            drawMountains(canvas, w, h)
            drawTrees(canvas, w, h)
            drawRiver(canvas, w, h)
            drawRoad(canvas, w, h)
            drawCar(canvas, w, h)
            drawChild(canvas, w, h)
            drawLearningPanel(canvas, w, h)
            drawTopInfo(canvas, w, h)

            if (driving) {
                postInvalidateOnAnimation()
            }
        }

        // ---------------------------------------------------------
        // SKY
        // ---------------------------------------------------------

        private fun drawSky(
            canvas: Canvas,
            w: Float,
            h: Float
        ) {
            val sky = LinearGradient(
                0f,
                0f,
                0f,
                h * 0.60f,
                Color.rgb(115, 200, 255),
                Color.rgb(225, 245, 255),
                Shader.TileMode.CLAMP
            )

            paint.shader = sky
            canvas.drawRect(0f, 0f, w, h, paint)
            paint.shader = null
        }

        // ---------------------------------------------------------
        // SUN
        // ---------------------------------------------------------

        private fun drawSun(
            canvas: Canvas,
            w: Float,
            h: Float
        ) {
            paint.color = Color.rgb(255, 218, 90)

            canvas.drawCircle(
                w * 0.82f,
                h * 0.15f,
                minOf(w, h) * 0.07f,
                paint
            )
        }

        // ---------------------------------------------------------
        // MOUNTAINS
        // ---------------------------------------------------------

        private fun drawMountains(
            canvas: Canvas,
            w: Float,
            h: Float
        ) {
            val mountainBack = Path()

            mountainBack.moveTo(0f, h * 0.52f)
            mountainBack.lineTo(w * 0.16f, h * 0.27f)
            mountainBack.lineTo(w * 0.30f, h * 0.45f)
            mountainBack.lineTo(w * 0.48f, h * 0.20f)
            mountainBack.lineTo(w * 0.66f, h * 0.45f)
            mountainBack.lineTo(w * 0.82f, h * 0.25f)
            mountainBack.lineTo(w, h * 0.48f)
            mountainBack.lineTo(w, h * 0.62f)
            mountainBack.lineTo(0f, h * 0.62f)
            mountainBack.close()

            paint.color = Color.rgb(92, 145, 125)
            canvas.drawPath(mountainBack, paint)

            val mountainFront = Path()

            mountainFront.moveTo(0f, h * 0.59f)
            mountainFront.lineTo(w * 0.22f, h * 0.38f)
            mountainFront.lineTo(w * 0.40f, h * 0.57f)
            mountainFront.lineTo(w * 0.59f, h * 0.35f)
            mountainFront.lineTo(w * 0.76f, h * 0.57f)
            mountainFront.lineTo(w, h * 0.39f)
            mountainFront.lineTo(w, h * 0.68f)
            mountainFront.lineTo(0f, h * 0.68f)
            mountainFront.close()

            paint.color = Color.rgb(67, 125, 100)
            canvas.drawPath(mountainFront, paint)
        }

        // ---------------------------------------------------------
        // TREES
        // ---------------------------------------------------------

        private fun drawTrees(
            canvas: Canvas,
            w: Float,
            h: Float
        ) {
            val positions = floatArrayOf(
                0.06f,
                0.15f,
                0.27f,
                0.72f,
                0.84f,
                0.94f
            )

            for (i in positions.indices) {

                val x = w * positions[i]

                val baseY =
                    h * (0.59f + (i % 2) * 0.035f)

                val scale =
                    0.75f + (i % 3) * 0.15f

                drawTree(
                    canvas,
                    x,
                    baseY,
                    scale
                )
            }
        }

        private fun drawTree(
            canvas: Canvas,
            x: Float,
            y: Float,
            scale: Float
        ) {
            // trunk
            paint.color = Color.rgb(105, 68, 38)

            val trunk = RectF(
                x - 12f * scale,
                y - 130f * scale,
                x + 12f * scale,
                y,
            )

            canvas.drawRoundRect(
                trunk,
                8f,
                8f,
                paint
            )

            // foliage
            paint.color = Color.rgb(35, 125, 65)

            canvas.drawCircle(
                x,
                y - 155f * scale,
                48f * scale,
                paint
            )

            canvas.drawCircle(
                x - 35f * scale,
                y - 130f * scale,
                38f * scale,
                paint
            )

            canvas.drawCircle(
                x + 35f * scale,
                y - 130f * scale,
                38f * scale,
                paint
            )

            paint.color = Color.rgb(55, 155, 75)

            canvas.drawCircle(
                x - 12f * scale,
                y - 178f * scale,
                27f * scale,
                paint
            )
        }

        // ---------------------------------------------------------
        // RIVER
        // ---------------------------------------------------------

        private fun drawRiver(
            canvas: Canvas,
            w: Float,
            h: Float
        ) {
            val river = Path()

            river.moveTo(0f, h)
            river.cubicTo(
                w * 0.18f,
                h * 0.76f,
                w * 0.25f,
                h * 0.64f,
                w * 0.43f,
                h * 0.55f
            )

            river.cubicTo(
                w * 0.57f,
                h * 0.50f,
                w * 0.75f,
                h * 0.72f,
                w,
                h * 0.83f
            )

            river.lineTo(w, h)
            river.close()

            paint.color = Color.rgb(55, 165, 220)

            canvas.drawPath(
                river,
                paint
            )

            // water highlights
            paint.color = Color.argb(
                120,
                220,
                250,
                255
            )

            paint.strokeWidth = 4f

            for (i in 0..5) {

                val yy =
                    h * 0.78f +
                            i * 25f

                canvas.drawLine(
                    w * 0.70f,
                    yy,
                    w * 0.88f,
                    yy + 4f,
                    paint
                )
            }
        }

        // ---------------------------------------------------------
        // ROAD
        // ---------------------------------------------------------

        private fun drawRoad(
            canvas: Canvas,
            w: Float,
            h: Float
        ) {
            val road = Path()

            road.moveTo(
                w * 0.40f,
                h * 0.57f
            )

            road.lineTo(
                w * 0.60f,
                h * 0.57f
            )

            road.lineTo(
                w * 0.95f,
                h
            )

            road.lineTo(
                w * 0.05f,
                h
            )

            road.close()

            roadPaint.color =
                Color.rgb(48, 51, 55)

            canvas.drawPath(
                road,
                roadPaint
            )

            // road edges
            roadPaint.color =
                Color.rgb(235, 235, 235)

            roadPaint.strokeWidth = 5f
            roadPaint.style = Paint.Style.STROKE

            canvas.drawPath(
                road,
                roadPaint
            )

            roadPaint.style = Paint.Style.FILL

            // moving center lines
            val movement =
                if (driving) {
                    (time * 8L % 130L).toFloat()
                } else {
                    0f
                }

            var y = h * 0.61f + movement

            while (y < h) {

                val t =
                    ((y - h * 0.57f) /
                            (h * 0.43f))
                        .coerceIn(0f, 1f)

                val width =
                    4f + t * 28f

                roadPaint.color = Color.WHITE

                canvas.drawRect(
                    w / 2f - width,
                    y,
                    w / 2f + width,
                    y + 12f + t * 18f,
                    roadPaint
                )

                y += 85f + t * 70f
            }
        }

        // ---------------------------------------------------------
        // CAR
        // ---------------------------------------------------------

        private fun drawCar(
            canvas: Canvas,
            w: Float,
            h: Float
        ) {
            val bob =
                if (driving) {
                    sin(time / 5.0).toFloat() * 3f
                } else {
                    0f
                }

            val cx = w / 2f
            val cy = h * 0.80f + bob

            // shadow
            paint.color = Color.argb(
                100,
                0,
                0,
                0
            )

            canvas.drawOval(
                RectF(
                    cx - 115f,
                    cy + 25f,
                    cx + 115f,
                    cy + 58f
                ),
                paint
            )

            // body
            paint.color = Color.rgb(
                18,
                135,
                78
            )

            canvas.drawRoundRect(
                RectF(
                    cx - 112f,
                    cy - 42f,
                    cx + 112f,
                    cy + 38f
                ),
                25f,
                25f,
                paint
            )

            // roof
            val roof = Path()

            roof.moveTo(
                cx - 68f,
                cy - 42f
            )

            roof.lineTo(
                cx - 40f,
                cy - 82f
            )

            roof.lineTo(
                cx + 45f,
                cy - 82f
            )

            roof.lineTo(
                cx + 72f,
                cy - 42f
            )

            roof.close()

            paint.color =
                Color.rgb(15, 105, 62)

            canvas.drawPath(
                roof,
                paint
            )

            // windows
            paint.color =
                Color.rgb(170, 220, 235)

            canvas.drawRoundRect(
                RectF(
                    cx - 38f,
                    cy - 72f,
                    cx + 4f,
                    cy - 43f
                ),
                8f,
                8f,
                paint
            )

            canvas.drawRoundRect(
                RectF(
                    cx + 8f,
                    cy - 72f,
                    cx + 43f,
                    cy - 43f
                ),
                8f,
                8f,
                paint
            )

            // lights
            paint.color =
                Color.rgb(255, 240, 150)

            canvas.drawCircle(
                cx - 100f,
                cy - 8f,
                9f,
                paint
            )

            canvas.drawCircle(
                cx + 100f,
                cy - 8f,
                9f,
                paint
            )

            // wheels
            paint.color =
                Color.rgb(25, 25, 25)

            canvas.drawCircle(
                cx - 70f,
                cy + 38f,
                24f,
                paint
            )

            canvas.drawCircle(
                cx + 70f,
                cy + 38f,
                24f,
                paint
            )

            paint.color =
                Color.rgb(170, 170, 170)

            canvas.drawCircle(
                cx - 70f,
                cy + 38f,
                10f,
                paint
            )

            canvas.drawCircle(
                cx + 70f,
                cy + 38f,
                10f,
                paint
            )
        }

        // ---------------------------------------------------------
        // CHILD - CAP + PANJABI
        // ---------------------------------------------------------

        private fun drawChild(
            canvas: Canvas,
            w: Float,
            h: Float
        ) {
            val x = w * 0.15f
            val y = h * 0.74f

            // body / panjabi
            paint.color =
                Color.rgb(245, 245, 235)

            canvas.drawRoundRect(
                RectF(
                    x - 27f,
                    y + 45f,
                    x + 27f,
                    y + 115f
                ),
                12f,
                12f,
                paint
            )

            // panjabi collar
            paint.color =
                Color.rgb(220, 220, 210)

            canvas.drawRect(
                x - 5f,
                y + 48f,
                x + 5f,
                y + 78f,
                paint
            )

            // head
            paint.color =
                Color.rgb(198, 145, 105)

            canvas.drawCircle(
                x,
                y + 20f,
                24f,
                paint
            )

            // beard/hair detail
            paint.color =
                Color.rgb(55, 40, 30)

            canvas.drawCircle(
                x - 17f,
                y + 10f,
                5f,
                paint
            )

            // cap
            paint.color =
                Color.rgb(45, 105, 70)

            canvas.drawOval(
                RectF(
                    x - 29f,
                    y - 4f,
                    x + 29f,
                    y + 16f
                ),
                paint
            )

            canvas.drawRect(
                x - 22f,
                y + 2f,
                x + 22f,
                y + 14f,
                paint
            )

            // eyes
            paint.color = Color.BLACK

            canvas.drawCircle(
                x - 8f,
                y + 20f,
                2.5f,
                paint
            )

            canvas.drawCircle(
                x + 8f,
                y + 20f,
                2.5f,
                paint
            )

            // smile
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f

            canvas.drawArc(
                RectF(
                    x - 9f,
                    y + 20f,
                    x + 9f,
                    y + 34f
                ),
                0f,
                180f,
                false,
                paint
            )

            paint.style = Paint.Style.FILL
        }

        // ---------------------------------------------------------
        // LEARNING PANEL
        // ---------------------------------------------------------

        private fun drawLearningPanel(
            canvas: Canvas,
            w: Float,
            h: Float
        ) {
            val left = 18f
            val right = w - 18f
            val bottom = h - 18f
            val top = h - 110f

            paint.color =
                Color.argb(
                    225,
                    255,
                    255,
                    255
                )

            canvas.drawRoundRect(
                RectF(
                    left,
                    top,
                    right,
                    bottom
                ),
                22f,
                22f,
                paint
            )

            textPaint.textAlign =
                Paint.Align.LEFT

            textPaint.color =
                Color.rgb(30, 90, 60)

            textPaint.textSize = 17f

            canvas.drawText(
                "LEARN",
                left + 18f,
                top + 28f,
                textPaint
            )

            textPaint.color = Color.DKGRAY
            textPaint.textSize = 22f

            canvas.drawText(
                "Level $level   •   ${questions[questionIndex]}",
                left + 18f,
                top + 60f,
                textPaint
            )

            textPaint.color =
                Color.rgb(70, 70, 70)

            textPaint.textSize = 14f

            canvas.drawText(
                if (driving)
                    "গাড়ি চলছে — শেখো এবং এগিয়ে যাও!"
                else
                    "স্ক্রিনে আঙুল ধরে রাখলে গাড়ি চলবে",
                left + 18f,
                top + 84f,
                textPaint
            )
        }

        // ---------------------------------------------------------
        // TOP INFORMATION
        // ---------------------------------------------------------

        private fun drawTopInfo(
            canvas: Canvas,
            w: Float,
            h: Float
        ) {
            textPaint.textAlign =
                Paint.Align.CENTER

            textPaint.color = Color.WHITE
            textPaint.setShadowLayer(
                5f,
                0f,
                2f,
                Color.DKGRAY
            )

            textPaint.textSize = 32f

            canvas.drawText(
                "LEARNOVA",
                w / 2f,
                h * 0.09f,
                textPaint
            )

            textPaint.textSize = 16f

            canvas.drawText(
                if (driving)
                    "DRIVING • LEARNING • DISCOVERING"
                else
                    "TOUCH AND HOLD TO DRIVE",
                w / 2f,
                h * 0.13f,
                textPaint
            )

            textPaint.clearShadowLayer()
        }
    }
}
