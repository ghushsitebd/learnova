package com.learnova.app

import android.content.Context
import android.util.Base64
import android.view.Choreographer
import android.view.SurfaceView
import android.widget.FrameLayout
import com.google.android.filament.utils.Float3
import com.google.android.filament.utils.ModelViewer
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream
import java.nio.ByteBuffer

/**
 * Learnova's first production 3D rendering layer.
 *
 * The game UI/learning state remains Android-native, while the world renderer is
 * migrated to Filament/PBR. This keeps the educational interaction lightweight
 * and gives the road/vehicle world a real 3D scene graph, physically based
 * materials, real camera perspective and hardware rendering.
 */
class Learnova3DView(context: Context) : FrameLayout(context) {

    private val surface = SurfaceView(context)
    private val viewer = ModelViewer(surface)
    private val choreographer = Choreographer.getInstance()
    private var frameCallback: Choreographer.FrameCallback? = null
    private var started = false
    private var driving = false
    private var driveTime = 0.0

    init {
        addView(
            surface,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
        surface.setZOrderOnTop(false)

        val modelBytes = decodeModel()
        viewer.loadModelGlb(ByteBuffer.wrap(modelBytes))

        viewer.camera.lookAt(
            4.8, 2.8, 6.8,
            0.0, 1.0, 14.0,
            0.0, 1.0, 0.0
        )
        viewer.camera.setProjection(
            62.0,
            0.10,
            250.0,
            1.0
        )

        viewer.view.dynamicResolutionOptions = viewer.view.dynamicResolutionOptions.apply {
            enabled = true
            quality = com.google.android.filament.View.QualityLevel.MEDIUM
        }
        viewer.view.antiAliasing = com.google.android.filament.View.AntiAliasing.FXAA
        viewer.view.ambientOcclusionOptions = viewer.view.ambientOcclusionOptions.apply { enabled = true }
        viewer.view.bloomOptions = viewer.view.bloomOptions.apply { enabled = true }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!started) {
            started = true
            frameCallback = Choreographer.FrameCallback { time ->
                if (!started) return@FrameCallback
                if (driving) driveTime += 1.0 / 60.0
                updateDriveScene()
                viewer.render(time)
                choreographer.postFrameCallback(frameCallback)
            }
            choreographer.postFrameCallback(frameCallback)
        }
    }

    fun setDriving(value: Boolean) { driving = value }

    private fun updateDriveScene() {
        val z = 6.8 + (if (driving) (driveTime * 0.42) % 1.8 else 0.0)
        val yaw = if (driving) kotlin.math.sin(driveTime * 0.72) * 0.035 else 0.0
        viewer.camera.lookAt(4.8 + yaw, 2.8, z, 0.0, 1.0, 14.0, 0.0, 1.0, 0.0)
    }

    override fun onDetachedFromWindow() {
        started = false
        frameCallback?.let { choreographer.removeFrameCallback(it) }
        frameCallback = null
        viewer.destroy()
        super.onDetachedFromWindow()
    }

    private fun decodeModel(): ByteArray {
        val compressed = Base64.decode(MODEL_GZ, Base64.DEFAULT)
        return GZIPInputStream(ByteArrayInputStream(compressed)).use { input ->
            input.readBytes()
        }
    }

