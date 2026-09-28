package com.learnova.baselineprofile

import androidx.benchmark.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

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
                device.click(device.displayWidth / 2, device.displayHeight / 2)
                device.waitForIdle()
                device.click(device.displayWidth / 2, device.displayHeight / 2)
                device.waitForIdle()
            }
        }
    )
}
