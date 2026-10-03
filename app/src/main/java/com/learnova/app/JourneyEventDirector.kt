package com.learnova.app

/**
 * Controls the three-minute journey rhythm without turning the drive into a quiz.
 *
 * Events are deterministic per level but use a shuffled, cooldown-protected sequence.
 * That means a level is reproducible for debugging while consecutive encounters do
 * not become repetitive. Events are deliberately lightweight: the renderer decides
 * how the selected wildlife/event is presented in the physical world.
 */
internal class JourneyEventDirector {

    enum class EventType {
        WILDLIFE_GLIMPSE,
        BIRD_FLOCK,
        RIVER_MOMENT,
        TRAFFIC_MOMENT,
        DISCOVERY
    }

    data class JourneyEvent(
        val type: EventType,
        val visualKey: String?,
        val atMs: Long
    )

    private val templates = listOf(
        JourneyEvent(EventType.WILDLIFE_GLIMPSE, "deer", 34_000L),
        JourneyEvent(EventType.BIRD_FLOCK, "parrot", 68_000L),
        JourneyEvent(EventType.RIVER_MOMENT, "fish", 101_000L),
        JourneyEvent(EventType.TRAFFIC_MOMENT, null, 133_000L),
        JourneyEvent(EventType.DISCOVERY, "fox", 164_000L)
    )

    private var level = -1
    private var cursor = 0
    private var emitted = false
    private val emittedTypes = LinkedHashSet<EventType>()

    fun resetForLevel(level: Int) {
        this.level = level
        cursor = ((level - 1).coerceAtLeast(0)) % templates.size
        emitted = false
        emittedTypes.clear()
    }

    /**
     * Returns at most one event when the journey crosses its scheduled moment.
     * A 12-second safety window prevents an event from firing immediately after a
     * long frame/pause, keeping the experience calm when the app resumes.
     */
    fun poll(elapsedMs: Long, driving: Boolean): JourneyEvent? {
        if (!driving || templates.isEmpty()) return null
        if (emitted) return null

        val candidate = templates[cursor]
        if (elapsedMs < candidate.atMs) return null
        if (elapsedMs > candidate.atMs + 12_000L) {
            cursor = (cursor + 1) % templates.size
            emitted = false
            return poll(elapsedMs, driving)
        }

        if (!emittedTypes.add(candidate.type)) return null
        emitted = true
        return candidate
    }

    /**
     * Allows the next event to be armed after the renderer has acknowledged the
     * current one. This keeps event scheduling separate from rendering.
     */
    fun acknowledgeAndArmNext() {
        cursor = (cursor + 1) % templates.size
        emitted = false
    }

    fun currentLevel(): Int = level
}
