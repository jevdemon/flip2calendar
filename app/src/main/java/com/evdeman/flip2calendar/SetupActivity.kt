package com.evdeman.flip2calendar

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SetupActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        val etClientId = findViewById<EditText>(R.id.etClientId)
        val etClientSecret = findViewById<EditText>(R.id.etClientSecret)
        val btnSave = findViewById<Button>(R.id.btnSaveCredentials)
        val tvLink = findViewById<TextView>(R.id.tvSetupLink)

        // Make the GitHub link clickable
        tvLink.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW,
                android.net.Uri.parse("https://github.com/jevdemon/flip2calendar")))
        }

        // Pre-fill if credentials already exist (user came from settings)
        val prefs = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val existingId = prefs.getString(MainActivity.KEY_CLIENT_ID, "")
        val existingSecret = prefs.getString(MainActivity.KEY_CLIENT_SECRET, "")
        if (!existingId.isNullOrEmpty()) etClientId.setText(existingId)
        if (!existingSecret.isNullOrEmpty()) etClientSecret.setText(existingSecret)

        btnSave.setOnClickListener {
            val clientId = etClientId.text.toString().trim()
            val clientSecret = etClientSecret.text.toString().trim()

            if (clientId.isEmpty()) {
                Toast.makeText(this, "Please enter your Client ID", Toast.LENGTH_SHORT).show()
                etClientId.requestFocus()
                return@setOnClickListener
            }
            if (!clientId.contains("apps.googleusercontent.com")) {
                Toast.makeText(this, "Client ID should end with .apps.googleusercontent.com", Toast.LENGTH_LONG).show()
                etClientId.requestFocus()
                return@setOnClickListener
            }
            if (clientSecret.isEmpty()) {
                Toast.makeText(this, "Please enter your Client Secret", Toast.LENGTH_SHORT).show()
                etClientSecret.requestFocus()
                return@setOnClickListener
            }

            // Save credentials
            prefs.edit()
                .putString(MainActivity.KEY_CLIENT_ID, clientId)
                .putString(MainActivity.KEY_CLIENT_SECRET, clientSecret)
                .apply()

            // Clear any existing tokens so OAuth restarts with new credentials
            prefs.edit()
                .remove(MainActivity.KEY_ACCESS_TOKEN)
                .remove(MainActivity.KEY_REFRESH_TOKEN)
                .apply()

            Toast.makeText(this, "Credentials saved!", Toast.LENGTH_SHORT).show()

            // Launch MainActivity
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}