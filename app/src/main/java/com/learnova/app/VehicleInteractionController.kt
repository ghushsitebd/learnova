package com.learnova.app

/**
 * Runtime interaction state for vehicle entry/exit.
 *
 * The controller deliberately contains no rendering code. Filament animation
 * bindings can consume these states later, while gameplay remains deterministic
 * and safe when a child taps repeatedly.
 */
internal class VehicleInteractionController {

    enum class State {
        OUTSIDE,
        DOOR_OPENING,
        ENTERING,
        OCCUPIED,
        EXITING,
        DOOR_CLOSING
    }

    var state: State = State.OUTSIDE
        private set

    private var elapsed = 0f

    fun requestEnter(profile: VehicleInteractionProfile): Boolean {
        if (state != State.OUTSIDE) return false
        elapsed = 0f
        state = if (profile.doorAnimation == "none") {
            State.ENTERING
        } else {
            State.DOOR_OPENING
        }
        return true
    }

    fun requestExit(profile: VehicleInteractionProfile): Boolean {
        if (state != State.OCCUPIED) return false
        elapsed = 0f
        state = if (profile.doorAnimation == "none") {
            State.EXITING
        } else {
            State.EXITING
        }
        return true
    }

    /**
     * Advances the interaction using seconds of simulation time.
     * Returns true when the state changes.
     */
    fun update(deltaSeconds: Float, profile: VehicleInteractionProfile): Boolean {
        val previous = state
        elapsed += deltaSeconds.coerceAtLeast(0f)

        when (state) {
            State.OUTSIDE -> Unit

            State.DOOR_OPENING -> if (elapsed >= doorDuration(profile)) {
                elapsed = 0f
                state = State.ENTERING
            }

            State.ENTERING -> if (elapsed >= 0.65f) {
                elapsed = 0f
                state = State.OCCUPIED
            }

            State.OCCUPIED -> Unit

            State.EXITING -> if (elapsed >= 0.65f) {
                elapsed = 0f
                state = if (profile.doorAnimation == "none") {
                    State.OUTSIDE
                } else {
                    State.DOOR_CLOSING
                }
            }

            State.DOOR_CLOSING -> if (elapsed >= doorDuration(profile)) {
                elapsed = 0f
                state = State.OUTSIDE
            }
        }

        return previous != state
    }

    fun isInside(): Boolean = state == State.OCCUPIED

    fun reset() {
        elapsed = 0f
        state = State.OUTSIDE
    }

    private fun doorDuration(profile: VehicleInteractionProfile): Float = when {
        profile.doorAnimation == "open_side_door" -> 0.55f
        profile.doorAnimation == "open_passenger_door" -> 0.70f
        else -> 0.0f
    }
}
