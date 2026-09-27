package com.learnova.app

import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Lightweight offline nature ambience.
 *
 * The layer contains only natural/environmental sound synthesis:
 * wind, flowing water, rain, leaves/insects and sparse bird calls.
 * No musical instruments, melody, beat or background music are used.
 *
 * Everything is generated locally, so no audio recordings are required in the APK.
 */
class LearnovaNatureAudio {

    private val sampleRate = 44100
    private val channelMask = AudioFormat.CHANNEL_OUT_STEREO
    private val encoding = AudioFormat.ENCODING_PCM_16BIT
    private val minBuffer = AudioTrack.getMinBufferSize(sampleRate, channelMask, encoding)
        .coerceAtLeast(sampleRate / 4)

    @Volatile private var region = "Nature"
    @Volatile private var weather = "Clear"
    @Volatile private var time = "Day"\n    @Volatile private var vehicleKind = "car"\n    @Volatile private var vehicleMoving = false
    @Volatile private var enabled = true

    private var track: AudioTrack? = null
    private var thread: Thread? = null

    fun setEnvironment(newRegion: String, newWeather: String, newTime: String) {
        region = newRegion
        weather = newWeather
        time = newTime
    }

    fun start() {
        if (!enabled || thread?.isAlive == true) return

        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val format = AudioFormat.Builder()
            .setEncoding(encoding)
            .setSampleRate(sampleRate)
            .setChannelMask(channelMask)
            .build()

        val audioTrack = AudioTrack(
            attrs,
            format,
            minBuffer,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )
        track = audioTrack

        thread = Thread({
            val chunk = ShortArray(2048)\n            val leftRight = ShortArray(4096)
            val random = Random(20260927)
            var low = 0.0
            var high = 0.0
            var phase = 0.0
            var sampleIndex = 0L
            var nextBird = sampleRate * 2L\n            var enginePhase = 0.0\n            var enginePhase2 = 0.0

            try {
                audioTrack.play()

                while (!Thread.currentThread().isInterrupted && enabled) {
                    val localWeather = weather
                    val localRegion = region
                    val localTime = time\n                    val localVehicleKind = vehicleKind\n                    val localVehicleMoving = vehicleMoving

                    for (i in chunk.indices) {
                        val white = random.nextDouble(-1.0, 1.0)
                        low += (white - low) * 0.012
                        high += (white - high) * 0.18

                        val waterWorld = localRegion.contains("River", true) ||
                            localRegion.contains("Water", true) ||
                            localRegion.contains("Beach", true) ||
                            localRegion.contains("Harbor", true) ||
                            localRegion.contains("Fishing", true) ||
                            localRegion.contains("Wetland", true) ||
                            localRegion.contains("Island", true) ||
                            localRegion.contains("Boat", true)

                        val forestWorld = localRegion.contains("Forest", true) ||
                            localRegion.contains("Safari", true) ||
                            localRegion.contains("Animal", true) ||
                            localRegion.contains("Garden", true) ||
                            localRegion.contains("Village", true)

                        var sample = 0.0

                        // Soft air movement / wind. This is intentionally filtered noise,
                        // not a musical tone.
                        val windGain = if (localTime == "Night") 0.035 else 0.026
                        sample += low * windGain

                        // Flowing water: irregular filtered noise plus gentle turbulence.
                        if (waterWorld) {
                            val waterMod = 0.55 + 0.45 * sin(phase * 0.37)
                            sample += (high * 0.018 + low * 0.032) * waterMod
                        }

                        // Rain is a sparse natural broadband texture, never rhythmic.
                        if (localWeather == "Rainy") {
                            sample += high * 0.060 + low * 0.018
                        }

                        // Leaves/insects in green habitats.
                        if (forestWorld && localWeather != "Rainy") {
                            sample += high * 0.010
                        }

                        // Sparse bird-like chirps in daylight. These are isolated natural
                        // cues rather than a repeating melody.
                        if (forestWorld && localTime != "Night") {
                            if (sampleIndex >= nextBird) {
                                nextBird = sampleIndex + sampleRate * (3 + random.nextInt(5))
                            }
                            val birdDistance = nextBird - sampleIndex
                            if (birdDistance in 1..(sampleRate / 3).toLong()) {
                                val t = 1.0 - birdDistance.toDouble() / (sampleRate / 3.0)
                                val envelope = sin(PI * t).coerceAtLeast(0.0)
                                val sweep = 1450.0 + 850.0 * sin(PI * t)
                                sample += sin(phase * sweep / 22050.0) * envelope * 0.018
                            }
                        }

                        // Keep the ambience gentle under spoken Arabic/English lessons.
                        val master = 0.72
                        val clipped = (sample * master).coerceIn(-0.30, 0.30)
                        chunk[i] = (clipped * Short.MAX_VALUE).toInt().toShort()

                        phase += 1.0
                        sampleIndex++
                    }

                    audioTrack.write(leftRight, 0, leftRight.size, AudioTrack.WRITE_BLOCKING)
                }
            } catch (_: Throwable) {
                // Audio is optional. A device/audio-route problem must never crash gameplay.
            } finally {
                try { audioTrack.pause() } catch (_: Throwable) {}
                try { audioTrack.flush() } catch (_: Throwable) {}
            }
        }, "Learnova-Nature-Audio").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        enabled = false
        thread?.interrupt()
        try { track?.pause() } catch (_: Throwable) {}
        try { track?.flush() } catch (_: Throwable) {}
        thread = null
        track = null
        enabled = true
    }

    fun release() {
        enabled = false
        thread?.interrupt()
        try { track?.stop() } catch (_: Throwable) {}
        try { track?.release() } catch (_: Throwable) {}
        thread = null
        track = null
    }
}
