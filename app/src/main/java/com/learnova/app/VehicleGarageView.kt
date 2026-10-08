package com.learnova.app

import android.content.Context
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.view.MotionEvent
import android.view.View
import kotlin.math.ceil
import kotlin.math.min

/**
 * Lightweight, child-friendly 500-vehicle garage.
 *
 * Every catalog entry is available immediately. There is deliberately no
 * unlock, payment, level, timer or progression gate.
 */
internal class VehicleGarageView(
    context: Context,
    private val onSelected: (VehicleDefinition) -> Unit,
    private val onClosed: () -> Unit
) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
    }

    private var page = 0
    private var selectedId = 1
    private val pageSize = 12
    private val totalPages = ceil(VehicleCatalog.all.size / pageSize.toDouble()).toInt()

    init {
        setBackgroundColor(Color.rgb(10, 18, 28))
        isFocusable = true
    }

    fun setSelected(id: Int) {
        selectedId = id.coerceIn(1, VehicleCatalog.all.size)
        page = (selectedId - 1) / pageSize
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        paint.color = Color.rgb(16, 29, 43)
        c.drawRect(0f, 0f, w, h, paint)

        title.color = Color.WHITE
        title.textAlign = Paint.Align.LEFT
        title.textSize = min(w * 0.065f, 28f)
        c.drawText("GARAGE", 24f, 42f, title)

        title.textSize = min(w * 0.038f, 17f)
        title.color = Color.rgb(180, 215, 232)
        c.drawText("500 vehicles • All available", 24f, 68f, title)

        title.textAlign = Paint.Align.RIGHT
        c.drawText("${page + 1} / $totalPages", w - 24f, 42f, title)

        val gap = 12f
        val cols = if (w >= 900f) 4 else 3
        val rows = 4
        val top = 88f
        val bottom = 72f
        val cardW = (w - gap * (cols + 1)) / cols
        val cardH = (h - top - bottom - gap * (rows + 1)) / rows

        for (slot in 0 until pageSize) {
            val index = page * pageSize + slot
            if (index >= VehicleCatalog.all.size) break

            val row = slot / cols
            val col = slot % cols
            val left = gap + col * (cardW + gap)
            val topY = top + gap + row * (cardH + gap)
            drawCard(c, VehicleCatalog.all[index], left, topY, cardW, cardH)
        }

        drawButton(c, 18f, h - 58f, 120f, 42f, "CLOSE", false)
        drawButton(c, w - 138f, h - 58f, 120f, 42f, "PLAY", true)
        drawButton(c, w / 2f - 42f, h - 58f, 84f, 42f, "NEXT", false)
    }

    private fun drawCard(c: Canvas, v: VehicleDefinition, x: Float, y: Float, cw: Float, ch: Float) {
        val selected = v.id == selectedId
        paint.color = if (selected) Color.rgb(0, 180, 120) else Color.rgb(27, 43, 58)
        c.drawRoundRect(RectF(x, y, x + cw, y + ch), 18f, 18f, paint)

        paint.color = if (selected) Color.argb(45, 255, 255, 255) else Color.rgb(38, 58, 75)
        c.drawRoundRect(RectF(x + 7f, y + 7f, x + cw - 7f, y + ch * 0.58f), 13f, 13f, paint)

        drawVehicleSilhouette(c, x + cw / 2f, y + ch * 0.34f, cw * 0.34f, v.type)

        title.textAlign = Paint.Align.CENTER
        title.color = Color.WHITE
        title.textSize = min(cw * 0.095f, 16f)
        c.drawText(v.name, x + cw / 2f, y + ch * 0.77f, title)

        title.textSize = min(cw * 0.075f, 12f)
        title.color = Color.rgb(175, 205, 220)
        c.drawText("AVAILABLE", x + cw / 2f, y + ch * 0.91f, title)
    }

    private fun drawVehicleSilhouette(c: Canvas, cx: Float, cy: Float, s: Float, type: String) {
        paint.color = Color.rgb(215, 230, 238)
        when (type.lowercase()) {
            "bus", "van" -> {
                c.drawRoundRect(RectF(cx - s, cy - s * .42f, cx + s, cy + s * .38f), s * .16f, s * .16f, paint)
            }
            "truck", "sixwheel" -> {
                c.drawRect(cx - s, cy - s * .34f, cx + s * .65f, cy + s * .35f, paint)
                c.drawRoundRect(RectF(cx + s * .52f, cy - s * .26f, cx + s, cy + s * .35f), 6f, 6f, paint)
            }
            "bike", "sportbike", "bicycle" -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = maxOf(3f, s * .08f)
                c.drawCircle(cx - s * .55f, cy + s * .22f, s * .22f, paint)
                c.drawCircle(cx + s * .55f, cy + s * .22f, s * .22f, paint)
                c.drawLine(cx - s * .55f, cy + s * .22f, cx, cy - s * .05f, paint)
                c.drawLine(cx, cy - s * .05f, cx + s * .55f, cy + s * .22f, paint)
                paint.style = Paint.Style.FILL
            }
            "air", "space" -> {
                val path = Path()
                path.moveTo(cx, cy - s * .62f)
                path.lineTo(cx + s * .18f, cy + s * .58f)
                path.lineTo(cx, cy + s * .38f)
                path.lineTo(cx - s * .18f, cy + s * .58f)
                path.close()
                c.drawPath(path, paint)
                c.drawOval(RectF(cx - s * .82f, cy - s * .08f, cx + s * .82f, cy + s * .10f), paint)
            }
            else -> {
                val body = Path()
                body.moveTo(cx - s, cy + s * .28f)
                body.lineTo(cx - s * .62f, cy - s * .28f)
                body.lineTo(cx - s * .18f, cy - s * .52f)
                body.lineTo(cx + s * .42f, cy - s * .45f)
                body.lineTo(cx + s, cy + s * .18f)
                body.close()
                c.drawPath(body, paint)
                paint.color = Color.rgb(20, 30, 38)
                c.drawCircle(cx - s * .58f, cy + s * .32f, s * .18f, paint)
                c.drawCircle(cx + s * .58f, cy + s * .32f, s * .18f, paint)
            }
        }
    }

    private fun drawButton(c: Canvas, x: Float, y: Float, bw: Float, bh: Float, label: String, active: Boolean) {
        paint.color = if (active) Color.rgb(0, 200, 120) else Color.rgb(50, 70, 86)
        c.drawRoundRect(RectF(x, y, x + bw, y + bh), 16f, 16f, paint)
        title.color = Color.WHITE
        title.textAlign = Paint.Align.CENTER
        title.textSize = 14f
        c.drawText(label, x + bw / 2f, y + bh / 2f + 5f, title)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked != MotionEvent.ACTION_UP) return true
        val w = width.toFloat()
        val h = height.toFloat()

        if (e.y >= h - 72f) {
            when {
                e.x < 150f -> onClosed()
                e.x > w - 155f -> {
                    onSelected(VehicleCatalog.all[selectedId - 1])
                    onClosed()
                }
                else -> {
                    page = (page + 1) % totalPages
                    invalidate()
                }
            }
            return true
        }

        val gap = 12f
        val cols = if (w >= 900f) 4 else 3
        val top = 88f
        val bottom = 72f
        val cardW = (w - gap * (cols + 1)) / cols
        val cardH = (h - top - bottom - gap * 5f) / 4f

        val col = ((e.x - gap) / (cardW + gap)).toInt()
        val row = ((e.y - top - gap) / (cardH + gap)).toInt()
        if (col !in 0 until cols || row !in 0..3) return true

        val index = page * pageSize + row * cols + col
        if (index in VehicleCatalog.all.indices) {
            selectedId = VehicleCatalog.all[index].id
            invalidate()
        }
        return true
    }
}
