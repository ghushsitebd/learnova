package com.learnova.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View

/**
 * Parent-facing progress summary. It is intentionally read-only and uses the same
 * offline SharedPreferences as the game, so parents can see learning progress
 * without an account or network connection.
 */
class LearnovaParentReportView(
    context: Context,
    private val onClose: () -> Unit
) : View(context) {

    private val prefs = context.getSharedPreferences("learnova_progress", Context.MODE_PRIVATE)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 25f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    private val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(36, 50, 48)
        textSize = 16f
        typeface = android.graphics.Typeface.DEFAULT
    }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(82, 98, 94)
        textSize = 13f
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val w = width.toFloat()
        val h = height.toFloat()

        paint.color = Color.argb(245, 9, 38, 34)
        c.drawRect(0f, 0f, w, h, paint)

        paint.color = Color.rgb(21, 117, 82)
        c.drawRoundRect(RectF(18f, 24f, w - 18f, 112f), 24f, 24f, paint)
        c.drawText("Parent Learning Report", 38f, 62f, title)
        small.color = Color.WHITE
        c.drawText("Offline • private on this device", 38f, 88f, small)

        val level = prefs.getInt("level", 1)
        val completed = prefs.getInt("completed_lessons", 0)
        val points = prefs.getInt("learning_points", 0)
        val stars = prefs.getInt("stars", 0)
        val world = prefs.getInt("worldSceneId", 1)
        val lesson = prefs.getInt("question", 0) + 1

        drawMetric(c, 18f, 136f, w * .48f - 10f, 232f, "Level", level.toString())
        drawMetric(c, w * .52f + 10f, 136f, w - 18f, 232f, "Lessons completed", completed.toString())
        drawMetric(c, 18f, 246f, w * .48f - 10f, 342f, "Learning points", points.toString())
        drawMetric(c, w * .52f + 10f, 246f, w - 18f, 342f, "Stars earned", stars.toString())

        paint.color = Color.WHITE
        c.drawRoundRect(RectF(18f, 360f, w - 18f, minOf(h - 92f, 505f)), 22f, 22f, paint)
        body.color = Color.rgb(27, 105, 69)
        body.typeface = android.graphics.Typeface.DEFAULT_BOLD
        c.drawText("What your child is learning", 36f, 395f, body)
        body.typeface = android.graphics.Typeface.DEFAULT
        body.color = Color.rgb(45, 58, 56)
        c.drawText("• Letters, sounds and simple words", 36f, 428f, body)
        c.drawText("• Learning through touch, movement and play", 36f, 458f, body)
        c.drawText("• Arabic and Quran learning as progression unlocks", 36f, 488f, body)
        c.drawText("• Short achievements keep progress visible", 36f, 518f, body)

        small.color = Color.rgb(225, 238, 232)
        c.drawText("Current learning item: $lesson   •   World: $world", 24f, h - 62f, small)

        paint.color = Color.rgb(240, 176, 62)
        c.drawRoundRect(RectF(w - 142f, h - 112f, w - 24f, h - 30f), 20f, 20f, paint)
        title.textSize = 16f
        title.color = Color.WHITE
        c.drawText("CLOSE", w - 111f, h - 62f, title)
        title.textSize = 25f
    }

    private fun drawMetric(c: Canvas, left: Float, top: Float, right: Float, bottom: Float, label: String, value: String) {
        paint.color = Color.WHITE
        c.drawRoundRect(RectF(left, top, right, bottom), 20f, 20f, paint)
        body.color = Color.rgb(82, 98, 94)
        c.drawText(label, left + 16f, top + 32f, body)
        body.color = Color.rgb(21, 117, 82)
        body.typeface = android.graphics.Typeface.DEFAULT_BOLD
        body.textSize = 28f
        c.drawText(value, left + 16f, top + 74f, body)
        body.textSize = 16f
        body.typeface = android.graphics.Typeface.DEFAULT
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP &&
            event.x > width - 165f && event.y > height - 135f) {
            onClose()
        }
        return true
    }
}
