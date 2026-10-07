package com.learnova.app

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Deterministic vehicle dynamics layer for the 3D journey.
 * Rendering consumes this snapshot; physics remains independent of assets.
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

    private var speed = 0.0
    private var distance = 0.0
    private var wheelRotation = 0.0
    private var last = RoadSpline.sample(0.0)

    fun reset(distanceMeters: Double = 0.0) {
        distance = distanceMeters.coerceAtLeast(0.0)
        speed = 0.0
        wheelRotation = 0.0
        last = RoadSpline.sample(distance)
    }

    fun update(deltaSeconds: Double, requestedMotion: Boolean, learningPause: Boolean = false): Snapshot {
        val dt = deltaSeconds.coerceIn(0.0, 0.25)
        val sample = RoadSpline.sample(distance)
        val target = when {
            !requestedMotion -> 0.0
            learningPause -> 1.4
            else -> FutureVehicleBehavior.effectiveTargetSpeed(profile)
        }

        val acceleration = if (target > speed) 2.8 else 4.6
        speed = if (target > speed) {
            min(target, speed + acceleration * dt)
        } else {
            max(target, speed - acceleration * dt)
        }

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
            braking = target < speed - 0.05
        )
    }

    fun snapshot(): Snapshot = Snapshot(
        speed, distance,
        ((last.yaw - RoadSpline.sample(max(0.0, distance - 0.5)).yaw) * 3.2).coerceIn(-1.0, 1.0),
        last.yaw, last.bank, last.grade, wheelRotation, false
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
