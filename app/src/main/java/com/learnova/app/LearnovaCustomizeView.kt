package com.learnova.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.min

/** One child-friendly place for choosing both the vehicle and the journey environment. */
internal class LearnovaCustomizeView(
    context: Context,
    private val onVehicleSelected: (VehicleDefinition) -> Unit,
    private val onEnvironmentSelected: (String) -> Unit,
    private val onClosed: () -> Unit
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val type = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
    }
    private var tab = 0
    private var page = 0
    private var selectedVehicleId = 1
    private var selectedEnvironment = "Forest"
    private val pageSize = 12
    private val environments = listOf(
        "Forest" to "জঙ্গল",
        "Village" to "গ্রাম",
        "Desert" to "মরুভূমি",
        "River" to "নদী",
        "Mountain" to "পাহাড়",
        "Market" to "বাজার",
        "Coast" to "সমুদ্র"
    )

    init {
        setBackgroundColor(Color.rgb(10, 18, 28))
        isFocusable = true
    }

    fun setSelectedVehicle(id: Int) {
        selectedVehicleId = id.coerceIn(1, VehicleCatalog.all.size)
        page = (selectedVehicleId - 1) / pageSize
        invalidate()
    }

    fun setSelectedEnvironment(name: String?) {
        selectedEnvironment = environments.firstOrNull { it.first.equals(name, ignoreCase = true) }?.first ?: "Forest"
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        paint.color = Color.rgb(10, 18, 28)
        c.drawRect(0f, 0f, w, h, paint)

        type.textAlign = Paint.Align.LEFT
        type.color = Color.WHITE
        type.textSize = min(w * .07f, 30f)
        c.drawText("CUSTOMIZE", 22f, 42f, type)
        type.textSize = min(w * .036f, 16f)
        type.color = Color.rgb(190, 215, 230)
        c.drawText("Choose your car and world", 22f, 66f, type)

        val tabY = 82f
        drawButton(c, 18f, tabY, w / 2f - 24f, 44f, "CAR", tab == 0)
        drawButton(c, w / 2f + 6f, tabY, w / 2f - 24f, 44f, "ENVIRONMENT", tab == 1)

        if (tab == 0) {
            type.textAlign = Paint.Align.RIGHT
            type.color = Color.rgb(190, 215, 230)
            type.textSize = 14f
            c.drawText("${page + 1} / ${kotlin.math.ceil(VehicleCatalog.all.size / pageSize.toDouble()).toInt()}", w - 20f, 151f, type)
            val gap = 10f
            val cols = if (w >= 900f) 4 else 3
            val top = 162f
            val bottom = 78f
            val cardW = (w - gap * (cols + 1)) / cols
            val cardH = (h - top - bottom - gap * 3f) / 4f
            for (slot in 0 until pageSize) {
                val index = page * pageSize + slot
                if (index !in VehicleCatalog.all.indices) break
                val vehicle = VehicleCatalog.all[index]
                val row = slot / cols
                val col = slot % cols
                val x = gap + col * (cardW + gap)
                val y = top + row * (cardH + gap)
                paint.color = if (vehicle.id == selectedVehicleId) Color.rgb(0, 150, 105) else Color.rgb(27, 43, 58)
                c.drawRoundRect(RectF(x, y, x + cardW, y + cardH), 14f, 14f, paint)
                type.textAlign = Paint.Align.CENTER
                type.color = Color.WHITE
                type.textSize = min(cardW * .10f, 16f)
                val name = vehicle.name
                c.drawText(if (name.length > 17) name.take(15) + "…" else name, x + cardW / 2f, y + cardH * .56f, type)
                type.textSize = min(cardW * .075f, 12f)
                type.color = Color.rgb(190, 215, 230)
                c.drawText(if (vehicle.id == selectedVehicleId) "SELECTED" else "CHOOSE", x + cardW / 2f, y + cardH * .80f, type)
            }
        } else {
            val gap = 12f
            val top = 148f
            val buttonH = min(64f, (h - top - 92f - gap * 3f) / 4f)
            environments.forEachIndexed { index, env ->
                val col = index % 2
                val row = index / 2
                val bw = (w - 3f * gap) / 2f
                val x = gap + col * (bw + gap)
                val y = top + row * (buttonH + gap)
                paint.color = if (selectedEnvironment == env.first) Color.rgb(0, 150, 105) else Color.rgb(27, 43, 58)
                c.drawRoundRect(RectF(x, y, x + bw, y + buttonH), 16f, 16f, paint)
                type.textAlign = Paint.Align.CENTER
                type.color = Color.WHITE
                type.textSize = min(w * .045f, 19f)
                c.drawText(env.first, x + bw / 2f, y + buttonH * .43f, type)
                type.textSize = min(w * .035f, 15f)
                type.color = Color.rgb(200, 220, 230)
                c.drawText(env.second, x + bw / 2f, y + buttonH * .75f, type)
            }
        }
        drawButton(c, 18f, h - 60f, 110f, 42f, "BACK", false)
        drawButton(c, w / 2f - 48f, h - 60f, 96f, 42f, if (tab == 0) "NEXT" else "CAR", false)
        drawButton(c, w - 138f, h - 60f, 120f, 42f, "APPLY", true)
    }

    private fun drawButton(c: Canvas, x: Float, y: Float, bw: Float, bh: Float, label: String, active: Boolean) {
        paint.color = if (active) Color.rgb(0, 190, 120) else Color.rgb(50, 70, 86)
        c.drawRoundRect(RectF(x, y, x + bw, y + bh), 14f, 14f, paint)
        type.color = Color.WHITE
        type.textAlign = Paint.Align.CENTER
        type.textSize = 15f
        c.drawText(label, x + bw / 2f, y + bh / 2f + 5f, type)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked != MotionEvent.ACTION_UP) return true
        val w = width.toFloat()
        val h = height.toFloat()
        if (e.y >= h - 72f) {
            when {
                e.x < 150f -> onClosed()
                e.x > w - 155f -> {
                    // Apply both current choices so changing tabs never discards a selection.
                    onVehicleSelected(VehicleCatalog.all[selectedVehicleId - 1])
                    if (tab == 1) onEnvironmentSelected(selectedEnvironment)
                    onClosed()
                }
                else -> { tab = 1 - tab; invalidate() }
            }
            return true
        }
        if (e.y in 82f..130f) {
            tab = if (e.x < w / 2f) 0 else 1
            invalidate()
            return true
        }
        if (tab == 0) {
            val gap = 10f
            val cols = if (w >= 900f) 4 else 3
            val top = 162f
            val cardW = (w - gap * (cols + 1)) / cols
            val cardH = (h - top - 78f - gap * 3f) / 4f
            val col = ((e.x - gap) / (cardW + gap)).toInt()
            val row = ((e.y - top) / (cardH + gap)).toInt()
            val index = page * pageSize + row * cols + col
            if (col in 0 until cols && row in 0..3 && index in VehicleCatalog.all.indices) {
                selectedVehicleId = VehicleCatalog.all[index].id
                invalidate()
            }
        } else {
            val gap = 12f
            val top = 148f
            val buttonH = min(64f, (h - top - 92f - gap * 3f) / 4f)
            val bw = (w - 3f * gap) / 2f
            val col = ((e.x - gap) / (bw + gap)).toInt()
            val row = ((e.y - top) / (buttonH + gap)).toInt()
            val index = row * 2 + col
            if (col in 0..1 && index in environments.indices) {
                selectedEnvironment = environments[index].first
                invalidate()
            }
        }
        return true
    }
}
