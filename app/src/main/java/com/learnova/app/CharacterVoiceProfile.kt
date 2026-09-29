package com.learnova.app

import java.util.Locale

/**
 * Stable character voice identities. Voice names are hints only because Android
 * devices ship different TTS engines/voice packs; every profile has a deterministic
 * rate/pitch fallback, so character identity survives across phones.
 */
internal data class CharacterVoiceProfile(
    val id: String,
    val locale: Locale,
    val rate: Float,
    val pitch: Float,
    val voiceHints: List<String>
) {
    companion object {
        fun forName(name: String): CharacterVoiceProfile = when (name.lowercase()) {
            "bear" -> CharacterVoiceProfile("bear", Locale.US, .62f, .78f, listOf("male", "en-us"))
            "monkey" -> CharacterVoiceProfile("monkey", Locale.US, .92f, 1.42f, listOf("female", "en-us"))
            "rabbit" -> CharacterVoiceProfile("rabbit", Locale.US, .98f, 1.55f, listOf("female", "en-us"))
            "panda" -> CharacterVoiceProfile("panda", Locale.US, .76f, 1.18f, listOf("en-us"))
            "tiger" -> CharacterVoiceProfile("tiger", Locale.US, .70f, .92f, listOf("male", "en-us"))
            "lion" -> CharacterVoiceProfile("lion", Locale.US, .58f, .70f, listOf("male", "en-us"))
            "dog" -> CharacterVoiceProfile("dog", Locale.US, .82f, 1.28f, listOf("en-us"))
            else -> CharacterVoiceProfile("fox", Locale.US, .84f, 1.34f, listOf("female", "en-us"))
        }
    }
}
