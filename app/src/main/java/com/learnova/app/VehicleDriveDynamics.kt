package com.learnova.app

/**
 * Deterministic vehicle dynamics layer for the 3D journey.
 *
 * Motion authority lives here:
 * - tap/start requests acceleration toward the selected vehicle target speed;
 * - tap/stop releases propulsion and lets the vehicle coast down naturally;
 * - learning mode uses a controlled crawl speed;
 * - braking is reported from the same authoritative state consumed by rendering.
 *
 * Rendering must never integrate speed, distance or wheel rotation independently.
 */
internal class VehicleDriveDynamics(
    private val profile: VehicleDefinition
) {
    data class Snapshot(
        val speedMetersPerSecond: Double,
        val distanceMeters: Double,
        val steering: Double,
        val yaw: Float,
        val bank: Float,
        val grade: Float,
        val wheelRotationRadians: Double,
        val braking: Boolean
    )

    private companion object {
        const val DRIVE_ACCELERATION_METERS_PER_SECOND_SQUARED = 2.8
        const val COMFORT_DECELERATION_METERS_PER_SECOND_SQUARED = 2.4
        const val CONTROLLED_BRAKING_METERS_PER_SECOND_SQUARED = 4.8
        const val LEARNING_CRAWL_SPEED_METERS_PER_SECOND = 1.4
        const val STOP_EPSILON_METERS_PER_SECOND = 0.025
    }

    private var speed = 0.0
    private var distance = 0.0
    private var wheelRotation = 0.0
    private var braking = false
    private var last = RoadSpline.sample(0.0)

    fun reset(distanceMeters: Double = 0.0) {
        distance = distanceMeters.coerceAtLeast(0.0)
        speed = 0.0
        wheelRotation = 0.0
        braking = false
        last = RoadSpline.sample(distance)
    }

    /**
     * Advances authoritative vehicle motion.
     *
     * requestedMotion=false means propulsion is released, not an instantaneous
     * hard stop. The vehicle continues along the road while speed naturally
     * falls to zero. The caller may use the same path for an actual braking
     * condition later without changing renderer ownership of motion.
     */
    fun update(
        deltaSeconds: Double,
        requestedMotion: Boolean,
        learningPause: Boolean = false
    ): Snapshot {
        val dt = deltaSeconds.coerceIn(0.0, 0.25)
        val sample = RoadSpline.sample(distance)

        val target = when {
            requestedMotion && learningPause ->
                LEARNING_CRAWL_SPEED_METERS_PER_SECOND
            requestedMotion ->
                FutureVehicleBehavior.effectiveTargetSpeed(profile)
            else ->
                0.0
        }

        val accelerating = target > speed + STOP_EPSILON_METERS_PER_SECOND
        val decelerating = target < speed - STOP_EPSILON_METERS_PER_SECOND

        speed = when {
            accelerating ->
                min(
                    target,
                    speed + DRIVE_ACCELERATION_METERS_PER_SECOND_SQUARED * dt
                )
            decelerating -> {
                val rate = if (requestedMotion) {
                    CONTROLLED_BRAKING_METERS_PER_SECOND_SQUARED
                } else {
                    COMFORT_DECELERATION_METERS_PER_SECOND_SQUARED
                }
                max(target, speed - rate * dt)
            }
            else -> target.coerceAtLeast(0.0)
        }

        if (speed < STOP_EPSILON_METERS_PER_SECOND && target <= 0.0) {
            speed = 0.0
        }

        braking = decelerating

        distance += speed * dt

        val next = RoadSpline.sample(distance)
        val steering = ((next.yaw - sample.yaw) * 3.2).coerceIn(-1.0, 1.0)
        wheelRotation += speed * dt / profile.wheelRadius.coerceAtLeast(0.18)
        last = next

        return Snapshot(
            speedMetersPerSecond = speed,
            distanceMeters = distance,
            steering = steering,
            yaw = next.yaw,
            bank = next.bank,
            grade = next.grade,
            wheelRotationRadians = wheelRotation,
            braking = braking
        )
    }

    fun snapshot(): Snapshot = Snapshot(
        speedMetersPerSecond = speed,
        distanceMeters = distance,
        steering = (
            (last.yaw - RoadSpline.sample(max(0.0, distance - 0.5)).yaw) * 3.2
        ).coerceIn(-1.0, 1.0),
        yaw = last.yaw,
        bank = last.bank,
        grade = last.grade,
        wheelRotationRadians = wheelRotation,
        braking = braking
    )

    fun isStable(): Boolean =
        speed >= 0.0 &&
        distance >= 0.0 &&
        abs(last.bank) <= 0.12 &&
        abs(last.grade) <= 0.16
}

internal object FutureVehicleBehavior {
    fun effectiveTargetSpeed(vehicle: VehicleDefinition): Double = when {
        vehicle.assetKey.contains("2050") || vehicle.assetKey.contains("future") ->
            vehicle.targetSpeed.coerceIn(5.0, 10.0)
        vehicle.type == "electric" ->
            (vehicle.targetSpeed * 1.04).coerceAtMost(9.5)
        else -> vehicle.targetSpeed
    }
}
