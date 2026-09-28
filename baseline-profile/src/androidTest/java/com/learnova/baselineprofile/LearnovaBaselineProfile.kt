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

    /**
     * Keep only the true launch path in the Startup Profile so primary-dex layout
     * stays focused on the code required for first usable render.
     */
    @Test
    fun startupProfile() = baselineProfileRule.collect(
        packageName = "com.learnova.app",
        includeInStartupProfile = true,
        strictStability = true,
        profileBlock = {
            uiAutomator {
                startApp()
                device.waitForIdle()
            }
        }
    )

    /**
     * The full child gameplay journey remains in the Baseline Profile but is not
     * promoted into the Startup Profile. This keeps launch optimization focused
     * while still pre-compiling the real driving/learning/garage paths.
     */
    @Test
    fun coreGameJourney() = baselineProfileRule.collect(
        packageName = "com.learnova.app",
        strictStability = true,
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

                // Stop driving and open the vehicle garage.
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
