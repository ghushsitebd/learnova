package com.learnova.app

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val textView = TextView(this)
        textView.text = "Learnova"
        textView.textSize = 32f
        textView.gravity = android.view.Gravity.CENTER

        setContentView(textView)
    }
}
