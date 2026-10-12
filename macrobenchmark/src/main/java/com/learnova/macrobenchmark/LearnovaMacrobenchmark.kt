package com.learnova.macrobenchmark

import android.os.Build

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
    fun drivingFrameTiming() {
        val traceMetrics = listOf(
            TraceSectionMetric("Learnova.scene", TraceSectionMetric.Mode.Sum),
            TraceSectionMetric("Learnova.vehicle", TraceSectionMetric.Mode.Sum),
            TraceSectionMetric("Learnova.filamentRender", TraceSectionMetric.Mode.Sum),
            TraceSectionMetric("Learnova.adaptiveQuality", TraceSectionMetric.Mode.Sum)
        )
        // Hosted API-35 emulators can produce invalid software-renderer
        // ColorBuffers, leaving FrameTimingMetric without frame slices.
        // Keep runtime trace validation on CI and collect frame timing on
        // physical devices, where frame slices are available.
        val runtimeMetrics = if (Build.FINGERPRINT.startsWith("generic")) {
            traceMetrics
        } else {
            listOf(FrameTimingMetric()) + traceMetrics
        }
        benchmarkRule.measureRepeated(
            packageName = "com.learnova.app",
            metrics = runtimeMetrics,
            compilationMode = CompilationMode.DEFAULT,
            iterations = 5,
            startupMode = StartupMode.WARM,
            setupBlock = {
                pressHome()
                startActivityAndWait()
            }
        ) {
            // Hold the drive area for 2.5 seconds; UP tests release/coast.
            val x = device.displayWidth / 2
            val y = (device.displayHeight * 0.42f).toInt()
            device.executeShellCommand("input touchscreen swipe $x $y $x $y 2500")
            Thread.sleep(500)
        }
    }
}
