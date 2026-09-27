package com.learnova.app

import android.content.Context
import android.app.ActivityManager
import android.os.Build
import android.os.PowerManager
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
    private val viewer = ModelViewer(surface)
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
    private var chassisPitch = 0.0
    private var chassisRoll = 0.0
    private var lastFrameNanos = 0L
    // Shared render-loop timestep keeps vehicle physics deterministic across devices.
    private var frameDeltaSeconds = 1.0 / 60.0
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
    private var renderProfile = VehicleRenderProfile.forType(activeVehicle.type)
    private val vehicleAssetResolver = VehicleAssetResolver(context)
    private var assetIoExecutor: ExecutorService = newAssetIoExecutor()
    private val assetLoadGeneration = AtomicInteger(0)
    private var loadedVehicleAssetKey = "base"
    private var requestedVehicleAssetKey = "base"
    private val adaptiveQuality = LearnovaAdaptiveQuality()
    private var constrainedDevice = false
    private var thermalConstrained = false

    // Physical entry/exit state is kept separate from the child-simple drive
    // control. If a vehicle asset contains named door nodes, this layer animates
    // those nodes; otherwise it remains a safe no-op until the production GLB arrives.
    private val vehicleInteraction = VehicleInteractionController()
    private var interactionProfile = VehicleInteractionProfiles.forVehicle(activeVehicle.type)
    private var doorEntities = IntArray(0)
    private val doorBaseTransforms = HashMap<Int, FloatArray>()
    private val doorHingeSigns = HashMap<Int, Float>()

    init {
        addView(
            surface,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
        surface.setZOrderOnTop(false)
        configureDeviceRenderProfile()

        val modelBytes = decodeModel()
        viewer.loadModelGlb(ByteBuffer.wrap(modelBytes))
        // Never force-unwrap the parsed asset during Activity startup. A malformed
        // or unsupported GLB must degrade to a renderer without the road mesh rather
        // than crashing the entire app before the child can reach the learning screen.
        viewer.asset?.let { asset ->
            proceduralRoad = ProceduralRoadMesh(viewer.engine, viewer.scene, asset).also { it.build() }
            terrainMesh = LearnovaTerrainMesh(viewer.engine, viewer.scene, asset).also { it.build() }
            roadsideWorld = RoadsideWorld(viewer.engine, viewer.scene, asset).also { it.build() }
            waterSurfaceWorld = WaterSurfaceWorld(viewer.engine, viewer.scene, asset).also { it.build() }
            shorelineWorld = ShorelineWorld(viewer.engine, viewer.scene, asset).also { it.build() }
            worldLife = WorldLifeSimulation(viewer.engine, viewer.scene, asset).also { it.build() }
        }
        configureRealisticSunLight()
        updateSkybox(WorldDirector.atmosphere(0.0), force = true)
        cacheWheelEntities()
        cacheVehicleRoot()
        cacheDoorEntities()

        viewer.camera.lookAt(
            4.8, 2.8, 6.8,
            0.0, 1.0, 14.0,
            0.0, 1.0, 0.0
        )
        viewer.view.antiAliasing = com.google.android.filament.View.AntiAliasing.FXAA
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (assetIoExecutor.isShutdown || assetIoExecutor.isTerminated) {
            assetIoExecutor = newAssetIoExecutor()
        }
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
                frameDeltaSeconds = dt

                // Child-simple input, physically smoother motion: one tap starts,
                // the next tap requests a controlled stop. Speed is integrated with
                // acceleration/deceleration instead of assuming a fixed 60 FPS rate.
                val requestedSpeed = if (driving) targetSpeed else 0.0
                val response = if (driving) 2.8 else 6.5
                val blend = (response * dt).coerceAtMost(1.0)
                previousVehicleSpeed = vehicleSpeed
                vehicleSpeed += (requestedSpeed - vehicleSpeed) * blend
                vehicleAcceleration = ((vehicleSpeed - previousVehicleSpeed) / dt)
                    .coerceIn(-8.0, 8.0)
                driveTime += dt * (if (vehicleSpeed > 0.02) 1.0 else 0.0)
                vehicleDistance += vehicleSpeed * dt

                val steerTarget = if (driving) steeringInput * 0.55 else 0.0
                val steerBlend = (dt * 5.5).coerceAtMost(1.0)
                lateralVelocity += (steerTarget - lateralVelocity) * steerBlend
                lateralOffset += lateralVelocity * dt * (2.6 + vehicleSpeed * 0.08)

                // Keep the child-friendly steering inside the actual driving lane.
                // Near the road edge, progressive resistance slows the lateral motion
                // before the vehicle can cross onto the shoulder. This feels much more
                // natural than a hard invisible wall.
                val laneLimit = 2.72
                val edgeRatio = (kotlin.math.abs(lateralOffset) / laneLimit).coerceIn(0.0, 1.25)
                if (edgeRatio > 0.82) {
                    val edgeBrake = ((edgeRatio - 0.82) / 0.43).coerceIn(0.0, 1.0)
                    lateralVelocity *= (1.0 - edgeBrake * dt * 7.5).coerceAtLeast(0.20)
                    lateralOffset *= (1.0 - edgeBrake * dt * 1.8).coerceAtLeast(0.70)
                }
                if (!driving) lateralOffset *= (1.0 - (dt * 2.8).coerceAtMost(0.9))
                lateralOffset = lateralOffset.coerceIn(-laneLimit, laneLimit)

                // The camera follows the vehicle; the world itself is never
                // animated backward. Procedural meshes are regenerated around the
                // vehicle only to extend the visible horizon.
                updateDriveScene()
                if (kotlin.math.abs(vehicleDistance - renderOriginDistance) >= 4.0) {
                    renderOriginDistance = vehicleDistance
                }
                proceduralRoad?.update(vehicleDistance)
                terrainMesh?.update(vehicleDistance)
                roadsideWorld?.update(vehicleDistance)
                waterSurfaceWorld?.update(vehicleDistance)
                shorelineWorld?.update(vehicleDistance)
                worldLife?.update(vehicleDistance)
                updateVehicleMechanics()
                // Camera/body inertia reads the same lateral dynamics as the vehicle.
                // This couples steering, chassis motion and the horizon instead of
                // making the camera behave like a detached follow camera.
                updateDrivingInertia(dt)
                vehicleInteraction.update(dt.toFloat(), interactionProfile)
                updateVehicleInteractionVisuals()
                viewer.render(time)
                adaptiveQuality.sample(dt * 1000.0, constrainedDevice || thermalConstrained)?.let { applyQualityTier(it) }
                choreographer.postFrameCallback(frameCallback)
            }
            choreographer.postFrameCallback(frameCallback)
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
            val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            thermalConstrained = when (power?.currentThermalStatus) {
                PowerManager.THERMAL_STATUS_SEVERE,
                PowerManager.THERMAL_STATUS_CRITICAL,
                PowerManager.THERMAL_STATUS_EMERGENCY -> true
                else -> false
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            surface.holder.surface.setFrameRate(
                60.0f,
                android.view.Surface.FRAME_RATE_COMPATIBILITY_DEFAULT
            )
        }

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
     * Physically plausible daylight foundation for the 3D world.
     *
     * Filament uses real-world photometric units for directional light intensity,
     * so this is deliberately expressed as sunlight-like illuminance rather than
     * an arbitrary game brightness value. The light is created once and reused.
     */
    private fun applyQualityTier(tier: LearnovaAdaptiveQuality.Tier) {
        when (tier) {
            LearnovaAdaptiveQuality.Tier.HIGH -> {
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
        loadVehicleAsset(definition)
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
        val wheelAngle = (vehicleDistance / wheelRadius).toFloat()

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

        // Four independent tyre contact patches. Each tyre samples the road at
        // its own longitudinal/lateral position, including the road's banking.
        // This makes the body settle differently over bumps, cambers and curves.
        val contactHeights = HashMap<Int, Double>()
        val contactCompression = HashMap<Int, Double>()
        var frontSum = 0.0
        var rearSum = 0.0
        var leftSum = 0.0
        var rightSum = 0.0
        var frontCount = 0
        var rearCount = 0
        var leftCount = 0
        var rightCount = 0

        for (entity in wheelEntities) {
            val longitudinal = wheelContactLongitudinal[entity] ?: continue
            val lateral = wheelContactLateral[entity] ?: continue
            val sample = RoadSpline.sampleRelative(
                vehicleDistance + longitudinal,
                renderOriginDistance
            )

            // Offset from road centerline into the banked road surface.
            val lateralLift = Math.sin(sample.bank.toDouble()) * lateral
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

            val front = frontWheelEntities.contains(entity)
            if (front) {
                frontSum += contactY
                frontCount++
            } else {
                rearSum += contactY
                rearCount++
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
        val leftHeight = if (leftCount > 0) leftSum / leftCount else road.y.toDouble()
        val rightHeight = if (rightCount > 0) rightSum / rightCount else road.y.toDouble()

        val axlePitch = Math.atan2(
            frontHeight - rearHeight,
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
                val steeringYaw = (steeringInput * 0.095 *
                    (vehicleSpeed / targetSpeed.coerceAtLeast(0.1)).coerceIn(0.0, 1.0))
                    .coerceIn(-0.095, 0.095)
                val chassisYaw = road.yaw + steeringYaw
                val chassis = Mat4.of(*baseRoot) *
                    Mat4.of(
                        1f, 0f, 0f, (road.x + kotlin.math.cos(road.yaw) * lateralOffset).toFloat(),
                        0f, 1f, 0f, road.y.toFloat() + suspensionDisplacement.toFloat(),
                        0f, 0f, 1f, (road.z - kotlin.math.sin(road.yaw) * lateralOffset).toFloat(),
                        0f, 0f, 0f, 1f
                    ) *
                    rotation(Float3(0.0f, 1.0f, 0.0f), chassisYaw) *
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
                    val steeringTarget = kotlin.math.atan(
                        2.30 * (
                            kotlin.math.atan2(
                                kotlin.math.sin(roadAhead.yaw - roadBehind.yaw),
                                kotlin.math.cos(roadAhead.yaw - roadBehind.yaw)
                            ) / 3.6
                        )
                    )
                    val playerSteer = if (driving) steeringInput * 0.34 else 0.0
                    val steerTarget = if (isFrontWheel) {
                        (steeringTarget + playerSteer)
                            .coerceIn(-steeringLimit, steeringLimit)
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
     * The game remains touch-simple: the child only steers left/right. The renderer
     * derives a small, filtered roll/yaw response from lateral acceleration so the
     * vehicle and camera visually carry momentum through a turn.
     */
    private fun updateDrivingInertia(dt: Double) {
        val safeDt = dt.coerceIn(1.0 / 240.0, 0.05)
        val lateralAcceleration = ((lateralVelocity * vehicleSpeed) * 0.42)
            .coerceIn(-4.5, 4.5)

        val rollTarget = (-lateralAcceleration * 0.018)
            .coerceIn(-0.075, 0.075)
        chassisRoll += (rollTarget - chassisRoll) * (safeDt * 7.0).coerceAtMost(1.0)

        // Road yaw rate is already measured from the authoritative spline. Blend a
        // small steering contribution into the visual chassis pitch/roll envelope.
        val steeringLoad = steeringInput * (vehicleSpeed / targetSpeed.coerceAtLeast(0.1))
            .coerceIn(-1.0, 1.0)
        val pitchTarget = (-vehicleAcceleration * 0.0028 + kotlin.math.abs(steeringLoad) * 0.003)
            .coerceIn(-0.035, 0.035)
        chassisPitch += (pitchTarget - chassisPitch) * (safeDt * 6.0).coerceAtMost(1.0)
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
        lastFrameNanos = 0L
        vehicleSpeed = 0.0
        previousVehicleSpeed = 0.0
        vehicleAcceleration = 0.0
        chassisPitch = 0.0
        chassisRoll = 0.0
        previousRoadYaw = 0.0
        roadYawRate = 0.0
        cameraBank = 0.0
        cameraYaw = 0.0
        steeringInput = 0.0
        lateralOffset = 0.0
        lateralVelocity = 0.0
        lastSkyR = Float.NaN
        lastSkyG = Float.NaN
        lastSkyB = Float.NaN
        assetLoadGeneration.incrementAndGet()
        frameCallback?.let { choreographer.removeFrameCallback(it) }
        frameCallback = null
        proceduralRoad?.destroy()
        proceduralRoad = null
        terrainMesh?.destroy()
        terrainMesh = null
        roadsideWorld?.destroy()
        waterSurfaceWorld?.destroy()
        shorelineWorld?.destroy()
        worldLife?.destroy()
        worldLife = null
        roadsideWorld = null
        waterSurfaceWorld = null
        if (sunEntity != 0) {
            viewer.scene.removeEntity(sunEntity)
            viewer.engine.lightManager.destroy(sunEntity)
            EntityManager.get().destroy(sunEntity)
            sunEntity = 0
        }
        assetIoExecutor.shutdownNow()
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

// Realism stage: smooth suspension response.

// Realism stage: terrain-aware camera height.
