package com.learnova.baselineprofile

import androidx.benchmark.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Critical user journeys used to pre-compile Learnova's real interaction paths:
 * startup, touch-to-drive, one-finger steering, learning progression and garage access.
 */
@RunWith(AndroidJUnit4::class)
class LearnovaBaselineProfile {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun startupAndCoreGameJourney() = baselineProfileRule.collect(
        packageName = "com.learnova.app",
        profileBlock = {
            uiAutomator {
                startApp()
                device.waitForIdle()

                // Primary child interaction: tap centre to start driving.
                device.click(device.displayWidth / 2, (device.displayHeight * 0.42f).toInt())
                device.waitForIdle()

                // One-finger left/right steering paths.
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

                // Tap the learning card to exercise lesson/voice progression.
                device.click(
                    (device.displayWidth * 0.50f).toInt(),
                    (device.displayHeight * 0.76f).toInt()
                )
                device.waitForIdle()

                // Stop driving and open the vehicle garage, covering the real
                // navigation path without requiring text selectors.
                device.click(device.displayWidth / 2, (device.displayHeight * 0.42f).toInt())
                device.waitForIdle()
                device.click(
                    (device.displayWidth * 0.88f).toInt(),
                    (device.displayHeight * 0.12f).toInt()
                )
                device.waitForIdle()
            }
        }
    )
}
