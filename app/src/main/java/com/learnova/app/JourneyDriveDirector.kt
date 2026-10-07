package com.learnova.app

/** Deterministic 500-level child-simple journey controller. */
class JourneyDriveDirector(
    private val levelCount: Int = 500,
    private val minimumLevelSeconds: Double = 180.0,
    private val learningWindowSeconds: Double = 90.0,
    private val vehicle: VehicleDefinition = VehicleCatalog.byId(100)
) {
    private val driveDynamics = VehicleDriveDynamics(vehicle)
    enum class State { READY, DRIVING, LEARNING, STOPPED, COMPLETED }

    data class Snapshot(
        val level: Int,
        val state: State,
        val elapsedSeconds: Double,
        val learningElapsedSeconds: Double,
        val progress: Double,
        val learningProgress: Double,
        val levelCompleted: Boolean,
        val vehicleSpeedMetersPerSecond: Double,
        val vehicleDistanceMeters: Double,
        val vehicleSteering: Double,
        val vehicleYaw: Float,
        val vehicleBank: Float,
        val vehicleGrade: Float,
        val wheelRotationRadians: Double,
        val braking: Boolean
    )

    private var level = 1
    private var state = State.READY
    private var elapsed = 0.0
    private var learningElapsed = 0.0
    private var learningTriggered = false
    private var completionLatched = false

    val currentLevel: Int get() = level

    /**
     * Synchronizes the renderer-owned journey state with the persisted gameplay level.
     * The renderer remains the single source of motion state, while MainActivity owns
     * persistence. Calling this while a journey is active is intentionally rejected
     * so a level cannot change underneath a moving vehicle.
     */
    fun setCurrentLevel(targetLevel: Int): Boolean {
        if (isDriving) return false
        val safe = targetLevel.coerceIn(1, levelCount)
        if (safe == level) return true
        level = safe
        elapsed = 0.0
        learningElapsed = 0.0
        learningTriggered = false
        completionLatched = false
        state = State.READY
        driveDynamics.reset()
        return true
    }
    val isDriving: Boolean get() = state == State.DRIVING || state == State.LEARNING
    val isLearning: Boolean get() = state == State.LEARNING
    val isComplete: Boolean get() = state == State.COMPLETED

    fun toggleDrive(): Boolean = when (state) {
        State.READY, State.STOPPED -> { state = State.DRIVING; true }
        State.DRIVING, State.LEARNING -> { state = State.STOPPED; false }
        State.COMPLETED -> false
    }

    fun update(deltaSeconds: Double): Snapshot {
        val dt = deltaSeconds.coerceIn(0.0, 0.25)
        if (isDriving) {
            elapsed = (elapsed + dt).coerceAtMost(minimumLevelSeconds)
            driveDynamics.update(dt, requestedMotion = true, learningPause = state == State.LEARNING)
            if (learningTriggered && state == State.LEARNING) {
                learningElapsed = (learningElapsed + dt).coerceAtMost(learningWindowSeconds)
                if (learningElapsed >= learningWindowSeconds) {
                    state = State.DRIVING
                }
            }
            // Match the production gameplay contract: the mandatory 90-second
            // learning chapter begins after the first 60 seconds of the 180-second
            // journey, leaving a continuous 30-second drive finish.
            if (!learningTriggered && elapsed >= 60.0) {
                learningTriggered = true
                learningElapsed = 0.0
                state = State.LEARNING
            }
            if (elapsed >= minimumLevelSeconds) {
                completionLatched = true
                state = State.COMPLETED
            }
        }
        return snapshot()
    }

    fun advanceToNextLevel(): Boolean {
        if (!completionLatched || level >= levelCount) return false
        level++
        elapsed = 0.0
        learningElapsed = 0.0
        learningTriggered = false
        completionLatched = false
        state = State.READY
        driveDynamics.reset()
        return true
    }

    fun resetCurrentLevel() {
        elapsed = 0.0
        learningElapsed = 0.0
        learningTriggered = false
        completionLatched = false
        state = State.READY
        driveDynamics.reset()
    }

    fun snapshot(): Snapshot {
        val drive = driveDynamics.snapshot()
        return Snapshot(
            level, state, elapsed, learningElapsed,
            (elapsed / minimumLevelSeconds).coerceIn(0.0, 1.0),
            (learningElapsed / learningWindowSeconds).coerceIn(0.0, 1.0),
            completionLatched,
            drive.speedMetersPerSecond,
            drive.distanceMeters,
            drive.steering,
            drive.yaw,
            drive.bank,
            drive.grade,
            drive.wheelRotationRadians,
            drive.braking
        )
    }

    fun validate(): Boolean =
        levelCount == 500 &&
        minimumLevelSeconds >= 180.0 &&
        learningWindowSeconds > 0.0 &&
        learningWindowSeconds <= minimumLevelSeconds
}