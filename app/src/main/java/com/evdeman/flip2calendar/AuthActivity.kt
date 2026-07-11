package com.evdeman.flip2calendar

import android.app.Activity
import android.content.Intent
import android.os.Bundle

class AuthActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val data = intent?.data
        if (data != null) {
            val intent = Intent(this, MainActivity::class.java)
            intent.data = data
            intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(intent)
        }
        finish()
    }
}