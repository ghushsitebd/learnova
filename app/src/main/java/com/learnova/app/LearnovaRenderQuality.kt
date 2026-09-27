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
    private val memoryClassMb: Int =
        (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)
            ?.memoryClass ?: 128

    private var frameSamples = 0
    private var slowFrames = 0
    private var goodFrames = 0
    private var quality = when {
        memoryClassMb >= 512 -> 3
        memoryClassMb >= 256 -> 2
        memoryClassMb >= 192 -> 1
        else -> 0
    }

    fun beginFrame(): Long = System.nanoTime()

    fun endFrame(startNanos: Long) {
        val ms = (System.nanoTime() - startNanos) / 1_000_000.0
        frameSamples++
        when {
            ms > 22.0 -> {
                slowFrames++
                goodFrames = 0
            }
            ms < 14.0 -> {
                goodFrames++
            }
            else -> {
                goodFrames = 0
            }
        }

        if (frameSamples >= 45) {
            if (slowFrames >= 8) quality = max(0, quality - 1)
            else if (slowFrames == 0 && goodFrames >= 30) quality = min(3, quality + 1)
            frameSamples = 0
            slowFrames = 0
            goodFrames = 0
        }
    }

    fun distantTrafficCount(): Int = when (quality) {
        0 -> 2
        1 -> 3
        2 -> 5
        else -> 6
    }

    fun animalCount(): Int = when (quality) {
        0 -> 3
        1 -> 5
        2 -> 7
        else -> 9
    }

    fun treeDetail(): Float = when (quality) {
        0 -> 0.78f
        1 -> 0.88f
        2 -> 0.96f
        else -> 1.0f
    }

    fun shadowAlpha(): Int = when (quality) {
        0 -> 42
        1 -> 52
        2 -> 62
        else -> 72
    }

    fun level(): Int = quality

    fun supportsModernGraphics(): Boolean =
        Build.VERSION.SDK_INT >= 29
}
