package com.learnova.app

import android.content.Context
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.Scene
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.gltfio.Animator
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderProvider
import java.nio.ByteBuffer

/**
 * Near-field authored creature presenter.
 *
 * When a species GLB is bundled, this controller loads the real asset into the
 * existing Filament scene. Distant life and missing-asset cases remain on the
 * lightweight fallback path, so the world never depends on every catalog entry
 * having a binary model.
 */
internal class CreatureGlbController(
    context: Context,
    private val engine: Engine,
    private val scene: Scene
) {
    private val resolver = CreatureAssetResolver(context)
    private val materialProvider = UbershaderProvider(engine)
    private val assetLoader = AssetLoader(engine, materialProvider, EntityManager.get())
    private var resourceLoader: ResourceLoader? = null
    private var activeAsset: FilamentAsset? = null
    private var activeAnimator: Animator? = null
    private var animationStartSeconds = 0.0
    private var activeAnimationIndex = 0
    private var activeAnimationDuration = 0f

    fun show(species: String, x: Double, y: Double, z: Double, yaw: Double, scale: Double): Boolean {
        val bytes = resolver.load(species) ?: return false
        val asset = runCatching {
            assetLoader.createAsset(ByteBuffer.wrap(bytes))
        }.getOrNull() ?: return false

        runCatching {
            resourceLoader?.destroy()
            resourceLoader = ResourceLoader(engine)
            resourceLoader!!.loadResources(asset)
            scene.addEntities(asset.entities)
            position(asset, x, y, z, yaw, scale)
            activeAsset = asset
            activeAnimator = asset.instance?.animator?.takeIf { it.animationCount > 0 }
            activeAnimationIndex = 0
            activeAnimationDuration = activeAnimator?.getAnimationDuration(0) ?: 0f
            animationStartSeconds = System.nanoTime() * 1e-9
            true
        }.getOrElse {
            destroyAsset(asset)
            false
        }
        return activeAsset === asset
    }

    fun hide() {
        activeAsset?.let(::destroyAsset)
        activeAsset = null
        activeAnimator = null
        activeAnimationIndex = 0
        activeAnimationDuration = 0f
    }

    fun move(x: Double, y: Double, z: Double, yaw: Double, scale: Double) {
        val asset = activeAsset ?: return
        position(asset, x, y, z, yaw, scale)
        val animator = activeAnimator ?: return
        val elapsed = (System.nanoTime() * 1e-9 - animationStartSeconds).coerceAtLeast(0.0)
        val duration = activeAnimationDuration.toDouble()
        if (duration > 0.0) animator.applyAnimation(activeAnimationIndex, (elapsed % duration).toFloat())
        animator.updateBoneMatrices()
    }

    private fun position(
        asset: FilamentAsset,
        x: Double,
        y: Double,
        z: Double,
        yaw: Double,
        scale: Double
    ) {
        val root = asset.root
        val transform = engine.transformManager.getInstance(root)
        if (transform != 0) {
            val c = kotlin.math.cos(yaw).toFloat()
            val s = kotlin.math.sin(yaw).toFloat()
            val sc = scale.toFloat()
            val matrix = floatArrayOf(
                c * sc, 0f, -s * sc, 0f,
                0f, sc, 0f, 0f,
                s * sc, 0f, c * sc, 0f,
                x.toFloat(), y.toFloat(), z.toFloat(), 1f
            )
            engine.transformManager.setTransform(transform, matrix)
        }
    }

    private fun destroyAsset(asset: FilamentAsset) {
        runCatching { scene.removeEntities(asset.entities) }
        runCatching { assetLoader.destroyAsset(asset) }
    }

    fun destroy() {
        activeAsset?.let(::destroyAsset)
        activeAsset = null
        activeAnimator = null
        runCatching { resourceLoader?.destroy() }
        resourceLoader = null
        runCatching { materialProvider.destroyMaterials() }
        runCatching { materialProvider.destroy() }
    }
}
