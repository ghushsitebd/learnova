package com.learnova.app

import android.content.Context

class LearnovaAdsManager(context: Context) {
    private val prefs = context.getSharedPreferences("learnova_ads", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(value) = prefs.edit().putBoolean("enabled", value).apply()

    var title: String
        get() = prefs.getString("title", "Learnova") ?: "Learnova"
        set(value) = prefs.edit().putString("title", value.take(80)).apply()

    var message: String
        get() = prefs.getString("message", "Keep learning and exploring!") ?: "Keep learning and exploring!"
        set(value) = prefs.edit().putString("message", value.take(180)).apply()

    var maxPer24h: Int
        get() = prefs.getInt("max_per_24h", 2).coerceIn(1, 2)
        set(value) = prefs.edit().putInt("max_per_24h", value.coerceIn(1, 2)).apply()

    private var windowStart: Long
        get() = prefs.getLong("window_start", 0L)
        set(value) = prefs.edit().putLong("window_start", value).apply()

    private var shownCount: Int
        get() = prefs.getInt("shown_count", 0)
        set(value) = prefs.edit().putInt("shown_count", value).apply()

    fun canShow(now: Long = System.currentTimeMillis()): Boolean {
        if (!enabled) return false
        if (windowStart == 0L || now - windowStart >= 24L * 60L * 60L * 1000L) {
            windowStart = now
            shownCount = 0
        }
        return shownCount < maxPer24h
    }

    fun recordShown(now: Long = System.currentTimeMillis()) {
        if (!canShow(now)) return
        shownCount += 1
    }

    fun resetCounter() {
        windowStart = 0L
        shownCount = 0
    }

    fun statusText(now: Long = System.currentTimeMillis()): String {
        if (windowStart == 0L || now - windowStart >= 24L * 60L * 60L * 1000L) {
            return "0 / $maxPer24h shown"
        }
        return "$shownCount / $maxPer24h shown"
    }
}
