package com.learnova.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LearnovaBaselineProfile {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun startupProfile() = baselineProfileRule.collect(
        packageName = "com.learnova.app",
        includeInStartupProfile = true,
        strictStability = true,
        profileBlock = {
            startActivityAndWait()
        }
    )

    @Test
    fun coreGameJourney() = baselineProfileRule.collect(
        packageName = "com.learnova.app",
        strictStability = true,
        profileBlock = {
            startActivityAndWait()
            device.waitForIdle()

            device.click(
                device.displayWidth / 2,
                (device.displayHeight * 0.42f).toInt()
            )
            device.waitForIdle()

            device.swipe(
                (device.displayWidth * 0.18f).toInt(),
                (device.displayHeight * 0.42f).toInt(),
                (device.displayWidth * 0.82f).toInt(),
                (device.displayHeight * 0.42f).toInt(),
                450
            )
            device.swipe(
                (device.displayWidth * 0.82f).toInt(),
                (device.displayHeight * 0.42f).toInt(),
                (device.displayWidth * 0.18f).toInt(),
                (device.displayHeight * 0.42f).toInt(),
                450
            )
            device.waitForIdle()

            device.click(
                (device.displayWidth * 0.50f).toInt(),
                (device.displayHeight * 0.76f).toInt()
            )
            device.waitForIdle()

            // Keep the target process alive at the end of the journey. This is
            // important on Gradle Managed Devices because BaselineProfileRule
            // flushes ART profiles by killing the target process after the block.
            // Re-launching here makes the capture deterministic even if a UI
            // interaction caused the activity to be recreated or backgrounded.
            device.pressHome()
            device.waitForIdle()
            startActivityAndWait()
            device.waitForIdle()
        }
    )
}
