package com.checkout.risk_sdk_android

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.TextView
import com.checkout.risk.Risk

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Find the TextView by its ID
        val helloTextView: TextView = findViewById(R.id.helloTextView)

        // Set the text dynamically
        helloTextView.text = Risk().getHelloWorld()
    }
}
