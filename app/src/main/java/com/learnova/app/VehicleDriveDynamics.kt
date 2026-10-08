package com.learnova.app

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Deterministic vehicle dynamics layer for the 3D journey.
 *
 * Motion authority lives here:
 * - tap/start requests acceleration toward the selected vehicle target speed;
 * - the road spline automatically shapes speed before curves and on grades;
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
        const val LEARNING_CRAWL_SPEED_METERS_PER_SECOND = 1.4
        const val STOP_EPSILON_METERS_PER_SECOND = 0.025

        // The vehicle reads the road a few metres ahead so it can settle its
        // speed before a bend rather than braking at the last moment.
        const val SPEED_LOOK_AHEAD_METERS = 6.0
        const val MAX_CURVE_SPEED_REDUCTION = 0.38
        const val CURVE_SENSITIVITY = 1.8
        const val GRADE_SENSITIVITY = 0.50
        const val MIN_ROAD_SPEED_FACTOR = 0.62
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
     * falls to zero.
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
                automaticRoadTargetSpeed(sample)
            else ->
                0.0
        }

        val accelerating = target > speed + STOP_EPSILON_METERS_PER_SECOND
        val decelerating = target < speed - STOP_EPSILON_METERS_PER_SECOND

        speed = when {
            accelerating ->
                min(
                    target,
                    speed + profile.driveAcceleration * dt
                )
            decelerating -> {
                val rate = if (requestedMotion) {
                    profile.serviceBraking
                } else {
                    profile.coastDeceleration
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
        val steering = shortestYawDelta(sample.yaw, next.yaw)
            .times(profile.steeringResponse)
            .coerceIn(-1.0, 1.0)
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

    /**
     * Computes a child-friendly automatic speed limit from the authoritative
     * road spline. The vehicle anticipates curvature and terrain instead of
     * waiting until the bend is visually underneath it.
     */
    private fun automaticRoadTargetSpeed(sample: RoadSpline.Sample): Double {
        val base = FutureVehicleBehavior.effectiveTargetSpeed(profile)
        val ahead = RoadSpline.sample(distance + SPEED_LOOK_AHEAD_METERS)

        val curvaturePerMeter =
            abs(shortestYawDelta(sample.yaw, ahead.yaw)) / SPEED_LOOK_AHEAD_METERS

        val curveReduction =
            (curvaturePerMeter * CURVE_SENSITIVITY)
                .coerceIn(0.0, MAX_CURVE_SPEED_REDUCTION)

        val gradeReduction =
            (abs(sample.grade.toDouble()) * GRADE_SENSITIVITY)
                .coerceIn(0.0, 0.08)

        val roadFactor =
            (1.0 - curveReduction - gradeReduction)
                .coerceIn(MIN_ROAD_SPEED_FACTOR, 1.0)

        return (base * roadFactor).coerceAtLeast(3.5)
    }

    private fun shortestYawDelta(from: Float, to: Float): Double {
        var delta = to.toDouble() - from.toDouble()
        while (delta > Math.PI) delta -= Math.PI * 2.0
        while (delta < -Math.PI) delta += Math.PI * 2.0
        return delta
    }

    fun snapshot(): Snapshot = Snapshot(
        speedMetersPerSecond = speed,
        distanceMeters = distance,
        steering = shortestYawDelta(
            RoadSpline.sample(max(0.0, distance - 0.5)).yaw,
            last.yaw
        ).times(profile.steeringResponse).coerceIn(-1.0, 1.0),
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
