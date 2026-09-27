package com.learnova.app

import android.graphics.*
import android.os.Bundle
import android.content.SharedPreferences
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.abs
import kotlin.math.pow

class MainActivity : AppCompatActivity() {

    private lateinit var gameView: LearnovaGameView
    private lateinit var voice: LearnovaVoice
    private lateinit var threeDWorld: Learnova3DView
    private lateinit var garageView: VehicleGarageView
    private lateinit var rootLayout: FrameLayout
    private val natureAudio = LearnovaNatureAudio()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setStatusBarColor(Color.rgb(78, 175, 235))
        window.setNavigationBarColor(Color.BLACK)
        voice = LearnovaVoice(this)
        gameView = LearnovaGameView()
        threeDWorld = Learnova3DView(this)
        rootLayout = FrameLayout(this)
        rootLayout.addView(threeDWorld, FrameLayout.LayoutParams(-1, -1))
        rootLayout.addView(gameView, FrameLayout.LayoutParams(-1, -1))
        garageView = VehicleGarageView(
            this,
            onSelected = { definition -> gameView.selectVehicleFromGarage(definition) },
            onClosed = { closeGarage() }
        ).apply { visibility = View.GONE }
        rootLayout.addView(garageView, FrameLayout.LayoutParams(-1, -1))
        setContentView(rootLayout)
    }

    private fun openGarage() {
        if (::gameView.isInitialized && ::garageView.isInitialized) {
            gameView.setDrivingFromGarage(false)
            garageView.setSelected(getSharedPreferences("learnova_progress", MODE_PRIVATE)
                .getInt("garage_vehicle_id", 1))
            garageView.visibility = View.VISIBLE
        }
    }

    private fun closeGarage() {
        if (::garageView.isInitialized) garageView.visibility = View.GONE
    }

    private inner class LearnovaGameView : View(this@MainActivity) {

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }

        private var running = false
        private val use3DWorld = true
        private var frame = 0L
        private var distance = 0f
        private var vehicleProgress = 0.78f
        private var wheelSpin = 0f
        private var speed = 0f
        private var laneOffset = 0f
        private var steering = 0f
        private var vehicleHeading = 0f
        private var lateralVelocity = 0f
        private var suspensionOffset = 0f
        private var suspensionVelocity = 0f
        private var level = 1
        private var vehicle = 0
        private var levelProgress = 0f
        private var levelComplete = false
        private var lessonStage = 0
        private var completedLessons = 0
        private var learningPoints = 0
        private var celebrationUntil = 0L
        private val sessionStartedAt = System.currentTimeMillis()
        private var worldSceneId = 1
        private var question = 0
        private var lastTap = 0L
        private var salamPlayedForSession = false
        private val prefs: SharedPreferences = getSharedPreferences("learnova_progress", MODE_PRIVATE)
        private val renderQuality = LearnovaRenderQuality(this@MainActivity)

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

        fun setDrivingFromGarage(value: Boolean) {
            running = value
            threeDWorld.setDriving(value)
            invalidate()
        }

        fun selectVehicleFromGarage(definition: VehicleDefinition) {
            // The catalog selection is stored independently from the current renderer's
            // legacy 65-role vehicle set. This allows all 100 garage choices immediately;
            // unique GLB assets can be streamed in later without changing the UI contract.
            val mapped = (definition.id - 1) % LearnovaUnlimitedWorld.vehicles.size
            vehicle = mapped
            threeDWorld.setVehicle(definition)
            prefs.edit()
                .putInt("vehicle", mapped)
                .putInt("garage_vehicle_id", definition.id)
                .apply()
            voice.speakVehicle(definition.name)
            invalidate()
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return true

            val x = event.x
            val y = event.y

            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                // The vehicle badge now opens the real 100-slot garage.
                // Driving remains intentionally simple: tap once to drive, tap again to stop.
                if (y < h * 0.22f && x > w * 0.76f) {
                    openGarage()
                    return true
                }

                // One-tap driving: tap the road/play area to toggle drive/stop.
                if (y > h * 0.22f && y < h * 0.63f) {
                    val now = System.currentTimeMillis()
                    if (now - lastTap > 220L) {
                        lastTap = now
                        running = !running
                        threeDWorld.setDriving(running)
                        if (running) {
                            natureAudio.start()
                            if (!salamPlayedForSession) {
                                salamPlayedForSession = true
                                voice.playSalamExchange()
                            } else {
                                voice.playChildLesson(SmartLearningEngine.lesson(question))
                            }
                        } else {
                            natureAudio.stop()
                            voice.speakInstruction(false)
                        }
                        performClick()
                        invalidate()
                    }
                    return true
                }

                // The learning card is the child's simple "learn while travelling"
                // path: one tap moves through See -> Listen -> Connect.
                if (y >= h * 0.63f && y <= h * 0.91f && x < w * 0.76f) {
                    lessonStage = (lessonStage + 1) % 3
                    voice.speakSmartLesson(SmartLearningEngine.lesson(question))
                    invalidate()
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
            val frameStart = renderQuality.beginFrame()

            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return

            if (running) {
                frame++

                // Vehicle dynamics: acceleration, road-following steering, lateral
                // inertia and suspension are derived from the same road curve used by
                // the renderer. The player still has only one control: tap to drive,
                // tap again to stop.
                val roadNow = roadCenterAt(vehicleProgress.coerceIn(0f, 1f), w, worldSceneId)
                val roadAhead = roadCenterAt((vehicleProgress + 0.055f).coerceAtMost(1f), w, worldSceneId)
                val roadFar = roadCenterAt((vehicleProgress + 0.14f).coerceAtMost(1f), w, worldSceneId)
                val nearSlope = (roadAhead - roadNow) / w
                val farSlope = (roadFar - roadAhead) / w
                val curvatureSteer = (nearSlope * 2.8f + farSlope * 1.6f).coerceIn(-0.12f, 0.12f)
                val laneCorrection = (-laneOffset * 0.24f).coerceIn(-0.055f, 0.055f)
                val targetSteer = (curvatureSteer + laneCorrection).coerceIn(-0.14f, 0.14f)

                steering += (targetSteer - steering) * 0.085f
                vehicleHeading += (steering * 7.0f - vehicleHeading) * 0.11f

                val targetSpeed = 0.018f
                speed += (targetSpeed - speed) * 0.022f
                speed = speed.coerceIn(0f, targetSpeed)
                distance += speed
                levelProgress += speed / LearnovaUnlimitedWorld.level(level).targetDistance * 0.006f
                levelProgress = levelProgress.coerceAtMost(1f)
                if (levelProgress >= 1f) levelComplete = true

                // Lateral inertia makes the body settle into a curve instead of
                // snapping sideways.
                lateralVelocity += (steering * 0.0028f - lateralVelocity) * 0.10f
                laneOffset += lateralVelocity
                laneOffset += (laneCorrection - laneOffset) * 0.012f
                laneOffset = laneOffset.coerceIn(-0.16f, 0.16f)

                // Suspension reacts to speed and changing road direction.
                val bump = sin(frame / 5.2).toFloat() * (0.35f + speed * 18f)
                suspensionVelocity += (bump - suspensionOffset) * 0.16f
                suspensionVelocity *= 0.76f
                suspensionOffset += suspensionVelocity
                suspensionOffset = suspensionOffset.coerceIn(-4.5f, 4.5f)

                wheelSpin = (wheelSpin + speed * 900f) % 360f
                vehicleProgress += speed * 0.16f
                if (vehicleProgress > 1f) vehicleProgress = 0.70f
            } else {
                // Tap-to-stop uses natural braking/coasting rather than an instant
                // freeze, while steering and suspension settle smoothly.
                speed *= 0.91f
                steering *= 0.88f
                vehicleHeading *= 0.90f
                lateralVelocity *= 0.82f
                laneOffset += (-laneOffset) * 0.06f
                suspensionVelocity *= 0.70f
                suspensionOffset *= 0.78f
                if (levelProgress >= 1f) levelComplete = true
            }

            val world = LearnovaUnlimitedWorld.scene(worldSceneId)
            if (running) {
                natureAudio.setEnvironment(world.region, world.weather, world.time)
            }

            // The production 3D renderer now owns the physical world when enabled.
            // Keep the Android Canvas layer for the learning HUD, voice/lesson state and
            // compatibility fallback. This makes migration incremental instead of a
            // risky all-at-once rewrite.
            if (!use3DWorld) {
                drawSky(canvas, w, h, world)
                drawSun(canvas, w, h, world)
                drawClouds(canvas, w, h)
                drawMountains(canvas, w, h)
                drawGround(canvas, w, h)
                drawRiver(canvas, w, h)
                drawTrees(canvas, w, h)
                drawHabitatDetails(canvas, w, h, world)
                drawForestRouteDepth(canvas, w, h, world)
                drawRoad(canvas, w, h, world)
                drawRoadMaterialPass(canvas, w, h, world)
                drawRoadReflections(canvas, w, h, world)
                drawRoadInfrastructure(canvas, w, h, world)
                if (world.region.contains("Village")) drawVillageRoadsideDepth(canvas, w, h, world)
                drawAtmosphere(canvas, w, h, world)
                drawCinematicLighting(canvas, w, h, world)
                drawDistantWorld(canvas, w, h, world)
                drawRoadsideInteractions(canvas, w, h, world)
                drawEnvironmentMotion(canvas, w, h, world)
                drawAnimals(canvas, w, h, world)
                drawVehicle(canvas, w, h)
                drawVehicleContactEffects(canvas, w, h, world)
            }
            drawTopBar(canvas, w, h, world)
            drawLearningCard(canvas, w, h, world)
            drawHint(canvas, w, h)
            renderQuality.endFrame(frameStart)

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
            for (i in 0 until renderQuality.distantTrafficCount()) {
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
            val baseXs = floatArrayOf(.04f, .12f, .22f, .31f, .69f, .78f, .88f, .96f)
            val count = when (renderQuality.level()) {
                0 -> 5
                1 -> 6
                2 -> 7
                else -> 8
            }
            for (i in 0 until count) {
                val xNorm = baseXs[i]
                val scale = (0.58f + (i % 4) * .11f) * renderQuality.treeDetail()
                val depthY = .65f + (i % 3) * .022f
                drawTree(c, w * xNorm, h * depthY, scale)
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
                world.region == "Mosque Courtyard" || world.region == "Quran School" || world.region.contains("Quran Learning") || world.region.contains("Arabic Learning") || world.region == "Islamic Library" || world.region == "Wudu Garden" || world.region == "Islamic History Museum" || world.region == "Charity Center" -> drawLearningWorld(c,w,h,groundY,seed,true)
                world.region == "Ramadan Community Market" || world.region == "Halal Food Street" || world.region == "Calligraphy Market" -> drawMarketWorld(c,w,h,groundY,seed)
                world.region == "Eid Festival Ground" -> drawGardenWorld(c,w,h,groundY,seed)
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
            // Islamic village/market population: modest, child-safe clothing.
            // Men: thobe/panjabi silhouettes with kufi/tupi. Women: long modest
            // abaya/hijab silhouettes. Colors vary deterministically by scene.
            val variant = abs((x.toInt() * 13 + frame.toInt() / 60)) % 4
            val isWoman = variant == 1 || variant == 3
            val garment = if (isWoman) Color.rgb(92 + variant*18, 84 + variant*16, 116 + variant*12)
                           else Color.rgb(224 - variant*18, 216 - variant*12, 198 - variant*10)

            if (isWoman) {
                paint.color = garment
                c.drawRoundRect(RectF(x-9f*s,y-30f*s,x+9f*s,y+2f*s),7f*s,7f*s,paint)
                c.drawCircle(x,y-39f*s,10f*s,paint)
                // Hijab/head covering and long modest silhouette.
                paint.color = Color.rgb(48,48,52)
                c.drawArc(RectF(x-12f*s,y-51f*s,x+12f*s,y-27f*s),180f,180f,true,paint)
                c.drawRoundRect(RectF(x-11f*s,y-29f*s,x+11f*s,y+5f*s),8f*s,8f*s,paint)
            } else {
                paint.color = garment
                c.drawRoundRect(RectF(x-7f*s,y-31f*s,x+7f*s,y+2f*s),4f*s,4f*s,paint)
                paint.color = Color.rgb(226,181,143)
                c.drawCircle(x,y-40f*s,7f*s,paint)
                // Tupi/kufi cap.
                paint.color = Color.rgb(245,245,238)
                c.drawOval(RectF(x-7f*s,y-48f*s,x+7f*s,y-41f*s),paint)
            }
            // Natural walking legs/arms kept subtle and non-exaggerated.
            paint.color = Color.rgb(42,42,44)
            c.drawLine(x-2f*s,y+1f*s,x-7f*s,y+18f*s+walk,paint)
            c.drawLine(x+2f*s,y+1f*s,x+7f*s,y+18f*s-walk,paint)
            c.drawLine(x-5f*s,y-20f*s,x-12f*s,y-7f*s-walk*.5f,paint)
            c.drawLine(x+5f*s,y-20f*s,x+12f*s,y-7f*s+walk*.5f,paint)
        }

        private fun drawShop(c:Canvas,x:Float,y:Float,s:Float,seed:Int) {
            drawBuilding(c,x,y,82f*s,55f*s,featureColor(seed,2,150,118,80),Color.rgb(120,70,42),false)
            paint.color=Color.rgb(245,245,232)
            c.drawRect(x+12f*s,y-39f*s,x+70f*s,y-8f*s,paint)
            paint.color=featureColor(seed,4,220,80,55)
            c.drawRect(x+7f*s,y-52f*s,x+75f*s,y-40f*s,paint)
        }

        private fun drawVillageWorld(c: Canvas, w: Float, h: Float, g: Float, seed: Int) {
            // Bangladesh-inspired village composition: raised homesteads, courtyards,
            // ponds, crops, bamboo fencing, livestock sheds and small roadside shops.
            // Everything is procedural so the APK stays small and scenes can vary.
            val phase = if (running) frame * 0.035f else 0f

            // Distant rice/crop plots.
            paint.color = Color.rgb(103, 160, 69)
            for (i in 0..5) {
                val left = w * (0.01f + i * 0.10f)
                val top = g - 2f + (i % 2) * 10f
                c.drawRect(left, top, left + w * 0.085f, g + 34f, paint)
                paint.color = Color.rgb(137, 178, 72)
                for (r in 0..3) {
                    val x = left + 8f + r * w * 0.019f
                    c.drawLine(x, top + 5f, x + 3f, top - 4f, paint)
                }
                paint.color = Color.rgb(103, 160, 69)
            }

            // Raised homesteads with varied local house forms.
            val houseX = floatArrayOf(.05f, .30f, .55f, .78f)
            for (i in houseX.indices) {
                val x = w * houseX[i]
                val width = w * (0.16f + ((seed + i) and 1) * 0.025f)
                val body = when (i % 3) {
                    0 -> Color.rgb(181, 151, 112)
                    1 -> Color.rgb(197, 180, 151)
                    else -> Color.rgb(164, 173, 167)
                }
                val roof = when (i % 2) {
                    0 -> Color.rgb(113, 78, 52)
                    else -> Color.rgb(92, 93, 82)
                }
                // Raised earth plinth.
                paint.color = Color.rgb(144, 108, 69)
                c.drawOval(RectF(x - 8f, g - 2f, x + width + 10f, g + 17f), paint)
                drawBuilding(c, x, g + 3f, width, 54f + (i % 2) * 10f, body, roof)
                drawBambooFence(c, x - 12f, g + 8f, width + 24f, 30f)
                drawTree(c, x + width * .78f, g + 5f, .52f + (i % 3) * .08f, seed + i)
            }

            // Courtyard / kitchen / cowshed details.
            for (i in 0..2) {
                val x = w * (.22f + i * .27f)
                paint.color = Color.rgb(164, 126, 79)
                c.drawRect(x, g + 4f, x + 42f, g + 34f, paint)
                paint.color = Color.rgb(106, 79, 51)
                c.drawRect(x + 4f, g - 8f, x + 38f, g + 5f, paint)
                drawCow(c, x + 21f, g + 1f, .42f)
            }

            // Village pond with bank, water reflection and a simple ghat.
            val px = w * .58f
            val py = g + 22f
            paint.color = Color.rgb(87, 127, 68)
            c.drawOval(RectF(px - 10f, py - 8f, w * .98f, py + 68f), paint)
            paint.color = Color.rgb(71, 145, 171)
            c.drawOval(RectF(px, py, w * .96f, py + 53f), paint)
            paint.color = Color.argb(90, 235, 250, 250)
            for (i in 0..4) {
                val x = px + 22f + i * w * .055f + sin(phase + i).toFloat() * 3f
                c.drawLine(x, py + 14f + i * 7f, x + 22f, py + 14f + i * 7f, paint)
            }
            paint.color = Color.rgb(128, 91, 57)
            c.drawRect(px + 22f, py + 48f, px + 75f, py + 55f, paint)
            for (i in 0..2) c.drawRect(px + 26f + i * 16f, py + 55f, px + 38f + i * 16f, py + 61f, paint)

            // Banana/coconut/betel-nut cluster makes the homestead silhouette distinct.
            for (i in 0..5) {
                val x = w * (.08f + i * .16f)
                val y = g - 6f
                drawPalmTree(c, x, y, .48f + (i % 2) * .10f, phase + i)
            }

            // Bamboo bridge / foot crossing near the pond.
            paint.color = Color.rgb(151, 112, 65)
            c.drawRoundRect(RectF(w * .48f, g + 73f, w * .68f, g + 81f), 4f, 4f, paint)
            paint.strokeWidth = 2f
            for (i in 0..5) c.drawLine(w * (.49f + i * .035f), g + 72f, w * (.49f + i * .035f), g + 82f, paint)

            // Roadside tea/grocery stall.
            val sx = w * .08f
            drawShop(c, sx, g + 8f, .72f, seed + 20)
            paint.color = Color.rgb(235, 184, 65)
            c.drawCircle(sx + 34f, g - 12f, 7f, paint)
            c.drawCircle(sx + 54f, g - 12f, 7f, paint)

            // People, bicycles and poultry give the scene life without crowding it.
            for (i in 0..4) {
                val x = w * (.18f + i * .16f)
                drawPerson(c, x, g + 24f, .55f, sin(phase * 3f + i).toFloat() * 2f)
                if (i % 2 == 0) drawBicycle(c, x + 18f, g + 22f, .45f, phase + i)
            }
            for (i in 0..3) drawChicken(c, w * (.36f + i * .08f), g + 31f, .32f, phase + i)

            // A narrow irrigation channel connects the field to the pond.
            paint.color = Color.rgb(72, 135, 156)
            c.drawRect(w * .40f, g + 24f, w * .47f, h, paint)
            paint.color = Color.argb(70, 235, 250, 255)
            c.drawLine(w * .415f, g + 28f, w * .415f, h, paint)
        }

        private fun drawBambooFence(c: Canvas, x: Float, y: Float, width: Float, height: Float) {
            paint.color = Color.rgb(145, 105, 58)
            paint.strokeWidth = 2.2f
            var px = x
            while (px <= x + width) {
                c.drawLine(px, y - height * .35f, px, y + height * .65f, paint)
                px += 10f
            }
            c.drawLine(x, y, x + width, y, paint)
            c.drawLine(x, y + height * .45f, x + width, y + height * .45f, paint)
        }

        private fun drawPalmTree(c: Canvas, x: Float, y: Float, s: Float, phase: Float) {
            paint.color = Color.rgb(112, 82, 45)
            c.drawRoundRect(RectF(x - 4f*s, y - 76f*s, x + 4f*s, y), 3f, 3f, paint)
            paint.color = Color.rgb(37, 122, 61)
            for (i in 0..5) {
                val a = -1.15f + i * .46f
                val sway = sin(phase + i).toFloat() * 2.5f
                val ex = x + cos(a) * 29f * s + sway
                val ey = y - 78f*s + sin(a) * 20f*s
                c.drawLine(x, y - 78f*s, ex, ey, paint)
                c.drawCircle(ex, ey, 7f*s, paint)
            }
        }

        private fun drawCow(c: Canvas, x: Float, y: Float, s: Float) {
            paint.color = Color.rgb(221, 214, 191)
            c.drawOval(RectF(x - 25f*s, y - 14f*s, x + 20f*s, y + 8f*s), paint)
            c.drawCircle(x + 22f*s, y - 8f*s, 9f*s, paint)
            paint.color = Color.rgb(84, 67, 53)
            c.drawCircle(x + 24f*s, y - 10f*s, 2f*s, paint)
            paint.strokeWidth = 2f*s
            c.drawLine(x - 13f*s, y + 5f*s, x - 13f*s, y + 15f*s, paint)
            c.drawLine(x + 7f*s, y + 5f*s, x + 7f*s, y + 15f*s, paint)
        }

        private fun drawBicycle(c: Canvas, x: Float, y: Float, s: Float, phase: Float) {
            paint.color = Color.rgb(55, 70, 76)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f*s
            c.drawCircle(x - 10f*s, y, 9f*s, paint)
            c.drawCircle(x + 12f*s, y, 9f*s, paint)
            c.drawLine(x - 10f*s, y, x, y - 9f*s, paint)
            c.drawLine(x, y - 9f*s, x + 12f*s, y, paint)
            c.drawLine(x, y - 9f*s, x + 5f*s, y - 14f*s, paint)
            paint.style = Paint.Style.FILL
            if (running) c.drawCircle(x + sin(phase).toFloat() * 2f, y - 14f*s, 1.5f*s, paint)
        }

        private fun drawChicken(c: Canvas, x: Float, y: Float, s: Float, phase: Float) {
            paint.color = Color.rgb(239, 233, 211)
            c.drawOval(RectF(x - 9f*s, y - 7f*s, x + 8f*s, y + 5f*s), paint)
            c.drawCircle(x + 7f*s, y - 5f*s, 4f*s, paint)
            paint.color = Color.rgb(201, 55, 44)
            c.drawCircle(x + 9f*s, y - 7f*s, 2f*s, paint)
            paint.strokeWidth = 1.5f
            c.drawLine(x - 3f*s, y + 4f*s, x - 3f*s + sin(phase).toFloat()*2f, y + 9f*s, paint)
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

        private fun roadCenterAt(tRaw: Float, w: Float, sceneId: Int): Float {
            val t = tRaw.coerceIn(0f, 1f)
            val roadType = abs(sceneId * 17 + sceneId * 7) % 12
            val curveSeed = sceneId * 0.73f + roadType * 0.41f
            val curve = (sin(curveSeed + frame / 900.0) * 0.62 +
                    sin(curveSeed * 0.47 + frame / 1450.0) * 0.38).toFloat()
            val bend = curve * w * when (roadType) {
                3, 4, 7 -> 0.13f
                8, 9 -> 0.10f
                else -> 0.085f
            }
            return w * 0.50f + bend * t * t + laneOffset * w * t * 0.18f
        }

        private fun drawForestRouteDepth(c: Canvas, w: Float, h: Float, world: SmartScene) {
            // Forest routes reveal a distant track through the trees, so the world
            // does not feel like a flat straight road.
            val forest = world.region == "Forest" || world.region == "Forest Camp" ||
                    world.region == "Safari" || world.region == "Safari Lodge" ||
                    world.region == "Animal Rescue Center" || world.region == "Dinosaur Valley"
            if (!forest) return

            val horizon = h * .59f
            val seed = abs(world.id * 29 + worldSceneId * 11)
            val drift = sin(seed * .17 + frame / 520.0).toFloat()

            // Depth-scaled trunks and crowns form a natural forest corridor.
            val count = when (renderQuality.level()) { 0 -> 7; 1 -> 9; 2 -> 11; else -> 13 }
            for (i in 0 until count) {
                val side = if (i % 2 == 0) -1f else 1f
                val t = .08f + ((i * 17 + seed) % 72) / 100f
                val x0 = if (side < 0f) w * (.02f + .34f * t) else w * (.98f - .34f * t)
                val x = x0 + drift * (5f + 13f * t)
                val y = horizon + (h - horizon) * (.18f + .56f * t)
                val tw = 3f + 9f * t
                val th = 26f + 105f * t
                paint.color = if (i % 3 == 0) Color.rgb(73,61,43) else Color.rgb(91,69,45)
                c.drawRoundRect(RectF(x-tw,y-th,x+tw,y+8f),tw,tw,paint)
                paint.color = if (i % 2 == 0) Color.rgb(28,91,50) else Color.rgb(39,112,57)
                val crown = 18f + 34f * t
                c.drawCircle(x-crown*.55f,y-th-crown*.35f,crown,paint)
                c.drawCircle(x+crown*.25f,y-th-crown*.55f,crown*1.12f,paint)
                c.drawCircle(x+crown*.70f,y-th,crown*.72f,paint)
            }

            // A shallow branch track is visible far ahead; it is scenery, not a
            // second control route.
            val branch = Path()
            branch.moveTo(w*.47f,horizon+2f)
            branch.cubicTo(w*(.43f+drift*.025f),h*.63f,w*(.30f+drift*.045f),h*.69f,w*(.17f+drift*.07f),h*.76f)
            branch.lineTo(w*.24f,h*.77f)
            branch.cubicTo(w*(.38f+drift*.045f),h*.69f,w*(.48f+drift*.025f),h*.64f,w*.53f,horizon+4f)
            branch.close()
            paint.color = Color.rgb(102,86,64)
            c.drawPath(branch,paint)
            paint.color = Color.argb(105,220,205,168)
            paint.strokeWidth = 1.5f
            for (i in 1..5) {
                val t = i / 6f
                val y = horizon + h*.18f*t
                val x = w*(.49f-.30f*t+drift*.02f)
                c.drawLine(x-7f*t,y,x+11f*t,y+1.5f,paint)
            }
        }
        /** Procedural road material pass: perspective bands, shoulder wear and wet highlights. */
        private fun drawRoadMaterialPass(c: Canvas, w: Float, h: Float, world: SmartScene) {
            val horizon = h * 0.585f
            val bottom = h * 0.92f
            val segments = 18
            val wet = world.weather == "Rainy"
            for (i in 0 until segments) {
                val t0 = i.toFloat() / segments
                val t1 = (i + 1).toFloat() / segments
                val y0 = horizon + (bottom - horizon) * t0 * t0
                val y1 = horizon + (bottom - horizon) * t1 * t1
                val c0 = roadCenterAt(t0 * 0.92f, w, worldSceneId)
                val c1 = roadCenterAt(t1 * 0.92f, w, worldSceneId)
                val half0 = w * (0.045f + 0.39f * t0)
                val half1 = w * (0.045f + 0.39f * t1)
                val p = Path()
                p.moveTo(c0 - half0, y0); p.lineTo(c0 + half0, y0)
                p.lineTo(c1 + half1, y1); p.lineTo(c1 - half1, y1); p.close()
                paint.color = if (i % 2 == 0) Color.argb(if (wet) 18 else 8,255,255,255)
                    else Color.argb(if (wet) 12 else 5,20,25,28)
                c.drawPath(p, paint)
                paint.color = Color.argb(if (wet) 42 else 24,235,235,220)
                paint.strokeWidth = 1.2f + 2.8f * t1
                c.drawLine(c1-half1*.94f,y1,c1-half1*.99f,y1+1.5f,paint)
                c.drawLine(c1+half1*.94f,y1,c1+half1*.99f,y1+1.5f,paint)
                if (wet && i % 2 == 0) {
                    paint.color=Color.argb(32,180,210,220)
                    paint.strokeWidth=1f+2f*t1
                    c.drawLine(c0-half0*.32f,y0,c1-half1*.32f,y1,paint)
                    c.drawLine(c0+half0*.32f,y0,c1+half1*.32f,y1,paint)
                }
            }
        }

        private fun drawRoad(c: Canvas, w: Float, h: Float, world: SmartScene) {
            // A deterministic road generator creates many distinct road families while
            // keeping the renderer asset-light: urban boulevard, highway, village road,
            // mountain pass, bridge, coastal road, dirt track, wetland causeway and
            // railway-crossing approaches. The scene id selects the road, so progression
            // continually exposes new road geometry instead of repeating one template.
            val horizonY = h * 0.60f
            val bottomY = h
            val roadType = abs(world.id * 17 + worldSceneId * 7) % 12
            val curveSeed = world.id * 0.73f + roadType * 0.41f
            val curve = (sin(curveSeed + frame / 900.0) * 0.62 +
                    sin(curveSeed * 0.47 + frame / 1450.0) * 0.38).toFloat()
            val bend = curve * w * when (roadType) {
                3, 4, 7 -> 0.13f
                8, 9 -> 0.10f
                else -> 0.085f
            }

            fun roadCenter(t: Float): Float =
                w * 0.50f + bend * t * t + laneOffset * w * t * 0.18f

            fun roadHalfWidth(t: Float): Float {
                val base = when (roadType) {
                    0 -> 0.44f   // city boulevard
                    1 -> 0.47f   // divided/highway
                    2 -> 0.36f   // village
                    3 -> 0.40f   // mountain
                    4 -> 0.43f   // bridge
                    5 -> 0.45f   // coastal
                    6 -> 0.34f   // dirt
                    7 -> 0.39f   // wetland
                    8 -> 0.46f   // airport/industrial
                    9 -> 0.42f   // railway approach
                    10 -> 0.38f  // forest
                    else -> 0.45f
                }
                return w * (0.022f + base * t.pow(1.10f))
            }

            val road = Path()
            road.moveTo(roadCenter(0f) - roadHalfWidth(0f), horizonY)
            for (i in 1..28) {
                val t = i / 28f
                val y = horizonY + (bottomY - horizonY) * t
                road.lineTo(roadCenter(t) - roadHalfWidth(t), y)
            }
            for (i in 28 downTo 0) {
                val t = i / 28f
                val y = horizonY + (bottomY - horizonY) * t
                road.lineTo(roadCenter(t) + roadHalfWidth(t), y)
            }
            road.close()

            val asphaltTop = when (roadType) {
                6 -> Color.rgb(116, 101, 78)
                4, 7 -> Color.rgb(61, 72, 72)
                else -> Color.rgb(67, 70, 73)
            }
            val asphaltMid = when (roadType) {
                6 -> Color.rgb(91, 78, 59)
                else -> Color.rgb(43, 45, 47)
            }
            val asphaltBottom = when (roadType) {
                6 -> Color.rgb(67, 57, 43)
                else -> Color.rgb(31, 32, 34)
            }
            paint.shader = LinearGradient(
                0f, horizonY, 0f, bottomY,
                intArrayOf(asphaltTop, asphaltMid, asphaltBottom),
                floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP
            )
            c.drawPath(road, paint)
            paint.shader = null

            // Road shoulders and edge lines.
            val shoulderColor = if (roadType == 6) Color.rgb(151, 126, 88) else Color.rgb(190, 188, 174)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = if (roadType == 6) 6f else 4.5f
            paint.color = shoulderColor
            c.drawPath(road, paint)
            paint.style = Paint.Style.FILL

            // Dashed/solid edge lines vary by road class.
            for (side in -1..1 step 2) {
                var t = 0.035f
                while (t < 1f) {
                    val y = horizonY + (bottomY - horizonY) * t
                    val cx = roadCenter(t)
                    val edge = cx + side * roadHalfWidth(t)
                    val thickness = 1.3f + 3.2f * t
                    paint.color = if (roadType == 6) Color.rgb(181, 147, 92) else Color.rgb(235, 235, 222)
                    if (roadType == 2 || roadType == 10) {
                        c.drawRoundRect(RectF(edge - thickness, y, edge + thickness, y + 5f + 12f*t), thickness, thickness, paint)
                    } else {
                        c.drawRect(edge - thickness, y, edge + thickness, y + 4f + 10f*t, paint)
                    }
                    t += 0.11f + 0.13f*t
                }
            }

            // Lane system: one, two or three visible lanes depending on the road.
            val laneCount = when (roadType) {
                1, 0, 8 -> 3
                2, 6, 10 -> 1
                else -> 2
            }
            if (laneCount > 1) {
                for (lane in 1 until laneCount) {
                    val normalized = lane.toFloat() / laneCount
                    var t = 0.02f
                    while (t < 1f) {
                        val y = horizonY + (bottomY - horizonY) * t
                        val cx = roadCenter(t)
                        val hw = roadHalfWidth(t)
                        val x = cx - hw + hw * 2f * normalized
                        val thickness = 1.0f + 4.5f*t
                        val dashLen = 5f + 26f*t
                        val color = if (roadType == 0 || roadType == 1 || roadType == 8)
                            Color.rgb(232,232,220) else Color.rgb(216,216,204)
                        paint.color = color
                        c.drawRoundRect(RectF(x-thickness, y, x+thickness, y+dashLen), thickness, thickness, paint)
                        t += 0.075f + 0.15f*t
                    }
                }
            }

            // Central divider/median for major roads. It gives the player a realistic
            // sense of opposing traffic without changing the one-tap control model.
            if (roadType == 0 || roadType == 1 || roadType == 8) {
                var t = 0.025f
                while (t < 1f) {
                    val y = horizonY + (bottomY - horizonY) * t
                    val cx = roadCenter(t)
                    val hw = roadHalfWidth(t)
                    paint.color = Color.rgb(246, 239, 177)
                    c.drawRoundRect(RectF(cx-2.2f-3.5f*t, y, cx+2.2f+3.5f*t, y+8f+22f*t), 3f, 3f, paint)
                    t += 0.105f + 0.16f*t
                }
            } else {
                // Center line for normal two-way roads.
                var t = 0.015f
                val travel = if (running) (frame * 0.010f) % 1f else 0f
                while (t < 1f) {
                    val tt = (t + travel) % 1f
                    val y = horizonY + (bottomY - horizonY) * tt
                    val cx = roadCenter(tt)
                    val half = 1.3f + 6.5f*tt
                    val length = 6f + 32f*tt
                    paint.color = if (roadType == 6) Color.rgb(214,184,112) else Color.rgb(248,247,236)
                    c.drawRoundRect(RectF(cx-half,y,cx+half,y+length),half,half,paint)
                    t += 0.105f + 0.16f*tt
                }
            }

            // Special road surfaces/structures.
            if (roadType == 4) {
                // Bridge deck expansion joints.
                paint.color = Color.argb(90, 215, 220, 220)
                for (i in 1..7) {
                    val t = i/8f
                    val y = horizonY + (bottomY-horizonY)*t
                    val cx = roadCenter(t)
                    val hw = roadHalfWidth(t)
                    c.drawRect(cx-hw,y,cx+hw,y+2f+3f*t,paint)
                }
            }
            if (roadType == 9 || world.region.contains("Railway", true)) {
                // Railway crossing warning bands.
                for (i in 0..3) {
                    val t = 0.44f + i*0.028f
                    val y = horizonY + (bottomY-horizonY)*t
                    val cx = roadCenter(t)
                    val hw = roadHalfWidth(t)*.96f
                    paint.color = if (i%2==0) Color.rgb(245,245,238) else Color.rgb(45,48,50)
                    c.drawRect(cx-hw,y,cx+hw,y+3f+4f*t,paint)
                }
            }

            // Asphalt micro-texture. Keep density adaptive so realism scales with hardware.
            paint.color = Color.argb(if (renderQuality.supportsModernGraphics()) 42 else 28,255,255,255)
            val textureCount = if (renderQuality.supportsModernGraphics()) 44 else 24
            for (i in 0 until textureCount) {
                val t = ((i*0.071f + frame*0.0012f) % 1f).coerceIn(0f,1f)
                val y = horizonY + (bottomY-horizonY)*t
                val cx = roadCenter(t)
                val hw = roadHalfWidth(t)
                val x = cx + sin(i*7.3).toFloat()*hw*.72f
                c.drawCircle(x,y,.6f+1.8f*t,paint)
            }

            // Raised reflectors and delineator posts, positioned in perspective.
            for (side in -1..1 step 2) {
                for (i in 1..9) {
                    val tt = i/10f
                    val y = horizonY + (bottomY-horizonY)*tt
                    val cx = roadCenter(tt)
                    val edge = cx + side*roadHalfWidth(tt)
                    paint.color = if (roadType == 6) Color.rgb(236,190,76) else Color.rgb(255,214,78)
                    c.drawCircle(edge,y,1.8f+3.7f*tt,paint)
                    val postHeight = 9f+22f*tt
                    paint.color = Color.rgb(225,225,215)
                    c.drawRoundRect(RectF(
                        edge + side*(5f+12f*tt), y-postHeight,
                        edge + side*(10f+15f*tt), y
                    ),2f,2f,paint)
                }
            }

            // Rain/wet-road response.
            if (world.weather == "Rainy") {
                paint.color = Color.argb(58,190,220,235)
                for (i in 0..9) {
                    val tt = .12f+i*.09f
                    val y = horizonY+(bottomY-horizonY)*tt
                    val cx = roadCenter(tt)
                    val hw = roadHalfWidth(tt)*.74f
                    c.drawRoundRect(RectF(cx-hw,y,cx+hw,y+2f+4f*tt),3f,3f,paint)
                }
            }

            // Motion particles and wheel spray stay close to the vehicle so the world
            // remains readable and the learning card stays unobstructed.
            if (running && speed > .004f) {
                paint.color = if (world.weather=="Rainy") Color.argb(60,225,240,245) else Color.argb(38,235,235,225)
                for (i in 0..9) {
                    val spread=(i-4.5f)*13f
                    val yy=h*(.85f+(i%3)*.025f)
                    c.drawCircle(w/2f+spread,yy,2f+(i%3),paint)
                }
            }
        }

        private fun drawAnimals(c: Canvas, w: Float, h: Float, world: SmartScene) {
            val base = h * .61f
            val travel = if (running) frame.toFloat() * 0.85f else 0f
            val primaryPhase = sin(frame / 7.0).toFloat()
            val secondaryPhase = sin(frame / 9.5 + 1.4).toFloat()
            val drift = if (running) sin(frame / 24.0).toFloat() * w * .018f else 0f

            // Rare wildlife encounters: a predator can suddenly leave the habitat,
            // sprint beside/across the road, then disappear. The event is deterministic
            // (not random every frame), so it remains reproducible and lightweight.
            val encounterWindow = ((frame + world.id * 137L) % 960L).toFloat() / 960f
            val aggressive = running && encounterWindow < .17f
            if (aggressive) {
                val progress = (encounterWindow / .17f).coerceIn(0f, 1f)
                val chargeX = if (world.id % 2 == 0) -w*.12f + progress*w*1.24f
                               else w*1.12f - progress*w*1.24f
                // Aggressive encounters now cover the three natural domains:
                // land, water and sky. The event stays brief and non-contact so the
                // child sees a living world without turning the learning game into
                // a combat mechanic.
                val water = world.region == "Ocean" || world.region == "Island" || world.region == "Wetland" ||
                        world.region == "Harbor" || world.region == "Beach Town" || world.region == "Fishing Village"
                val air = world.region == "Sky" || world.region == "Airport" || world.region == "Mountain" ||
                        world.region == "Dinosaur Valley"
                if (water) {
                    val waterX = if (world.id % 2 == 0) -w*.10f + progress*w*1.20f
                                 else w*1.10f - progress*w*1.20f
                    val waterY = h * (.73f + .055f * sin(frame / 9.0).toFloat())
                    val predator = when (world.id % 3) {
                        0 -> "shark"
                        1 -> "crocodile"
                        else -> "orca"
                    }
                    drawAquaticPredator(c, predator, waterX, waterY, .72f + progress*.28f, true)
                } else if (air) {
                    val airX = if (world.id % 2 == 0) -w*.16f + progress*w*1.32f
                               else w*1.16f - progress*w*1.32f
                    val airY = h * (.24f + .055f * sin(frame / 7.0).toFloat())
                    drawAirPredator(c, airX, airY, .62f + progress*.28f, world.id % 2 == 0)
                } else {
                    val predator = when (world.id % 4) {
                        0 -> "tiger"
                        1 -> "cheetah"
                        2 -> "dinosaur"
                        else -> "elephant"
                    }
                    drawAnimatedAnimal(c, predator, chargeX, base + 8f, travel*.18f, aggressive = true, chargeProgress = progress)
                }
            } else {
                when (world.region) {
                    "Dinosaur Valley" -> {
                        drawAnimatedAnimal(c, "dinosaur", w*.78f+drift, base+primaryPhase*3f, travel)
                        drawAnimatedAnimal(c, if (world.id%2==0) "dinosaur" else "bird",
                            w*.18f-drift, base-28f+secondaryPhase*2f, -travel*.7f)
                    }
                    "Forest" -> {
                        val a=when(world.id%4){0->"bear";1->"deer";2->"fox";else->"bird"}
                        val b=when(world.id%3){0->"bird";1->"deer";else->"fox"}
                        drawAnimatedAnimal(c,a,w*.16f+drift,base+primaryPhase*2f,travel)
                        drawAnimatedAnimal(c,b,w*.76f-drift,base-20f+secondaryPhase*2f,-travel*.65f)
                    }
                    "Safari" -> {
                        val a=when(world.id%4){0->"elephant";1->"giraffe";2->"zebra";else->"lion"}
                        val b=when(world.id%3){0->"zebra";1->"lion";else->"giraffe"}
                        drawAnimatedAnimal(c,a,w*.78f+drift,base+primaryPhase*2.5f,travel)
                        drawAnimatedAnimal(c,b,w*.20f-drift,base-12f+secondaryPhase*2f,-travel*.55f)
                    }
                    "Ocean","Island","Wetland" -> {
                        val a=when(world.id%4){0->"dolphin";1->"fish";2->"crocodile";else->"bird"}
                        val b=if(world.id%2==0)"fish" else "bird"
                        drawAnimatedAnimal(c,a,w*.78f+drift,base-5f+primaryPhase*5f,travel)
                        drawAnimatedAnimal(c,b,w*.28f-drift,base-42f+secondaryPhase*4f,-travel*.8f)
                    }
                    "Arctic" -> {
                        drawAnimatedAnimal(c,"penguin",w*.76f+drift,base+primaryPhase*2f,travel)
                        drawAnimatedAnimal(c,"penguin",w*.30f-drift,base-4f+secondaryPhase*2f,-travel*.6f)
                    }
                    else -> {
                        val a=if(world.id%2==0)"elephant" else "bear"
                        val b=if(world.id%3==0)"bird" else "fox"
                        drawAnimatedAnimal(c,a,w*.80f+drift,base+primaryPhase*2f,travel)
                        drawAnimatedAnimal(c,b,w*.18f-drift,base-18f+secondaryPhase*2f,-travel*.55f)
                    }
                }
            }

            // Prehistoric sky encounters: pterosaurs and giant flying predators appear
            // only occasionally, with wing beats and depth scaling.
            val skyCycle = ((frame + world.id * 211L) % 1500L).toFloat() / 1500f
            if (running && (world.region == "Dinosaur Valley" || world.id % 9 == 0) && skyCycle < .30f) {
                val p = skyCycle/.30f
                val fx = if (world.id%2==0) -w*.18f+p*w*1.36f else w*1.18f-p*w*1.36f
                val fy = h*.22f + sin(frame/8.0).toFloat()*18f
                drawFlyingPrehistoric(c,fx,fy,1.0f+0.35f*sin(world.id*1.7f).toFloat(), world.id%3==0)
            }
        }

        private fun drawAquaticPredator(c: Canvas, kind: String, x: Float, y: Float, scale: Float, aggressive: Boolean) {
            // Lightweight water predator renderer: no bitmap assets, depth-friendly
            // silhouettes, surface wake and a restrained motion cue.
            c.save()
            c.translate(x, y)
            c.scale(scale, scale)
            val surge = if (aggressive) abs(sin(frame / 3.8)).toFloat() * 5f else 0f

            paint.color = Color.argb(55, 10, 55, 70)
            c.drawOval(RectF(-92f, 18f, 92f, 34f), paint)

            when (kind) {
                "shark" -> {
                    paint.color = Color.rgb(88, 112, 121)
                    val body = Path()
                    body.moveTo(-78f, 0f)
                    body.cubicTo(-32f, -28f, 38f, -26f, 78f, 0f)
                    body.cubicTo(38f, 27f, -32f, 28f, -78f, 0f)
                    body.close()
                    c.drawPath(body, paint)
                    val fin = Path()
                    fin.moveTo(-8f, -13f); fin.lineTo(8f, -52f); fin.lineTo(28f, -12f); fin.close()
                    c.drawPath(fin, paint)
                    val tail = Path()
                    tail.moveTo(74f, 0f); tail.lineTo(104f, -28f); tail.lineTo(98f, 0f); tail.lineTo(104f, 28f); tail.close()
                    c.drawPath(tail, paint)
                    paint.color = Color.rgb(235, 241, 238)
                    c.drawOval(RectF(-52f, -2f, 48f, 19f), paint)
                    paint.color = Color.rgb(30, 42, 45)
                    c.drawCircle(48f, -5f, 3.2f, paint)
                }
                "orca" -> {
                    paint.color = Color.rgb(24, 35, 40)
                    c.drawOval(RectF(-76f, -25f, 72f, 22f), paint)
                    val fin = Path()
                    fin.moveTo(-5f, -18f); fin.lineTo(8f, -60f); fin.lineTo(25f, -18f); fin.close()
                    c.drawPath(fin, paint)
                    val tail = Path()
                    tail.moveTo(68f, 0f); tail.lineTo(103f, -24f); tail.lineTo(92f, 0f); tail.lineTo(103f, 24f); tail.close()
                    c.drawPath(tail, paint)
                    paint.color = Color.rgb(235, 241, 238)
                    c.drawOval(RectF(-54f, -8f, 32f, 15f), paint)
                    paint.color = Color.rgb(35, 45, 48)
                    c.drawCircle(48f, -8f, 3f, paint)
                }
                else -> {
                    paint.color = Color.rgb(69, 92, 76)
                    c.drawOval(RectF(-86f, -12f, 74f, 18f), paint)
                    val jaw = Path()
                    jaw.moveTo(50f, 0f); jaw.lineTo(104f, -12f); jaw.lineTo(88f, 10f); jaw.close()
                    c.drawPath(jaw, paint)
                    paint.color = Color.rgb(205, 191, 141)
                    c.drawOval(RectF(18f, 2f, 82f, 15f), paint)
                    paint.color = Color.rgb(30, 40, 32)
                    c.drawCircle(55f, -7f, 3f, paint)
                    for (i in -2..2) c.drawLine(-50f + i*18f, 12f, -42f + i*18f, 24f + surge, paint)
                }
            }

            // Wake/surface ripples anchor the animal to the water.
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            paint.color = Color.argb(115, 205, 238, 244)
            c.drawArc(RectF(-108f, 18f, -22f, 48f), 190f, 110f, false, paint)
            c.drawArc(RectF(12f, 16f, 98f, 45f), 210f, 105f, false, paint)
            paint.style = Paint.Style.FILL
            c.restore()
        }

        private fun drawAirPredator(c: Canvas, x: Float, y: Float, scale: Float, dark: Boolean) {
            // Distant raptor/eagle-like silhouette: wing beats, body tilt and soft
            // motion trail create a believable aerial encounter without assets.
            c.save()
            c.translate(x, y)
            c.scale(scale, scale)
            val flap = sin(frame / 3.5).toFloat() * 18f
            paint.color = if (dark) Color.rgb(57, 55, 51) else Color.rgb(86, 76, 62)
            val left = Path()
            left.moveTo(0f, 0f); left.lineTo(-88f, -34f - flap); left.lineTo(-34f, 9f); left.lineTo(0f, 5f); left.close()
            c.drawPath(left, paint)
            val right = Path()
            right.moveTo(0f, 0f); right.lineTo(88f, -34f + flap); right.lineTo(34f, 9f); right.lineTo(0f, 5f); right.close()
            c.drawPath(right, paint)
            c.drawOval(RectF(-14f, -6f, 25f, 11f), paint)
            val beak = Path()
            beak.moveTo(22f, 0f); beak.lineTo(48f, 5f); beak.lineTo(22f, 9f); beak.close()
            c.drawPath(beak, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.6f
            paint.color = Color.argb(75, 255, 255, 255)
            c.drawLine(-62f, 14f, 58f, 18f, paint)
            paint.style = Paint.Style.FILL
            c.restore()
        }

        private fun drawAnimatedAnimal(
            c: Canvas,
            kind: String,
            x: Float,
            y: Float,
            travel: Float,
            aggressive: Boolean = false,
            chargeProgress: Float = 0f
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

            paint.color = Color.argb(renderQuality.shadowAlpha() + (depth * 18f).toInt(), 0, 0, 0)
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

        private fun drawTiger(c: Canvas,x:Float,y:Float,aggressive:Boolean){
            paint.color=Color.rgb(214,142,42)
            c.drawOval(RectF(x-70f,y-45f,x+58f,y+8f),paint)
            c.drawCircle(x+57f,y-42f,28f,paint)
            paint.color=Color.BLACK
            for(i in -2..2){ c.drawRect(x-35f+i*20f,y-43f+i*2f,x-28f+i*20f,y+1f+i*2f,paint) }
            c.drawCircle(x+48f,y-50f,3f,paint); c.drawCircle(x+66f,y-50f,3f,paint)
            paint.style=Paint.Style.STROKE; paint.strokeWidth=7f
            val run=if(aggressive) abs(sin(frame/3.0)).toFloat()*18f else 0f
            c.drawLine(x-38f,y-5f,x-55f,y+25f-run,paint); c.drawLine(x+25f,y-5f,x+42f,y+25f-run,paint)
            paint.style=Paint.Style.FILL
        }

        private fun drawCheetah(c: Canvas,x:Float,y:Float,aggressive:Boolean){
            paint.color=Color.rgb(208,164,62)
            c.drawOval(RectF(x-78f,y-38f,x+62f,y+5f),paint)
            c.drawCircle(x+65f,y-35f,23f,paint)
            paint.color=Color.BLACK
            for(i in 0..11){ val sx=x-48f+(i%6)*17f; val sy=y-28f+(i/6)*17f; c.drawCircle(sx,sy,2.5f,paint) }
            c.drawCircle(x+57f,y-42f,3f,paint); c.drawCircle(x+73f,y-42f,3f,paint)
            paint.style=Paint.Style.STROKE; paint.strokeWidth=6f
            val run=if(aggressive) abs(sin(frame/2.7)).toFloat()*20f else 0f
            c.drawLine(x-45f,y-2f,x-75f,y+22f-run,paint); c.drawLine(x+25f,y-2f,x+55f,y+22f-run,paint)
            paint.style=Paint.Style.FILL
        }

        private fun drawFlyingPrehistoric(c:Canvas,x:Float,y:Float,scale:Float,menacing:Boolean){
            c.save(); c.translate(x,y); c.scale(scale,scale)
            val flap=sin(frame/4.0).toFloat()*22f
            paint.color=if(menacing) Color.rgb(75,65,62) else Color.rgb(105,88,70)
            val wing=Path()
            wing.moveTo(0f,0f); wing.lineTo(-95f,-45f-flap); wing.lineTo(-35f,8f); wing.lineTo(0f,4f); wing.close()
            c.drawPath(wing,paint)
            val wing2=Path()
            wing2.moveTo(0f,0f); wing2.lineTo(95f,-45f+flap); wing2.lineTo(35f,8f); wing2.lineTo(0f,4f); wing2.close()
            c.drawPath(wing2,paint)
            paint.color=Color.rgb(85,70,60)
            c.drawOval(RectF(-18f,-12f,42f,18f),paint)
            c.drawCircle(45f,-2f,12f,paint)
            paint.color=Color.rgb(220,205,170); c.drawCircle(49f,-5f,2.5f,paint)
            paint.style=Paint.Style.STROKE; paint.strokeWidth=4f
            c.drawLine(53f,2f,68f,7f,paint)
            paint.style=Paint.Style.FILL
            c.restore()
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
            val selected = LearnovaUnlimitedWorld.vehicles[vehicle.coerceIn(0, LearnovaUnlimitedWorld.vehicles.lastIndex)]
            val p = vehicleProgress.coerceIn(0f, 1f)

            // Camera-follow placement uses the exact road centerline used by the
            // road renderer, so the vehicle visually stays on the pavement through bends.
            val roadX = roadCenterAt(p, w, worldSceneId)
            val cx = roadX + laneOffset * w * (0.10f + 0.34f * p)
            val cy = h * (0.66f + 0.22f * p) +
                suspensionOffset +
                if (running) sin(frame / 4.5).toFloat() * (0.65f + 2.6f * p) else 0f
            val scale = 0.42f + 0.80f * p

            // Contact shadow reacts to height/suspension.
            paint.color = Color.argb((75 + 55 * p).toInt(), 0, 0, 0)
            c.drawOval(
                RectF(cx - 92f * scale, cy + 31f * scale,
                      cx + 92f * scale, cy + 54f * scale),
                paint
            )

            c.save()
            val bodyLean = vehicleHeading +
                steering * 3.2f +
                suspensionOffset * 0.55f +
                if (running) sin(frame / 9.0).toFloat() * 0.55f else 0f
            c.rotate(bodyLean, cx, cy)
            c.scale(scale, scale, cx, cy)

            // Vehicle-specific procedural rendering. Different real-world classes
            // get distinct proportions/details while sharing lightweight primitives.
            when (selected.kind) {
                "car" -> drawCar(c, cx, cy, selected.id)
                "bus" -> drawBus(c, cx, cy, selected.id)
                "truck" -> drawTruck(c, cx, cy, selected.id)
                "sixWheel" -> drawSixWheel(c, cx, cy, selected.id)
                "van" -> drawVan(c, cx, cy, selected.id)
                "bike" -> drawBike(c, cx, cy)
                "sportBike" -> drawSportBike(c, cx, cy)
                "bicycle" -> drawBicycle(c, cx, cy)
                "air" -> drawPlane(c, cx, cy)
                "boat" -> drawBoat(c, cx, cy)
                "train" -> drawTrain(c, cx, cy)
                "space" -> drawRocket(c, cx, cy)
                "micro" -> drawMicro(c, cx, cy)
                else -> drawCar(c, cx, cy, selected.id)
            }
            // Glass/body highlight: a restrained specular pass makes the procedural
            // vehicle read as a solid manufactured object instead of a flat silhouette.
            paint.shader = LinearGradient(
                cx - 70f * scale, cy - 82f * scale,
                cx + 70f * scale, cy + 12f * scale,
                Color.argb(72, 255, 255, 255),
                Color.argb(0, 255, 255, 255),
                Shader.TileMode.CLAMP
            )
            c.drawOval(
                RectF(cx - 78f * scale, cy - 74f * scale, cx + 78f * scale, cy + 10f * scale),
                paint
            )
            paint.shader = null

            c.restore()

            // Vehicle lighting: subtle forward beams at night and red brake lamps
            // make the vehicle read more naturally without adding bitmap assets.
            if (selected.kind != "air" && selected.kind != "space" && (worldSceneId > 0)) {
                if (LearnovaUnlimitedWorld.scene(worldSceneId).time == "Night") {
                    paint.color = Color.argb(32, 255, 244, 190)
                    val beam = Path()
                    beam.moveTo(cx - 42f * scale, cy - 12f * scale)
                    beam.lineTo(cx - 150f * scale, cy - 48f * scale)
                    beam.lineTo(cx - 150f * scale, cy + 18f * scale)
                    beam.close()
                    c.drawPath(beam, paint)
                    val beam2 = Path()
                    beam2.moveTo(cx + 42f * scale, cy - 12f * scale)
                    beam2.lineTo(cx + 150f * scale, cy - 48f * scale)
                    beam2.lineTo(cx + 150f * scale, cy + 18f * scale)
                    beam2.close()
                    c.drawPath(beam2, paint)
                }
                if (!running && speed > 0.001f) {
                    paint.color = Color.argb(205, 230, 45, 38)
                    c.drawCircle(cx - 48f * scale, cy + 13f * scale, 4f * scale, paint)
                    c.drawCircle(cx + 48f * scale, cy + 13f * scale, 4f * scale, paint)
                }
            }

            // Tiny speed streaks only at higher speed.
            if (running && speed > 0.010f && selected.kind != "air" && selected.kind != "space") {
                paint.color = Color.argb(38, 255, 255, 255)
                paint.strokeWidth = 2f
                c.drawLine(cx - 70f, cy + 8f, cx - 125f, cy + 13f, paint)
                c.drawLine(cx + 70f, cy + 12f, cx + 125f, cy + 17f, paint)
            }
        }

        private fun drawSixWheel(c: Canvas, x: Float, y: Float, variant: Int) {
            // True 6-wheel layout: three near-side wheels plus three far-side wheels,
            // with the far row partially occluded by the chassis for believable depth.
            val body = when (variant % 4) {
                0 -> Color.rgb(62, 88, 76)
                1 -> Color.rgb(42, 72, 92)
                2 -> Color.rgb(118, 72, 42)
                else -> Color.rgb(92, 96, 102)
            }
            val darkBody = Color.rgb(
                (Color.red(body) * .48f).toInt(),
                (Color.green(body) * .48f).toInt(),
                (Color.blue(body) * .48f).toInt()
            )

            paint.color = Color.argb(82, 0, 0, 0)
            c.drawOval(RectF(x - 132f, y + 40f, x + 132f, y + 68f), paint)

            val wheelXs = floatArrayOf(-84f, 0f, 84f)

            // Far-side wheels first: smaller and slightly higher to establish depth.
            for (wx in wheelXs) {
                drawWheel(c, x + wx + 7f, y + 35f, wheelSpin)
                paint.color = Color.argb(105, 18, 22, 24)
                c.drawOval(RectF(x + wx - 5f, y + 24f, x + wx + 19f, y + 55f), paint)
            }

            // Heavy 6x6 chassis.
            paint.shader = LinearGradient(
                x, y - 54f, x, y + 48f,
                body, darkBody, Shader.TileMode.CLAMP
            )
            c.drawRoundRect(RectF(x - 118f, y - 48f, x + 118f, y + 42f), 20f, 20f, paint)
            paint.shader = null

            // Raised expedition cabin.
            paint.color = Color.rgb(35, 55, 61)
            val cabin = Path()
            cabin.moveTo(x - 72f, y - 44f)
            cabin.lineTo(x - 48f, y - 92f)
            cabin.lineTo(x + 43f, y - 92f)
            cabin.lineTo(x + 76f, y - 43f)
            cabin.close()
            c.drawPath(cabin, paint)

            paint.color = Color.rgb(38, 74, 82)
            c.drawRoundRect(RectF(x - 42f, y - 82f, x - 3f, y - 52f), 6f, 6f, paint)
            c.drawRoundRect(RectF(x + 4f, y - 82f, x + 43f, y - 52f), 6f, 6f, paint)

            // Exposed side rails and wheel arches.
            paint.color = Color.rgb(24, 28, 30)
            c.drawRoundRect(RectF(x - 126f, y + 22f, x + 126f, y + 47f), 8f, 8f, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 6f
            for (wx in wheelXs) {
                c.drawArc(RectF(x + wx - 22f, y + 19f, x + wx + 22f, y + 63f), 180f, 180f, false, paint)
            }
            paint.style = Paint.Style.FILL

            // Near-side wheels: three large, independently spinning wheels.
            for (wx in wheelXs) {
                drawWheel(c, x + wx, y + 47f, wheelSpin)
            }

            // Axles and visible suspension articulation.
            paint.color = Color.rgb(52, 57, 60)
            paint.strokeWidth = 5f
            for (wx in wheelXs) {
                c.drawLine(x + wx - 18f, y + 35f, x + wx + 18f, y + 35f, paint)
            }
            if (running) {
                paint.color = Color.argb(135, 235, 235, 220)
                val suspension = sin(frame / 6.0).toFloat() * 4f
                c.drawLine(x - 72f, y + 7f, x - 72f, y + 30f + suspension, paint)
                c.drawLine(x + 72f, y + 7f, x + 72f, y + 30f - suspension, paint)
            }

            // Roof rack, expedition lights and protective bumpers.
            paint.color = Color.rgb(24, 28, 30)
            paint.strokeWidth = 5f
            c.drawLine(x - 48f, y - 96f, x + 48f, y - 96f, paint)
            c.drawLine(x - 43f, y - 96f, x - 55f, y - 77f, paint)
            c.drawLine(x + 43f, y - 96f, x + 55f, y - 77f, paint)

            paint.color = Color.rgb(255, 242, 180)
            c.drawRoundRect(RectF(x - 111f, y - 2f, x - 94f, y + 12f), 4f, 4f, paint)
            paint.color = Color.rgb(210, 48, 42)
            c.drawRoundRect(RectF(x + 94f, y - 2f, x + 111f, y + 12f), 4f, 4f, paint)
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

        private fun drawTrain(c: Canvas, x: Float, y: Float) {
            paint.color = Color.argb(75, 0, 0, 0)
            c.drawOval(RectF(x - 105f, y + 34f, x + 105f, y + 58f), paint)

            paint.shader = LinearGradient(
                x, y - 78f, x, y + 42f,
                Color.rgb(210, 48, 48), Color.rgb(92, 22, 25), Shader.TileMode.CLAMP
            )
            c.drawRoundRect(RectF(x - 92f, y - 72f, x + 92f, y + 42f), 18f, 18f, paint)
            paint.shader = null

            paint.color = Color.rgb(28, 48, 55)
            c.drawRoundRect(RectF(x - 65f, y - 52f, x - 8f, y - 15f), 7f, 7f, paint)
            c.drawRoundRect(RectF(x + 8f, y - 52f, x + 65f, y - 15f), 7f, 7f, paint)

            paint.color = Color.rgb(245, 244, 220)
            c.drawCircle(x - 58f, y + 18f, 7f, paint)
            c.drawCircle(x + 58f, y + 18f, 7f, paint)

            paint.color = Color.rgb(42, 46, 50)
            c.drawRoundRect(RectF(x - 102f, y + 26f, x + 102f, y + 47f), 8f, 8f, paint)
            drawWheel(c, x - 63f, y + 43f, wheelSpin)
            drawWheel(c, x + 63f, y + 43f, wheelSpin)
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
            val top = h * .63f
            val left = 18f
            val right = w - 18f
            val lesson = SmartLearningEngine.lesson(question)

            paint.color = Color.argb(235, 255, 255, 255)
            c.drawRoundRect(RectF(left, top, right, top + 108f), 22f, 22f, paint)

            text.textAlign = Paint.Align.LEFT
            text.color = Color.rgb(27, 105, 69)
            text.textSize = 12f
            c.drawText(
                lesson.domain + " • " + lesson.spokenName,
                left + 18f, top + 23f, text
            )

            // The glyph is the visual anchor. Arabic is rendered large and right-to-left
            // so the child learns the actual script, not only a Latin label.
            text.color = Color.rgb(25, 35, 38)
            text.textSize = if (lesson.rtl) 38f else 34f
            c.drawText(lesson.display, left + 18f, top + 61f, text)

            text.color = Color.rgb(72, 84, 84)
            text.textSize = 11f
            c.drawText(
                lesson.example + " • " + lesson.sound,
                left + 18f, top + 81f, text
            )

            paint.color = Color.rgb(225, 232, 228)
            c.drawRoundRect(RectF(left + 18f, top + 88f, right - 112f, top + 95f), 4f, 4f, paint)
            paint.color = Color.rgb(28, 155, 91)
            c.drawRoundRect(
                RectF(
                    left + 18f,
                    top + 88f,
                    left + 18f + (right - left - 130f) * levelProgress,
                    top + 95f
                ), 4f, 4f, paint
            )

            paint.color = if (running) Color.rgb(20, 150, 83) else Color.rgb(45, 100, 80)
            c.drawRoundRect(RectF(right - 90f, top + 18f, right - 18f, top + 86f), 18f, 18f, paint)

            text.textAlign = Paint.Align.CENTER
            text.color = Color.WHITE
            text.textSize = 12f
            c.drawText(
                if (levelComplete) "NEXT" else if (running) "DRIVING" else "LEARN",
                right - 54f, top + 56f, text
            )

            text.color = Color.rgb(35, 105, 78)
            text.textSize = 10f
            c.drawText(
                lesson.prompt,
                w / 2f, top + 103f, text
            )
        }

        private fun speakCurrentLesson() {
            voice.speakSmartLesson(SmartLearningEngine.lesson(question))
        }

        private fun nextLesson() {
            if (!levelComplete) return
            completedLessons += 1
            val reward = ChildSafeEngagementPolicy.rewardForCorrectLesson(completedLessons)
            learningPoints += reward.points
            if (reward.celebration != ChildSafeEngagementPolicy.Celebration.NONE) {
                celebrationUntil = System.currentTimeMillis() + 1800L
            }
            question = (question + 1) % lessons.size
            lessonStage = 0
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

        private fun drawDistantWorld(c: Canvas, w: Float, h: Float, world: SmartScene) {
            // Living distant world: lightweight traffic, trains and aircraft are
            // rendered far behind the player and become visually larger as they approach.
            val horizon = h * 0.59f
            val travel = if (running) frame * 1.35f else 0f
            val seed = abs(world.id * 41 + 17)

            // Distant road traffic. Deterministic lanes keep it stable per scene.
            for (i in 0..5) {
                val lane = if (i % 2 == 0) -1f else 1f
                val loop = ((travel * (0.65f + (i % 3) * 0.17f) + i * 137f) % 900f) / 900f
                val depth = 0.08f + loop * 0.55f
                val roadY = horizon + depth * (h * 0.30f)
                val center = w / 2f + sin(frame / 180.0 + i).toFloat() * w * 0.035f
                val x = center + lane * (w * (0.018f + depth * 0.105f))
                val scale = 0.12f + depth * 0.46f
                val bw = 18f * scale
                val bh = 8f * scale
                paint.color = Color.argb((45 + 95 * depth).toInt(), 20, 24, 27)
                c.drawRoundRect(RectF(x - bw, roadY - bh, x + bw, roadY + bh), 3f * scale, 3f * scale, paint)
                paint.color = if (i % 3 == 0) Color.rgb(205, 55, 45) else Color.rgb(45, 105, 150)
                c.drawRoundRect(RectF(x - bw * .8f, roadY - bh * .72f, x + bw * .8f, roadY + bh * .72f), 2f, 2f, paint)
                if (world.time == "Night") {
                    paint.color = Color.argb((100 + 100 * depth).toInt(), 255, 240, 175)
                    c.drawCircle(x + lane * bw * .72f, roadY, 1.4f + scale * 2f, paint)
                }
            }

            // Railway corridor in suitable worlds: the train is a moving world object,
            // not a static decoration. It stays behind the main road in perspective.
            val hasRail = world.region.contains("Railway") ||
                    world.environment.contains("railway", ignoreCase = true) ||
                    world.region == "City" || world.region.contains("Village") ||
                    world.region.contains("Mountain") || world.region == "Bangladesh Village"
            if (hasRail) {
                val railY = horizon + h * 0.085f
                val railCenter = w * 0.74f + sin(frame / 240.0).toFloat() * w * .035f
                paint.color = Color.rgb(80, 77, 72)
                paint.strokeWidth = 2.2f
                c.drawLine(railCenter - w*.18f, railY - 5f, railCenter + w*.18f, railY + 7f, paint)
                c.drawLine(railCenter - w*.18f, railY + 4f, railCenter + w*.18f, railY + 16f, paint)
                paint.color = Color.rgb(116, 88, 57)
                for (i in 0..8) {
                    val x = railCenter - w*.18f + i * w*.045f
                    c.drawLine(x, railY - 9f, x, railY + 22f, paint)
                }

                val trainT = ((travel * .72f + seed * 11f) % 1100f) / 1100f
                val trainX = w * .50f + trainT * w * .42f
                val trainScale = .24f + trainT * .34f
                val tw = 105f * trainScale
                val th = 25f * trainScale
                paint.color = Color.rgb(178, 185, 188)
                c.drawRoundRect(RectF(trainX - tw, railY - th, trainX + tw, railY + th), 5f * trainScale, 5f * trainScale, paint)
                paint.color = Color.rgb(38, 91, 124)
                c.drawRect(trainX - tw*.82f, railY - th*.55f, trainX + tw*.78f, railY - th*.02f, paint)
                paint.color = Color.rgb(235, 240, 242)
                for (i in 0..3) {
                    val wx = trainX - tw*.62f + i * tw*.38f
                    c.drawRoundRect(RectF(wx, railY-th*.45f, wx+tw*.18f, railY-th*.12f), 2f,2f,paint)
                }
                if (world.time == "Night") {
                    paint.color = Color.rgb(255, 238, 170)
                    c.drawCircle(trainX + tw*.94f, railY, 3f + 4f*trainScale, paint)
                }
            }

            // Air traffic gives open/city/airport scenes a second layer of depth.
            if (world.region == "Airport" || world.region == "City" || world.region == "Beach Town" ||
                world.region == "Harbor" || world.region == "Mountain Town") {
                val ax = (w * .18f + ((travel * .18f + seed * 29f) % (w * .72f)))
                val ay = h * .23f + sin(frame / 90.0).toFloat() * 9f
                val a = Path()
                a.moveTo(ax - 26f, ay)
                a.lineTo(ax + 30f, ay - 4f)
                a.lineTo(ax + 10f, ay + 5f)
                a.lineTo(ax - 30f, ay + 5f)
                a.close()
                paint.color = Color.argb(185, 235, 240, 242)
                c.drawPath(a, paint)
                paint.color = Color.argb(150, 95, 145, 175)
                c.drawRect(ax - 8f, ay - 1f, ax + 8f, ay + 2f, paint)
            }
        }

        private fun drawRoadsideInteractions(c: Canvas, w: Float, h: Float, world: SmartScene) {
            // Small world interactions break visual repetition while keeping the
            // one-tap driving model and the lightweight Canvas renderer intact.
            if (!running) return

            val horizon = h * .59f
            val cycle = (frame % 360L) / 360f
            val seed = abs(world.id * 31 + 7)

            // Forest/village roadside movement: a bicycle or small animal briefly
            // appears beside the road instead of permanently occupying the scene.
            val active = world.region.contains("Village") ||
                    world.region.contains("Forest") ||
                    world.region.contains("Safari") ||
                    world.region.contains("Farm")
            if (active && cycle < .62f) {
                val t = .20f + .30f * (cycle / .62f)
                val y = horizon + (h - horizon) * t
                val cx = roadCenterAt(t, w, worldSceneId)
                val side = if (seed % 2 == 0) -1f else 1f
                val x = cx + side * w * (.075f + .07f * t)
                val s = .25f + .45f * t

                // Bicycle silhouette, intentionally distant and non-interactive.
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2.2f * s
                paint.color = Color.rgb(45, 52, 48)
                c.drawCircle(x - 12f*s, y, 8f*s, paint)
                c.drawCircle(x + 12f*s, y, 8f*s, paint)
                c.drawLine(x - 12f*s, y, x, y - 11f*s, paint)
                c.drawLine(x, y - 11f*s, x + 12f*s, y, paint)
                c.drawLine(x, y - 11f*s, x + 5f*s, y - 17f*s, paint)
                paint.style = Paint.Style.FILL

                // A subtle moving shadow anchors the object to the ground.
                paint.color = Color.argb(38, 20, 25, 20)
                c.drawOval(RectF(x - 17f*s, y + 6f*s, x + 17f*s, y + 11f*s), paint)
            }

            // A quick bird crossing gives forest and open-country scenes another
            // layer of motion without adding image assets.
            if ((world.region.contains("Forest") || world.region.contains("Mountain") ||
                    world.region.contains("Village")) && cycle > .18f && cycle < .48f) {
                val q = (cycle - .18f) / .30f
                val bx = w * (.08f + .84f * q)
                val by = h * (.30f + .035f * sin(frame / 11.0).toFloat())
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.5f
                paint.color = Color.argb(155, 42, 55, 48)
                c.drawArc(RectF(bx - 8f, by - 3f, bx, by + 5f), 205f, 115f, false, paint)
                c.drawArc(RectF(bx, by - 3f, bx + 8f, by + 5f), 220f, 115f, false, paint)
                paint.style = Paint.Style.FILL
            }
        }

        private fun drawEnvironmentMotion(c: Canvas, w: Float, h: Float, world: SmartScene) {
            // Lightweight procedural motion: foliage/water movement responds to the
            // same frame clock as the vehicle, giving the world a less static feel.
            val horizon = h * 0.59f
            val wind = sin(frame / 38.0).toFloat()
            val count = renderQuality.animalCount().coerceAtMost(9)

            if (world.weather != "Rainy") {
                paint.strokeWidth = 1.5f
                for (i in 0 until count) {
                    val t = (i + 1) / (count + 1f)
                    val side = if (i % 2 == 0) -1f else 1f
                    val x = w * (0.06f + 0.88f * t) + wind * (4f + 12f * t)
                    val y = horizon + (h * 0.30f) * t
                    val sway = wind * (3f + 7f * t)
                    paint.color = if (world.time == "Night")
                        Color.argb(105, 72, 105, 74)
                    else
                        Color.argb(125, 50, 108, 58)
                    c.drawLine(x, y, x + sway, y - (7f + 18f * t), paint)
                    c.drawCircle(x + sway, y - (9f + 18f * t), 2.2f + 3f * t, paint)
                }
            }

            // Small water glints are depth-scaled and only appear near water regions.
            if (world.region == "Ocean" || world.region == "Island" || world.region == "Wetland") {
                paint.strokeWidth = 1.2f
                for (i in 0..8) {
                    val t = (i + 1) / 10f
                    val x = w * (0.08f + 0.84f * t)
                    val y = h * (0.73f + 0.14f * t) + sin(frame / 20.0 + i).toFloat() * 2f
                    paint.color = Color.argb(75, 225, 245, 248)
                    c.drawLine(x - 8f * t, y, x + 8f * t, y, paint)
                }
            }
        }

        private fun drawRoadReflections(c: Canvas, w: Float, h: Float, world: SmartScene) {
            // Subtle wet-surface reflections add physical depth while staying asset-free.
            if (world.weather != "Rainy") return

            val horizon = h * 0.60f
            val center = w * 0.50f
            val roadGlow = Path()
            roadGlow.moveTo(center - w * 0.025f, horizon)
            roadGlow.lineTo(center + w * 0.025f, horizon)
            roadGlow.lineTo(center + w * 0.22f, h)
            roadGlow.lineTo(center - w * 0.22f, h)
            roadGlow.close()

            paint.shader = LinearGradient(
                0f, horizon, 0f, h,
                Color.argb(22, 220, 235, 242),
                Color.argb(4, 220, 235, 242),
                Shader.TileMode.CLAMP
            )
            c.drawPath(roadGlow, paint)
            paint.shader = null

            // Broken highlights follow perspective, so the wet road does not look flat.
            paint.strokeCap = Paint.Cap.ROUND
            for (i in 0..10) {
                val t = (i + 1) / 12f
                val y = horizon + (h - horizon) * t
                val half = w * (0.018f + 0.18f * t)
                val cx = center + sin(frame / 700.0 + i * 0.37).toFloat() * w * 0.012f
                paint.color = Color.argb((10 + 22 * t).toInt(), 235, 242, 245)
                paint.strokeWidth = 1.2f + 2.8f * t
                c.drawLine(cx - half, y, cx + half, y, paint)
            }
            paint.strokeCap = Paint.Cap.BUTT
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
            if (world.region.contains("Village") && running) {
                // Lightweight village traffic: a rickshaw/van silhouette occasionally
                // passes in the distance, moving with the road's perspective.
                val cycle = ((frame + world.id * 73L) % 420L).toFloat() / 420f
                if (cycle < .55f) {
                    val q = cycle / .55f
                    val t = .18f + .34f * q
                    val y = horizon + (h - horizon) * t
                    val x = roadCenterAt(t, w, worldSceneId) + if (world.id % 2 == 0) -w*.045f else w*.045f
                    drawVillageTraffic(c, x, y, .30f + .42f*t, world.id % 2 == 0)
                }
            }

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

        private fun drawVillageRoadsideDepth(c: Canvas, w: Float, h: Float, world: SmartScene) {
            // Project roadside details from the same road curve used by the vehicle.
            val horizon = h * .60f
            val seed = abs(world.id * 13 + 5)
            for (i in 0..5) {
                val t = .16f + i * .13f
                val y = horizon + (h - horizon) * t
                val cx = roadCenterAt(t, w, worldSceneId)
                val side = if ((i + seed) % 2 == 0) -1f else 1f
                val roadHalf = w * (.022f + .36f * t.pow(1.10f))
                val x = cx + side * (roadHalf + w * (.045f + .035f * t))
                val s = .45f + .55f * t

                paint.color = Color.rgb(137, 108, 70)
                c.drawOval(RectF(x - 16f*s, y - 4f*s, x + 16f*s, y + 6f*s), paint)

                // Small drainage/culvert detail beside the village road.
                paint.color = Color.rgb(91, 102, 91)
                c.drawRect(x - 11f*s, y - 8f*s, x + 11f*s, y + 2f*s, paint)
                paint.color = Color.rgb(75, 135, 151)
                c.drawRect(x - 7f*s, y - 6f*s, x + 7f*s, y - 2f*s, paint)

                paint.color = Color.rgb(128, 91, 56)
                paint.strokeWidth = 2f + 2f*t
                c.drawLine(x + side * 15f*s, y, x + side * 15f*s, y - 18f*s, paint)
                paint.color = Color.rgb(226, 202, 128)
                c.drawRoundRect(RectF(x + side * 12f*s, y - 22f*s, x + side * 22f*s, y - 15f*s), 2f, 2f, paint)
            }

            if (world.time != "Night") {
                paint.color = Color.argb(28, 25, 55, 28)
                for (i in 0..4) {
                    val t = .22f + i * .14f
                    val y = horizon + (h - horizon) * t
                    val cx = roadCenterAt(t, w, worldSceneId)
                    val drift = sin(frame / 42.0 + i).toFloat() * (4f + 7f*t)
                    c.drawOval(RectF(cx - 65f*t + drift, y + 7f, cx + 65f*t + drift, y + 15f), paint)
                }
            }
        }

        private fun drawVillageTraffic(c: Canvas, x: Float, y: Float, s: Float, reverse: Boolean) {
            val wheel = 7f * s
            paint.color = Color.rgb(42, 48, 48)
            c.drawCircle(x - 18f*s, y, wheel, paint)
            c.drawCircle(x + 18f*s, y, wheel, paint)
            paint.color = Color.rgb(31, 105, 91)
            c.drawRoundRect(RectF(x - 28f*s, y - 20f*s, x + 28f*s, y - 2f*s), 5f*s, 5f*s, paint)
            paint.color = Color.rgb(201, 166, 96)
            c.drawRoundRect(RectF(x - 21f*s, y - 31f*s, x + 20f*s, y - 15f*s), 5f*s, 5f*s, paint)
            paint.color = Color.argb(190, 205, 228, 232)
            c.drawRect(x - 15f*s, y - 28f*s, x - 2f*s, y - 18f*s, paint)
            c.drawRect(x + 2f*s, y - 28f*s, x + 15f*s, y - 18f*s, paint)
            if (reverse) {
                paint.color = Color.rgb(221, 54, 42)
                c.drawCircle(x + 27f*s, y - 7f*s, 2.5f*s, paint)
            } else {
                paint.color = Color.rgb(245, 222, 130)
                c.drawCircle(x - 27f*s, y - 7f*s, 2.5f*s, paint)
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

        /**
         * Cinematic lighting pass: adds depth, atmospheric perspective and a soft
         * road light response without shipping large bitmap textures. Android's
         * hardware Canvas is used by the View, so the pass stays GPU friendly.
         */
        private fun drawCinematicLighting(c: Canvas, w: Float, h: Float, world: SmartScene) {
            val horizon = h * 0.60f
            val warm = world.time == "Sunset"
            val night = world.time == "Night"

            if (!night) {
                val lightColor = if (warm) Color.rgb(255, 190, 120) else Color.rgb(255, 238, 190)
                paint.shader = RadialGradient(
                    if (warm) w * .72f else w * .83f,
                    if (warm) h * .30f else h * .15f,
                    maxOf(w, h) * .42f,
                    Color.argb(if (warm) 38 else 26, Color.red(lightColor), Color.green(lightColor), Color.blue(lightColor)),
                    Color.argb(0, Color.red(lightColor), Color.green(lightColor), Color.blue(lightColor)),
                    Shader.TileMode.CLAMP
                )
                c.drawRect(0f, 0f, w, h * .78f, paint)
                paint.shader = null
            }

            // Distant haze separates the road/world planes and reduces the flat-Cartoon look.
            val haze = when {
                night -> 8
                world.weather == "Rainy" -> 22
                world.weather == "Cloudy" -> 16
                else -> 10
            }
            paint.shader = LinearGradient(
                0f, horizon - h * .10f, 0f, horizon + h * .22f,
                Color.argb(0, 225, 238, 242),
                Color.argb(haze, 225, 238, 242),
                Shader.TileMode.CLAMP
            )
            c.drawRect(0f, horizon - h * .10f, w, horizon + h * .22f, paint)
            paint.shader = null

            // Very subtle screen-space vignette: keeps the child's attention on the
            // road and learning area while remaining gentle and readable.
            paint.shader = LinearGradient(
                0f, 0f, 0f, h,
                Color.argb(10, 0, 0, 0),
                Color.argb(0, 0, 0, 0),
                Shader.TileMode.CLAMP
            )
            c.drawRect(0f, 0f, w, h, paint)
            paint.shader = null
        }

        /**
         * Contact cues make the vehicle feel attached to the road rather than floating:
         * soft tire shadows, reflected body light and a tiny road spray/dust response.
         */
        private fun drawVehicleContactEffects(c: Canvas, w: Float, h: Float, world: SmartScene) {
            val selected = LearnovaUnlimitedWorld.vehicles[vehicle]
            if (selected.kind == "air" || selected.kind == "space") return

            val p = vehicleProgress.coerceIn(0f, 1f)
            val roadX = roadCenterAt(p, w, worldSceneId)
            val cx = roadX + laneOffset * w * (0.10f + 0.34f * p)
            val cy = h * (0.66f + 0.22f * p) +
                suspensionOffset +
                if (running) sin(frame / 4.5).toFloat() * (0.65f + 2.6f * p) else 0f
            val scale = 0.42f + 0.80f * p

            paint.color = Color.argb((42 + 24 * p).toInt(), 8, 10, 11)
            c.drawOval(
                RectF(cx - 72f * scale, cy + 36f * scale, cx + 72f * scale, cy + 48f * scale),
                paint
            )

            if (world.weather == "Rainy") {
                paint.color = Color.argb(45, 220, 240, 245)
                paint.strokeWidth = maxOf(1f, 1.2f * scale)
                for (side in -1..1 step 2) {
                    c.drawLine(
                        cx + side * 38f * scale,
                        cy + 43f * scale,
                        cx + side * 58f * scale,
                        cy + 52f * scale,
                        paint
                    )
                }
            } else if (running && speed > 0.006f) {
                paint.color = Color.argb(22, 235, 225, 205)
                for (side in -1..1 step 2) {
                    c.drawCircle(cx + side * 48f * scale, cy + 43f * scale, 2f + 3f * p, paint)
                }
            }
        }

        private fun drawHint(c: Canvas, w: Float, h: Float) {
            text.textAlign = Paint.Align.CENTER
            text.color = Color.WHITE
            text.setShadowLayer(5f,0f,2f,Color.DKGRAY)
            text.textSize = 15f

            val sessionMinutes = ((System.currentTimeMillis() - sessionStartedAt) / 60000L).toInt()
            c.drawText(
                if (ChildSafeEngagementPolicy.shouldSuggestBreak(sessionMinutes))
                    "Nice learning • Take a short break when you are ready"
                else if (levelComplete) "Level complete • Tap NEXT LEVEL"
                else if (running) "Driving • Tap again to stop"
                else "Tap once to drive",
                w/2f,h*.965f,text
            )

            text.clearShadowLayer()
        }
    }

    override fun onPause() {
        natureAudio.stop()
        if (::voice.isInitialized) voice.stop()
        super.onPause()
    }

    override fun onDestroy() {
        natureAudio.release()
        if (::voice.isInitialized) voice.shutdown()
        super.onDestroy()
    }
}