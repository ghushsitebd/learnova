package com.learnova.macrobenchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalMetricApi::class)
class LearnovaMacrobenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartupWithBaselineProfile() = benchmarkRule.measureRepeated(
        packageName = "com.learnova.app",
        metrics = listOf(StartupTimingMetric()),
        compilationMode = CompilationMode.DEFAULT,
        iterations = 10,
        startupMode = StartupMode.COLD,
        setupBlock = { pressHome() }
    ) {
        startActivityAndWait()
    }

    @Test
    fun coldStartupWithoutCompilation() = benchmarkRule.measureRepeated(
        packageName = "com.learnova.app",
        metrics = listOf(StartupTimingMetric()),
        compilationMode = CompilationMode.None(),
        iterations = 10,
        startupMode = StartupMode.COLD,
        setupBlock = { pressHome() }
    ) {
        startActivityAndWait()
    }

    @Test
    fun drivingFrameTiming() = benchmarkRule.measureRepeated(
        packageName = "com.learnova.app",
        metrics = listOf(
            FrameTimingMetric(),
            TraceSectionMetric("Learnova.scene", TraceSectionMetric.Mode.Sum),
            TraceSectionMetric("Learnova.vehicle", TraceSectionMetric.Mode.Sum),
            TraceSectionMetric("Learnova.filamentRender", TraceSectionMetric.Mode.Sum),
            TraceSectionMetric("Learnova.adaptiveQuality", TraceSectionMetric.Mode.Sum)
        ),
        compilationMode = CompilationMode.DEFAULT,
        iterations = 5,
        startupMode = StartupMode.WARM,
        setupBlock = {
            pressHome()
            startActivityAndWait()
        }
    ) {
        // The gameplay contract is press-and-hold, not tap-to-toggle. Inject a
        // continuous touch in the central drive area for 2.5 seconds; the input
        // command's UP event then exercises the release/coast path as well.
        val x = device.displayWidth / 2
        val y = (device.displayHeight * 0.42f).toInt()
        device.executeShellCommand("input touchscreen swipe $x $y $x $y 2500")
        Thread.sleep(500)
    }
}
