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
    private var childNPC: ChildNPCWorld? = null
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
    private var steeringInput = 0.0
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
                childNPC = ChildNPCWorld(viewer.engine, viewer.scene, asset).also { it.build() }
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
        } catch (_: Throwable) {
            rendererReady = false
            proceduralRoad = null
            terrainMesh = null
            roadsideWorld = null
            waterSurfaceWorld = null
            shorelineWorld = null
            worldLife = null
            childNPC = null
        }
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
                    childNPC?.update(vehicleDistance)
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
        val steeringAuthority = (1.12 - 0.48 * speedRatio).coerceIn(0.64, 1.12)
        val maxLateralVelocity = (0.92 - 0.16 * speedRatio).coerceIn(0.62, 0.92)
        val desiredLateralVelocity = if (driving) steeringInput * maxLateralVelocity * steeringAuthority else 0.0
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
        if (!driving) lateralOffset *= (1.0 - (dt * 2.8).coerceAtMost(0.9))
        lateralOffset = lateralOffset.coerceIn(-laneLimit, laneLimit)

        if (kotlin.math.abs(vehicleDistance - renderOriginDistance) >= 180.0) {
            renderOriginDistance = vehicleDistance
        }
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
        // A child cannot start moving while the avatar is in the middle of
        // entering/exiting a vehicle. The normal gameplay path still remains
        // one tap to drive / one tap to stop.
        if (value && vehicleInteraction.state != VehicleInteractionController.State.OUTSIDE) return
        driving = value
        if (!value && vehicleSpeed < 0.02) vehicleSpeed = 0.0
        if (!value) steeringInput = 0.0
    }

    /** One-touch steering: -1 left, +1 right, 0 recentres naturally. */
    fun setSteeringInput(value: Float) {
        steeringInput = value.coerceIn(-1.0f, 1.0f).toDouble()
    }

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
                worldLife = null
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
            "Wheel_FL", "Wheel_FR", "Wheel_RL", "Wheel_RR",
            "wheel_fl", "wheel_fr", "wheel_rl", "wheel_rr",
            "FrontLeftWheel", "FrontRightWheel", "RearLeftWheel", "RearRightWheel"
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
            val lateralFromName = when {
                name.contains("_fl") || name.contains("frontleft") -> -1.0
                name.contains("_fr") || name.contains("frontright") -> 1.0
                name.contains("_rl") || name.contains("rearleft") -> -1.0
                name.contains("_rr") || name.contains("rearright") -> 1.0
                else -> {
                    val base = wheelBaseTransforms[entity]!!
                    if (base[12] >= 0.0f) 1.0 else -1.0
                }
            }

            // The production vehicle convention is +Z forward. Semantic wheel
            // names override the mesh's authored longitudinal position so that
            // different GLBs share the same physical contact model.
            wheelContactLongitudinal[entity] = if (front) 1.15 else -1.15
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