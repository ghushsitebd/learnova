package com.learnova.app

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.*

class AdminPanelActivity : Activity() {
    private lateinit var ads: LearnovaAdsManager
    private lateinit var statusText: TextView
    private lateinit var enabledSwitch: Switch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ads = LearnovaAdsManager(this)
        showDashboard()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun label(value: String, size: Float = 13f) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(Color.rgb(75, 85, 82))
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(16), dp(18), dp(16))
        setBackgroundColor(Color.WHITE)
        elevation = dp(3).toFloat()
    }

    private fun showDashboard() {
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(28))
            setBackgroundColor(Color.rgb(242, 247, 245))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(8), dp(4), dp(18))
        }
        header.addView(TextView(this).apply {
            text = "LEARNOVA ADMIN"
            textSize = 26f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(18, 91, 61))
        })
        header.addView(TextView(this).apply {
            text = "Advertisement Control Center"
            textSize = 15f
            setTextColor(Color.rgb(90, 105, 100))
            setPadding(0, dp(5), 0, 0)
        })
        root.addView(header)

        val statusCard = card()
        statusCard.addView(label("ADVERTISEMENT STATUS"))
        enabledSwitch = Switch(this).apply {
            text = if (ads.enabled) "Ads are ON" else "Ads are OFF"
            textSize = 17f
            isChecked = ads.enabled
            setOnCheckedChangeListener { _, checked ->
                text = if (checked) "Ads are ON" else "Ads are OFF"
            }
        }
        statusCard.addView(enabledSwitch)
        root.addView(statusCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })

        val usageCard = card()
        usageCard.addView(label("24-HOUR DELIVERY"))
        statusText = label("")
        statusText.textSize = 18f
        statusText.setTextColor(Color.rgb(18, 91, 61))
        statusText.setPadding(0, dp(8), 0, 0)
        usageCard.addView(statusText)
        usageCard.addView(label("Maximum allowed: 1 or 2 advertisements in each 24-hour window").apply {
            setPadding(0, dp(6), 0, 0)
        })
        root.addView(usageCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })

        val contentCard = card()
        contentCard.addView(label("ADVERTISEMENT CONTENT"))
        val titleInput = EditText(this).apply {
            hint = "Advertisement title"
            setText(ads.title)
            isSingleLine = true
        }
        contentCard.addView(titleInput)
        val messageInput = EditText(this).apply {
            hint = "Advertisement message"
            setText(ads.message)
            minLines = 4
            gravity = Gravity.TOP
        }
        contentCard.addView(messageInput)
        root.addView(contentCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })

        val limitCard = card()
        limitCard.addView(label("DELIVERY LIMIT"))
        val limit = Spinner(this)
        val options = arrayOf("1 advertisement / 24 hours", "2 advertisements / 24 hours")
        limit.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, options)
        limit.setSelection(if (ads.maxPer24h == 1) 0 else 1)
        limitCard.addView(limit, LinearLayout.LayoutParams(-1, dp(52)))
        root.addView(limitCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })

        val preview = Button(this).apply { text = "PREVIEW ADVERTISEMENT" }
        preview.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(titleInput.text.toString().trim().ifEmpty { "Learnova" })
                .setMessage(messageInput.text.toString().trim().ifEmpty { "Keep learning and exploring!" })
                .setPositiveButton("CLOSE", null)
                .show()
        }
        root.addView(preview, LinearLayout.LayoutParams(-1, dp(50)).apply { bottomMargin = dp(8) })

        val save = Button(this).apply { text = "SAVE SETTINGS"; textSize = 15f }
        save.setOnClickListener {
            ads.enabled = enabledSwitch.isChecked
            ads.title = titleInput.text.toString().trim().ifEmpty { "Learnova" }
            ads.message = messageInput.text.toString().trim().ifEmpty { "Keep learning and exploring!" }
            ads.maxPer24h = if (limit.selectedItemPosition == 0) 1 else 2
            refreshStatus()
            Toast.makeText(this, "Admin settings saved", Toast.LENGTH_SHORT).show()
        }
        root.addView(save, LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(8) })

        val reset = Button(this).apply { text = "RESET 24-HOUR COUNTER" }
        reset.setOnClickListener {
            ads.resetCounter()
            refreshStatus()
            Toast.makeText(this, "24-hour counter reset", Toast.LENGTH_SHORT).show()
        }
        root.addView(reset, LinearLayout.LayoutParams(-1, dp(50)).apply { bottomMargin = dp(16) })

        val back = TextView(this).apply {
            text = "← Back to Learnova"
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(18, 91, 61))
            setPadding(0, dp(12), 0, dp(12))
            setOnClickListener { finish() }
        }
        root.addView(back)

        scroll.addView(root)
        setContentView(scroll)
        refreshStatus()
    }

    private fun refreshStatus() {
        if (::statusText.isInitialized) {
            statusText.text = "${ads.statusText()}  •  ${if (ads.enabled) "ACTIVE" else "OFF"}"
        }
    }
}