    companion object {
        // Tiny procedural GLB: road, ground, PBR car body, cabin and four wheels.
        // It is compressed at source so the migration adds very little source size.
        private const val MODEL_GZ = "H4sIAEu6uGoC/+2Y327jRBTGhwUWWGCB3WW5rXzDjRt5xn/iVIrTprCoqNuu2mq5WPXCbZ3UUmJXjluoqkg8AA/BFeIlNg69bl+D52DGTp3x+Nhs4kiVVumFm/w85/tmjo/PRNPtHbx4gBD6+3uEtp8i9PP+7s6VZA8GTiitXUkXTjBwfU9ak0hNkWSp63hOYId+QMm2Yweef2GvqD+s9N0uxWzkUJYGx3SUtKZMPg2ktTdXkuefxJ8UGctEVmVN1mVDrh8OD+X0Hh1l92mktOfbJ9St7wxOY50wsL1BLzFgElTEPBzK6fhNO2j7J5dpCM6HYFnJRhy5XjqeAONrBpGVmqrzUb+cOk4vjVLFqFVcIzoNqhOZfpwp9J0jtRLTGSOLA38K/HNv+gTq+fSsKjUSP4TDZNDk+Z0Fbt8N3YvJVzsMA/foPGRfr6RXu/tbB1u7O/Ej3dnde7mxTR8VLRjXO3GP2Rj6HPp26ASu3aODhvGU3lFSnUpqvKTOS+KZJI2pZJ2XNHlJMpNkg1u4wmtizIuqM4liwqmqGVWtgqrOqRoZ1XoFVZNTbWSevTK/KsFTVUIyqiqvqg2Tgp2ATM/ZGJyd2r2QFv3ZUfDSCe1ezz3e88+7p7SFxX5H9sDZ9Ht+8MI+jlvgG6WmGPQNUursYsqYagd3EXeDlFpjmG1Vr2zXm9lHpRbx65rY9CeBU5c66E2zwb/XPdrZ53HGrBfiImtWKoA3zngfuIEzqzXN6fTaKMyvPoR61yxOmD1GVUtWCtrgIauco/NOh26Jcd0cXYbOtuN1Q9ohdWxq0/uvXefXyZj4e9zw2PDdTifeWCdf0+g6bTWhHXTZPVVrGIStpyA2HsxH10k2WC0JNjSzgjUmRJvfG5OGUcXcZGZzmzc0rYI50YkyvznRG6SCuYoNc35zlWhVyk01cYV6U02zSsFpmlGh4DRdrVJwOsb/W3D0lbeP6UYz8AP+hWcNINY79vtnvud44cHlmcMkicHguceqgv0gi7H0+sdNlf3acuMfdEYt/n2F5VWN9r14v/qNcoYZjeF03nN6Zd9MUQ5Dcmoqx/I6kdvf3Nje2JNEATLn2jFbNm3FdKtbJdziGU8wAVZPFrt6terqtTlXT/eydPX0c7p6xhPMaGW38tXrVVdvzL16XI8XTpJ/ms4lgN1K7sQ3KnuW56BeNQfmPeTAXGwOGlVzgJV7SAJedCes3AoxuY80LLgl4so9Ec/bFImS7IeKLmyI8Y2Yg1siXnBfxLM3xsPh+jOE2ls7CKHx+u3NzVuEVq4R+r2F0r+UtwHeLOGizpjTiQB+DfBmCW8Xj2d/d978fEDeFvi4hL/leJTPD8ivBT4u4ILOnbfom+FNgK8XcMH3zlucZ4Y3AT6G+QeIndM+QB+ij9DH6CG9PkSfoE/RZ+gRvT5Cn6Mv0JfoMb0+Rl+hr9E36Am9PkFP0TP0LXpOr8/Rd+j25o9Wp/PXiCWErx+OrwPcKuGiTsTpRAAfA9wq4evF46f1k50PyNcFHpXwEcejfH5APhZ4VMAFncQ775vhFsBbBVzwTbzz88xwC+ARxBdVh51OJ54P/R/x9cPxFsCtEi7qRJxOBPAI4FYJbxWPn9ZPdj4gbwk8KuEjjkf5/IA8EnhUwAWdxDvvm+EWwFsFXPBNvPPzzHAL4BHEF1WHqqpatNbpfP4d8fXDcQvgVgkXdUacTgTwEcCtEm4Vj5/WT3Y+ILcEPirhI45H+fyAfCTwUQEXdBLvvG+GWwC3Crjgm3jn55nhFsBHEF/W4bIOl3W4rMNlHS7rcFmH718dIvTnxu3NNnBuk/I2wJslXNT5h9OJAH4N8GYJbxeP589b+PmAXDif4fVzHDif4fMDcuF8hl9vmc6dt+ib4cD5jLiunI5w3iLOM8OB8xkxz4utw/8Ahv+D76gnAAA="
    }
}
