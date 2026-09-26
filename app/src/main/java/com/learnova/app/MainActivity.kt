package com.learnova.app

import android.graphics.*
import android.os.Bundle
import android.content.SharedPreferences
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var gameView: LearnovaGameView
    private lateinit var voice: LearnovaVoice

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setStatusBarColor(Color.rgb(78, 175, 235))
        window.setNavigationBarColor(Color.BLACK)
        voice = LearnovaVoice(this)
        gameView = LearnovaGameView()
        setContentView(gameView)
    }

    private inner class LearnovaGameView : View(this@MainActivity) {

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }

        private var running = false
        private var frame = 0L
        private var distance = 0f
        private var vehicleProgress = 0.78f
        private var wheelSpin = 0f
        private var speed = 0f
        private var laneOffset = 0f
        private var steering = 0f
        private var level = 1
        private var vehicle = 0
        private var worldSceneId = 1
        private var question = 0
        private var lastTap = 0L
        private val prefs: SharedPreferences = getSharedPreferences("learnova_progress", MODE_PRIVATE)

        private val lessons = arrayOf(
            "A", "B", "C", "D", "E", "F", "G", "H",
            "I", "J", "K", "L", "M", "N", "O", "P",
            "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z",
            "ا", "ب", "ت", "ث", "ج", "ح", "خ", "د", "ذ", "ر", "ز",
            "س", "ش", "ص", "ض", "ط", "ظ", "ع", "غ",
            "ف", "ق", "ك", "ل", "م", "ن", "ه", "و", "ي",
            "الفاتحة"
        )

        private val englishWords = arrayOf(
            "Apple", "Ball", "Cat", "Dog", "Elephant", "Fish", "Grapes", "House",
            "Ice cream", "Juice", "Kite", "Lion", "Moon", "Nest", "Orange", "Parrot",
            "Queen", "Rabbit", "Sun", "Tiger", "Umbrella", "Van", "Whale", "Xylophone",
            "Yak", "Zebra"
        )

        private val lessonHints = Array(lessons.size) { index ->
            when {
                index < 26 -> "English alphabet"
                index < lessons.size - 1 -> "Arabic letters"
                else -> "Quran learning"
            }
        }

        init {
            level = prefs.getInt("level", 1).coerceAtLeast(1)
            vehicle = prefs.getInt("vehicle", 0).coerceIn(0, LearnovaUnlimitedWorld.vehicles.lastIndex)
            worldSceneId = prefs.getInt("worldSceneId", 1).coerceAtLeast(1)
            question = prefs.getInt("question", 0).coerceIn(0, lessons.lastIndex)
            speakCurrentLesson()
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.actionMasked != MotionEvent.ACTION_UP) return true

            val now = System.currentTimeMillis()
            if (now - lastTap < 180L) return true
            lastTap = now

            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return true

            val x = event.x
            val y = event.y

            // Main child-friendly control: one tap ON, next tap OFF.
            if (y > h * 0.34f && y < h * 0.63f) {
                running = !running
                voice.speakInstruction(running)
            }

            // Tap the learning card to hear the current lesson again.
            if (y >= h * 0.63f && y <= h * 0.91f && x < w * 0.76f) {
                voice.speakLesson(lessons[question])
            }

            // Tap the vehicle name to switch vehicle.
            if (y < h * 0.18f && x > w * 0.68f) {
                vehicle = (vehicle + 1) % LearnovaUnlimitedWorld.vehicles.size
                saveProgress()
            }

            // Tap the very bottom to move to the next lesson.
            if (y > h * 0.91f) {
                nextLesson()
            }

            invalidate()
            if (running) postInvalidateOnAnimation()
            return true
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return

            if (running) {
                frame++
                speed += 0.00045f
                speed = speed.coerceAtMost(0.022f)
                distance += speed
                wheelSpin = (wheelSpin + speed * 820f) % 360f
                vehicleProgress += speed * 0.18f
                if (vehicleProgress > 1f) vehicleProgress = 0.70f
                val targetSteer = sin(frame / 70.0).toFloat() * 0.055f
                steering += (targetSteer - steering) * 0.08f
                laneOffset += (steering * 0.7f - laneOffset) * 0.045f
            } else {
                speed *= 0.84f
                steering *= 0.88f
                laneOffset *= 0.92f
            }

            val world = LearnovaUnlimitedWorld.scene(worldSceneId)

            drawSky(canvas, w, h, world)
            drawSun(canvas, w, h, world)
            drawClouds(canvas, w, h)
            drawMountains(canvas, w, h)
            drawGround(canvas, w, h)
            drawRiver(canvas, w, h)
            drawTrees(canvas, w, h)
            drawHabitatDetails(canvas, w, h, world)
            drawRoad(canvas, w, h)
            drawAnimals(canvas, w, h, world)
            drawVehicle(canvas, w, h)
            drawTopBar(canvas, w, h, world)
            drawLearningCard(canvas, w, h, world)
            drawHint(canvas, w, h)

            if (running) postInvalidateOnAnimation()
        }

        private fun drawSky(c: Canvas, w: Float, h: Float, world: SmartScene) {
            val top = when (world.time) {
                "Night" -> Color.rgb(18, 32, 74)
                "Sunset" -> Color.rgb(241, 139, 92)
                else -> if (world.weather == "Rainy") Color.rgb(105, 139, 156) else Color.rgb(77, 178, 244)
            }
            val bottom = when (world.time) {
                "Night" -> Color.rgb(64, 78, 126)
                "Sunset" -> Color.rgb(255, 214, 156)
                else -> if (world.weather == "Rainy") Color.rgb(194, 215, 220) else Color.rgb(218, 246, 255)
            }
            paint.shader = LinearGradient(0f, 0f, 0f, h * 0.68f, top, bottom, Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w, h, paint)
            paint.shader = null

            if (world.time == "Night") {
                paint.color = Color.argb(210, 255, 255, 220)
                for (i in 0..28) {
                    val x = ((i * 83 + world.id * 17) % 1000) / 1000f * w
                    val y = ((i * 47 + world.id * 11) % 420) / 420f * h * .48f
                    c.drawCircle(x, y, if (i % 5 == 0) 2.2f else 1.2f, paint)
                }
            }

            if (world.weather == "Rainy") {
                paint.color = Color.argb(85, 235, 250, 255)
                paint.strokeWidth = 2f
                for (i in 0..22) {
                    val x = ((i * 91 + frame * 2L) % 1100).toFloat() / 1000f * w
                    val y = ((i * 53 + frame * 4L) % 500).toFloat() / 500f * h * .65f
                    c.drawLine(x, y, x - 7f, y + 18f, paint)
                }
            }
        }

        private fun drawSun(c: Canvas, w: Float, h: Float, world: SmartScene) {
            if (world.time == "Night") return
            val x = if (world.time == "Sunset") w * 0.72f else w * 0.83f
            val y = if (world.time == "Sunset") h * 0.30f else h * 0.15f
            val radius = minOf(w, h) * 0.065f
            paint.color = if (world.time == "Sunset") Color.rgb(255, 176, 92) else Color.rgb(255, 221, 100)
            c.drawCircle(x, y, radius, paint)
            paint.color = Color.argb(45, 255, 244, 180)
            c.drawCircle(x, y, radius * 1.62f, paint)
        }

        private fun drawClouds(c: Canvas, w: Float, h: Float) {
            val shift = if (running) (frame % 900L).toFloat() else 0f
            drawCloud(c, (w * 0.18f + shift * 0.06f) % (w + 180f) - 90f, h * 0.19f, 0.8f)
            drawCloud(c, (w * 0.58f + shift * 0.04f) % (w + 220f) - 110f, h * 0.27f, 0.62f)
        }

        private fun drawCloud(c: Canvas, x: Float, y: Float, s: Float) {
            paint.color = Color.argb(205, 255, 255, 255)
            c.drawCircle(x, y, 26f * s, paint)
            c.drawCircle(x + 28f * s, y - 10f * s, 34f * s, paint)
            c.drawCircle(x + 62f * s, y, 25f * s, paint)
            c.drawRoundRect(
                RectF(x - 5f * s, y, x + 70f * s, y + 25f * s),
                12f * s, 12f * s, paint
            )
        }

        private fun drawMountains(c: Canvas, w: Float, h: Float) {
            val back = Path()
            back.moveTo(0f, h * 0.58f)
            back.lineTo(w * 0.17f, h * 0.29f)
            back.lineTo(w * 0.32f, h * 0.48f)
            back.lineTo(w * 0.50f, h * 0.22f)
            back.lineTo(w * 0.69f, h * 0.49f)
            back.lineTo(w * 0.84f, h * 0.30f)
            back.lineTo(w, h * 0.50f)
            back.lineTo(w, h * 0.68f)
            back.lineTo(0f, h * 0.68f)
            back.close()
            paint.color = Color.rgb(101, 157, 137)
            c.drawPath(back, paint)

            val front = Path()
            front.moveTo(0f, h * 0.65f)
            front.lineTo(w * 0.21f, h * 0.40f)
            front.lineTo(w * 0.39f, h * 0.59f)
            front.lineTo(w * 0.60f, h * 0.37f)
            front.lineTo(w * 0.77f, h * 0.60f)
            front.lineTo(w, h * 0.42f)
            front.lineTo(w, h * 0.72f)
            front.lineTo(0f, h * 0.72f)
            front.close()
            paint.color = Color.rgb(60, 125, 91)
            c.drawPath(front, paint)
        }

        private fun drawRiver(c: Canvas, w: Float, h: Float) {
            val river = Path()
            river.moveTo(0f, h * 0.80f)
            river.cubicTo(w * 0.20f, h * 0.69f, w * 0.30f, h * 0.60f, w * 0.46f, h * 0.58f)
            river.cubicTo(w * 0.64f, h * 0.55f, w * 0.78f, h * 0.70f, w, h * 0.76f)
            river.lineTo(w, h)
            river.lineTo(0f, h)
            river.close()
            paint.color = Color.rgb(53, 164, 219)
            c.drawPath(river, paint)

            paint.color = Color.argb(135, 220, 250, 255)
            paint.strokeWidth = 4f
            for (i in 0..5) {
                val y = h * 0.79f + i * 24f
                c.drawLine(w * 0.67f, y, w * 0.92f, y + 3f, paint)
            }
        }

        private fun drawGround(c: Canvas, w: Float, h: Float) {
            paint.color = Color.rgb(89, 165, 75)
            c.drawRect(0f, h * 0.64f, w, h, paint)
            paint.color = Color.argb(45, 255, 255, 255)
            for (i in 0..11) {
                val x = (i * w / 11f) + if (running) ((frame % 60L).toFloat()) else 0f
                c.drawCircle(x % w, h * 0.68f + (i % 3) * 11f, 3f, paint)
            }
        }

        private fun drawTrees(c: Canvas, w: Float, h: Float) {
            val xs = floatArrayOf(.05f, .16f, .29f, .71f, .84f, .95f)
            for (i in xs.indices) {
                val scale = 0.65f + (i % 3) * .12f
                drawTree(c, w * xs[i], h * (.66f + (i % 2) * .025f), scale)
            }
        }

        private fun drawTree(c: Canvas, x: Float, y: Float, s: Float) {
            paint.color = Color.rgb(104, 70, 40)
            c.drawRoundRect(RectF(x - 10f*s, y - 78f*s, x + 10f*s, y), 7f, 7f, paint)
            paint.color = Color.rgb(32, 120, 58)
            c.drawCircle(x, y - 105f*s, 38f*s, paint)
            c.drawCircle(x - 27f*s, y - 87f*s, 28f*s, paint)
            c.drawCircle(x + 27f*s, y - 87f*s, 28f*s, paint)
            paint.color = Color.rgb(67, 153, 70)
            c.drawCircle(x - 10f*s, y - 120f*s, 20f*s, paint)
        }

        private fun drawHabitatDetails(c: Canvas, w: Float, h: Float, world: SmartScene) {
            val groundY = h * 0.64f
            val phase = if (running) frame.toFloat() * 0.03f else 0f

            when (world.region) {
                "Farm" -> {
                    paint.color = Color.rgb(181, 137, 70)
                    for (i in 0..5) {
                        val x = w * (0.08f + i * 0.17f)
                        c.drawRoundRect(RectF(x, groundY + 22f, x + 5f, h * 0.86f), 2f, 2f, paint)
                        paint.color = Color.rgb(86, 133, 52)
                        c.drawCircle(x + 2.5f, groundY + 15f, 11f, paint)
                        paint.color = Color.rgb(181, 137, 70)
                    }
                    paint.color = Color.rgb(232, 198, 116)
                    c.drawRect(w * 0.05f, groundY + 58f, w * 0.34f, groundY + 63f, paint)
                    c.drawRect(w * 0.05f, groundY + 83f, w * 0.34f, groundY + 88f, paint)
                }
                "Village", "City" -> {
                    val heights = intArrayOf(42, 68, 52, 86, 58)
                    for (i in heights.indices) {
                        val x = w * (0.06f + i * 0.20f)
                        val bh = heights[i].toFloat()
                        paint.color = if (world.region == "City") Color.rgb(92, 108, 118) else Color.rgb(191, 155, 104)
                        c.drawRoundRect(RectF(x, groundY - bh, x + w * 0.12f, groundY + 8f), 5f, 5f, paint)
                        paint.color = Color.rgb(244, 214, 116)
                        for (row in 0..1) {
                            c.drawRect(x + 10f, groundY - bh + 12f + row * 20f, x + 22f, groundY - bh + 22f + row * 20f, paint)
                        }
                    }
                    paint.color = Color.rgb(72, 78, 76)
                    c.drawRect(w * 0.03f, groundY + 5f, w * 0.97f, groundY + 12f, paint)
                }
                "Desert" -> {
                    paint.color = Color.rgb(220, 181, 91)
                    for (i in 0..4) {
                        val x = w * (0.10f + i * 0.20f)
                        val y = groundY + 28f + sin(i.toDouble() + phase * 0.15).toFloat() * 5f
                        c.drawOval(RectF(x - 38f, y - 10f, x + 42f, y + 10f), paint)
                    }
                    paint.color = Color.rgb(54, 125, 68)
                    for (i in 0..2) {
                        val x = w * (0.16f + i * 0.34f)
                        c.drawRect(x - 5f, groundY - 5f, x + 5f, groundY + 38f, paint)
                        c.drawCircle(x, groundY - 8f, 17f, paint)
                    }
                }
                "Arctic" -> {
                    paint.color = Color.rgb(238, 248, 252)
                    c.drawRect(0f, groundY, w, h, paint)
                    paint.color = Color.rgb(177, 215, 230)
                    for (i in 0..5) {
                        val x = w * (0.06f + i * 0.18f)
                        c.drawCircle(x, groundY + 16f, 24f, paint)
                        c.drawCircle(x + 22f, groundY + 22f, 18f, paint)
                    }
                }
                "Ocean", "Island", "Wetland" -> {
                    paint.color = Color.argb(105, 255, 255, 255)
                    for (i in 0..4) {
                        val x = (w * (0.12f + i * 0.22f) + phase * 7f) % (w + 80f) - 40f
                        val y = groundY + 18f + (i % 2) * 28f
                        c.drawOval(RectF(x, y, x + 62f, y + 8f), paint)
                    }
                    if (world.region == "Wetland") {
                        paint.color = Color.rgb(71, 132, 67)
                        for (i in 0..6) {
                            val x = w * (0.05f + i * 0.15f)
                            c.drawLine(x, groundY + 20f, x + 4f, groundY - 8f, paint)
                        }
                    }
                }
                "Dinosaur Valley" -> {
                    paint.color = Color.rgb(117, 91, 58)
                    for (i in 0..4) {
                        val x = w * (0.08f + i * 0.21f)
                        c.drawOval(RectF(x - 24f, groundY + 28f, x + 30f, groundY + 40f), paint)
                    }
                    paint.color = Color.rgb(77, 126, 64)
                    for (i in 0..3) {
                        val x = w * (0.12f + i * 0.25f)
                        c.drawLine(x, groundY + 18f, x + 8f, groundY - 5f, paint)
                    }
                }
                "Cave" -> {
                    paint.color = Color.rgb(68, 72, 78)
                    c.drawPath(Path().apply {
                        moveTo(0f, groundY + 8f)
                        lineTo(w * 0.20f, groundY - 35f)
                        lineTo(w * 0.34f, groundY + 8f)
                        close()
                    }, paint)
                    c.drawPath(Path().apply {
                        moveTo(w, groundY + 8f)
                        lineTo(w * 0.80f, groundY - 35f)
                        lineTo(w * 0.66f, groundY + 8f)
                        close()
                    }, paint)
                }
                "Science Park", "Garden", "Quran Learning Garden", "Arabic Learning Garden", "Kindness Village" -> {
                    paint.color = Color.rgb(218, 178, 78)
                    for (i in 0..5) {
                        val x = w * (0.08f + i * 0.17f)
                        c.drawCircle(x, groundY + 22f, 5f, paint)
                        paint.color = Color.rgb(67, 145, 76)
                        c.drawRect(x - 2f, groundY + 25f, x + 2f, groundY + 43f, paint)
                        paint.color = Color.rgb(218, 178, 78)
                    }
                }
                "Sky", "Space" -> {
                    paint.color = Color.argb(110, 255, 255, 255)
                    for (i in 0..4) {
                        val x = (w * (0.12f + i * 0.21f) + phase * 4f) % (w + 120f) - 60f
                        c.drawOval(RectF(x, h * 0.52f + (i % 2) * 22f, x + 70f, h * 0.55f + (i % 2) * 22f), paint)
                    }
                }
            }
        }

        private fun drawRoad(c: Canvas, w: Float, h: Float) {
            val horizonY = h * .61f
            val bottomY = h
            val bend = sin(frame / 115.0).toFloat() * w * 0.055f
            val road = Path()
            road.moveTo(w * .43f + bend * .10f, horizonY)
            road.cubicTo(w * .46f + bend * .28f, h * .72f, w * .57f + bend * .62f, h * .88f, w * .96f + bend, bottomY)
            road.lineTo(w * .04f + bend, bottomY)
            road.cubicTo(w * .43f - bend * .62f, h * .88f, w * .46f - bend * .28f, h * .72f, w * .57f + bend * .10f, horizonY)
            road.close()

            paint.color = Color.rgb(49, 52, 56)
            c.drawPath(road, paint)
            paint.color = Color.WHITE
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 4f
            c.drawPath(road, paint)
            paint.style = Paint.Style.FILL

            val travel = if (running) (frame * 7L % 120L).toFloat() else 0f
            var y = horizonY + 10f + travel
            while (y < bottomY) {
                val t = ((y - horizonY) / (bottomY - horizonY)).coerceIn(0f, 1f)
                val half = 3f + 30f * t
                val center = w / 2f + bend * t + laneOffset * w * t
                paint.color = Color.WHITE
                c.drawRoundRect(RectF(center - half, y, center + half, y + 7f + 20f * t), 4f, 4f, paint)
                y += 62f + 110f * t
            }

            for (side in -1..1 step 2) {
                for (i in 0..5) {
                    val t = (i + 1) / 7f
                    val y = horizonY + (bottomY - horizonY) * t
                    val edgeX = w / 2f + side * (w * (.08f + .42f * t)) + bend * t
                    paint.color = Color.rgb(255, 220, 90)
                    c.drawCircle(edgeX, y, 2.5f + 4f * t, paint)
                }
            }
        }

        private fun drawAnimals(c: Canvas, w: Float, h: Float, world: SmartScene) {
            val base = h * .61f
            val travel = if (running) frame.toFloat() * 0.85f else 0f
            val primaryPhase = sin(frame / 7.0).toFloat()
            val secondaryPhase = sin(frame / 9.5 + 1.4).toFloat()
            val drift = if (running) sin(frame / 24.0).toFloat() * w * .018f else 0f

            when (world.region) {
                "Dinosaur Valley" -> {
                    drawAnimatedAnimal(c, "dinosaur", w * .78f + drift, base + primaryPhase * 3f, travel)
                    drawAnimatedAnimal(c, if (world.id % 2 == 0) "dinosaur" else "bird",
                        w * .18f - drift, base - 28f + secondaryPhase * 2f, -travel * .7f)
                }
                "Forest" -> {
                    val a = when (world.id % 4) { 0 -> "bear"; 1 -> "deer"; 2 -> "fox"; else -> "bird" }
                    val b = when (world.id % 3) { 0 -> "bird"; 1 -> "deer"; else -> "fox" }
                    drawAnimatedAnimal(c, a, w * .16f + drift, base + primaryPhase * 2f, travel)
                    drawAnimatedAnimal(c, b, w * .76f - drift, base - 20f + secondaryPhase * 2f, -travel * .65f)
                }
                "Safari" -> {
                    val a = when (world.id % 4) { 0 -> "elephant"; 1 -> "giraffe"; 2 -> "zebra"; else -> "lion" }
                    val b = when (world.id % 3) { 0 -> "zebra"; 1 -> "lion"; else -> "giraffe" }
                    drawAnimatedAnimal(c, a, w * .78f + drift, base + primaryPhase * 2.5f, travel)
                    drawAnimatedAnimal(c, b, w * .20f - drift, base - 12f + secondaryPhase * 2f, -travel * .55f)
                }
                "Ocean", "Island", "Wetland" -> {
                    val a = when (world.id % 4) { 0 -> "dolphin"; 1 -> "fish"; 2 -> "crocodile"; else -> "bird" }
                    val b = if (world.id % 2 == 0) "fish" else "bird"
                    drawAnimatedAnimal(c, a, w * .78f + drift, base - 5f + primaryPhase * 5f, travel)
                    drawAnimatedAnimal(c, b, w * .28f - drift, base - 42f + secondaryPhase * 4f, -travel * .8f)
                }
                "Arctic" -> {
                    drawAnimatedAnimal(c, "penguin", w * .76f + drift, base + primaryPhase * 2f, travel)
                    drawAnimatedAnimal(c, "penguin", w * .30f - drift, base - 4f + secondaryPhase * 2f, -travel * .6f)
                }
                else -> {
                    val a = if (world.id % 2 == 0) "elephant" else "bear"
                    val b = if (world.id % 3 == 0) "bird" else "fox"
                    drawAnimatedAnimal(c, a, w * .80f + drift, base + primaryPhase * 2f, travel)
                    drawAnimatedAnimal(c, b, w * .18f - drift, base - 18f + secondaryPhase * 2f, -travel * .55f)
                }
            }
        }

        private fun drawAnimatedAnimal(
            c: Canvas,
            kind: String,
            x: Float,
            y: Float,
            travel: Float
        ) {
            val wrappedX = ((x + travel) % (wSafe(c) + 180f)) - 90f
            val hop = if (running) sin((frame / 5.5) + x * 0.01).toFloat() * 2.5f else 0f
            val bobbedY = y + hop
            when (kind) {
                "elephant" -> drawElephant(c, wrappedX, bobbedY)
                "bear" -> drawBear(c, wrappedX, bobbedY)
                "deer" -> drawDeer(c, wrappedX, bobbedY)
                "fox" -> drawFox(c, wrappedX, bobbedY)
                "bird" -> drawBird(c, wrappedX, bobbedY - if (running) abs(sin(frame / 6.0).toFloat()) * 18f else 0f)
                "giraffe" -> drawGiraffe(c, wrappedX, bobbedY)
                "zebra" -> drawZebra(c, wrappedX, bobbedY)
                "lion" -> drawLion(c, wrappedX, bobbedY)
                "fish" -> drawFish(c, wrappedX, bobbedY)
                "crocodile" -> drawCrocodile(c, wrappedX, bobbedY)
                "penguin" -> drawPenguin(c, wrappedX, bobbedY)
                "dinosaur" -> drawDinosaur(c, wrappedX, bobbedY)
                "dolphin" -> drawDolphin(c, wrappedX, bobbedY)
            }
        }

        private fun wSafe(c: Canvas): Float = c.width.toFloat()

        private fun drawElephant(c: Canvas, x: Float, y: Float) {
            paint.color = Color.rgb(120, 128, 126)
            c.drawOval(RectF(x-42f,y-70f,x+42f,y-12f), paint)
            c.drawCircle(x+45f, y-60f, 28f, paint)
            paint.color = Color.rgb(145, 151, 148)
            c.drawCircle(x+57f,y-78f,14f,paint)
            c.drawRect(x+55f,y-48f,x+69f,y-8f,paint)
            paint.color = Color.DKGRAY
            c.drawCircle(x+55f,y-66f,3f,paint)
            c.drawRect(x-27f,y-15f,x-17f,y+9f,paint)
            c.drawRect(x+15f,y-15f,x+25f,y+9f,paint)
        }

        private fun drawBear(c: Canvas, x: Float, y: Float) {
            paint.color = Color.rgb(120, 77, 48)
            c.drawCircle(x, y-50f, 34f, paint)
            c.drawCircle(x-25f,y-77f,13f,paint)
            c.drawCircle(x+25f,y-77f,13f,paint)
            c.drawCircle(x,y-41f,15f,paint)
            paint.color = Color.BLACK
            c.drawCircle(x-11f,y-55f,3f,paint)
            c.drawCircle(x+11f,y-55f,3f,paint)
            c.drawCircle(x,y-42f,3f,paint)
        }

        private fun drawDeer(c: Canvas, x: Float, y: Float) {
            paint.color=Color.rgb(145,95,55)
            c.drawOval(RectF(x-48f,y-45f,x+42f,y-5f),paint)
            c.drawCircle(x+48f,y-48f,22f,paint)
            paint.strokeWidth=4f
            c.drawLine(x+52f,y-66f,x+43f,y-92f,paint)
            c.drawLine(x+58f,y-66f,x+68f,y-90f,paint)
            c.drawRect(x-25f,y-8f,x-17f,y+18f,paint); c.drawRect(x+18f,y-8f,x+26f,y+18f,paint)
        }

        private fun drawFox(c: Canvas, x: Float, y: Float) {
            paint.color=Color.rgb(205,105,45)
            c.drawOval(RectF(x-45f,y-42f,x+38f,y-5f),paint)
            val head=Path(); head.moveTo(x+25f,y-48f); head.lineTo(x+52f,y-78f); head.lineTo(x+72f,y-45f); head.lineTo(x+45f,y-25f); head.close(); c.drawPath(head,paint)
            paint.color=Color.WHITE; c.drawOval(RectF(x+43f,y-50f,x+69f,y-32f),paint)
            paint.color=Color.rgb(75,50,35); c.drawRect(x-20f,y-8f,x-13f,y+16f,paint); c.drawRect(x+15f,y-8f,x+22f,y+16f,paint)
        }

        private fun drawBird(c: Canvas, x: Float, y: Float) {
            paint.color=Color.rgb(55,105,175)
            c.drawOval(RectF(x-25f,y-28f,x+25f,y+5f),paint)
            val wing=Path(); wing.moveTo(x,y-5f); wing.quadTo(x-28f,y-45f,x-42f,y-20f); wing.quadTo(x-20f,y-5f,x,y-5f); wing.close(); c.drawPath(wing,paint)
            paint.color=Color.rgb(235,180,55); c.drawCircle(x+28f,y-12f,10f,paint)
        }

        private fun drawGiraffe(c: Canvas, x: Float, y: Float) {
            paint.color=Color.rgb(235,190,70)
            c.drawOval(RectF(x-40f,y-65f,x+45f,y-18f),paint)
            c.drawRoundRect(RectF(x+18f,y-120f,x+35f,y-48f),8f,8f,paint)
            c.drawCircle(x+28f,y-125f,18f,paint)
            paint.color=Color.rgb(120,80,35)
            for(i in 0..5) c.drawCircle(x-25f+i*12f,y-45f+(i%2)*12f,5f,paint)
            c.drawRect(x-20f,y-20f,x-13f,y+15f,paint); c.drawRect(x+18f,y-20f,x+25f,y+15f,paint)
        }

        private fun drawZebra(c: Canvas, x: Float, y: Float) {
            paint.color=Color.WHITE
            c.drawOval(RectF(x-48f,y-55f,x+45f,y-12f),paint)
            c.drawCircle(x+50f,y-48f,21f,paint)
            paint.color=Color.DKGRAY; paint.strokeWidth=4f
            for(i in -2..2) c.drawLine(x-20f+i*15f,y-52f,x-30f+i*15f,y-18f,paint)
            c.drawRect(x-25f,y-15f,x-18f,y+12f,paint); c.drawRect(x+18f,y-15f,x+25f,y+12f,paint)
        }

        private fun drawLion(c: Canvas, x: Float, y: Float) {
            paint.color=Color.rgb(165,115,55); c.drawCircle(x,y-48f,31f,paint)
            paint.color=Color.rgb(120,75,35); c.drawCircle(x,y-48f,43f,paint)
            paint.color=Color.rgb(215,165,85); c.drawCircle(x,y-48f,25f,paint)
            paint.color=Color.BLACK; c.drawCircle(x-9f,y-53f,3f,paint); c.drawCircle(x+9f,y-53f,3f,paint)
        }

        private fun drawFish(c: Canvas, x: Float, y: Float) {
            paint.color=Color.rgb(55,145,205); c.drawOval(RectF(x-48f,y-25f,x+45f,y+20f),paint)
            val tail=Path(); tail.moveTo(x-45f,y); tail.lineTo(x-78f,y-24f); tail.lineTo(x-78f,y+24f); tail.close(); c.drawPath(tail,paint)
            paint.color=Color.WHITE; c.drawCircle(x+25f,y-8f,5f,paint); paint.color=Color.BLACK; c.drawCircle(x+26f,y-8f,2f,paint)
        }

        private fun drawCrocodile(c: Canvas, x: Float, y: Float) {
            paint.color=Color.rgb(55,125,70); c.drawOval(RectF(x-85f,y-28f,x+75f,y+12f),paint)
            val snout=Path(); snout.moveTo(x+35f,y-20f); snout.lineTo(x+100f,y-10f); snout.lineTo(x+45f,y+4f); snout.close(); c.drawPath(snout,paint)
            paint.color=Color.WHITE; c.drawCircle(x+55f,y-22f,6f,paint); paint.color=Color.BLACK; c.drawCircle(x+56f,y-23f,2f,paint)
        }

        private fun drawPenguin(c: Canvas, x: Float, y: Float) {
            paint.color=Color.rgb(35,45,55); c.drawOval(RectF(x-30f,y-80f,x+30f,y+8f),paint)
            paint.color=Color.WHITE; c.drawOval(RectF(x-18f,y-55f,x+18f,y-5f),paint)
            paint.color=Color.rgb(240,170,45); c.drawCircle(x,y-78f,18f,paint); c.drawOval(RectF(x-18f,y-5f,x-2f,y+8f),paint); c.drawOval(RectF(x+2f,y-5f,x+18f,y+8f),paint)
        }

        private fun drawDinosaur(c: Canvas, x: Float, y: Float) {
            paint.color = Color.rgb(54, 143, 76)

            val body = Path()
            body.moveTo(x-48f,y-25f)
            body.cubicTo(x-20f,y-65f,x+35f,y-62f,x+52f,y-22f)
            body.lineTo(x+22f,y-7f)
            body.lineTo(x-48f,y-25f)
            body.close()
            c.drawPath(body,paint)

            val neck = Path()
            neck.moveTo(x+25f,y-35f)
            neck.lineTo(x+55f,y-92f)
            neck.lineTo(x+75f,y-92f)
            neck.lineTo(x+42f,y-25f)
            neck.close()
            c.drawPath(neck,paint)

            c.drawCircle(x+84f,y-94f,20f,paint)
            paint.color = Color.BLACK
            c.drawCircle(x+90f,y-99f,3f,paint)
            paint.color = Color.rgb(39,105,58)
            c.drawRect(x-28f,y-15f,x-18f,y+15f,paint)
            c.drawRect(x+20f,y-15f,x+30f,y+15f,paint)
        }

        private fun drawVehicle(c: Canvas, w: Float, h: Float) {
            val selected = LearnovaUnlimitedWorld.vehicles[vehicle]
            val p = vehicleProgress.coerceIn(0f, 1f)
            val cx = w / 2f + laneOffset * w * (0.45f + 0.55f * p)
            val roadBend = sin(frame / 115.0).toFloat() * w * 0.055f
            val cy = h * (0.64f + 0.24f * p) + roadBend * p * 0.10f +
                if (running) sin(frame / 4.0).toFloat() * (1.2f + 3.5f * p) else 0f
            val scale = 0.45f + 0.75f * p

            c.save()
            c.rotate(steering * 7f, cx, cy)
            c.scale(scale, scale, cx, cy)
            when (selected.kind) {
                "car" -> drawCar(c, cx, cy)
                "bus" -> drawBus(c, cx, cy)
                "truck" -> drawTruck(c, cx, cy)
                "van" -> drawVan(c, cx, cy)
                "bike" -> drawBike(c, cx, cy)
                "sportBike" -> drawSportBike(c, cx, cy)
                "bicycle" -> drawBicycle(c, cx, cy)
                "air" -> drawPlane(c, cx, cy)
                "boat" -> drawBoat(c, cx, cy)
                "space" -> drawRocket(c, cx, cy)
                "micro" -> drawMicro(c, cx, cy)
                else -> drawCar(c, cx, cy)
            }
            c.restore()
        }

        private fun drawCar(c: Canvas, x: Float, y: Float) {
            paint.color = Color.argb(90,0,0,0)
            c.drawOval(RectF(x-115f,y+30f,x+115f,y+55f),paint)

            paint.color = Color.rgb(15,137,76)
            c.drawRoundRect(RectF(x-110f,y-40f,x+110f,y+35f),24f,24f,paint)

            val roof = Path()
            roof.moveTo(x-67f,y-40f)
            roof.lineTo(x-38f,y-80f)
            roof.lineTo(x+45f,y-80f)
            roof.lineTo(x+70f,y-40f)
            roof.close()
            paint.color = Color.rgb(10,103,58)
            c.drawPath(roof,paint)

            paint.color = Color.rgb(177,225,240)
            c.drawRoundRect(RectF(x-36f,y-69f,x+3f,y-43f),7f,7f,paint)
            c.drawRoundRect(RectF(x+8f,y-69f,x+41f,y-43f),7f,7f,paint)

            paint.color = Color.rgb(255,239,130)
            c.drawCircle(x-98f,y-5f,8f,paint)
            c.drawCircle(x+98f,y-5f,8f,paint)

            drawWheel(c,x-68f,y+34f, wheelSpin)
            drawWheel(c,x+68f,y+34f, wheelSpin)
        }

        private fun drawWheel(c: Canvas, x: Float, y: Float, angle: Float) {
            paint.color = Color.rgb(25,25,25)
            c.drawCircle(x,y,23f,paint)
            paint.color = Color.rgb(175,175,175)
            c.drawCircle(x,y,9f,paint)
            paint.color = Color.rgb(70,70,70)
            paint.strokeWidth = 2.5f
            c.save()
            c.rotate(angle, x, y)
            for (i in 0..3) {
                val a = i * 90f
                val dx = cos(Math.toRadians(a.toDouble())).toFloat() * 8f
                val dy = sin(Math.toRadians(a.toDouble())).toFloat() * 8f
                c.drawLine(x, y, x + dx, y + dy, paint)
            }
            c.restore()
        }

        private fun drawBus(c: Canvas, x: Float, y: Float) {
            paint.color = Color.rgb(235, 178, 45)
            c.drawRoundRect(RectF(x-125f,y-58f,x+125f,y+38f),22f,22f,paint)
            paint.color = Color.rgb(180,225,238)
            c.drawRoundRect(RectF(x-88f,y-44f,x+88f,y-10f),10f,10f,paint)
            paint.color = Color.rgb(30,45,50)
            c.drawRoundRect(RectF(x-105f,y-8f,x+105f,y+22f),7f,7f,paint)
            drawWheel(c,x-78f,y+36f,wheelSpin)
            drawWheel(c,x+78f,y+36f,wheelSpin)
        }

        private fun drawTruck(c: Canvas, x: Float, y: Float) {
            paint.color = Color.rgb(205, 70, 52)
            c.drawRoundRect(RectF(x-120f,y-40f,x+15f,y+38f),12f,12f,paint)
            paint.color = Color.rgb(80,105,115)
            c.drawRoundRect(RectF(x+5f,y-65f,x+108f,y+38f),15f,15f,paint)
            paint.color = Color.rgb(190,225,238)
            c.drawRoundRect(RectF(x+22f,y-52f,x+91f,y-20f),8f,8f,paint)
            drawWheel(c,x-72f,y+37f,wheelSpin)
            drawWheel(c,x+72f,y+37f,wheelSpin)
        }

        private fun drawVan(c: Canvas, x: Float, y: Float) {
            paint.color = Color.rgb(70,105,145)
            c.drawRoundRect(RectF(x-115f,y-58f,x+112f,y+38f),20f,20f,paint)
            paint.color = Color.rgb(185,225,238)
            c.drawRoundRect(RectF(x-78f,y-43f,x+65f,y-10f),10f,10f,paint)
            paint.color = Color.rgb(245,245,245)
            c.drawRect(x-65f,y+2f,x+72f,y+25f,paint)
            drawWheel(c,x-70f,y+36f,wheelSpin)
            drawWheel(c,x+70f,y+36f,wheelSpin)
        }

        private fun drawBike(c: Canvas, x: Float, y: Float) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 7f
            paint.color = Color.rgb(28,45,48)

            drawWheel(c,x-52f,y+18f,wheelSpin)
            drawWheel(c,x+52f,y+18f,wheelSpin)
            paint.color = Color.rgb(28,45,48)
            c.drawLine(x-52f,y+18f,x-10f,y-12f,paint)
            c.drawLine(x-10f,y-12f,x+52f,y+18f,paint)
            c.drawLine(x-10f,y-12f,x+10f,y+18f,paint)
            c.drawLine(x+10f,y+18f,x-52f,y+18f,paint)

            paint.style = Paint.Style.FILL
            paint.color = Color.rgb(20,145,82)
            c.drawCircle(x,y-35f,14f,paint)
            c.drawRect(x-9f,y-22f,x+9f,y+12f,paint)
        }

        private fun drawSportBike(c: Canvas, x: Float, y: Float) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 6f
            paint.color = Color.rgb(28,32,36)
            drawWheel(c,x-48f,y+25f,wheelSpin)
            drawWheel(c,x+48f,y+25f,wheelSpin)
            c.drawLine(x-48f,y+25f,x-8f,y-5f,paint)
            c.drawLine(x-8f,y-5f,x+48f,y+25f,paint)
            c.drawLine(x-8f,y-5f,x+5f,y+25f,paint)
            paint.style = Paint.Style.FILL
            paint.color = Color.rgb(215,45,45)
            val fairing = Path()
            fairing.moveTo(x-22f,y-8f); fairing.lineTo(x+12f,y-34f); fairing.lineTo(x+58f,y-8f)
            fairing.lineTo(x+20f,y+17f); fairing.lineTo(x-25f,y+12f); fairing.close()
            c.drawPath(fairing,paint)
            paint.color = Color.rgb(35,35,40)
            c.drawRoundRect(RectF(x-3f,y-42f,x+24f,y-25f),8f,8f,paint)
        }

        private fun drawBicycle(c: Canvas, x: Float, y: Float) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 5f
            paint.color = Color.rgb(35,75,55)
            drawWheel(c,x-55f,y+24f,wheelSpin)
            drawWheel(c,x+55f,y+24f,wheelSpin)
            c.drawLine(x-55f,y+24f,x-8f,y-8f,paint)
            c.drawLine(x-8f,y-8f,x+55f,y+24f,paint)
            c.drawLine(x-8f,y-8f,x+8f,y+24f,paint)
            c.drawLine(x-8f,y-8f,x-28f,y+24f,paint)
            c.drawLine(x+55f,y+24f,x+48f,y-10f,paint)
            c.drawLine(x+48f,y-10f,x+62f,y-13f,paint)
            c.drawLine(x-28f,y+24f,x-5f,y+27f,paint)
            paint.style = Paint.Style.FILL
            paint.color = Color.rgb(40,125,78)
            c.drawCircle(x+2f,y-30f,12f,paint)
            c.drawRoundRect(RectF(x-7f,y-18f,x+10f,y+8f),7f,7f,paint)
        }

        private fun drawPlane(c: Canvas, x: Float, y: Float) {
            val bob = if (running) sin(frame/12.0).toFloat()*7f else 0f

            paint.color = Color.argb(70,0,0,0)
            c.drawOval(RectF(x-90f,y+55f,x+90f,y+75f),paint)

            paint.color = Color.rgb(235,239,242)
            c.drawOval(RectF(x-95f,y-18f+bob,x+95f,y+18f+bob),paint)

            val wing = Path()
            wing.moveTo(x-5f,y+bob)
            wing.lineTo(x-65f,y+55f+bob)
            wing.lineTo(x-20f,y+45f+bob)
            wing.lineTo(x+20f,y+bob)
            wing.close()
            c.drawPath(wing,paint)

            paint.color = Color.rgb(20,135,78)
            c.drawRect(x+38f,y-13f+bob,x+65f,y+13f+bob,paint)
            c.drawCircle(x+68f,y+bob,6f,paint)
        }

        private fun drawDolphin(c: Canvas, x: Float, y: Float) {
            paint.color = Color.rgb(105, 165, 190)
            val body = Path()
            body.moveTo(x - 55f, y - 10f)
            body.cubicTo(x - 20f, y - 45f, x + 42f, y - 45f, x + 60f, y - 5f)
            body.cubicTo(x + 25f, y + 18f, x - 30f, y + 20f, x - 55f, y - 10f)
            body.close()
            c.drawPath(body, paint)
            paint.color = Color.DKGRAY
            c.drawCircle(x + 45f, y - 15f, 3f, paint)
        }

        private fun drawBoat(c: Canvas, x: Float, y: Float) {
            paint.color = Color.rgb(30,95,130)
            val hull = Path()
            hull.moveTo(x-85f,y-5f)
            hull.lineTo(x+85f,y-5f)
            hull.lineTo(x+55f,y+35f)
            hull.lineTo(x-55f,y+35f)
            hull.close()
            c.drawPath(hull,paint)
            paint.color = Color.WHITE
            val sail = Path()
            sail.moveTo(x,y-95f)
            sail.lineTo(x,y-10f)
            sail.lineTo(x+55f,y-10f)
            sail.close()
            c.drawPath(sail,paint)
        }

        private fun drawRocket(c: Canvas, x: Float, y: Float) {
            val bob = if (running) sin(frame/8.0).toFloat()*8f else 0f
            paint.color = Color.rgb(225,230,235)
            c.drawOval(RectF(x-30f,y-105f+bob,x+30f,y+45f+bob),paint)
            paint.color = Color.rgb(20,130,80)
            c.drawCircle(x,y-70f+bob,13f,paint)
            val flame = Path()
            flame.moveTo(x-15f,y+40f+bob)
            flame.lineTo(x,y+85f+bob)
            flame.lineTo(x+15f,y+40f+bob)
            flame.close()
            paint.color = Color.rgb(255,180,50)
            c.drawPath(flame,paint)
        }

        private fun drawMicro(c: Canvas, x: Float, y: Float) {
            paint.color = Color.argb(80,0,0,0)
            c.drawOval(RectF(x-75f,y+30f,x+75f,y+52f),paint)

            paint.color = Color.rgb(35,125,190)
            c.drawRoundRect(RectF(x-75f,y-35f,x+75f,y+32f),30f,30f,paint)

            paint.color = Color.rgb(185,230,245)
            c.drawRoundRect(RectF(x-35f,y-27f,x+35f,y+3f),13f,13f,paint)

            drawWheel(c,x-48f,y+30f, wheelSpin)
            drawWheel(c,x+48f,y+30f, wheelSpin)
        }

        private fun drawTopBar(c: Canvas, w: Float, h: Float, world: SmartScene) {
            paint.color = Color.argb(190,20,65,55)
            c.drawRoundRect(RectF(14f,14f,w-14f,70f),22f,22f,paint)

            text.textAlign = Paint.Align.LEFT
            text.color = Color.WHITE
            text.textSize = 20f
            c.drawText("LEARNOVA",30f,48f,text)

            text.textAlign = Paint.Align.CENTER
            text.textSize = 13f
            c.drawText("LEVEL " + level,w*.53f,37f,text)
            c.drawText(if (running) "● ON" else "● OFF",w*.53f,56f,text)

            text.textSize = 11f
            c.drawText("WORLD " + world.id,w*.70f,35f,text)
            c.drawText(world.region,w*.70f,54f,text)
            text.textSize = 11f
            c.drawText("VEHICLE",w*.88f,35f,text)
            c.drawText(LearnovaUnlimitedWorld.vehicles[vehicle].name,w*.88f,54f,text)
        }

        private fun drawLearningCard(c: Canvas, w: Float, h: Float, world: SmartScene) {
            val top = h*.63f
            val left = 18f
            val right = w-18f

            paint.color = Color.argb(225,255,255,255)
            c.drawRoundRect(RectF(left,top,right,top+105f),22f,22f,paint)

            text.textAlign = Paint.Align.LEFT
            text.color = Color.rgb(27,105,69)
            text.textSize = 13f
            val lessonLabel = if (question < 26) {
                "English • Letter ${lessons[question]} • ${englishWords[question]}"
            } else {
                lessonHints[question]
            }
            c.drawText(lessonLabel,left+18f,top+25f,text)

            text.color = Color.rgb(35,45,48)
            text.textSize = if (lessons[question].length > 4) 25f else 34f
            c.drawText(lessons[question],left+18f,top+61f,text)

            text.color = Color.rgb(85,90,90)
            text.textSize = 12f
            c.drawText(world.activity,left+18f,top+88f,text)

            paint.color = if (running) Color.rgb(20,150,83) else Color.rgb(45,100,80)
            c.drawRoundRect(RectF(right-90f,top+19f,right-18f,top+86f),18f,18f,paint)

            text.textAlign = Paint.Align.CENTER
            text.color = Color.WHITE
            text.textSize = 12f
            c.drawText(if (running) "STOP" else "START",right-54f,top+57f,text)

            text.textAlign = Paint.Align.CENTER
            text.color = Color.rgb(27,105,69)
            text.textSize = 11f
            text.color = Color.rgb(35,105,78)
            text.textSize = 10f
            c.drawText("Tap card to listen • Tap bottom for next", w/2f, top+101f, text)
        }

        private fun speakCurrentLesson() {
            if (question < 26) {
                voice.speakEnglishLesson(lessons[question], englishWords[question])
            } else {
                voice.speakLesson(lessons[question])
            }
        }

        private fun nextLesson() {
            question = (question + 1) % lessons.size
            level += 1
            worldSceneId += 1
            saveProgress()
            speakCurrentLesson()
        }

        private fun saveProgress() {
            prefs.edit()
                .putInt("level", level)
                .putInt("vehicle", vehicle)
                .putInt("worldSceneId", worldSceneId)
                .putInt("question", question)
                .apply()
        }

        private fun drawHint(c: Canvas, w: Float, h: Float) {
            text.textAlign = Paint.Align.CENTER
            text.color = Color.WHITE
            text.setShadowLayer(5f,0f,2f,Color.DKGRAY)
            text.textSize = 15f

            c.drawText(
                if (running) "গাড়ি চলছে • ট্যাপ করুন থামাতে"
                else "গাড়ি চালাতে একবার ট্যাপ করুন",
                w/2f,h*.965f,text
            )

            text.clearShadowLayer()
        }
    }

    override fun onPause() {
        if (::voice.isInitialized) voice.stop()
        super.onPause()
    }

    override fun onDestroy() {
        if (::voice.isInitialized) voice.shutdown()
        super.onDestroy()
    }
}
