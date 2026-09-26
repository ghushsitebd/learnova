package com.learnova.app

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.*

class AdminPanelActivity : Activity() {
    private lateinit var ads: LearnovaAdsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ads = LearnovaAdsManager(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 28, 28, 28)
            setBackgroundColor(Color.rgb(246, 250, 248))
        }

        val title = TextView(this).apply {
            text = "Learnova • Ads Admin"
            textSize = 25f
            setTextColor(Color.rgb(20, 75, 52))
            gravity = Gravity.CENTER
        }
        root.addView(title, LinearLayout.LayoutParams(-1, -2))

        val note = TextView(this).apply {
            text = "Offline-first control • maximum 2 ads per 24 hours"
            textSize = 14f
            setTextColor(Color.DKGRAY)
            setPadding(0, 10, 0, 18)
        }
        root.addView(note)

        val enabled = CheckBox(this).apply {
            text = "Ads enabled"
            textSize = 17f
            isChecked = ads.enabled
        }
        root.addView(enabled)

        val adTitle = EditText(this).apply {
            hint = "Advertisement title"
            setText(ads.title)
            isSingleLine = true
        }
        root.addView(adTitle, LinearLayout.LayoutParams(-1, -2))

        val adMessage = EditText(this).apply {
            hint = "Advertisement message"
            setText(ads.message)
            minLines = 3
        }
        root.addView(adMessage, LinearLayout.LayoutParams(-1, -2))

        val limit = Spinner(this)
        val options = arrayOf("1 ad / 24h", "2 ads / 24h")
        limit.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, options)
        limit.setSelection(if (ads.maxPer24h == 1) 0 else 1)
        root.addView(limit, LinearLayout.LayoutParams(-1, -2))

        val save = Button(this).apply { text = "SAVE AD SETTINGS" }
        root.addView(save)

        val reset = Button(this).apply { text = "RESET 24-HOUR COUNTER" }
        root.addView(reset)

        val status = TextView(this).apply {
            textSize = 14f
            setPadding(0, 16, 0, 0)
        }
        root.addView(status)

        fun refresh() {
            status.text = "Today: ${ads.statusText()}\nAds are shown only after learning progression."
        }
        refresh()

        save.setOnClickListener {
            ads.enabled = enabled.isChecked
            ads.title = adTitle.text.toString().trim().ifEmpty { "Learnova" }
            ads.message = adMessage.text.toString().trim().ifEmpty { "Keep learning and exploring!" }
            ads.maxPer24h = if (limit.selectedItemPosition == 0) 1 else 2
            refresh()
            Toast.makeText(this, "Ad settings saved", Toast.LENGTH_SHORT).show()
        }

        reset.setOnClickListener {
            ads.resetCounter()
            refresh()
            Toast.makeText(this, "24-hour counter reset", Toast.LENGTH_SHORT).show()
        }

        setContentView(root)
    }
}
