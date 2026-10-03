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
            // Learnova continuously renders its 3D world, so an unconditional
            // waitForIdle() can wait forever on a managed device. Use bounded
            // pauses around the real tap journey instead.
            startActivityAndWait()
            Thread.sleep(1200)

            device.click(
                device.displayWidth / 2,
                (device.displayHeight * 0.42f).toInt()
            )
            Thread.sleep(1200)

            device.swipe(
                (device.displayWidth * 0.25f).toInt(),
                (device.displayHeight * 0.42f).toInt(),
                (device.displayWidth * 0.75f).toInt(),
                (device.displayHeight * 0.42f).toInt(),
                350
            )
            Thread.sleep(900)

            device.click(
                device.displayWidth / 2,
                (device.displayHeight * 0.42f).toInt()
            )
            Thread.sleep(900)

            // Return to a stable foreground state so ART can flush the profile
            // without depending on renderer-idle detection.
            device.pressHome()
            Thread.sleep(700)
            startActivityAndWait()
            Thread.sleep(900)
        }
    )
}
