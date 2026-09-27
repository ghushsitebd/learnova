package com.learnova.app

import android.content.Context
import android.util.Base64
import android.view.Choreographer
import android.view.SurfaceView
import android.widget.FrameLayout
import com.google.android.filament.utils.Float3
import com.google.android.filament.utils.ModelViewer
import com.google.android.filament.utils.Mat4
import com.google.android.filament.utils.rotation
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream
import java.nio.ByteBuffer

/**
 * Learnova's first production 3D rendering layer.
 *
 * The game UI/learning state remains Android-native, while the world renderer is
 * migrated to Filament/PBR. This keeps the educational interaction lightweight
 * and gives the road/vehicle world a real 3D scene graph, physically based
 * materials, real camera perspective and hardware rendering.
 */
class Learnova3DView(context: Context) : FrameLayout(context) {

    private val surface = SurfaceView(context)
    private val viewer = ModelViewer(surface)
    private val choreographer = Choreographer.getInstance()
    private var frameCallback: Choreographer.FrameCallback? = null
    private var started = false
    private var driving = false
    private var driveTime = 0.0
    private var vehicleDistance = 0.0
    private var vehicleSpeed = 0.0
    private var lastFrameNanos = 0L
    private var wheelEntities = IntArray(0)
    private val wheelBaseTransforms = HashMap<Int, FloatArray>()
    private var vehicleRootEntity = 0
    private var vehicleRootBaseTransform: FloatArray? = null

    private data class RoadSample(
        val x: Double,
        val z: Double,
        val yaw: Float,
        val bank: Float
    )


    init {
        addView(
            surface,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
        surface.setZOrderOnTop(false)

        val modelBytes = decodeModel()
        viewer.loadModelGlb(ByteBuffer.wrap(modelBytes))
        cacheWheelEntities()
        cacheVehicleRoot()

        viewer.camera.lookAt(
            4.8, 2.8, 6.8,
            0.0, 1.0, 14.0,
            0.0, 1.0, 0.0
        )
        viewer.camera.setProjection(
            62.0,
            0.10,
            250.0,
            1.0
        )

        viewer.view.dynamicResolutionOptions = viewer.view.dynamicResolutionOptions.apply {
            enabled = true
            quality = com.google.android.filament.View.QualityLevel.MEDIUM
        }
        viewer.view.antiAliasing = com.google.android.filament.View.AntiAliasing.FXAA
        viewer.view.ambientOcclusionOptions = viewer.view.ambientOcclusionOptions.apply { enabled = true }
        viewer.view.bloomOptions = viewer.view.bloomOptions.apply { enabled = true }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!started) {
            started = true
            frameCallback = Choreographer.FrameCallback { time ->
                if (!started) return@FrameCallback

                val dt = if (lastFrameNanos == 0L) {
                    1.0 / 60.0
                } else {
                    ((time - lastFrameNanos).coerceIn(0L, 50_000_000L)).toDouble() / 1_000_000_000.0
                }
                lastFrameNanos = time

                // Child-simple input, physically smoother motion: one tap starts,
                // the next tap requests a controlled stop. Speed is integrated with
                // acceleration/deceleration instead of assuming a fixed 60 FPS rate.
                val targetSpeed = if (driving) 7.2 else 0.0
                val response = if (driving) 2.8 else 6.5
                val blend = (response * dt).coerceAtMost(1.0)
                vehicleSpeed += (targetSpeed - vehicleSpeed) * blend
                driveTime += dt * (if (vehicleSpeed > 0.02) 1.0 else 0.0)
                vehicleDistance += vehicleSpeed * dt

                updateDriveScene()
                updateVehicleMechanics()
                viewer.render(time)
                choreographer.postFrameCallback(frameCallback)
            }
            choreographer.postFrameCallback(frameCallback)
        }
    }

    fun setDriving(value: Boolean) {
        driving = value
        if (!value && vehicleSpeed < 0.02) vehicleSpeed = 0.0
    }


