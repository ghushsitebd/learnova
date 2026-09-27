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
import kotlin.math.pow

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
        private var levelProgress = 0f
        private var levelComplete = false
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
            levelProgress = prefs.getFloat("levelProgress", 0f).coerceIn(0f, 1f)
            question = prefs.getInt("question", 0).coerceIn(0, lessons.lastIndex)
            speakCurrentLesson()
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return true

            val x = event.x
            val y = event.y

            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                // One-tap driving: tap the road/play area to toggle drive/stop.
                if (y > h * 0.22f && y < h * 0.63f) {
                    val now = System.currentTimeMillis()
                    if (now - lastTap > 220L) {
                        lastTap = now
                        running = !running
                        if (running) {
                            voice.speakInstruction(true)
                        } else {
                            voice.speakInstruction(false)
                        }
                        performClick()
                        invalidate()
                    }
                    return true
                }

                // The learning card only replays the current lesson.
                if (y >= h * 0.63f && y <= h * 0.91f && x < w * 0.76f) {
                    voice.speakLesson(lessons[question])
                    return true
                }
            }

            if (event.actionMasked == MotionEvent.ACTION_UP && y > h * 0.91f) {
                if (levelComplete) nextLesson()
                return true
            }

            return true
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return

            if (running) {
                frame++
                // Smooth acceleration gives the vehicle believable weight and momentum.
                speed += 0.00032f
                speed = speed.coerceAtMost(0.018f)
                distance += speed
                levelProgress += speed / LearnovaUnlimitedWorld.level(level).targetDistance * 0.006f
                levelProgress = levelProgress.coerceAtMost(1f)
                if (levelProgress >= 1f) levelComplete = true
                wheelSpin = (wheelSpin + speed * 900f) % 360f
                vehicleProgress += speed * 0.16f
                if (vehicleProgress > 1f) vehicleProgress = 0.70f
                val targetSteer = sin(frame / 70.0).toFloat() * 0.055f
                steering += (targetSteer - steering) * 0.08f
                laneOffset += (steering * 0.7f - laneOffset) * 0.045f
            } else {
                // Release is no longer a control action: after a tap-to-stop,
                // the vehicle coasts down naturally before coming to rest.
                speed *= 0.91f
                steering *= 0.88f
                if (levelProgress >= 1f) levelComplete = true
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
            drawRoad(canvas, w, h, world)
            drawRoadInfrastructure(canvas, w, h, world)
            drawAtmosphere(canvas, w, h, world)
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
            val seed = abs(world.id * 37 + 11)

            // The world is deliberately built from reusable procedural features.
            // This keeps the APK light while allowing hundreds of destinations to
            // have distinct visual identities and deterministic layouts.
            when {
                world.region.contains("Village") || world.region == "Bangladesh Village" -> drawVillageWorld(c,w,h,groundY,seed)
                world.region.contains("Market") || world.region.contains("Bazaar") || world.region == "Food Street" || world.region == "Night Market" -> drawMarketWorld(c,w,h,groundY,seed)
                world.region == "Mosque Courtyard" || world.region == "Quran School" || world.region.contains("Quran Learning") || world.region.contains("Arabic Learning") -> drawLearningWorld(c,w,h,groundY,seed,true)
                world.region == "School Campus" || world.region == "Library" || world.region == "Science Museum" || world.region == "Science Park" || world.region == "Space Center" || world.region == "Dinosaur Museum" -> drawLearningWorld(c,w,h,groundY,seed,false)
                world.region == "Farm" || world.region == "Farmhouse" || world.region == "Rice Field" || world.region == "Tea Garden" -> drawFarmWorld(c,w,h,groundY,seed)
                world.region == "Railway Station" || world.region == "Bus Terminal" || world.region == "Boat Terminal" || world.region == "Airport" -> drawTransportWorld(c,w,h,groundY,seed,world.region)
                world.region == "Hospital District" || world.region == "Fire Station" || world.region == "Police Station" || world.region == "Construction Zone" || world.region == "Rescue District" -> drawServiceWorld(c,w,h,groundY,seed,world.region)
                world.region == "Beach Town" || world.region == "Harbor" || world.region == "Fishing Village" || world.region == "Water Park" -> drawWaterfrontWorld(c,w,h,groundY,seed)
                world.region == "Mountain Town" || world.region == "Mountain Pass" -> drawMountainWorld(c,w,h,groundY,seed)
                world.region == "Forest Camp" || world.region == "Animal Rescue Center" || world.region == "Safari Lodge" || world.region == "Safari" -> drawWildlifeWorld(c,w,h,groundY,seed)
                world.region == "Dinosaur Valley" -> drawDinosaurWorld(c,w,h,groundY,seed)
                world.region == "City Center" || world.region == "City" || world.region == "Hospital District" -> drawCityWorld(c,w,h,groundY,seed)
                world.region == "Arctic" -> drawArcticWorld(c,w,h,groundY,seed)
                world.region == "Ocean" || world.region == "Island" || world.region == "Wetland" -> drawWaterWorld(c,w,h,groundY,seed)
                world.region == "Desert" -> drawDesertWorld(c,w,h,groundY,seed)
                else -> drawGardenWorld(c,w,h,groundY,seed)
            }
        }

        private fun featureColor(seed:Int, shift:Int, a:Int, b:Int, d:Int):Int {
            val v = abs(seed * 53 + shift * 97) % 32
            return Color.rgb((a + v).coerceAtMost(255), (b + v/2).coerceAtMost(255), (d + v/3).coerceAtMost(255))
        }

        private fun drawBuilding(c:Canvas,x:Float,y:Float,width:Float,height:Float,body:Int,roof:Int,windows:Boolean=true) {
            paint.color=body
            c.drawRoundRect(RectF(x,y-height,x+width,y+8f),6f,6f,paint)
            paint.color=roof
            c.drawPath(Path().apply{moveTo(x-5f,y-height);lineTo(x+width/2f,y-height-18f);lineTo(x+width+5f,y-height);close()},paint)
            if(windows){
                paint.color=Color.rgb(180,220,228)
                val cols=if(width>130f) 3 else 2
                for(r in 0..2) for(k in 0 until cols){
                    val wx=x+12f+k*(width-28f)/cols
                    val wy=y-height+20f+r*24f
                    c.drawRoundRect(RectF(wx,wy,wx+12f,wy+12f),2f,2f,paint)
                }
            }
        }

        private fun drawTree(c:Canvas,x:Float,y:Float,s:Float,seed:Int) {
            paint.color=Color.rgb(105,72,42)
            c.drawRoundRect(RectF(x-4f*s,y-38f*s,x+4f*s,y+8f*s),3f*s,3f*s,paint)
            paint.color=featureColor(seed,9,48,125,55)
            c.drawCircle(x,y-48f*s,22f*s,paint)
            c.drawCircle(x-18f*s,y-38f*s,17f*s,paint)
            c.drawCircle(x+18f*s,y-38f*s,18f*s,paint)
        }

        private fun drawPerson(c:Canvas,x:Float,y:Float,s:Float,walk:Float) {
            paint.color=Color.rgb(45,78,128)
            c.drawRoundRect(RectF(x-5f*s,y-28f*s,x+5f*s,y),3f*s,3f*s,paint)
            paint.color=Color.rgb(226,180,142)
            c.drawCircle(x,y-38f*s,7f*s,paint)
            paint.color=Color.rgb(35,35,35)
            c.drawLine(x-2f*s,y,x-7f*s,y+18f*s+walk,paint)
            c.drawLine(x+2f*s,y,x+7f*s,y+18f*s-walk,paint)
        }

        private fun drawShop(c:Canvas,x:Float,y:Float,s:Float,seed:Int) {
            drawBuilding(c,x,y,82f*s,55f*s,featureColor(seed,2,150,118,80),Color.rgb(120,70,42),false)
            paint.color=Color.rgb(245,245,232)
            c.drawRect(x+12f*s,y-39f*s,x+70f*s,y-8f*s,paint)
            paint.color=featureColor(seed,4,220,80,55)
            c.drawRect(x+7f*s,y-52f*s,x+75f*s,y-40f*s,paint)
        }

        private fun drawVillageWorld(c:Canvas,w:Float,h:Float,g:Float,seed:Int){
            for(i in 0..5){ val x=w*(0.03f+i*.18f); drawBuilding(c,x,g+4f,105f,55f+(i%3)*12f,Color.rgb(188,151,103),Color.rgb(132,91,58)); drawTree(c,x+105f,g+4f,.72f,seed+i) }
            for(i in 0..2) drawShop(c,w*(.12f+i*.34f),g+8f,.72f,seed+i)
            paint.color=Color.rgb(92,150,91); c.drawOval(RectF(w*.60f,g+18f,w*.94f,g+75f),paint)
            paint.color=Color.rgb(110,177,201); c.drawOval(RectF(w*.65f,g+28f,w*.88f,g+58f),paint)
            for(i in 0..4) drawPerson(c,w*(.15f+i*.17f),g+18f,0.7f,if(running)sin(frame/8.0).toFloat()*2f else 0f)
        }

        private fun drawMarketWorld(c:Canvas,w:Float,h:Float,g:Float,seed:Int){
            for(i in 0..4){
                val x=w*(.04f+i*.22f)
                drawShop(c,x,g+12f,.85f,seed+i)
                paint.color=Color.rgb(220,70,65)
                c.drawRect(x+8f,g-60f,x+86f,g-48f,paint)
                paint.color=Color.rgb(242,201,91)
                c.drawCircle(x+25f,g-18f,9f,paint); c.drawCircle(x+54f,g-18f,9f,paint)
            }
            for(i in 0..6) drawPerson(c,w*(.08f+i*.13f),g+28f,.58f,if(running)sin(frame/7.0+i).toFloat()*2f else 0f)
        }

        private fun drawLearningWorld(c:Canvas,w:Float,h:Float,g:Float,seed:Int,faith:Boolean){
            val body=if(faith) Color.rgb(222,214,188) else Color.rgb(205,214,220)
            drawBuilding(c,w*.16f,g+8f,w*.68f,110f,body,if(faith)Color.rgb(55,120,105) else Color.rgb(80,105,130))
            if(faith){
                paint.color=Color.rgb(220,215,190)
                c.drawRect(w*.47f,g-155f,w*.53f,g-90f,paint)
                c.drawCircle(w*.50f,g-158f,32f,paint)
            } else {
                paint.color=Color.rgb(92,145,175)
                c.drawRoundRect(RectF(w*.28f,g-100f,w*.72f,g-54f),5f,5f,paint)
                for(i in 0..3) drawPerson(c,w*(.25f+i*.17f),g+20f,.55f,0f)
            }
            paint.color=Color.rgb(115,170,105)
            c.drawCircle(w*.10f,g+10f,32f,paint); c.drawCircle(w*.90f,g+10f,32f,paint)
            for(i in 0..2) drawTree(c,w*(.08f+i*.42f),g+10f,.55f,seed+i)
        }

        private fun drawFarmWorld(c:Canvas,w:Float,h:Float,g:Float,seed:Int){
            paint.color=Color.rgb(145,116,65)
            for(i in 0..6) c.drawRect(w*.04f,g+18f+i*18f,w*.46f,g+25f+i*18f,paint)
            paint.color=Color.rgb(84,150,65)
            for(i in 0..10) c.drawLine(w*.05f+i*.038f*w,g+10f,w*.05f+i*.038f*w,g+115f,paint)
            drawBuilding(c,w*.70f,g+4f,100f,65f,Color.rgb(184,139,82),Color.rgb(125,83,48))
            for(i in 0..2) drawTree(c,w*(.55f+i*.16f),g+15f,.6f,seed+i)
        }

        private fun drawTransportWorld(c:Canvas,w:Float,h:Float,g:Float,seed:Int,type:String){
            when(type){
                "Railway Station" -> { drawBuilding(c,w*.15f,g+2f,w*.7f,90f,Color.rgb(175,182,187),Color.rgb(70,80,88)); paint.color=Color.DKGRAY; c.drawRect(w*.08f,g+35f,w*.92f,g+40f,paint); c.drawRect(w*.08f,g+58f,w*.92f,g+63f,paint) }
                "Bus Terminal" -> { drawBuilding(c,w*.12f,g+2f,w*.76f,80f,Color.rgb(164,176,182),Color.rgb(64,86,96)); for(i in 0..2){paint.color=Color.rgb(230,230,220);c.drawRect(w*(.2f+i*.22f),g-28f,w*(.36f+i*.22f),g-18f,paint)} }
                "Boat Terminal" -> { paint.color=Color.rgb(110,160,185);c.drawRect(0f,g+25f,w,h,paint); paint.color=Color.rgb(135,98,62);c.drawRect(w*.08f,g,w*.72f,g+15f,paint); drawShop(c,w*.72f,g+5f,.75f,seed) }
                else -> { drawBuilding(c,w*.18f,g+2f,w*.64f,75f,Color.rgb(208,212,215),Color.rgb(70,90,105)); paint.color=Color.rgb(225,225,220); c.drawRect(w*.30f,g-45f,w*.70f,g-20f,paint) }
            }
            for(i in 0..2) drawPerson(c,w*(.22f+i*.22f),g+20f,.55f,0f)
        }

        private fun drawServiceWorld(c:Canvas,w:Float,h:Float,g:Float,seed:Int,type:String){
            val body=when(type){"Hospital District"->Color.rgb(235,235,230);"Fire Station"->Color.rgb(190,70,62);"Police Station"->Color.rgb(78,104,145);else->Color.rgb(150,155,160)}
            drawBuilding(c,w*.18f,g+5f,w*.64f,105f,body,Color.rgb(80,85,90))
            paint.color=Color.WHITE
            c.drawRect(w*.46f,g-74f,w*.54f,g-20f,paint); c.drawRect(w*.43f,g-58f,w*.57f,g-36f,paint)
            for(i in 0..2) drawPerson(c,w*(.28f+i*.22f),g+22f,.58f,0f)
        }

        private fun drawWaterfrontWorld(c:Canvas,w:Float,h:Float,g:Float,seed:Int){
            paint.color=Color.rgb(70,150,190); c.drawRect(0f,g+10f,w,h,paint)
            paint.color=Color.rgb(240,240,235)
            for(i in 0..5){val x=w*(.08f+i*.18f); c.drawOval(RectF(x,g+30f,x+65f,g+36f),paint)}
            drawBuilding(c,w*.10f,g+8f,145f,62f,Color.rgb(222,198,154),Color.rgb(85,72,58))
            for(i in 0..2) drawTree(c,w*(.60f+i*.15f),g+12f,.65f,seed+i)
        }

        private fun drawMountainWorld(c:Canvas,w:Float,h:Float,g:Float,seed:Int){
            paint.color=Color.rgb(93,102,112)
            for(i in 0..3){val x=w*(i*.30f-.08f); c.drawPath(Path().apply{moveTo(x,g+10f);lineTo(x+w*.18f,g-150f);lineTo(x+w*.38f,g+10f);close()},paint)}
            paint.color=Color.rgb(78,128,72)
            for(i in 0..5) drawTree(c,w*(.06f+i*.17f),g+18f,.55f,seed+i)
        }

        private fun drawWildlifeWorld(c:Canvas,w:Float,h:Float,g:Float,seed:Int){
            paint.color=Color.rgb(112,145,75); c.drawRect(0f,g,w,h,paint)
            for(i in 0..6) drawTree(c,w*(.04f+i*.15f),g+15f,.55f,seed+i)
            drawBuilding(c,w*.68f,g+6f,125f,65f,Color.rgb(167,128,82),Color.rgb(91,68,45))
        }

        private fun drawDinosaurWorld(c:Canvas,w:Float,h:Float,g:Float,seed:Int){
            paint.color=Color.rgb(125,95,62)
            c.drawRect(0f,g+8f,w,h,paint)
            for(i in 0..4) drawTree(c,w*(.08f+i*.22f),g+18f,.72f,seed+i)
            paint.color=Color.rgb(77,126,64)
            c.drawOval(RectF(w*.60f,g-25f,w*.84f,g+12f),paint)
        }

        private fun drawCityWorld(c:Canvas,w:Float,h:Float,g:Float,seed:Int){
            for(i in 0..5){
                val x=w*(.02f+i*.17f)
                drawBuilding(c,x,g+5f,100f,75f+(i%3)*32f,Color.rgb(92+i*7,105+i*5,118+i*4),Color.rgb(55,65,72))
            }
            for(i in 0..3) drawTree(c,w*(.08f+i*.28f),g+12f,.45f,seed+i)
        }

        private fun drawArcticWorld(c:Canvas,w:Float,h:Float,g:Float,seed:Int){
            paint.color=Color.rgb(238,248,252); c.drawRect(0f,g,w,h,paint)
            paint.color=Color.rgb(177,215,230)
            for(i in 0..6){val x=w*(.05f+i*.16f);c.drawCircle(x,g+18f,24f,paint);c.drawCircle(x+22f,g+23f,18f,paint)}
        }

        private fun drawWaterWorld(c:Canvas,w:Float,h:Float,g:Float,seed:Int){
            paint.color=Color.rgb(73,151,188);c.drawRect(0f,g,w,h,paint)
            paint.color=Color.argb(150,255,255,255)
            for(i in 0..6){val x=(w*(.04f+i*.16f)+phaseForWorld(seed)*8f)%(w+80f)-40f;c.drawOval(RectF(x,g+18f+(i%3)*22f,x+60f,g+24f+(i%3)*22f),paint)}
        }

        private fun drawDesertWorld(c:Canvas,w:Float,h:Float,g:Float,seed:Int){
            paint.color=Color.rgb(220,181,91);c.drawRect(0f,g,w,h,paint)
            for(i in 0..4){val x=w*(.08f+i*.21f);c.drawOval(RectF(x-45f,g+20f,x+45f,g+42f),paint)}
            for(i in 0..2){val x=w*(.16f+i*.34f);paint.color=Color.rgb(54,125,68);c.drawRect(x-5f,g-5f,x+5f,g+38f,paint);c.drawCircle(x,g-8f,17f,paint)}
        }

        private fun drawGardenWorld(c:Canvas,w:Float,h:Float,g:Float,seed:Int){
            paint.color=Color.rgb(92,156,84);c.drawRect(0f,g,w,h,paint)
            for(i in 0..7) drawTree(c,w*(.04f+i*.13f),g+16f,.48f,seed+i)
            paint.color=Color.rgb(224,194,95)
            for(i in 0..5){val x=w*(.08f+i*.17f);c.drawCircle(x,g+20f,5f,paint);c.drawLine(x,g+25f,x,g+42f,paint)}
        }

        private fun phaseForWorld(seed:Int):Float = if(running) frame.toFloat()+seed else seed.toFloat()

        private fun drawRoad(c: Canvas, w: Float, h: Float, world: SmartScene) {
            // Perspective road: the vanishing point stays near the horizon while the
            // lane, shoulders, reflectors and surface texture expand toward the camera.
            val horizonY = h * 0.60f
            val bottomY = h
            val roadPhase = worldSceneId * 0.73f
            val curve = (sin(roadPhase + frame / 900.0) * 0.72 + sin(roadPhase * 0.47 + frame / 1450.0) * 0.28).toFloat()
            val bend = curve * w * 0.085f

            fun roadCenter(t: Float): Float =
                w * 0.50f + bend * (t * t) + laneOffset * w * t * 0.18f

            fun roadHalfWidth(t: Float): Float =
                w * (0.025f + 0.49f * t.pow(1.12f))

            val road = Path()
            road.moveTo(roadCenter(0f) - roadHalfWidth(0f), horizonY)
            for (i in 1..24) {
                val t = i / 24f
                val y = horizonY + (bottomY - horizonY) * t
                road.lineTo(roadCenter(t) - roadHalfWidth(t), y)
            }
            for (i in 24 downTo 0) {
                val t = i / 24f
                val y = horizonY + (bottomY - horizonY) * t
                road.lineTo(roadCenter(t) + roadHalfWidth(t), y)
            }
            road.close()

            paint.shader = LinearGradient(
                0f, horizonY, 0f, bottomY,
                intArrayOf(Color.rgb(67,70,73), Color.rgb(43,45,47), Color.rgb(31,32,34)),
                floatArrayOf(0f, .55f, 1f),
                Shader.TileMode.CLAMP
            )
            c.drawPath(road, paint)
            paint.shader = null

            // Soft road-edge shoulders.
            paint.color = Color.rgb(190, 188, 174)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 5f
            c.drawPath(road, paint)
            paint.style = Paint.Style.FILL

            // Subtle asphalt texture, kept cheap for low-end phones.
            paint.color = Color.argb(34, 255, 255, 255)
            for (i in 0..30) {
                val t = ((i * 0.071f + frame * 0.0012f) % 1f).coerceIn(0f,1f)
                val y = horizonY + (bottomY - horizonY) * t
                val cx = roadCenter(t)
                val hw = roadHalfWidth(t)
                val x = cx + sin(i * 7.3).toFloat() * hw * .72f
                c.drawCircle(x, y, 0.7f + 1.8f * t, paint)
            }

            // Dashed center line with true perspective scaling.
            val travel = if (running) (frame * 0.010f) % 1f else 0f
            var t = 0.015f
            while (t < 1f) {
                val tt = (t + travel) % 1f
                val y = horizonY + (bottomY - horizonY) * tt
                val cx = roadCenter(tt)
                val half = 1.5f + 7.5f * tt
                val length = 7f + 34f * tt
                paint.color = Color.rgb(248, 247, 236)
                c.drawRoundRect(RectF(cx - half, y, cx + half, y + length), half, half, paint)
                t += 0.105f + 0.16f * tt
            }

            // Raised lane reflectors and roadside posts.
            for (side in -1..1 step 2) {
                for (i in 1..8) {
                    val tt = i / 9f
                    val y = horizonY + (bottomY - horizonY) * tt
                    val cx = roadCenter(tt)
                    val edge = cx + side * roadHalfWidth(tt)
                    paint.color = Color.rgb(255, 214, 78)
                    c.drawCircle(edge, y, 2f + 3.5f * tt, paint)

                    val postHeight = 10f + 20f * tt
                    paint.color = Color.rgb(225, 225, 215)
                    c.drawRoundRect(
                        RectF(edge + side * (5f + 12f * tt), y - postHeight,
                             edge + side * (10f + 15f * tt), y),
                        2f, 2f, paint
                    )
                }
            }

            // Weather-aware wet asphalt: restrained reflections make rain scenes feel grounded.
            if (world.weather == "Rainy") {
                paint.color = Color.argb(55, 190, 220, 235)
                for (i in 0..7) {
                    val tt = 0.16f + i * 0.105f
                    val y = horizonY + (bottomY - horizonY) * tt
                    val cx = roadCenter(tt)
                    val hw = roadHalfWidth(tt) * 0.72f
                    c.drawRoundRect(RectF(cx - hw, y, cx + hw, y + 2f + 4f * tt), 3f, 3f, paint)
                }
            }

            // Moving dust/road spray makes forward motion visible without adding assets.
            if (running && speed > 0.004f) {
                paint.color = Color.argb(35, 235, 235, 225)
                for (i in 0..7) {
                    val spread = (i - 3.5f) * 13f
                    val yy = h * (.86f + (i % 3) * .025f)
                    c.drawCircle(w/2f + spread, yy, 2f + (i % 3), paint)
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

            // Depth + species variation: one lightweight renderer can represent a
            // large catalog without loading hundreds of megabytes of textures.
            val seed = abs((x.toInt() * 31L + frame / 18L).toInt())
            val species = LearnovaUnlimitedWorld.animal(seed % 1000 + 1)
            val depth = ((bobbedY / hSafe(c)).coerceIn(0.58f, 0.92f) - 0.58f) / 0.34f
            val depthScale = 0.62f + depth * 0.48f
            val scale = depthScale * species.scale
            val shadowWidth = 22f + 28f * scale

            paint.color = Color.argb((45f + depth * 45f).toInt(), 0, 0, 0)
            c.drawOval(
                RectF(
                    wrappedX - shadowWidth,
                    bobbedY + 18f * scale,
                    wrappedX + shadowWidth,
                    bobbedY + 25f * scale
                ),
                paint
            )

            c.save()
            c.translate(wrappedX, bobbedY)
            c.scale(scale, scale)
            val localX = 0f
            val localY = 0f
            when (kind) {
                "elephant" -> drawElephant(c, localX, localY)
                "bear" -> drawBear(c, localX, localY)
                "deer" -> drawDeer(c, localX, localY)
                "fox" -> drawFox(c, localX, localY)
                "bird" -> drawBird(c, localX, localY - if (running) abs(sin(frame / 6.0).toFloat()) * 18f else 0f)
                "giraffe" -> drawGiraffe(c, localX, localY)
                "zebra" -> drawZebra(c, localX, localY)
                "lion" -> drawLion(c, localX, localY)
                "fish" -> drawFish(c, localX, localY)
                "crocodile" -> drawCrocodile(c, localX, localY)
                "penguin" -> drawPenguin(c, localX, localY)
                "dinosaur" -> drawDinosaur(c, localX, localY)
                "dolphin" -> drawDolphin(c, localX, localY)
            }
            c.restore()
        }

        private fun hSafe(c: Canvas): Float = c.height.toFloat()

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

        private fun drawZebra(c: Canvas, x: Float, y: Float) {            paint.color=Color.WHITE
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

            // Camera-follow placement: the vehicle grows as it approaches and follows
            // the road curve rather than sliding across a flat screen.
            val roadCurve = sin(frame / 120.0).toFloat() * w * 0.065f
            val cx = w * 0.50f + roadCurve * p * p + laneOffset * w * (0.16f + 0.42f * p)
            val cy = h * (0.66f + 0.22f * p) +
                if (running) sin(frame / 4.5).toFloat() * (1.0f + 3.8f * p) else 0f
            val scale = 0.42f + 0.80f * p

            // Contact shadow reacts to height/suspension.
            paint.color = Color.argb((75 + 55 * p).toInt(), 0, 0, 0)
            c.drawOval(
                RectF(cx - 92f * scale, cy + 31f * scale,
                      cx + 92f * scale, cy + 54f * scale),
                paint
            )

            c.save()
            val bodyLean = steering * 5.5f + if (running) sin(frame / 9.0).toFloat() * 0.7f else 0f
            c.rotate(bodyLean, cx, cy)
            c.scale(scale, scale, cx, cy)

            // Vehicle-specific procedural rendering. Different real-world classes
            // get distinct proportions/details while sharing lightweight primitives.
            when (selected.kind) {
                "car" -> drawCar(c, cx, cy, selected.id)
                "bus" -> drawBus(c, cx, cy, selected.id)
                "truck" -> drawTruck(c, cx, cy, selected.id)
                "van" -> drawVan(c, cx, cy, selected.id)
                "bike" -> drawBike(c, cx, cy)
                "sportBike" -> drawSportBike(c, cx, cy)
                "bicycle" -> drawBicycle(c, cx, cy)
                "air" -> drawPlane(c, cx, cy)
                "boat" -> drawBoat(c, cx, cy)
                "space" -> drawRocket(c, cx, cy)
                "micro" -> drawMicro(c, cx, cy)
                else -> drawCar(c, cx, cy, selected.id)
            }
            c.restore()

            // Tiny speed streaks only at higher speed.
            if (running && speed > 0.010f && selected.kind != "air" && selected.kind != "space") {
                paint.color = Color.argb(38, 255, 255, 255)
                paint.strokeWidth = 2f
                c.drawLine(cx - 70f, cy + 8f, cx - 125f, cy + 13f, paint)
                c.drawLine(cx + 70f, cy + 12f, cx + 125f, cy + 17f, paint)
            }
        }

        private fun drawCar(c: Canvas, x: Float, y: Float, variant: Int = 1) {
            // Parameterized pseudo-3D vehicle studio: geometry changes by model family,
            // while gradients/details stay lightweight enough for low-end devices.
            val family = when (variant) {
                1, 2, 16, 17, 25, 57, 58 -> 0 // sedan / crossover
                3, 4 -> 1 // hatchback
                5, 6, 7, 8, 9 -> 2 // SUV
                26, 27, 31 -> 3 // sports
                28 -> 4 // convertible
                29 -> 5 // classic
                30 -> 6 // rally
                else -> 0
            }
            val bodyColor = when (variant % 8) {
                0 -> Color.rgb(205,55,55)
                1 -> Color.rgb(25,145,92)
                2 -> Color.rgb(38,105,190)
                3 -> Color.rgb(235,175,45)
                4 -> Color.rgb(155,60,175)
                5 -> Color.rgb(28,150,160)
                6 -> Color.rgb(225,225,225)
                else -> Color.rgb(55,65,72)
            }
            val bodyLeft = when (family) { 1 -> -102f; 2 -> -118f; 3 -> -116f; else -> -110f }
            val bodyRight = when (family) { 1 -> 108f; 2 -> 118f; 3 -> 116f; else -> 110f }
            val bodyTop = when (family) { 2 -> -48f; 3 -> -38f; 5 -> -35f; else -> -42f }
            val bodyBottom = if (family == 3) 34f else 40f

            paint.shader = LinearGradient(x, y + bodyTop, x, y + bodyBottom,
                bodyColor, Color.rgb(
                    (Color.red(bodyColor) * .52f).toInt(),
                    (Color.green(bodyColor) * .52f).toInt(),
                    (Color.blue(bodyColor) * .52f).toInt()
                ), Shader.TileMode.CLAMP)
            c.drawRoundRect(RectF(x+bodyLeft,y+bodyTop,x+bodyRight,y+bodyBottom),
                if (family == 3) 18f else 24f, if (family == 3) 18f else 24f, paint)
            paint.shader = null

            // Lower sill / bumper gives depth and a visible contact edge.
            paint.color = Color.argb(230, 20, 27, 30)
            c.drawRoundRect(RectF(x+bodyLeft+8f,y+18f,x+bodyRight-8f,y+46f),12f,12f,paint)

            val roof = Path()
            when (family) {
                1 -> { // hatchback: short roof and rear hatch
                    roof.moveTo(x-62f,y-37f); roof.lineTo(x-38f,y-72f)
                    roof.lineTo(x+34f,y-72f); roof.lineTo(x+72f,y-34f); roof.close()
                }
                2 -> { // SUV: taller cabin
                    roof.moveTo(x-76f,y-40f); roof.lineTo(x-48f,y-88f)
                    roof.lineTo(x+48f,y-88f); roof.lineTo(x+78f,y-40f); roof.close()
                }
                3 -> { // sports: low, swept roof
                    roof.moveTo(x-70f,y-35f); roof.lineTo(x-28f,y-70f)
                    roof.lineTo(x+42f,y-66f); roof.lineTo(x+75f,y-34f); roof.close()
                }
                4 -> { // convertible: low open cabin
                    roof.moveTo(x-42f,y-35f); roof.lineTo(x-20f,y-59f)
                    roof.lineTo(x+35f,y-59f); roof.lineTo(x+50f,y-34f); roof.close()
                }
                5 -> { // classic: rounded high roof
                    roof.moveTo(x-68f,y-36f); roof.lineTo(x-48f,y-78f)
                    roof.lineTo(x+42f,y-78f); roof.lineTo(x+70f,y-36f); roof.close()
                }
                6 -> { // rally: compact aggressive cabin
                    roof.moveTo(x-62f,y-38f); roof.lineTo(x-34f,y-76f)
                    roof.lineTo(x+34f,y-76f); roof.lineTo(x+65f,y-38f); roof.close()
                }
                else -> { // sedan / executive
                    roof.moveTo(x-70f,y-38f); roof.lineTo(x-42f,y-82f)
                    roof.lineTo(x+43f,y-82f); roof.lineTo(x+72f,y-38f); roof.close()
                }
            }
            paint.shader = LinearGradient(x, y-90f, x, y-28f,
                Color.rgb(65, 190, 160), Color.rgb(7, 63, 52), Shader.TileMode.CLAMP)
            c.drawPath(roof,paint)
            paint.shader = null

            // Individual window geometry follows the roof family.
            paint.color = Color.rgb(24,57,66)
            when (family) {
                2 -> {
                    c.drawRoundRect(RectF(x-42f,y-75f,x-2f,y-45f),7f,7f,paint)
                    c.drawRoundRect(RectF(x+5f,y-75f,x+47f,y-45f),7f,7f,paint)
                }
                3,4 -> c.drawRoundRect(RectF(x-30f,y-62f,x+38f,y-40f),7f,7f,paint)
                else -> {
                    c.drawRoundRect(RectF(x-42f,y-69f,x-4f,y-43f),7f,7f,paint)
                    c.drawRoundRect(RectF(x+5f,y-69f,x+43f,y-43f),7f,7f,paint)
                }
            }
            paint.color = Color.argb(125,220,250,255)
            c.drawRect(x-35f,y-66f,x-7f,y-62f,paint)
            c.drawRect(x+11f,y-66f,x+38f,y-62f,paint)

            // Model-specific front identity: grille, lamps, or sporty intake.
            paint.shader = RadialGradient(x-96f,y-5f,18f,Color.WHITE,
                Color.argb(20,255,255,255),Shader.TileMode.CLAMP)
            c.drawCircle(x-96f,y-5f,10f,paint)
            paint.shader = RadialGradient(x+96f,y-5f,18f,Color.WHITE,
                Color.argb(20,255,255,255),Shader.TileMode.CLAMP)
            c.drawCircle(x+96f,y-5f,10f,paint)
            paint.shader = null
            paint.color = Color.rgb(12,39,34)
            if (family == 3) {
                c.drawRoundRect(RectF(x-55f,y+9f,x+55f,y+24f),7f,7f,paint)
                paint.color = Color.argb(180,255,255,255)
                c.drawRoundRect(RectF(x-75f,y-29f,x+75f,y-24f),3f,3f,paint)
                paint.color = Color.rgb(15,20,25)
                c.drawRoundRect(RectF(x+45f,y-78f,x+63f,y-67f),3f,3f,paint) // spoiler
            } else {
                c.drawRoundRect(RectF(x-38f,y+12f,x+38f,y+25f),6f,6f,paint)
            }

            // Door/side contour and model badges.
            paint.color = Color.argb(85,255,255,255)
            paint.strokeWidth = 2f
            paint.style = Paint.Style.STROKE
            c.drawLine(x-55f,y-18f,x-55f,y+18f,paint)
            c.drawLine(x+55f,y-18f,x+55f,y+18f,paint)
            paint.style = Paint.Style.FILL

            drawWheel(c,x + if (family == 3) -72f else -69f,y+35f,wheelSpin)
            drawWheel(c,x + if (family == 3) 72f else 69f,y+35f,wheelSpin)

            // Convertible cabin cue / rally roof bar.
            if (family == 4) {
                paint.color = Color.rgb(35,35,38)
                paint.strokeWidth = 5f
                c.drawLine(x-38f,y-35f,x-20f,y-58f,paint)
                c.drawLine(x+22f,y-58f,x+42f,y-35f,paint)
            } else if (family == 6) {
                paint.color = Color.rgb(25,25,28)
                c.drawRoundRect(RectF(x-46f,y-84f,x+46f,y-78f),3f,3f,paint)
            }
        }

        private fun drawWheel(c: Canvas, x: Float, y: Float, angle: Float) {
            paint.shader = RadialGradient(x-5f,y-6f,25f,
                intArrayOf(Color.rgb(75,75,75),Color.rgb(18,18,18),Color.BLACK),
                floatArrayOf(0f,.62f,1f),Shader.TileMode.CLAMP)
            c.drawCircle(x,y,24f,paint)
            paint.shader = null
            paint.color = Color.rgb(145,145,145)
            c.drawCircle(x,y,10f,paint)
            paint.color = Color.rgb(55,55,55)
            c.save(); c.rotate(angle,x,y)
            for(i in 0..7){
                val a=i*45f
                val dx=cos(Math.toRadians(a.toDouble())).toFloat()*9f
                val dy=sin(Math.toRadians(a.toDouble())).toFloat()*9f
                c.drawLine(x,y,x+dx,y+dy,paint)
            }
            c.restore()
        }

        private fun drawBus(c: Canvas, x: Float, y: Float, variant: Int = 1) {
            val type = when (variant) { 18 -> 0; 19 -> 1; 20 -> 2; 49 -> 3; else -> 4 }
            val body = when (type) {
                0 -> Color.rgb(236,178,45) // school
                1 -> Color.rgb(45,125,205) // city
                2 -> Color.rgb(70,85,105)  // coach
                3 -> Color.rgb(45,150,95)  // airport
                else -> Color.rgb(205,75,55)
            }
            val h = if (type == 2) 64f else 58f
            paint.shader = LinearGradient(x,y-h,x,y+42f,body,
                Color.rgb((Color.red(body)*.55f).toInt(),(Color.green(body)*.55f).toInt(),(Color.blue(body)*.55f).toInt()),
                Shader.TileMode.CLAMP)
            c.drawRoundRect(RectF(x-128f,y-h,x+128f,y+40f),22f,22f,paint)
            paint.shader = null
            paint.color = Color.rgb(185,225,238)
            val windowTop = if (type == 2) y-48f else y-45f
            c.drawRoundRect(RectF(x-105f,windowTop,x+102f,y-13f),9f,9f,paint)
            paint.color = Color.rgb(30,45,50)
            c.drawRoundRect(RectF(x-110f,y-8f,x+110f,y+22f),7f,7f,paint)
            paint.color = Color.argb(110,255,255,255)
            c.drawRect(x-95f,y-41f,x-20f,y-37f,paint)
            c.drawRect(x-10f,y-41f,x+75f,y-37f,paint)
            if (type == 0) {
                paint.color = Color.rgb(35,35,35)
                c.drawRect(x-125f,y-2f,x+125f,y+5f,paint)
            } else if (type == 3) {
                paint.color = Color.WHITE
                c.drawRoundRect(RectF(x-35f,y+2f,x+35f,y+18f),5f,5f,paint)
            }
            drawWheel(c,x-82f,y+38f,wheelSpin)
            drawWheel(c,x+82f,y+38f,wheelSpin)
        }

        private fun drawTruck(c: Canvas, x: Float, y: Float, variant: Int = 1) {
            val type = when (variant) {
                10 -> 0; 11 -> 1; 12 -> 2; 13,55 -> 3; 14,54 -> 4; 52 -> 5; 53 -> 6; 59 -> 7; 51 -> 8; else -> 0
            }
            val cabColor = when (type) {
                3 -> Color.rgb(210,55,45)
                4 -> Color.rgb(45,120,175)
                5 -> Color.rgb(205,145,45)
                6 -> Color.rgb(75,125,75)
                8 -> Color.rgb(105,75,45)
                else -> Color.rgb(60,105,145)
            }
            val cargoColor = when (type) {
                2 -> Color.rgb(235,235,235)
                3 -> Color.rgb(225,70,55)
                4 -> Color.rgb(70,125,165)
                6 -> Color.rgb(100,115,105)
                else -> Color.rgb(165,175,180)
            }
            paint.color = cargoColor
            val cargoRight = if (type == 0) 112f else 120f
            c.drawRoundRect(RectF(x-118f,y-42f,x+12f,y+38f),10f,10f,paint)
            paint.shader = LinearGradient(x+55f,y-68f,x+55f,y+40f,cabColor,
                Color.rgb((Color.red(cabColor)*.5f).toInt(),(Color.green(cabColor)*.5f).toInt(),(Color.blue(cabColor)*.5f).toInt()),
                Shader.TileMode.CLAMP)
            c.drawRoundRect(RectF(x+5f,y-66f,x+cargoRight,y+39f),15f,15f,paint)
            paint.shader = null
            paint.color = Color.rgb(190,225,238)
            c.drawRoundRect(RectF(x+21f,y-53f,x+91f,y-20f),8f,8f,paint)
            paint.color = Color.argb(105,255,255,255)
            c.drawRect(x+28f,y-48f,x+82f,y-44f,paint)
            if (type == 3 || type == 4) {
                paint.color = Color.rgb(245,245,245)
                c.drawRoundRect(RectF(x-108f,y-25f,x+2f,y+18f),6f,6f,paint)
            } else if (type == 6) {
                paint.color = Color.rgb(45,55,50)
                c.drawRoundRect(RectF(x-110f,y-28f,x+3f,y+20f),6f,6f,paint)
            } else if (type == 8) {
                paint.color = Color.rgb(120,75,35)
                c.drawCircle(x-50f,y-2f,18f,paint)
            }
            paint.color = Color.rgb(30,35,38)
            c.drawRoundRect(RectF(x-122f,y+22f,x+110f,y+45f),8f,8f,paint)
            drawWheel(c,x-72f,y+38f,wheelSpin)
            drawWheel(c,x+76f,y+38f,wheelSpin)
        }

        private fun drawVan(c: Canvas, x: Float, y: Float, variant: Int = 1) {
            val type = when (variant) { 15 -> 0; 21 -> 1; 22 -> 2; 23 -> 3; 56 -> 4; 60 -> 5; else -> 3 }
            val body = when (type) {
                0 -> Color.rgb(238,238,238)
                1 -> Color.rgb(80,105,145)
                2 -> Color.rgb(45,140,90)
                3 -> Color.rgb(220,220,220)
                4 -> Color.rgb(225,75,55)
                else -> Color.rgb(55,145,120)
            }
            paint.shader = LinearGradient(x,y-62f,x,y+42f,body,
                Color.rgb((Color.red(body)*.55f).toInt(),(Color.green(body)*.55f).toInt(),(Color.blue(body)*.55f).toInt()),
                Shader.TileMode.CLAMP)
            c.drawRoundRect(RectF(x-116f,y-60f,x+116f,y+40f),20f,20f,paint)
            paint.shader = null
            paint.color = Color.rgb(185,225,238)
            if (type == 1) {
                c.drawRoundRect(RectF(x-82f,y-45f,x-8f,y-12f),9f,9f,paint)
                c.drawRoundRect(RectF(x,y-45f,x+73f,y-12f),9f,9f,paint)
            } else {
                c.drawRoundRect(RectF(x-82f,y-45f,x+72f,y-12f),9f,9f,paint)
            }
            paint.color = Color.argb(120,220,250,255)
            c.drawRect(x-72f,y-41f,x+58f,y-37f,paint)
            paint.color = Color.rgb(245,245,245)
            c.drawRoundRect(RectF(x-68f,y+1f,x+75f,y+24f),7f,7f,paint)
            if (type == 0) {
                paint.color = Color.rgb(205,55,55)
                c.drawCircle(x+90f,y-5f,7f,paint)
                paint.color = Color.rgb(25,110,180)
                c.drawCircle(x+102f,y-5f,7f,paint)
            } else if (type == 4) {
                paint.color = Color.rgb(25,45,55)
                c.drawRoundRect(RectF(x-50f,y-55f,x+48f,y-50f),3f,3f,paint)
            }
            drawWheel(c,x-70f,y+38f,wheelSpin)
            drawWheel(c,x+70f,y+38f,wheelSpin)
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
            val info = LearnovaUnlimitedWorld.level(level)
            c.drawText("LEVEL " + level,w*.53f,37f,text)
            c.drawText(if (levelComplete) "✓ COMPLETE" else if (running) "● DRIVING" else "● READY",w*.53f,56f,text)
            text.textSize = 9f
            c.drawText(info.difficulty.uppercase(),w*.53f,67f,text)

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
            text.textSize = 11f
            val info = LearnovaUnlimitedWorld.level(level)
            c.drawText("${info.lessonType} • ${info.difficulty}",left+18f,top+82f,text)
            paint.color = Color.rgb(225,232,228)
            c.drawRoundRect(RectF(left+18f,top+88f,right-112f,top+95f),4f,4f,paint)
            paint.color = Color.rgb(28,155,91)
            c.drawRoundRect(RectF(left+18f,top+88f,left+18f+(right-left-130f)*levelProgress,top+95f),4f,4f,paint)

            paint.color = if (running) Color.rgb(20,150,83) else Color.rgb(45,100,80)
            c.drawRoundRect(RectF(right-90f,top+19f,right-18f,top+86f),18f,18f,paint)

            text.textAlign = Paint.Align.CENTER
            text.color = Color.WHITE
            text.textSize = 12f
            c.drawText(if (levelComplete) "NEXT LEVEL" else if (running) "DRIVING" else "READY",right-54f,top+57f,text)

            text.textAlign = Paint.Align.CENTER
            text.color = Color.rgb(27,105,69)
            text.textSize = 11f
            text.color = Color.rgb(35,105,78)
            text.textSize = 10f
            c.drawText("Tap road once to drive • Tap again to stop", w/2f, top+101f, text)
        }

        private fun speakCurrentLesson() {
            if (question < 26) {
                voice.speakEnglishLesson(lessons[question], englishWords[question])
            } else {
                voice.speakLesson(lessons[question])
            }
        }

        private fun nextLesson() {
            if (!levelComplete) return
            question = (question + 1) % lessons.size
            level += 1
            worldSceneId += 1
            levelProgress = 0f
            levelComplete = false
            saveProgress()
            speakCurrentLesson()
        }

        private fun saveProgress() {
            prefs.edit()
                .putInt("level", level)
                .putInt("vehicle", vehicle)
                .putInt("worldSceneId", worldSceneId)
                .putInt("question", question)
                .putFloat("levelProgress", levelProgress)
                .apply()
        }

        private fun drawRoadInfrastructure(c: Canvas, w: Float, h: Float, world: SmartScene) {
            // Lightweight environmental details make the road read like a real place
            // without loading large bitmap assets. They are depth-scaled and scene-aware.
            val horizon = h * 0.59f
            val nearY = h * 0.88f
            val curve = sin(frame / 120.0).toFloat() * w * 0.035f

            when (world.region) {
                "City", "Village" -> {
                    paint.color = Color.rgb(72, 78, 76)
                    for (i in 0..5) {
                        val t = i / 5f
                        val y = horizon + (nearY - horizon) * t
                        val x = w * (0.08f + t * 0.84f) + curve * t
                        val postH = 10f + 24f * t
                        paint.strokeWidth = 2.2f
                        c.drawLine(x, y, x, y - postH, paint)
                        paint.color = if (world.time == "Night") Color.rgb(255, 226, 126) else Color.rgb(190, 205, 198)
                        c.drawCircle(x, y - postH, 2.8f + 1.5f * t, paint)
                        paint.color = Color.rgb(72, 78, 76)
                    }
                }
                "Mountain", "Forest", "Safari", "Dinosaur Valley" -> {
                    paint.color = Color.rgb(116, 116, 108)
                    paint.strokeWidth = 3f
                    for (side in -1..1 step 2) {
                        val points = Path()
                        points.moveTo(w / 2f + side * w * .20f, horizon + 18f)
                        points.cubicTo(
                            w / 2f + side * w * .29f, h * .68f,
                            w / 2f + side * w * .37f, h * .77f,
                            w / 2f + side * w * .46f, nearY
                        )
                        c.drawPath(points, paint)
                        for (i in 0..5) {
                            val t = i / 5f
                            val x = w / 2f + side * (w * (.20f + .26f * t))
                            val y = horizon + (nearY - horizon) * t
                            c.drawCircle(x, y, 2.2f + 2.5f * t, paint)
                        }
                    }
                }
                "Farm", "Wetland", "Island", "Arabic Learning Garden", "Quran Learning Garden", "Garden", "Kindness Village" -> {
                    paint.color = Color.rgb(139, 101, 61)
                    paint.strokeWidth = 2.5f
                    for (side in -1..1 step 2) {
                        var previousX = w / 2f + side * w * .22f
                        var previousY = horizon + 20f
                        for (i in 1..6) {
                            val t = i / 6f
                            val x = w / 2f + side * (w * (.22f + .25f * t))
                            val y = horizon + (nearY - horizon) * t
                            c.drawLine(previousX, previousY, x, y, paint)
                            c.drawLine(previousX, previousY - 5f - 7f * t, previousX, previousY + 8f, paint)
                            previousX = x
                            previousY = y
                        }
                    }
                }
                else -> {
                    paint.color = Color.rgb(132, 126, 111)
                    paint.strokeWidth = 2.5f
                    for (i in 0..4) {
                        val t = i / 4f
                        val y = horizon + (nearY - horizon) * t
                        val x = w * (.10f + .80f * t) + curve * t
                        c.drawLine(x, y - 7f - 8f * t, x, y + 7f, paint)
                    }
                }
            }

            // Tiny perspective dust/spray cues reinforce motion without obscuring the lesson UI.
            if (running) {
                paint.color = if (world.weather == "Rainy") Color.argb(65, 225, 240, 245) else Color.argb(48, 220, 205, 170)
                for (i in 0..7) {
                    val t = ((frame / 3L + i * 17L) % 90L) / 90f
                    val x = w / 2f + sin(i * 1.7 + frame / 25.0).toFloat() * (18f + 70f * t)
                    val y = h * .78f + t * h * .15f
                    c.drawCircle(x, y, 1.5f + 2.5f * t, paint)
                }
            }
        }

        private fun drawAtmosphere(c: Canvas, w: Float, h: Float, world: SmartScene) {
            // Depth cues: distant haze + low-angle light keep the procedural world readable
            // without heavy bitmap assets. This stays compatible with the existing GPU Canvas path.
            val horizon = h * 0.59f
            val hazeAlpha = when (world.weather) {
                "Rainy" -> 54
                "Cloudy" -> 34
                "Fresh" -> 18
                else -> if (world.time == "Sunset") 24 else 12
            }
            paint.shader = LinearGradient(
                0f, horizon - h * 0.16f, 0f, horizon + h * 0.10f,
                Color.argb(0, 235, 245, 248),
                Color.argb(hazeAlpha, 235, 245, 248),
                Shader.TileMode.CLAMP
            )
            c.drawRect(0f, horizon - h * 0.16f, w, horizon + h * 0.10f, paint)
            paint.shader = null

            if (world.time == "Sunset") {
                paint.color = Color.argb(24, 255, 190, 105)
                c.drawRect(0f, h * 0.34f, w, h * 0.67f, paint)
            }

            if (world.time == "Night") {
                paint.color = Color.argb(18, 20, 35, 70)
                c.drawRect(0f, h * 0.45f, w, h, paint)
            }
        }

        private fun drawHint(c: Canvas, w: Float, h: Float) {
            text.textAlign = Paint.Align.CENTER
            text.color = Color.WHITE
            text.setShadowLayer(5f,0f,2f,Color.DKGRAY)
            text.textSize = 15f

            c.drawText(
                if (levelComplete) "Level complete • Tap NEXT LEVEL"
                else if (running) "Driving • Tap again to stop"
                else "Tap once to drive",
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