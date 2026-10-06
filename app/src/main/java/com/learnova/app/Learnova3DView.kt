package com.learnova.app

import android.content.Context
import android.app.ActivityManager
import android.os.Build
import android.os.PowerManager
import android.os.Trace
import android.util.Base64
import android.view.Choreographer
import android.view.SurfaceView
import android.widget.FrameLayout
import com.google.android.filament.utils.Float3
import com.google.android.filament.utils.ModelViewer
import com.google.android.filament.utils.Mat4
import com.google.android.filament.utils.rotation
import com.google.android.filament.Colors
import com.google.android.filament.EntityManager
import com.google.android.filament.LightManager
import com.google.android.filament.Skybox
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

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
    private val viewer by lazy(LazyThreadSafetyMode.NONE) { ModelViewer(surface) }
    private val choreographer = Choreographer.getInstance()
    private var frameCallback: Choreographer.FrameCallback? = null
    private var started = false
    private var driving = false
    private val journeyDirector = JourneyDriveDirector()
    private var driveTime = 0.0
    private var vehicleDistance = 0.0
    // Floating origin keeps Filament coordinates close to the camera during very long sessions.
    private var renderOriginDistance = 0.0
    private var vehicleSpeed = 0.0
    private var previousVehicleSpeed = 0.0
    private var vehicleAcceleration = 0.0
    // Tyre spin is integrated from actual vehicle speed instead of being keyed directly
    // to travelled distance. This keeps wheel motion smooth during acceleration/braking.
    private var wheelSpinAngle = 0.0
    private var wheelSpinRate = 0.0
    private var chassisPitch = 0.0
    private var chassisRoll = 0.0
    private var lastFrameNanos = 0L
    // Shared render-loop timestep keeps vehicle physics deterministic across devices.
    private var frameDeltaSeconds = 1.0 / 60.0
    // Fixed-step simulation keeps gameplay independent of display refresh rate.
    private var physicsAccumulator = 0.0
    private val physicsStepSeconds = 1.0 / 60.0
    private val maxPhysicsStepsPerFrame = 4
    private var wheelEntities = IntArray(0)
    private val frontWheelEntities = HashSet<Int>()
    private val wheelBaseTransforms = HashMap<Int, FloatArray>()
    // Per-wheel contact state keeps the chassis response physical instead of using
    // one shared bounce value. The road spline is the authoritative ground surface.
    private val wheelContactLongitudinal = HashMap<Int, Double>()
    private val wheelContactLateral = HashMap<Int, Double>()
    private val wheelSuspensionDisplacement = HashMap<Int, Double>()
    private val wheelSuspensionVelocity = HashMap<Int, Double>()
    // Front-wheel steering inertia prevents an artificial instant snap when the road curves.
    private val wheelSteeringAngle = HashMap<Int, Double>()
    private var vehicleRootEntity = 0
    private var vehicleRootBaseTransform: FloatArray? = null
    private var sunEntity = 0
    private var activeSkyBiome: WorldDirector.Biome? = null
    private var lastSkyR = Float.NaN
    private var lastSkyG = Float.NaN
    private var lastSkyB = Float.NaN
    private var proceduralRoad: ProceduralRoadMesh? = null
    private var terrainMesh: LearnovaTerrainMesh? = null
    private var roadsideWorld: RoadsideWorld? = null
    private var waterSurfaceWorld: WaterSurfaceWorld? = null
    private var shorelineWorld: ShorelineWorld? = null
    private var worldLife: WorldLifeSimulation? = null
    private var nearFieldCreatureWorld: NearFieldCreatureWorld? = null
    private var childNPC: ChildNPCWorld? = null
    private var learningAnimalEncounter: LearningAnimalEncounter? = null
    private var learningSignWorld: LearningSignWorld? = null
    private var vehicleFriend: VehicleFriendWorld? = null
    private var activeVehicle: VehicleDefinition = VehicleCatalog.byId(1)
    private var targetSpeed = activeVehicle.targetSpeed
    private var wheelRadius = activeVehicle.wheelRadius
    private var suspensionDisplacement = 0.0
    private var suspensionVelocity = 0.0
    private var previousRoadYaw = 0.0
    private var roadYawRate = 0.0
    // Camera/body inertial stabilization: the view follows the road bank and yaw
    // progressively instead of snapping to the spline tangent.
    private var cameraBank = 0.0
    private var cameraYaw = 0.0
    // Steering is fully automatic: the road spline is the authoritative path.
    // There is deliberately no manual left/right steering state or control surface.
    private var autoSteeringInput = 0.0
    private var lateralOffset = 0.0
    private var lateralVelocity = 0.0
    // Filtered tyre-side slip estimate: used only for subtle grip/body cues,
    // never exposed as a complex control to the child.
    private var lateralSlip = 0.0
    private var renderProfile = VehicleRenderProfile.forType(activeVehicle.type)
    private val vehicleAssetResolver = VehicleAssetResolver(context)
    private var assetIoExecutor: ExecutorService = newAssetIoExecutor()
    private val assetLoadGeneration = AtomicInteger(0)
    private var loadedVehicleAssetKey = "base"
    private var requestedVehicleAssetKey = "base"
    private val adaptiveQuality = LearnovaAdaptiveQuality()
    private val headroomMonitor = LearnovaHeadroomMonitor(context)
    private var constrainedDevice = false
    private var thermalConstrained = false
    private var powerManager: PowerManager? = null
    private var thermalListener: PowerManager.OnThermalStatusChangedListener? = null
    private var traceFrameCounter = 0
    // The permanent companion occasionally leaves the vehicle for a short, safe roadside walk.
    private var friendWalkCycle = 0.0
    private var activeLearningLessonLevel = 0
    private var learningLessonTriggered = false

    // Physical entry/exit state is kept separate from the child-simple drive
    // control. If a vehicle asset contains named door nodes, this layer animates
    // those nodes; otherwise it remains a safe no-op until the production GLB arrives.
    private val vehicleInteraction = VehicleInteractionController()
    private var interactionProfile = VehicleInteractionProfiles.forVehicle(activeVehicle.type)
    private var doorEntities = IntArray(0)
    private val doorBaseTransforms = HashMap<Int, FloatArray>()
    private val doorHingeSigns = HashMap<Int, Float>()

    private var worldInitialized = false
    private var rendererReady = false

    init {
        addView(
            surface,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
        surface.setZOrderOnTop(false)
        // Never let an uninitialized/failed Filament SurfaceView cover the
        // child-friendly Canvas fallback with a black rectangle.
        surface.visibility = android.view.View.INVISIBLE
        // Keep Activity startup light: the expensive GLB decode and procedural-world
        // construction are deferred until the view is attached and the first UI
        // traversal has a chance to run. This reduces startup contention without
        // changing the child's gameplay or the final 3D scene.
    }

    private fun initializeWorldIfNeeded() {
        if (worldInitialized) return
        worldInitialized = true

        // Filament is an optional presentation layer. A device/emulator that cannot
        // initialize the native renderer must still be able to launch the learning app.
        try {
            val modelBytes = decodeModel()
            viewer.loadModelGlb(ByteBuffer.wrap(modelBytes))
            viewer.asset?.let { asset ->
                proceduralRoad = ProceduralRoadMesh(viewer.engine, viewer.scene, asset).also { it.build() }
                terrainMesh = LearnovaTerrainMesh(viewer.engine, viewer.scene, asset).also { it.build() }
                roadsideWorld = RoadsideWorld(viewer.engine, viewer.scene, asset).also { it.build() }
                waterSurfaceWorld = WaterSurfaceWorld(viewer.engine, viewer.scene, asset).also { it.build() }
                shorelineWorld = ShorelineWorld(viewer.engine, viewer.scene, asset).also { it.build() }
                worldLife = WorldLifeSimulation(viewer.engine, viewer.scene, asset).also { it.build() }
                nearFieldCreatureWorld = NearFieldCreatureWorld(context, viewer.engine, viewer.scene,).also { it.build() }
                childNPC = ChildNPCWorld(viewer.engine, viewer.scene, asset).also { it.build() }
                learningAnimalEncounter = LearningAnimalEncounter(context, viewer.engine, viewer.scene, asset).also { it.build() }
                learningSignWorld = LearningSignWorld(viewer.engine, viewer.scene, asset).also { it.build() }
                vehicleFriend = VehicleFriendWorld(viewer.engine, viewer.scene, asset).also { it.build(); it.setFriend(friendForVehicle(activeVehicle.id)) }
            }
            configureRealisticSunLight()
            updateSkybox(WorldDirector.atmosphere(0.0), force = true)
            cacheWheelEntities()
            cacheVehicleRoot()
            cacheDoorEntities()
            viewer.camera.lookAt(4.8, 2.8, 6.8, 0.0, 1.0, 14.0, 0.0, 1.0, 0.0)
            viewer.view.antiAliasing = com.google.android.filament.View.AntiAliasing.FXAA
            configureDeviceRenderProfile()
            rendererReady = true
            surface.visibility = android.view.View.VISIBLE
        } catch (_: Throwable) {
            rendererReady = false
            proceduralRoad = null
            terrainMesh = null
            roadsideWorld = null
            waterSurfaceWorld = null
            shorelineWorld = null
            worldLife = null
            childNPC = null
            surface.visibility = android.view.View.INVISIBLE
        }
    }

    /** True only when the Filament surface is initialized and safe to show. */
    fun isPresentationReady(): Boolean = rendererReady

    /** Shows the named learning animal directly in the child's current journey. */
    fun triggerLearningAnimal(animal: String) {
        if (!rendererReady) return
        learningAnimalEncounter?.trigger(animal, vehicleDistance + 12.0)
    }

    /** Shows a physical roadside learning marker ahead of the vehicle. */
    fun showLearningSign() {
        if (!rendererReady) return
        learningSignWorld?.show(vehicleDistance)
    }

    fun hideLearningSign() {
        learningSignWorld?.hide()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (assetIoExecutor.isShutdown || assetIoExecutor.isTerminated) {
            assetIoExecutor = newAssetIoExecutor()
        }
        if (!started) {
            // Defer heavy scene construction until after the initial UI traversal.
            // The first frame remains usable even if GLB/world creation takes longer.
            post { runCatching { initializeWorldIfNeeded() } }
            started = true
            headroomMonitor.start()
            frameCallback = Choreographer.FrameCallback { time ->
                if (!started) return@FrameCallback
                if (!rendererReady) {
                    choreographer.postFrameCallback(frameCallback)
                    return@FrameCallback
                }

                val dt = if (lastFrameNanos == 0L) {
                    1.0 / 60.0
                } else {
                    ((time - lastFrameNanos).coerceIn(0L, 50_000_000L)).toDouble() / 1_000_000_000.0
                }
                lastFrameNanos = time
                frameDeltaSeconds = dt

                // Rendering may run at 60/90/120Hz, but gameplay advances in
                // deterministic 60Hz steps. Long stalls are capped to avoid a spiral
                // of catch-up work while preserving stable vehicle speed.
                physicsAccumulator = (physicsAccumulator + dt).coerceAtMost(
                    physicsStepSeconds * maxPhysicsStepsPerFrame
                )
                var physicsSteps = 0
                while (physicsAccumulator >= physicsStepSeconds && physicsSteps < maxPhysicsStepsPerFrame) {
                    updatePhysicsStep(physicsStepSeconds)
                    journeyDirector.update(physicsStepSeconds)
                    updateLearningLessonDirector()
                    physicsAccumulator -= physicsStepSeconds
                    physicsSteps++
                }
                val traceThisFrame = BuildConfig.PERF_TRACE_ENABLED
                traceFrameCounter = (traceFrameCounter + 1) and 0x7fffffff

                traceSectionIfEnabled(traceThisFrame, "Learnova.scene") {
                    updateDriveScene()
                    proceduralRoad?.update(vehicleDistance)
                    terrainMesh?.update(vehicleDistance)
                    roadsideWorld?.update(vehicleDistance)
                    waterSurfaceWorld?.update(vehicleDistance)
                    shorelineWorld?.update(vehicleDistance)
                    worldLife?.update(vehicleDistance)
                    nearFieldCreatureWorld?.update(vehicleDistance)
                    childNPC?.update(vehicleDistance)
                    learningAnimalEncounter?.update(vehicleDistance)
                    learningSignWorld?.update(vehicleDistance)
                    vehicleFriend?.setDriving(driving)
                    friendWalkCycle += dt
                    val friendOutside = driving && (friendWalkCycle % 42.0) in 24.0..31.0
                    vehicleFriend?.setOutside(friendOutside)
                    vehicleFriend?.update(vehicleDistance)
                }
                traceSectionIfEnabled(traceThisFrame, "Learnova.vehicle") {
                    updateVehicleMechanics()
                    updateDrivingInertia(dt)
                    vehicleInteraction.update(dt.toFloat(), interactionProfile)
                    updateVehicleInteractionVisuals()
                }
                traceSectionIfEnabled(traceThisFrame, "Learnova.filamentRender") {
                    viewer.render(time)
                }
                traceSectionIfEnabled(traceThisFrame, "Learnova.adaptiveQuality") {
                    adaptiveQuality.sample(
                        dt * 1000.0,
                        constrainedDevice || thermalConstrained,
                        headroomMonitor.cpuHeadroom(),
                        headroomMonitor.gpuHeadroom()
                    )?.let { applyQualityTier(it) }
                }
                // Thermal state can change while the game is running; keep expensive
                // effects disabled immediately rather than waiting for the next
                // frame-time evaluation window.
                if (thermalConstrained) applyThermalSafetyProfile()
                choreographer.postFrameCallback(frameCallback)
            }
            choreographer.postFrameCallback(frameCallback)
        }
    }

    /**
     * Debug-only Perfetto trace slices. Release builds execute the body normally
     * without adding tracing calls. These slices let us distinguish world generation,
     * vehicle dynamics, Filament submission and the adaptive governor in a system
     * trace instead of guessing from aggregate frame time.
     */
    private inline fun traceSectionIfEnabled(
        enabled: Boolean,
        name: String,
        block: () -> Unit
    ) {
        if (!enabled) {
            block()
            return
        }
        Trace.beginSection(name)
        try {
            block()
        } finally {
            Trace.endSection()
        }
    }

    private fun updateLearningLessonDirector() {
        val snapshot = journeyDirector.snapshot()
        if (snapshot.level != activeLearningLessonLevel) {
            activeLearningLessonLevel = snapshot.level
            learningLessonTriggered = false
        }
        if (snapshot.state != JourneyDriveDirector.State.LEARNING || learningLessonTriggered) return
        if (snapshot.learningElapsedSeconds < 28.0) return

        // Resolve the encounter from the central multilingual level curriculum.
        val activityIndex = (snapshot.learningElapsedSeconds / 18.0)
            .toInt()
            .coerceIn(0, 2)
        val lesson = RoadsideLearningDirector().lessonForLearningSession(snapshot.level, activityIndex)
        learningLessonTriggered = true
        showLearningSign()
        lesson.visualKey?.let(::triggerLearningAnimal)
    }

    /** Fixed 60Hz gameplay simulation; rendering remains driven by Choreographer. */
    private fun updatePhysicsStep(dt: Double) {
        val requestedSpeed = if (driving) targetSpeed else 0.0
        val accelerationResponse = when (activeVehicle.type) {
            "motorcycle", "cycle", "three_wheeler", "electric" -> 3.8
            "sport", "concept" -> 3.4
            "truck", "bus", "emergency", "construction", "farm" -> 2.15
            "offroad", "safari" -> 2.55
            else -> 2.85
        }
        val brakingResponse = when (activeVehicle.type) {
            "motorcycle", "cycle" -> 7.2
            "truck", "bus", "construction" -> 5.4
            else -> 6.6
        }
        val response = if (driving) accelerationResponse else brakingResponse
        val blend = (response * dt).coerceAtMost(1.0)
        previousVehicleSpeed = vehicleSpeed
        vehicleSpeed += (requestedSpeed - vehicleSpeed) * blend
        vehicleAcceleration = ((vehicleSpeed - previousVehicleSpeed) / dt).coerceIn(-8.0, 8.0)
        driveTime += dt * (if (vehicleSpeed > 0.02) 1.0 else 0.0)
        vehicleDistance += vehicleSpeed * dt

        val speedRatio = (vehicleSpeed / targetSpeed.coerceAtLeast(0.1)).coerceIn(0.0, 1.0)
        // Autonomous steering reads the road ahead and anticipates curvature.
        // The child only controls start/stop; the vehicle follows the road itself.
        val autoRoad = RoadSpline.sampleRelative(vehicleDistance + 8.0, renderOriginDistance)
        val currentRoad = RoadSpline.sampleRelative(vehicleDistance, renderOriginDistance)
        val curvatureError = kotlin.math.atan2(
            kotlin.math.sin(autoRoad.yaw - currentRoad.yaw),
            kotlin.math.cos(autoRoad.yaw - currentRoad.yaw)
        )
        val desiredAutoSteer = (curvatureError / 0.34).coerceIn(-1.0, 1.0)
        val autoSteerResponse = (dt * (5.5 + 2.0 * speedRatio)).coerceAtMost(1.0)
        autoSteeringInput += (desiredAutoSteer - autoSteeringInput) * autoSteerResponse
        if (!driving) autoSteeringInput *= (1.0 - (dt * 6.0).coerceAtMost(1.0))
        val steeringAuthority = (1.12 - 0.48 * speedRatio).coerceIn(0.64, 1.12)
        val maxLateralVelocity = (0.92 - 0.16 * speedRatio).coerceIn(0.62, 0.92)
        val desiredLateralVelocity = 0.0
        val gripResponse = (dt * (6.8 - 1.4 * speedRatio)).coerceAtMost(1.0)
        val lateralError = desiredLateralVelocity - lateralVelocity
        lateralVelocity += lateralError * gripResponse
        val targetSlip = (lateralError * speedRatio * 0.22).coerceIn(-0.18, 0.18)
        lateralSlip += (targetSlip - lateralSlip) * (dt * 8.0).coerceAtMost(1.0)
        lateralVelocity -= kotlin.math.sign(lateralVelocity) * (kotlin.math.abs(lateralSlip) * 0.055 * speedRatio) * dt
        lateralOffset += lateralVelocity * dt * (2.6 + vehicleSpeed * 0.08)

        val laneLimit = 2.72
        val edgeRatio = (kotlin.math.abs(lateralOffset) / laneLimit).coerceIn(0.0, 1.25)
        if (edgeRatio > 0.82) {
            val edgeBrake = ((edgeRatio - 0.82) / 0.43).coerceIn(0.0, 1.0)
            lateralVelocity *= (1.0 - edgeBrake * dt * 7.5).coerceAtLeast(0.20)
            lateralOffset *= (1.0 - edgeBrake * dt * 1.8).coerceAtLeast(0.70)
        }
        // Autonomous path following keeps the vehicle centred on the authoritative
        // road spline; no manual lateral drift is introduced by steering controls.
        lateralVelocity *= (1.0 - (dt * 8.0).coerceAtMost(0.95))
        lateralOffset *= (1.0 - (dt * 8.0).coerceAtMost(0.95))
        lateralOffset = lateralOffset.coerceIn(-laneLimit, laneLimit)

        if (kotlin.math.abs(vehicleDistance - renderOriginDistance) >= 180.0) {
            renderOriginDistance = vehicleDistance
        }
    }

    private fun friendForVehicle(id: Int): String = when ((id - 1) % 8) {
        0 -> "Fox"
        1 -> "Bear"
        2 -> "Monkey"
        3 -> "Rabbit"
        4 -> "Panda"
        5 -> "Tiger"
        6 -> "Lion"
        else -> "Dog"
    }

    private fun newAssetIoExecutor(): ExecutorService =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "LearnovaVehicleAssetIO").apply { isDaemon = true }
        }

    /**
     * Device-aware quality policy: keep the same physically based scene, but scale
     * expensive post-processing on constrained phones. This avoids making the child
     * experience depend on a single hardware tier.
     */
    private fun configureDeviceRenderProfile() {
        val memoryClassMb = (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)
            ?.memoryClass ?: 128
        val constrained = memoryClassMb < 192
        constrainedDevice = constrained

        // Thermal headroom is part of mobile rendering quality. On supported
        // Android versions, start conservatively when the device reports a
        // serious thermal state; the adaptive frame-time sampler can recover
        // quality later when rendering becomes stable.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val power = powerManager
            thermalConstrained = isThermallyConstrained(power?.currentThermalStatus)

            // Android exposes live thermal transitions on supported versions. This
            // lets Learnova react before sustained frame-time degradation appears.
            if (power != null) {
                val listener = PowerManager.OnThermalStatusChangedListener { status ->
                    thermalConstrained = isThermallyConstrained(status)
                    if (thermalConstrained) {
                        post { applyThermalSafetyProfile() }
                    }
                }
                thermalListener = listener
                power.addThermalStatusListener(context.mainExecutor, listener)
            }
        }

        configureAdaptiveRefreshRateHint()

        viewer.view.dynamicResolutionOptions = viewer.view.dynamicResolutionOptions.apply {
            enabled = true
            quality = if (constrained || thermalConstrained) {
                com.google.android.filament.View.QualityLevel.LOW
            } else {
                com.google.android.filament.View.QualityLevel.MEDIUM
            }
        }
        viewer.view.ambientOcclusionOptions = viewer.view.ambientOcclusionOptions.apply {
            enabled = !constrained && !thermalConstrained
        }
        viewer.view.bloomOptions = viewer.view.bloomOptions.apply {
            enabled = !constrained && !thermalConstrained
        }
    }

    /**
     * Android 16+ adaptive-refresh integration.
     *
     * Gameplay remains deterministic at 60 Hz; this only tells the display scheduler
     * the preferred presentation rate. On older Android versions we retain the
     * stable 60 Hz hint. The call is intentionally made once at startup rather than
     * every frame, because frequent refresh-rate requests can introduce frame drops.
     */
    private fun configureAdaptiveRefreshRateHint() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return

        var preferredRate = 60.0f
        if (Build.VERSION.SDK_INT >= 36) {
            surface.display?.let { display ->
                try {
                    if (display.hasArrSupport()) {
                        val suggested = display.getSuggestedFrameRate(
                            android.view.Display.FRAME_RATE_CATEGORY_NORMAL
                        )
                        if (suggested.isFinite() && suggested > 0.0f) {
                            preferredRate = suggested
                        }
                    }
                } catch (_: Throwable) {
                    // OEM/API rollout differences must never block renderer startup.
                }
            }
        }

        surface.holder.surface.setFrameRate(
            preferredRate,
            android.view.Surface.FRAME_RATE_COMPATIBILITY_DEFAULT
        )
    }

    private fun isThermallyConstrained(status: Int?): Boolean = when (status) {
        PowerManager.THERMAL_STATUS_SEVERE,
        PowerManager.THERMAL_STATUS_CRITICAL,
        PowerManager.THERMAL_STATUS_EMERGENCY -> true
        else -> false
    }

    /** Immediate thermal safety ceiling; visual quality only, gameplay unchanged. */
    private fun applyThermalSafetyProfile() {
        viewer.view.dynamicResolutionOptions = viewer.view.dynamicResolutionOptions.apply {
            enabled = true
            quality = com.google.android.filament.View.QualityLevel.LOW
        }
        viewer.view.ambientOcclusionOptions = viewer.view.ambientOcclusionOptions.apply {
            enabled = false
        }
        viewer.view.bloomOptions = viewer.view.bloomOptions.apply {
            enabled = false
        }
    }

    /**
     * Physically plausible daylight foundation for the 3D world.
     *
     * Filament uses real-world photometric units for directional light intensity,
     * so this is deliberately expressed as sunlight-like illuminance rather than
     * an arbitrary game brightness value. The light is created once and reused.
     */
    private fun applyQualityTier(tier: LearnovaAdaptiveQuality.Tier) {
        when (tier) {
            LearnovaAdaptiveQuality.Tier.HIGH -> {
                if (thermalConstrained) {
                    applyThermalSafetyProfile()
                    return
                }
                viewer.view.dynamicResolutionOptions = viewer.view.dynamicResolutionOptions.apply {
                    enabled = true
                    quality = com.google.android.filament.View.QualityLevel.HIGH
                }
                viewer.view.ambientOcclusionOptions = viewer.view.ambientOcclusionOptions.apply {
                    enabled = true
                }
                viewer.view.bloomOptions = viewer.view.bloomOptions.apply {
                    enabled = true
                }
            }
            LearnovaAdaptiveQuality.Tier.MEDIUM -> {
                viewer.view.dynamicResolutionOptions = viewer.view.dynamicResolutionOptions.apply {
                    enabled = true
                    quality = com.google.android.filament.View.QualityLevel.MEDIUM
                }
                viewer.view.ambientOcclusionOptions = viewer.view.ambientOcclusionOptions.apply {
                    enabled = !constrainedDevice
                }
                viewer.view.bloomOptions = viewer.view.bloomOptions.apply {
                    enabled = !constrainedDevice
                }
            }
            LearnovaAdaptiveQuality.Tier.LOW -> {
                viewer.view.dynamicResolutionOptions = viewer.view.dynamicResolutionOptions.apply {
                    enabled = true
                    quality = com.google.android.filament.View.QualityLevel.LOW
                }
                viewer.view.ambientOcclusionOptions = viewer.view.ambientOcclusionOptions.apply {
                    enabled = false
                }
                viewer.view.bloomOptions = viewer.view.bloomOptions.apply {
                    enabled = false
                }
            }
        }
    }

    private fun configureRealisticSunLight() {
        if (sunEntity != 0) return

        sunEntity = EntityManager.get().create()
        val (r, g, b) = Colors.cct(5_500.0f)
        LightManager.Builder(LightManager.Type.SUN)
            .color(r, g, b)
            .intensity(100_000.0f)
            .direction(-0.35f, -1.0f, -0.55f)
            .castShadows(true)
            .build(viewer.engine, sunEntity)
        viewer.scene.addEntity(sunEntity)

        // Keep the shadow map useful for the actual driving corridor while
        // avoiding unnecessarily large GPU work on lower-end devices.
    }

    fun setDriving(value: Boolean) {
        vehicleFriend?.setDriving(value)
        if (value && vehicleInteraction.state != VehicleInteractionController.State.OUTSIDE) return
        if (value == driving) return
        if (value) {
            if (!journeyDirector.toggleDrive()) return
            driving = true
        } else {
            journeyDirector.toggleDrive()
            driving = false
        }
        if (!driving && vehicleSpeed < 0.02) vehicleSpeed = 0.0
    }

    /** Child-simple one-tap drive toggle; steering remains fully automatic. */
    fun toggleJourney(): Boolean {
        if (vehicleInteraction.state != VehicleInteractionController.State.OUTSIDE) return false
        val nowDriving = journeyDirector.toggleDrive()
        driving = nowDriving
        vehicleFriend?.setDriving(nowDriving)
        if (!nowDriving && vehicleSpeed < 0.02) vehicleSpeed = 0.0
        return nowDriving
    }

    fun journeySnapshot(): JourneyDriveDirector.Snapshot = journeyDirector.snapshot()

    /** Steering is intentionally not user-controlled; road curvature drives it. */

    /**
     * Physical vehicle entry/exit hook for the interaction layer.
     * Gameplay can call this while the vehicle is stopped; repeated taps during
     * a transition are ignored by the deterministic controller.
     */
    fun interactWithVehicle(): Boolean {
        if (driving) return false
        return if (vehicleInteraction.isInside()) {
            vehicleInteraction.requestExit(interactionProfile)
        } else {
            vehicleInteraction.requestEnter(interactionProfile)
        }
    }

    /**
     * Selects the physical profile used by the renderer.
     *
     * Asset replacement is intentionally separate: a catalog entry can point to
     * its future GLB without forcing 100 models into memory at the same time.
     */
    internal fun setVehicle(definition: VehicleDefinition) {
        activeVehicle = definition
        vehicleFriend?.setFriend(friendForVehicle(definition.id))
        renderProfile = VehicleRenderProfile.forType(definition.type)
        interactionProfile = VehicleInteractionProfiles.forVehicle(definition.type)
        vehicleInteraction.reset()
        targetSpeed = definition.targetSpeed.coerceIn(2.0, 18.0)
        wheelRadius = definition.wheelRadius.coerceIn(0.12, 0.80)
        if (rendererReady) loadVehicleAsset(definition)
    }

    /**
     * Streams only the selected garage vehicle into Filament.
     *
     * We deliberately do not preload 100 models. If a dedicated GLB is not
     * present yet, the verified base vehicle remains active instead of showing
     * a blank scene or crashing.
     */
    private fun loadVehicleAsset(definition: VehicleDefinition) {
        if (definition.assetKey == requestedVehicleAssetKey) {
            cacheWheelEntities()
            cacheVehicleRoot()
            return
        }

        requestedVehicleAssetKey = definition.assetKey
        val generation = assetLoadGeneration.incrementAndGet()

        // File I/O and GZIP decompression run off the UI thread. Filament
        // ModelViewer mutation is returned to the main thread.
        assetIoExecutor.execute {
            val requested = vehicleAssetResolver.load(definition.assetKey)
            val requestedKey = if (requested != null) definition.assetKey else "base"
            val bytes = requested ?: decodeModel()

            post {
                if (!started || generation != assetLoadGeneration.get()) return@post

                // ModelViewer.loadModelGlb() destroys the previous model before
                // installing the new one, so switching vehicles does not retain
                // the previous Filament asset.
                viewer.loadModelGlb(ByteBuffer.wrap(bytes))
                loadedVehicleAssetKey = requestedKey

                proceduralRoad?.destroy()
                terrainMesh?.destroy()
                roadsideWorld?.destroy()
                waterSurfaceWorld?.destroy()
                shorelineWorld?.destroy()
                worldLife?.destroy()
                nearFieldCreatureWorld?.destroy()
                vehicleFriend?.destroy()
                vehicleFriend = null
                worldLife = null
                nearFieldCreatureWorld = null
                roadsideWorld = null
                waterSurfaceWorld = null
                shorelineWorld = null
                terrainMesh = viewer.asset?.let { asset ->
                    LearnovaTerrainMesh(viewer.engine, viewer.scene, asset).also { it.build() }
                }
                proceduralRoad = viewer.asset?.let {
                    ProceduralRoadMesh(viewer.engine, viewer.scene, it).also { road -> road.build() }
                }
                waterSurfaceWorld = viewer.asset?.let {
                    WaterSurfaceWorld(viewer.engine, viewer.scene, it).also { world -> world.build() }
                }
                shorelineWorld = viewer.asset?.let {
                    ShorelineWorld(viewer.engine, viewer.scene, it).also { world -> world.build() }
                }
                worldLife = viewer.asset?.let {
                    WorldLifeSimulation(viewer.engine, viewer.scene, it).also { world -> world.build() }
                }
                nearFieldCreatureWorld = viewer.asset?.let {
                    NearFieldCreatureWorld(context, viewer.engine, viewer.scene).also { world -> world.build() }
                }
                roadsideWorld = viewer.asset?.let {
                    RoadsideWorld(viewer.engine, viewer.scene, it).also { world -> world.build() }
                }

                cacheWheelEntities()
                cacheVehicleRoot()
                cacheDoorEntities()
                vehicleInteraction.reset()
                vehicleDistance = 0.0
                vehicleSpeed = 0.0
            }
        }
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
            "Wheel_FL", "Wheel_FR", "Wheel_ML", "Wheel_MR", "Wheel_RL", "Wheel_RR",
            "wheel_fl", "wheel_fr", "wheel_ml", "wheel_mr", "wheel_rl", "wheel_rr",
            "FrontLeftWheel", "FrontRightWheel",
            "MiddleLeftWheel", "MiddleRightWheel",
            "RearLeftWheel", "RearRightWheel"
        )
        val found = ArrayList<Int>()
        val semanticNames = HashMap<Int, String>()
        for (name in names) {
            val entity = asset.getFirstEntityByName(name)
            if (entity != 0 && !found.contains(entity)) {
                found.add(entity)
                semanticNames[entity] = name
            }
        }
        wheelEntities = found.toIntArray()
        frontWheelEntities.clear()
        val frontNames = setOf(
            "Wheel_FL", "Wheel_FR", "wheel_fl", "wheel_fr",
            "FrontLeftWheel", "FrontRightWheel"
        )
        for (name in frontNames) {
            val entity = asset.getFirstEntityByName(name)
            if (entity != 0) frontWheelEntities.add(entity)
        }

        val tm = viewer.engine.transformManager
        wheelBaseTransforms.clear()
        wheelContactLongitudinal.clear()
        wheelContactLateral.clear()
        wheelSuspensionDisplacement.clear()
        wheelSuspensionVelocity.clear()
        wheelSteeringAngle.clear()

        for (entity in wheelEntities) {
            if (!tm.hasComponent(entity)) continue
            wheelBaseTransforms[entity] =
                tm.getTransform(tm.getInstance(entity), FloatArray(16))

            val name = semanticNames[entity].orEmpty().lowercase()
            val front = frontWheelEntities.contains(entity)
            val middle = name.contains("_ml") || name.contains("_mr") ||
                name.contains("middleleft") || name.contains("middleright")
            val lateralFromName = when {
                name.contains("_fl") || name.contains("frontleft") ||
                    name.contains("_ml") || name.contains("middleleft") ||
                    name.contains("_rl") || name.contains("rearleft") -> -1.0
                name.contains("_fr") || name.contains("frontright") ||
                    name.contains("_mr") || name.contains("middleright") ||
                    name.contains("_rr") || name.contains("rearright") -> 1.0
                else -> {
                    val base = wheelBaseTransforms[entity]!!
                    if (base[12] >= 0.0f) 1.0 else -1.0
                }
            }

            // The production vehicle convention is +Z forward. Six-wheel
            // production GLBs may expose FL/FR, ML/MR and RL/RR nodes.
            // Middle-axle contact is sampled at the chassis midpoint while
            // steering remains restricted to the front axle.
            wheelContactLongitudinal[entity] = when {
                front -> 1.15
                middle -> 0.0
                else -> -1.15
            }
            wheelContactLateral[entity] = lateralFromName * 0.78
            wheelSuspensionDisplacement[entity] = 0.0
            wheelSuspensionVelocity[entity] = 0.0
        }
    }


    /**
     * Discovers production GLB door nodes by semantic names.
     * Door pivots should be authored at the real hinge in the final asset;
     * this runtime then applies a physically plausible opening angle without
     * hard-coding entity ids.
     */
    private fun cacheDoorEntities() {
        val asset = viewer.asset ?: return
        val tm = viewer.engine.transformManager
        val candidates = arrayOf(
            "Door_FL" to -1f,
            "Door_FR" to 1f,
            "Door_RL" to -1f,
            "Door_RR" to 1f,
            "door_fl" to -1f,
            "door_fr" to 1f,
            "door_rl" to -1f,
            "door_rr" to 1f,
            "SideDoor_L" to -1f,
            "SideDoor_R" to 1f,
            "PassengerDoor_L" to -1f,
            "PassengerDoor_R" to 1f,
            "BusDoor_Front" to 1f,
            "BusDoor_Side" to 1f
        )

        val found = ArrayList<Int>()
        doorBaseTransforms.clear()
        doorHingeSigns.clear()

        for ((name, sign) in candidates) {
            val entity = asset.getFirstEntityByName(name)
            if (entity != 0 && !found.contains(entity) && tm.hasComponent(entity)) {
                found.add(entity)
                doorBaseTransforms[entity] =
                    tm.getTransform(tm.getInstance(entity), FloatArray(16))
                doorHingeSigns[entity] = sign
            }
        }
        doorEntities = found.toIntArray()
    }

    private fun updateVehicleInteractionVisuals() {
        if (doorEntities.isEmpty()) return
        if (interactionProfile.doorAnimation == "none") return

        val progress = vehicleInteraction.doorProgress(interactionProfile)
        val maxAngle = when (interactionProfile.doorAnimation) {
            "open_passenger_door" -> 78f
            else -> 72f
        }
        val angle = Math.toRadians(maxAngle.toDouble()).toFloat() * progress
        val tm = viewer.engine.transformManager

        tm.openLocalTransformTransaction()
        try {
            for (entity in doorEntities) {
                if (!tm.hasComponent(entity)) continue
                val base = doorBaseTransforms[entity] ?: continue
                val sign = doorHingeSigns[entity] ?: 1f
                val transform = Mat4.of(*base) *
                    rotation(Float3(0.0f, 1.0f, 0.0f), sign * angle)
                tm.setTransform(tm.getInstance(entity), transform.toFloatArray())
            }
        } finally {
            tm.commitLocalTransformTransaction()
        }
    }

    private fun cacheVehicleRoot() {
        val asset = viewer.asset ?: return
        val tm = viewer.engine.transformManager
        val wheelParents = wheelEntities.asSequence().mapNotNull { entity ->
            if (tm.hasComponent(entity)) tm.getParent(tm.getInstance(entity)) else null
        }.filter { it != 0 }.distinct().toList()

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
            vehicleRootBaseTransform = tm.getTransform(tm.getInstance(vehicleRootEntity), FloatArray(16))
        }
    }

    private fun updateVehicleMechanics() {
        if (wheelEntities.isEmpty() && vehicleRootEntity == 0) return

        val tm = viewer.engine.transformManager
        val dt = frameDeltaSeconds.coerceIn(1.0 / 240.0, 0.05)
        // Free-rolling tyre model: angular speed follows the real linear speed,
        // with a small response filter so acceleration and braking do not make the
        // wheel visually snap. Radius comes from the active vehicle definition.
        val targetWheelSpinRate = vehicleSpeed / wheelRadius.coerceAtLeast(0.12)
        wheelSpinRate += (targetWheelSpinRate - wheelSpinRate) * (dt * 14.0).coerceAtMost(1.0)
        wheelSpinAngle += wheelSpinRate * dt
        if (wheelSpinAngle > Math.PI * 2.0 || wheelSpinAngle < -Math.PI * 2.0) {
            wheelSpinAngle %= Math.PI * 2.0
        }
        val wheelAngle = wheelSpinAngle.toFloat()

        val road = RoadSpline.sampleRelative(vehicleDistance, renderOriginDistance)
        val roadAhead = RoadSpline.sampleRelative(
            vehicleDistance + 1.8,
            renderOriginDistance
        )
        val roadBehind = RoadSpline.sampleRelative(
            vehicleDistance - 1.8,
            renderOriginDistance
        )

        val yawDelta = Math.atan2(
            Math.sin(road.yaw - previousRoadYaw),
            Math.cos(road.yaw - previousRoadYaw)
        )
        roadYawRate += (yawDelta / dt - roadYawRate) * (dt * 7.0).coerceAtMost(1.0)
        previousRoadYaw = road.yaw.toDouble()

        // Independent tyre contact patches. Each tyre samples the road at
        // its own longitudinal/lateral position, including the road's banking.
        // This makes the body settle differently over bumps, cambers and curves.
        val contactHeights = HashMap<Int, Double>()
        val contactCompression = HashMap<Int, Double>()
        var frontSum = 0.0
        var rearSum = 0.0
        var middleSum = 0.0
        var leftSum = 0.0
        var rightSum = 0.0
        var frontCount = 0
        var rearCount = 0
        var middleCount = 0
        var leftCount = 0
        var rightCount = 0

        for (entity in wheelEntities) {
            val longitudinal = wheelContactLongitudinal[entity] ?: continue
            val lateral = wheelContactLateral[entity] ?: continue
            val sample = RoadSpline.sampleRelative(
                vehicleDistance + longitudinal,
                renderOriginDistance
            )

            // Sample the actual tyre position, not just the road centreline.
            // lateralOffset is the car's current position across the road; adding
            // the wheel's own lateral position makes suspension react correctly
            // when the child steers onto a banked road edge.
            val tyreLateral = lateralOffset + lateral
            val lateralLift = Math.sin(sample.bank.toDouble()) * tyreLateral
            val contactY = sample.y.toDouble() + lateralLift
            contactHeights[entity] = contactY

            val displacement = wheelSuspensionDisplacement[entity] ?: 0.0
            val velocity = wheelSuspensionVelocity[entity] ?: 0.0
            val target = ((contactY - road.y.toDouble()) * 0.22)
                .coerceIn(-0.075, 0.075)

            // Wheel spring/damper: critically damped enough for a child-friendly
            // smooth ride while preserving visible mechanical response.
            val springForce = (target - displacement) * 22.0
            val damperForce = velocity * 6.4
            val nextVelocity = velocity + (springForce - damperForce) * dt
            val nextDisplacement = (displacement + nextVelocity * dt)
                .coerceIn(-0.075, 0.075)
            wheelSuspensionVelocity[entity] = nextVelocity
            wheelSuspensionDisplacement[entity] = nextDisplacement
            contactCompression[entity] = nextDisplacement

            when {
                frontWheelEntities.contains(entity) -> {
                    frontSum += contactY
                    frontCount++
                }
                kotlin.math.abs(longitudinal) < 0.01 -> {
                    middleSum += contactY
                    middleCount++
                }
                else -> {
                    rearSum += contactY
                    rearCount++
                }
            }
            if (lateral < 0.0) {
                leftSum += contactY
                leftCount++
            } else {
                rightSum += contactY
                rightCount++
            }
        }

        val frontHeight = if (frontCount > 0) frontSum / frontCount else roadAhead.y.toDouble()
        val rearHeight = if (rearCount > 0) rearSum / rearCount else roadBehind.y.toDouble()
        val middleHeight = if (middleCount > 0) middleSum / middleCount else road.y.toDouble()
        val rearAxleReference = if (rearCount > 0 && middleCount > 0) {
            (rearHeight + middleHeight) * 0.5
        } else if (rearCount > 0) rearHeight else middleHeight
        val leftHeight = if (leftCount > 0) leftSum / leftCount else road.y.toDouble()
        val rightHeight = if (rightCount > 0) rightSum / rightCount else road.y.toDouble()

        val axlePitch = Math.atan2(
            frontHeight - rearAxleReference,
            2.30
        ).coerceIn(-0.16, 0.16).toFloat()
        val contactRoll = Math.atan2(
            rightHeight - leftHeight,
            1.56
        ).coerceIn(-0.10, 0.10).toFloat()

        val longitudinalWeightTransfer = (-vehicleAcceleration * 0.012)
            .coerceIn(-0.065, 0.065)
        val lateralWeightTransfer = (roadYawRate * vehicleSpeed * 0.0028)
            .coerceIn(-0.055, 0.055)
        chassisPitch += ((axlePitch + longitudinalWeightTransfer) - chassisPitch) *
            (dt * 8.0).coerceAtMost(1.0)
        chassisRoll += ((contactRoll + lateralWeightTransfer) - chassisRoll) *
            (dt * 9.0).coerceAtMost(1.0)

        val averageSuspension = if (contactCompression.isNotEmpty()) {
            contactCompression.values.average()
        } else 0.0
        suspensionVelocity = (averageSuspension - suspensionDisplacement) / dt
        suspensionDisplacement += (averageSuspension - suspensionDisplacement) *
            (dt * 12.0).coerceAtMost(1.0)
        suspensionDisplacement = suspensionDisplacement.coerceIn(-0.075, 0.075)

        tm.openLocalTransformTransaction()
        try {
            val baseRoot = vehicleRootBaseTransform
            if (vehicleRootEntity != 0 && baseRoot != null && tm.hasComponent(vehicleRootEntity)) {
                // Subtle steering yaw: the chassis points into the child's
                // turn before the lateral position catches up, like a real car.
                val steeringClassGain = when (activeVehicle.type) {
                    "motorcycle", "cycle" -> 0.11
                    "sport", "concept" -> 0.085
                    "truck", "bus", "construction", "farm" -> 0.070
                    "offroad", "safari" -> 0.090
                    else -> 0.095
                }
                val steeringYaw = (autoSteeringInput * steeringClassGain *
                    (vehicleSpeed / targetSpeed.coerceAtLeast(0.1)).coerceIn(0.0, 1.0))
                    .coerceIn(-steeringClassGain, steeringClassGain)
                val chassisYaw = road.yaw + steeringYaw
                val chassis = Mat4.of(*baseRoot) *
                    Mat4.of(
                        1f, 0f, 0f, (road.x + kotlin.math.cos(road.yaw) * lateralOffset).toFloat(),
                        0f, 1f, 0f, road.y.toFloat() + suspensionDisplacement.toFloat(),
                        0f, 0f, 1f, (road.z - kotlin.math.sin(road.yaw) * lateralOffset).toFloat(),
                        0f, 0f, 0f, 1f
                    ) *
                    rotation(Float3(0.0f, 1.0f, 0.0f), chassisYaw.toFloat()) *
                    // Apply each attitude component once. Road bank is the
                    // environment attitude; chassis roll/pitch are the vehicle's
                    // filtered response to suspension, steering and acceleration.
                    rotation(
                        Float3(0.0f, 0.0f, 1.0f),
                        (road.bank + chassisRoll + roadYawRate * 0.010)
                            .coerceIn(-0.16, 0.16).toFloat()
                    ) *
                    rotation(
                        Float3(1.0f, 0.0f, 0.0f),
                        chassisPitch.coerceIn(-0.18, 0.18).toFloat()
                    )
                tm.setTransform(tm.getInstance(vehicleRootEntity), chassis.toFloatArray())
            }

            if (renderProfile.enableWheelAnimation) {
                for (entity in wheelEntities) {
                    val base = wheelBaseTransforms[entity] ?: continue
                    if (!tm.hasComponent(entity)) continue

                    val isFrontWheel = frontWheelEntities.contains(entity)
                    val speedRatio = (vehicleSpeed / targetSpeed.coerceAtLeast(0.1))
                        .coerceIn(0.0, 1.0)
                    val steeringLimit = (0.58 - 0.20 * speedRatio)
                        .coerceIn(0.34, 0.58)
                    val curvatureYaw = kotlin.math.atan2(
                        kotlin.math.sin(roadAhead.yaw - roadBehind.yaw),
                        kotlin.math.cos(roadAhead.yaw - roadBehind.yaw)
                    )
                    val steeringTarget = kotlin.math.atan(2.30 * (curvatureYaw / 3.6))
                    // Different vehicle classes have different steering sensitivity.
                    // The control stays identical for the child, while the vehicle
                    // responds like its real-world class.
                    val steeringGain = when (activeVehicle.type) {
                        "motorcycle", "cycle" -> 0.40
                        "sport", "concept" -> 0.30
                        "truck", "bus", "construction", "farm" -> 0.25
                        "offroad", "safari" -> 0.32
                        else -> 0.34
                    }
                    // Front wheels are driven by autonomous road curvature.
                    val autoSteer = if (driving) autoSteeringInput * steeringGain else 0.0
                    val steerTarget = if (isFrontWheel) {
                        // Ackermann-inspired geometry: the inside front wheel
                        // turns slightly more than the outside wheel.
                        val side = wheelContactLateral[entity] ?: 0.0
                        val baseSteer = steeringTarget + autoSteer
                        val ackermannGain = 0.055 * kotlin.math.abs(baseSteer)
                        val adjusted = when {
                            baseSteer > 0.0 && side < 0.0 -> baseSteer + ackermannGain
                            baseSteer > 0.0 && side > 0.0 -> baseSteer - ackermannGain
                            baseSteer < 0.0 && side > 0.0 -> baseSteer - ackermannGain
                            baseSteer < 0.0 && side < 0.0 -> baseSteer + ackermannGain
                            else -> baseSteer
                        }
                        adjusted.coerceIn(-steeringLimit, steeringLimit)
                    } else 0.0
                    val previousSteer = wheelSteeringAngle[entity] ?: 0.0
                    // Steering rack inertia is speed-aware: gentle at low speed for
                    // child-friendly control, slightly firmer at speed for stability.
                    val steeringResponse = if (speedRatio < 0.35) 9.5 else 7.0
                    val steeringBlend = (dt * steeringResponse).coerceAtMost(1.0)
                    val smoothedSteer = previousSteer +
                        (steerTarget - previousSteer) * steeringBlend
                    wheelSteeringAngle[entity] = smoothedSteer
                    val steerAngle = smoothedSteer.toFloat()

                    val wheelSteering = if (isFrontWheel) {
                        rotation(Float3(0.0f, 1.0f, 0.0f), steerAngle)
                    } else Mat4.identity()

                    // Spin distance is converted to tyre rotation; keeping
                    // the angle continuous avoids visible snapping at high speed.
                    val wheelRotation = rotation(
                        Float3(1.0f, 0.0f, 0.0f),
                        wheelAngle
                    )
                    val localSuspension =
                        (wheelSuspensionDisplacement[entity] ?: 0.0).toFloat()
                    val wheelLift = Mat4.of(
                        1f, 0f, 0f, 0f,
                        0f, 1f, 0f, localSuspension,
                        0f, 0f, 1f, 0f,
                        0f, 0f, 0f, 1f
                    )
                    val transform =
                        wheelLift * Mat4.of(*base) * wheelSteering * wheelRotation
                    tm.setTransform(tm.getInstance(entity), transform.toFloatArray())
                }
            }
        } finally {
            tm.commitLocalTransformTransaction()
        }
    }

    /**
     * Keeps the distant atmosphere coherent with the biome.
     *
     * The skybox is rebuilt only when the journey crosses a biome chapter, not
     * every frame. This gives forest, river, mountain, desert, village and coast
     * genuinely different depth cues while keeping the runtime allocation small.
     */
    private fun updateSkybox(world: WorldDirector.Profile, force: Boolean = false) {
        // Rebuild only when the atmosphere has visibly changed. The interpolation
        // itself is continuous, but avoiding a Skybox allocation every frame keeps
        // mobile GPU/CPU pressure low.
        val changedEnough =
            force ||
                lastSkyR.isNaN() ||
                kotlin.math.abs(world.skyR - lastSkyR) > 0.018f ||
                kotlin.math.abs(world.skyG - lastSkyG) > 0.018f ||
                kotlin.math.abs(world.skyB - lastSkyB) > 0.018f

        if (!changedEnough) return

        activeSkyBiome = world.biome
        lastSkyR = world.skyR
        lastSkyG = world.skyG
        lastSkyB = world.skyB

        viewer.scene.skybox = Skybox.Builder()
            .color(world.skyR, world.skyG, world.skyB, world.skyA)
            .showSun(true)
            .build(viewer.engine)
    }

    /**
     * Shared lateral dynamics cues for believable driving.
     *
     * The child only starts/stops the journey. The renderer
     * derives a small, filtered roll/yaw response from lateral acceleration so the
     * vehicle and camera visually carry momentum through a turn.
     */
    private fun steeringLoadForDynamics(): Double {
        if (!driving) return 0.0
        val speedNorm = (vehicleSpeed / targetSpeed.coerceAtLeast(0.1)).coerceIn(0.0, 1.0)
        return (autoSteeringInput * (0.35 + 0.65 * speedNorm)).coerceIn(-1.0, 1.0)
    }

    private fun updateDrivingInertia(dt: Double) {
        val safeDt = dt.coerceIn(1.0 / 240.0, 0.05)
        // Approximate lateral load from the current velocity vector and steering.
        // Road curvature is converted into a smooth, speed-aware body response.
        // turns that input into a smooth, speed-aware body response.
        val lateralAcceleration = ((lateralVelocity * vehicleSpeed) * 0.58 +
            steeringLoadForDynamics() * vehicleSpeed * vehicleSpeed * 0.018)
            .coerceIn(-6.5, 6.5)

        val rollTarget = (-lateralAcceleration * 0.022)
            .coerceIn(-0.095, 0.095)
        // Road yaw rate is already measured from the authoritative spline. Blend a
        // small steering contribution into the visual chassis pitch/roll envelope.
        val steeringLoad = autoSteeringInput * (vehicleSpeed / targetSpeed.coerceAtLeast(0.1))
            .coerceIn(-1.0, 1.0)

        // Weight transfer: braking/acceleration changes the chassis pitch while
        // cornering adds a small load-dependent roll component. The damping keeps
        // the motion smooth enough for a child-facing camera.
        val pitchTarget = (-vehicleAcceleration * 0.0032 +
            kotlin.math.abs(steeringLoad) * 0.0025)
            .coerceIn(-0.042, 0.042)
        val dynamicRoll = (-steeringLoad * vehicleSpeed * 0.0022)
            .coerceIn(-0.035, 0.035)
        val combinedRollTarget = (rollTarget + dynamicRoll).coerceIn(-0.095, 0.095)

        chassisRoll += (combinedRollTarget - chassisRoll) *
            (safeDt * 7.5).coerceAtMost(1.0)
        chassisPitch += (pitchTarget - chassisPitch) *
            (safeDt * 6.5).coerceAtMost(1.0)
    }

    private fun updateDriveScene() {
        // The camera now reads a deterministic world profile as the child travels:
        // forest -> river -> mountain -> desert -> plateau -> market -> village -> coast.
        // This changes the visual rhythm without loading a large environment pack.
        val travel = vehicleDistance
        val world = WorldDirector.atmosphere(travel)
        updateSkybox(world)
        val road = RoadSpline.sampleRelative(travel, renderOriginDistance)
        // Adaptive look-ahead increases with speed, so the child sees curves and
        // the surrounding world early without turning the camera into an arcade view.
        val speedRatio = (vehicleSpeed / targetSpeed.coerceAtLeast(0.1)).coerceIn(0.0, 1.0)
        val lookAheadDistance = 24.0 + 24.0 * speedRatio
        val lookAhead = RoadSpline.sampleRelative(travel + lookAheadDistance, renderOriginDistance)
        val bodyBob = if (driving) kotlin.math.sin(travel * 3.4) * 0.012 else 0.0

        // Inertial camera: yaw and bank are filtered from the same road spline that
        // drives the chassis. This gives a believable driver's-eye response on curves
        // while keeping the road readable and avoiding abrupt child-unfriendly motion.
        val dt = frameDeltaSeconds.coerceIn(1.0 / 240.0, 0.05)
        val yawTarget = road.yaw
        val bankTarget = (road.bank + roadYawRate * 0.010).coerceIn(-0.12, 0.12)
        val cameraBlend = (dt * 8.5).coerceIn(0.0, 1.0)
        cameraYaw += kotlin.math.atan2(
            kotlin.math.sin(yawTarget - cameraYaw),
            kotlin.math.cos(yawTarget - cameraYaw)
        ) * cameraBlend
        cameraBank += (bankTarget - cameraBank) * (dt * 6.0).coerceIn(0.0, 1.0)

        // Place the camera on the actual road tangent rather than on a fixed
        // world-Z rail. This is the important realism correction for curves:
        // the child sees the vehicle follow the road naturally instead of the
        // camera appearing to slide sideways around bends.
        // Use the filtered camera yaw for both position and viewing direction.
        // This removes the subtle snap that appears when the road curvature changes.
        val forwardX = kotlin.math.sin(cameraYaw)
        val forwardZ = kotlin.math.cos(cameraYaw)
        val roadRightX = kotlin.math.cos(cameraYaw)
        val roadRightZ = -kotlin.math.sin(cameraYaw)
        val cameraX = road.x + roadRightX * lateralOffset - forwardX * 6.9 + kotlin.math.sin(travel * 0.18) * 0.035
        val cameraZ = road.z + roadRightZ * lateralOffset - forwardZ * 6.9

        // Small grade and acceleration cues make the camera feel attached to the
        // vehicle mass without introducing uncomfortable child-facing motion.
        val gradePitch = road.grade.toDouble() * 0.11
        val accelerationPitch = (vehicleAcceleration * 0.0065).coerceIn(-0.055, 0.055)
        val inertiaPitch = chassisPitch * 0.45
        val cameraY = world.cameraHeight + road.y + bodyBob + gradePitch - accelerationPitch + inertiaPitch

        // Roll the camera gently with the road bank. Keep the vertical axis
        // stable enough for children while preserving physical cornering cues.
        val totalBank = (cameraBank + chassisRoll * 0.55).coerceIn(-0.16, 0.16)
        val upX = -kotlin.math.sin(totalBank)
        val upY = kotlin.math.cos(totalBank)
        val upZ = kotlin.math.sin(totalBank * 0.18)

        viewer.camera.lookAt(
            cameraX, cameraY, cameraZ,
            lookAhead.x + roadRightX * lateralOffset, lookAhead.y + world.lookAheadLift, lookAhead.z + roadRightZ * lateralOffset,
            upX, upY, upZ
        )

        // Speed provides a restrained FOV change; each biome adds only a subtle
        // composition bias so the child notices a new place without nausea.
        val focalLengthMm = 48.0 + world.fovBias + 3.0 * speedRatio
        // Keep the physical camera lens while matching the real portrait viewport
        // aspect ratio. A fixed 1:1 aspect compresses the world on tall phones.
        // The 1000 m culling range preserves distant terrain/settlement silhouettes;
        // the floating origin keeps depth precision stable during long journeys.
        val aspect = if (surface.height > 0) {
            surface.width.toDouble() / surface.height.toDouble()
        } else 1.0
        viewer.camera.setLensProjection(focalLengthMm, aspect, 0.08, 1000.0)
        viewer.camera.setExposure(world.exposure, 1.0f / 120.0f, 100.0f)
    }

    override fun onDetachedFromWindow() {
        started = false
        surface.visibility = android.view.View.INVISIBLE
        lastFrameNanos = 0L
        vehicleSpeed = 0.0
        previousVehicleSpeed = 0.0
        vehicleAcceleration = 0.0
        wheelSpinAngle = 0.0
        wheelSpinRate = 0.0
        chassisPitch = 0.0
        chassisRoll = 0.0
        previousRoadYaw = 0.0
        roadYawRate = 0.0
        cameraBank = 0.0
        cameraYaw = 0.0
        autoSteeringInput = 0.0
        lateralOffset = 0.0
        lateralVelocity = 0.0
        lateralSlip = 0.0
        lastSkyR = Float.NaN
        lastSkyG = Float.NaN
        lastSkyB = Float.NaN
        assetLoadGeneration.incrementAndGet()
        frameCallback?.let { choreographer.removeFrameCallback(it) }
        frameCallback = null
        headroomMonitor.stop()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val power = powerManager
            val listener = thermalListener
            if (power != null && listener != null) {
                power.removeThermalStatusListener(listener)
            }
        }
        thermalListener = null
        powerManager = null
        physicsAccumulator = 0.0
        proceduralRoad?.destroy()
        proceduralRoad = null
        terrainMesh?.destroy()
        terrainMesh = null
        roadsideWorld?.destroy()
        waterSurfaceWorld?.destroy()
        shorelineWorld?.destroy()
        worldLife?.destroy()
        nearFieldCreatureWorld?.destroy()
        worldLife = null
        nearFieldCreatureWorld = null
        childNPC?.destroy()
        childNPC = null
        learningAnimalEncounter?.destroy()
        learningAnimalEncounter = null
        learningSignWorld?.destroy()
        learningSignWorld = null
        roadsideWorld = null
        waterSurfaceWorld = null
        if (rendererReady && sunEntity != 0) {
            viewer.scene.removeEntity(sunEntity)
            viewer.engine.lightManager.destroy(sunEntity)
            EntityManager.get().destroy(sunEntity)
            sunEntity = 0
        }
        assetIoExecutor.shutdownNow()
        if (rendererReady) {
            runCatching { viewer.destroy() }
            rendererReady = false
        }
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
        private const val MODEL_GZ = "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"
    }
}