    /**
     * Real-time vehicle mechanics layer.
     *
     * Wheel nodes are discovered from glTF names rather than hard-coded entity ids.
     * If a future vehicle asset uses FL/FR/RL/RR (or Wheel_*) names, the same
     * runtime automatically animates it without changing the game controller.
     */
    private fun cacheWheelEntities() {
        val asset = viewer.asset ?: return
        val names = arrayOf(
            "Wheel_FL", "Wheel_FR", "Wheel_RL", "Wheel_RR",
            "wheel_fl", "wheel_fr", "wheel_rl", "wheel_rr",
            "FrontLeftWheel", "FrontRightWheel", "RearLeftWheel", "RearRightWheel"
        )
        val found = ArrayList<Int>()
        for (name in names) {
            val entity = asset.getFirstEntityByName(name)
            if (entity != 0 && !found.contains(entity)) found.add(entity)
        }
        wheelEntities = found.toIntArray()
        val tm = viewer.engine.transformManager
        wheelBaseTransforms.clear()
        for (entity in wheelEntities) {
            if (tm.hasComponent(entity)) {
                wheelBaseTransforms[entity] = tm.getTransform(tm.getInstance(entity), null)
            }
        }
    }

    private fun cacheVehicleRoot() {
        val asset = viewer.asset ?: return
        val tm = viewer.engine.transformManager
        val wheelParents = wheelEntities.mapNotNull { entity ->
            if (tm.hasComponent(entity)) tm.getParent(tm.getInstance(entity)) else null
        }.filter { it != 0 }.distinct()

        fun commonAncestor(a: Int, b: Int): Int {
            val seen = HashSet<Int>()
            var current = a
            while (current != 0 && seen.add(current)) {
                current = tm.getParent(tm.getInstance(current))
            }
            current = b
            val visited = HashSet<Int>()
            while (current != 0 && visited.add(current)) {
                if (seen.contains(current)) return current
                current = tm.getParent(tm.getInstance(current))
            }
            return 0
        }

        var candidate = if (wheelParents.size >= 2) wheelParents.reduce(::commonAncestor) else 0
        if (candidate == asset.root) candidate = 0

        if (candidate == 0) {
            val fallbackNames = arrayOf("Vehicle", "Car", "CarRoot", "VehicleRoot", "Body", "CarBody")
            for (name in fallbackNames) {
                val entity = asset.getFirstEntityByName(name)
                if (entity != 0 && entity != asset.root && tm.hasComponent(entity)) {
                    candidate = entity
                    break
                }
            }
        }

        vehicleRootEntity = candidate
        if (vehicleRootEntity != 0 && tm.hasComponent(vehicleRootEntity)) {
            vehicleRootBaseTransform = tm.getTransform(tm.getInstance(vehicleRootEntity), null)
        }
    }

    private fun updateVehicleMechanics() {
        if (wheelEntities.isEmpty() && vehicleRootEntity == 0) return
        val tm = viewer.engine.transformManager
        // Approximate a 0.30 m tyre radius: angular travel = distance / radius.
        // This keeps wheel rotation tied to actual vehicle travel rather than time.
        val wheelAngle = (vehicleDistance / 0.30).toFloat()
        val suspension = if (driving) {
            kotlin.math.sin(vehicleDistance * 8.0).toFloat() * 0.025f
        } else 0f

        tm.openLocalTransformTransaction()
        try {
            val baseRoot = vehicleRootBaseTransform
            if (vehicleRootEntity != 0 && baseRoot != null && tm.hasComponent(vehicleRootEntity)) {
                val travel = vehicleDistance
                val pathX = kotlin.math.sin(travel * 0.23) * 2.15 +
                        kotlin.math.sin(travel * 0.075 + 0.8) * 0.85
                val pathAhead = kotlin.math.sin((travel + 0.25) * 0.23) * 2.15 +
                        kotlin.math.sin((travel + 0.25) * 0.075 + 0.8) * 0.85
                val yaw = kotlin.math.atan2(pathAhead - pathX, 0.25).toFloat()
                val chassis = Mat4.of(*baseRoot) *
                        Mat4.of(
                            1f, 0f, 0f, pathX.toFloat(),
                            0f, 1f, 0f, if (driving) kotlin.math.sin(travel * 4.2).toFloat() * 0.012f else 0f,
                            0f, 0f, 1f, travel.toFloat(),
                            0f, 0f, 0f, 1f
                        ) *
                        rotation(Float3(0.0f, 1.0f, 0.0f), yaw)
                tm.setTransform(tm.getInstance(vehicleRootEntity), chassis.toFloatArray())
            }

            for (entity in wheelEntities) {
                val base = wheelBaseTransforms[entity] ?: continue
                if (!tm.hasComponent(entity)) continue
                val wheelRotation = rotation(
                    Float3(1.0f, 0.0f, 0.0f),
                    wheelAngle
                )
                val bob = Mat4.of(
                    1f, 0f, 0f, 0f,
                    0f, 1f, 0f, suspension,
                    0f, 0f, 1f, 0f,
                    0f, 0f, 0f, 1f
                )
                val transform = bob * Mat4.of(*base) * wheelRotation
                tm.setTransform(tm.getInstance(entity), transform.toFloatArray())
            }
        } finally {
            tm.commitLocalTransformTransaction()
        }
    }

