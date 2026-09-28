package com.learnova.app

import android.content.Context
import android.os.Build
import android.os.CpuHeadroomParams
import android.os.GpuHeadroomParams
import android.os.health.SystemHealthManager
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Runtime quality governor for the mobile 3D world.
 *
 * Uses frame-time EMA plus hysteresis/cooldown. Optional CPU/GPU headroom
 * constraints are supplied by Android 16 ADPF when available.
 */
internal class LearnovaAdaptiveQuality {
    enum class Tier { HIGH, MEDIUM, LOW }

    private var emaMs = 16.67
    private var initialized = false
    private var evaluationFrames = 0
    private var cooldownFrames = 0

    var tier: Tier = Tier.MEDIUM
        private set

    fun sample(
        frameMs: Double,
        constrainedDevice: Boolean,
        cpuHeadroom: Float = Float.NaN,
        gpuHeadroom: Float = Float.NaN
    ): Tier? {
        val clamped = frameMs.coerceIn(1.0, 100.0)
        emaMs = if (!initialized) {
            initialized = true
            clamped
        } else {
            emaMs * 0.90 + clamped * 0.10
        }

        evaluationFrames++
        if (cooldownFrames > 0) cooldownFrames--
        if (evaluationFrames < 30) return null

        val old = tier
        val severeFrameTime = emaMs >= 24.0
        val excellent = emaMs <= 12.0
        val cpuTight = cpuHeadroom.isFinite() && cpuHeadroom < 15.0f
        val gpuTight = gpuHeadroom.isFinite() && gpuHeadroom < 15.0f
        val resourceTight = cpuTight || gpuTight
        val resourceHealthy =
            cpuHeadroom.isFinite() && gpuHeadroom.isFinite() &&
                cpuHeadroom >= 35.0f && gpuHeadroom >= 35.0f

        if (constrainedDevice && tier == Tier.HIGH) {
            tier = Tier.MEDIUM
            cooldownFrames = 90
        } else if (cooldownFrames == 0) {
            tier = when (tier) {
                Tier.HIGH -> if (severeFrameTime || resourceTight) Tier.MEDIUM else Tier.HIGH
                Tier.MEDIUM -> when {
                    severeFrameTime || resourceTight -> Tier.LOW
                    !constrainedDevice && excellent && resourceHealthy -> Tier.HIGH
                    else -> Tier.MEDIUM
                }
                Tier.LOW -> when {
                    severeFrameTime || resourceTight -> Tier.LOW
                    !constrainedDevice && excellent && resourceHealthy -> Tier.MEDIUM
                    else -> Tier.LOW
                }
            }
            if (old != tier) cooldownFrames = 90
        }

        evaluationFrames = 0
        return if (old != tier) tier else null
    }
}

/**
 * Android 16 ADPF headroom monitor.
 *
 * Polling is deliberately off the render thread because Android documents
 * headroom queries as potentially taking more than 1ms. Unsupported devices
 * simply keep NaN and the frame-time governor remains authoritative.
 */
internal class LearnovaHeadroomMonitor(context: Context) {
    private val executor: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "LearnovaHeadroom").apply { isDaemon = true }
        }
    private val running = AtomicBoolean(false)
    private val cpu = AtomicReference(Float.NaN)
    private val gpu = AtomicReference(Float.NaN)

    private val health: SystemHealthManager? =
        if (Build.VERSION.SDK_INT >= 36) {
            context.getSystemService(Context.SYSTEM_HEALTH_SERVICE) as? SystemHealthManager
        } else null

    fun start() {
        val manager = health ?: return
        if (!running.compareAndSet(false, true)) return

        executor.execute {
            poll(manager)
            val interval = maxOf(
                safeInterval(manager.getCpuHeadroomMinIntervalMillis()),
                safeInterval(manager.getGpuHeadroomMinIntervalMillis())
            )
            executor.scheduleAtFixedRate(
                { poll(manager) },
                interval,
                interval,
                TimeUnit.MILLISECONDS
            )
        }
    }

    fun cpuHeadroom(): Float = cpu.get()
    fun gpuHeadroom(): Float = gpu.get()

    fun stop() {
        running.set(false)
        executor.shutdownNow()
    }

    private fun poll(manager: SystemHealthManager) {
        if (!running.get()) return
        try {
            val cpuParams = CpuHeadroomParams.Builder()
                .setCalculationType(CpuHeadroomParams.CPU_HEADROOM_CALCULATION_TYPE_MIN)
                .build()
            val gpuParams = GpuHeadroomParams.Builder()
                .setCalculationType(GpuHeadroomParams.GPU_HEADROOM_CALCULATION_TYPE_MIN)
                .build()

            cpu.set(manager.getCpuHeadroom(cpuParams))
            gpu.set(manager.getGpuHeadroom(gpuParams))
        } catch (_: Throwable) {
            cpu.set(Float.NaN)
            gpu.set(Float.NaN)
        }
    }

    private fun safeInterval(value: Long): Long =
        value.coerceIn(250L, 5_000L)
}
