package com.learnova.app

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import kotlin.math.max
import kotlin.math.min

/**
 * Device-aware visual quality controller.
 *
 * Learnova keeps one gameplay path, but scales scene density from device memory
 * and recent frame time so realism does not come at the cost of stutter.
 */
class LearnovaRenderQuality(context: Context) {
    private val activityManager =
        context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    private val memoryClassMb: Int = activityManager?.memoryClass ?: 128
    private val lowRamDevice: Boolean = activityManager?.isLowRamDevice ?: true

    private val adaptiveQuality = AdaptiveFrameQuality(
        initialLevel = RenderQualityPolicy.initialLevel(memoryClassMb, lowRamDevice)
    )

    fun beginFrame(): Long = System.nanoTime()

    fun endFrame(startNanos: Long) {
        val ms = (System.nanoTime() - startNanos) / 1_000_000.0
        adaptiveQuality.observe(ms)
    }

    fun distantTrafficCount(): Int = when (adaptiveQuality.level()) {
        0 -> 2
        1 -> 3
        2 -> 5
        else -> 6
    }

    fun animalCount(): Int = when (adaptiveQuality.level()) {
        0 -> 3
        1 -> 5
        2 -> 7
        else -> 9
    }

    fun treeDetail(): Float = when (adaptiveQuality.level()) {
        0 -> 0.78f
        1 -> 0.88f
        2 -> 0.96f
        else -> 1.0f
    }

    fun shadowAlpha(): Int = when (adaptiveQuality.level()) {
        0 -> 42
        1 -> 52
        2 -> 62
        else -> 72
    }

    fun level(): Int = adaptiveQuality.level()

    fun supportsModernGraphics(): Boolean =
        Build.VERSION.SDK_INT >= 29
}

/**
 * Pure, deterministic frame-time controller. Keeping the policy separate from
 * Android lets CI test quality changes without a device or renderer.
 *
 * A 45-frame window avoids quality oscillation: sustained slow frames lower
 * scene density, while an entirely slow-frame-free window with enough fast
 * frames can cautiously restore detail.
 */
internal class AdaptiveFrameQuality(initialLevel: Int) {
    private companion object {
        const val MIN_LEVEL = 0
        const val MAX_LEVEL = 3
        const val WINDOW_FRAMES = 45
        const val SLOW_FRAME_MS = 18.0
        const val GOOD_FRAME_MS = 13.0
        const val SLOW_FRAMES_TO_DOWNGRADE = 6
        const val GOOD_FRAMES_TO_UPGRADE = 32
    }

    private var currentLevel = initialLevel.coerceIn(MIN_LEVEL, MAX_LEVEL)
    private var frameSamples = 0
    private var slowFrames = 0
    private var consecutiveGoodFrames = 0

    fun level(): Int = currentLevel

    fun observe(frameTimeMs: Double): Int {
        frameSamples++

        when {
            !frameTimeMs.isFinite() || frameTimeMs < 0.0 || frameTimeMs > SLOW_FRAME_MS -> {
                slowFrames++
                consecutiveGoodFrames = 0
            }
            frameTimeMs < GOOD_FRAME_MS -> consecutiveGoodFrames++
            else -> consecutiveGoodFrames = 0
        }

        if (frameSamples >= WINDOW_FRAMES) {
            when {
                slowFrames >= SLOW_FRAMES_TO_DOWNGRADE ->
                    currentLevel = max(MIN_LEVEL, currentLevel - 1)
                slowFrames == 0 && consecutiveGoodFrames >= GOOD_FRAMES_TO_UPGRADE ->
                    currentLevel = min(MAX_LEVEL, currentLevel + 1)
            }
            frameSamples = 0
            slowFrames = 0
            consecutiveGoodFrames = 0
        }

        return currentLevel
    }
}


/** Pure startup tier policy, including Android's explicit low-RAM device signal. */
internal object RenderQualityPolicy {
    fun initialLevel(memoryClassMb: Int, lowRamDevice: Boolean): Int {
        val memoryTier = when {
            memoryClassMb >= 512 -> 3
            memoryClassMb >= 256 -> 2
            memoryClassMb >= 192 -> 1
            else -> 0
        }
        // Low-RAM devices stay on a restrained scene density even if OEMs report
        // an unusually generous memory class. Frame-time adaptation can still
        // lower this tier further when the renderer is under pressure.
        return if (lowRamDevice) min(memoryTier, 1) else memoryTier
    }
}