    private fun updateDriveScene() {
        // Camera follows a shallow spline so the road reads as a real curved route,
        // while the one-tap driving state advances continuously through the world.
        // The curve is deliberately gentle for young players and low-end phones.
        val travel = vehicleDistance
        val pathZ = 6.8 + (travel % 18.0)
        val curve = kotlin.math.sin(travel * 0.23) * 2.15 +
                kotlin.math.sin(travel * 0.075 + 0.8) * 0.85
        val curveAhead = kotlin.math.sin((travel + 2.8) * 0.23) * 2.15 +
                kotlin.math.sin((travel + 2.8) * 0.075 + 0.8) * 0.85
        val bodyBob = if (driving) kotlin.math.sin(travel * 3.4) * 0.025 else 0.0
        val cameraX = curve + kotlin.math.sin(travel * 0.18) * 0.10
        val targetX = curveAhead

        viewer.camera.lookAt(
            cameraX, 2.82 + bodyBob, pathZ,
            targetX, 1.02, pathZ + 7.0,
            0.0, 1.0, 0.0
        )

        // Small banking cue follows road direction instead of using random sway.
        // This keeps the motion physically coherent without adding input complexity.
        viewer.camera.setLensProjection(48.0, 1.0, 0.10, 250.0)
        viewer.camera.setExposure(0.0, 0.0, 1000.0)
    }

    override fun onDetachedFromWindow() {
        started = false
        lastFrameNanos = 0L
        vehicleSpeed = 0.0
        frameCallback?.let { choreographer.removeFrameCallback(it) }
        frameCallback = null
        viewer.destroy()
        super.onDetachedFromWindow()
    }

    private fun decodeModel(): ByteArray {
        val compressed = Base64.decode(MODEL_GZ, Base64.DEFAULT)
        return GZIPInputStream(ByteArrayInputStream(compressed)).use { input ->
            input.readBytes()
        }
    }

