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
        private var steeringInput = 0f
        private var lateralVelocity = 0f
        // A side touch is a hold-to-steer gesture. Releasing the finger recentres
        // the wheel smoothly instead of leaving steering latched on.
        private var steeringTouchActive = false
        private var suspensionOffset = 0f
        private var suspensionVelocity = 0f
        private var level = 1