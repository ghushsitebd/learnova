package com.learnova.app

/** Deterministic 500-level child-simple journey controller. */
class JourneyDriveDirector(
    private val levelCount: Int = 500,
    private val minimumLevelSeconds: Double = 180.0,
    private val learningWindowSeconds: Double = 90.0
) {
    enum class State { READY, DRIVING, LEARNING, STOPPED, COMPLETED }

    data class Snapshot(
        val level: Int,
        val state: State,
        val elapsedSeconds: Double,
        val learningElapsedSeconds: Double,
        val progress: Double,
        val learningProgress: Double,
        val levelCompleted: Boolean
    )

    private var level = 1
    private var state = State.READY
    private var elapsed = 0.0
    private var learningElapsed = 0.0
    private var learningTriggered = false
    private var completionLatched = false

    val currentLevel: Int get() = level
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
            if (learningTriggered && state == State.LEARNING) {
                learningElapsed = (learningElapsed + dt).coerceAtMost(learningWindowSeconds)
                if (learningElapsed >= learningWindowSeconds) {
                    state = State.DRIVING
                }
            }
            if (!learningTriggered && elapsed >= 45.0) {
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
        return true
    }

    fun resetCurrentLevel() {
        elapsed = 0.0
        learningElapsed = 0.0
        learningTriggered = false
        completionLatched = false
        state = State.READY
    }

    fun snapshot() = Snapshot(
        level, state, elapsed, learningElapsed,
        (elapsed / minimumLevelSeconds).coerceIn(0.0, 1.0),
        (learningElapsed / learningWindowSeconds).coerceIn(0.0, 1.0),
        completionLatched
    )

    fun validate(): Boolean =
        levelCount == 500 &&
        minimumLevelSeconds >= 180.0 &&
        learningWindowSeconds > 0.0 &&
        learningWindowSeconds <= minimumLevelSeconds
}