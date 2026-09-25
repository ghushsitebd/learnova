package com.learnova.app

import android.graphics.*
import android.os.Bundle
import android.content.SharedPreferences
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.sin

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
            voice.speakLesson(lessons[question])
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
            if (y > h * 0.56f && y < h * 0.91f) {
                running = !running
                voice.speakInstruction(running)
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
                distance += 0.018f

                if (distance >= 1f) {
                    distance = 0f
                }
            }

            val world = LearnovaUnlimitedWorld.scene(worldSceneId)

            drawSky(canvas, w, h, world)
            drawSun(canvas, w, h, world)
            drawClouds(canvas, w, h)
            drawMountains(canvas, w, h)
            drawGround(canvas, w, h)
            drawRiver(canvas, w, h)
            drawTrees(canvas, w, h)
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

        private fun drawRoad(c: Canvas, w: Float, h: Float) {
            val road = Path()
            road.moveTo(w * .39f, h * .61f)
            road.lineTo(w * .61f, h * .61f)
            road.lineTo(w * .96f, h)
            road.lineTo(w * .04f, h)
            road.close()

            paint.color = Color.rgb(52, 55, 59)
            c.drawPath(road, paint)

            paint.color = Color.WHITE
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 4f
            c.drawPath(road, paint)
            paint.style = Paint.Style.FILL

            val offset = if (running) (frame * 9L % 115L).toFloat() else 0f
            var y = h * .63f + offset

            while (y < h) {
                val t = ((y - h * .61f) / (h * .39f)).coerceIn(0f, 1f)
                val half = 4f + 24f * t
                paint.color = Color.WHITE
                c.drawRect(w/2f-half, y, w/2f+half, y + 9f + 18f*t, paint)
                y += 70f + 90f*t
            }
        }

        private fun drawAnimals(c: Canvas, w: Float, h: Float, world: SmartScene) {
            val base = h * .61f
            val motion = if (running) sin(frame / 18.0).toFloat() * 12f else 0f
            when (world.region) {
                "Dinosaur Valley" -> drawDinosaur(c, w * .83f + motion, base)
                "Forest" -> drawBear(c, w * .14f + motion, base)
                "Safari" -> drawElephant(c, w * .82f + motion, base)
                "Ocean", "Island", "Wetland" -> drawDolphin(c, w * .82f + motion, base)
                else -> if (world.id % 2 == 0) drawElephant(c, w * .82f + motion, base) else drawBear(c, w * .14f + motion, base)
            }
        }

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
            val cx = w/2f
            val cy = h * .79f + if (running) sin(frame/4.0).toFloat()*2f else 0f
            when (selected.kind) {
                "car", "bus", "truck" -> drawCar(c,cx,cy)
                "bike" -> drawBike(c,cx,cy)
                "air" -> drawPlane(c,cx,cy)
                "boat" -> drawBoat(c,cx,cy)
                "space" -> drawRocket(c,cx,cy)
                else -> drawMicro(c,cx,cy)
            }
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

            drawWheel(c,x-68f,y+34f)
            drawWheel(c,x+68f,y+34f)
        }

        private fun drawWheel(c: Canvas, x: Float, y: Float) {
            paint.color = Color.rgb(25,25,25)
            c.drawCircle(x,y,23f,paint)
            paint.color = Color.rgb(175,175,175)
            c.drawCircle(x,y,9f,paint)
        }

        private fun drawBike(c: Canvas, x: Float, y: Float) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 7f
            paint.color = Color.rgb(28,45,48)

            c.drawCircle(x-52f,y+18f,25f,paint)
            c.drawCircle(x+52f,y+18f,25f,paint)
            c.drawLine(x-52f,y+18f,x-10f,y-12f,paint)
            c.drawLine(x-10f,y-12f,x+52f,y+18f,paint)
            c.drawLine(x-10f,y-12f,x+10f,y+18f,paint)
            c.drawLine(x+10f,y+18f,x-52f,y+18f,paint)

            paint.style = Paint.Style.FILL
            paint.color = Color.rgb(20,145,82)
            c.drawCircle(x,y-35f,14f,paint)
            c.drawRect(x-9f,y-22f,x+9f,y+12f,paint)
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

            drawWheel(c,x-48f,y+30f)
            drawWheel(c,x+48f,y+30f)
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
            c.drawText("Tap bottom for next lesson", w/2f, top+101f, text)
        }

        private fun nextLesson() {
            question = (question + 1) % lessons.size
            level += 1
            worldSceneId += 1
            saveProgress()
            voice.speakLesson(lessons[question])
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
                if (running) "ট্যাপ করলে গাড়ি থামবে • আবার ট্যাপ করলে চলবে"
                else "স্ক্রিনে একবার ট্যাপ করুন — গাড়ি চলবে",
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