    companion object {
        // Tiny procedural GLB: road, ground, PBR car body, cabin and four wheels.
        // It is compressed at source so the migration adds very little source size.
        private const val MODEL_GZ = "H4sIAEu6uGoC/+2Y327jRBTGhwUWWGCB3WW5rXzDjRt5xn/iVIrTprCoqNuu2mq5WPXCbZ3UUmJXjluoqkg8AA/BFeIlNg69bl+D52DGTp3x+Nhs4kiVVumFm/w85/tmjo/PRNPtHbx4gBD6+3uEtp8i9PP+7s6VZA8GTiitXUkXTjBwfU9ak0hNkWSp63hOYId+QMm2Yweef2GvqD+s9N0uxWzkUJYGx3SUtKZMPg2ktTdXkuefxJ8UGctEVmVN1mVDrh8OD+X0Hh1l92mktOfbJ9St7wxOY50wsL1BLzFgElTEPBzK6fhNO2j7J5dpCM6HYFnJRhy5XjqeAONrBpGVmqrzUb+cOk4vjVLFqFVcIzoNqhOZfpwp9J0jtRLTGSOLA38K/HNv+gTq+fSsKjUSP4TDZNDk+Z0Fbt8N3YvJVzsMA/foPGRfr6RXu/tbB1u7O/Ej3dnde7mxTR8VLRjXO3GP2Rj6HPp26ASu3aODhvGU3lFSnUpqvKTOS+KZJI2pZJ2XNHlJMpNkg1u4wmtizIuqM4liwqmqGVWtgqrOqRoZ1XoFVZNTbWSevTK/KsFTVUIyqiqvqg2Tgp2ATM/ZGJyd2r2QFv3ZUfDSCe1ezz3e88+7p7SFxX5H9sDZ9Ht+8MI+jlvgG6WmGPQNUursYsqYagd3EXeDlFpjmG1Vr2zXm9lHpRbx65rY9CeBU5c66E2zwb/XPdrZ53HGrBfiImtWKoA3zngfuIEzqzXN6fTaKMyvPoR61yxOmD1GVUtWCtrgIauco/NOh26Jcd0cXYbOtuN1Q9ohdWxq0/uvXefXyZj4e9zw2PDdTifeWCdf0+g6bTWhHXTZPVVrGIStpyA2HsxH10k2WC0JNjSzgjUmRJvfG5OGUcXcZGZzmzc0rYI50YkyvznRG6SCuYoNc35zlWhVyk01cYV6U02zSsFpmlGh4DRdrVJwOsb/W3D0lbeP6UYz8AP+hWcNINY79vtnvud44cHlmcMkicHguceqgv0gi7H0+sdNlf3acuMfdEYt/n2F5VWN9r14v/qNcoYZjeF03nN6Zd9MUQ5Dcmoqx/I6kdvf3Nje2JNEATLn2jFbNm3FdKtbJdziGU8wAVZPFrt6terqtTlXT/eydPX0c7p6xhPMaGW38tXrVVdvzL16XI8XTpJ/ms4lgN1K7sQ3KnuW56BeNQfmPeTAXGwOGlVzgJV7SAJedCes3AoxuY80LLgl4so9Ec/bFImS7IeKLmyI8Y2Yg1siXnBfxLM3xsPh+jOE2ls7CKHx+u3NzVuEVq4R+r2F0r+UtwHeLOGizpjTiQB+DfBmCW8Xj2d/d978fEDeFvi4hL/leJTPD8ivBT4u4ILOnbfom+FNgK8XcMH3zlucZ4Y3AT6G+QeIndM+QB+ij9DH6CG9PkSfoE/RZ+gRvT5Cn6Mv0JfoMb0+Rl+hr9E36Am9PkFP0TP0LXpOr8/Rd+j25o9Wp/PXiCWErx+OrwPcKuGiTsTpRAAfA9wq4evF46f1k50PyNcFHpXwEcejfH5APhZ4VMAFncQ775vhFsBbBVzwTbzz88xwC+ARxBdVh51OJ54P/R/x9cPxFsCtEi7qRJxOBPAI4FYJbxWPn9ZPdj4gbwk8KuEjjkf5/IA8EnhUwAWdxDvvm+EWwFsFXPBNvPPzzHAL4BHEF1WHqqpatNbpfP4d8fXDcQvgVgkXdUacTgTwEcCtEm4Vj5/WT3Y+ILcEPirhI45H+fyAfCTwUQEXdBLvvG+GWwC3Crjgm3jn55nhFsBHEF/W4bIOl3W4rMNlHS7rcFmH718dIvTnxu3NNnBuk/I2wJslXNT5h9OJAH4N8GYJbxeP589b+PmAXDif4fVzHDif4fMDcuF8hl9vmc6dt+ib4cD5jLiunI5w3iLOM8OB8xkxz4utw/8Ahv+D76gnAAA="
    }
}
