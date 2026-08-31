package com.evdeman.flip2calendar

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class OptionsActivity : AppCompatActivity() {

    companion object {
        const val KEY_SHOW_HOLIDAYS = "show_holidays"
        const val KEY_HIDE_FUTURE = "hide_future"
        const val KEY_DAYS_AHEAD = "days_ahead"
        const val DEFAULT_DAYS_AHEAD = 7
    }

    private lateinit var switchHolidays: Switch
    private lateinit var switchHideFuture: Switch
    private lateinit var tvDaysCount: TextView
    private lateinit var btnDaysDown: Button
    private lateinit var btnDaysUp: Button
    private lateinit var btnSave: Button
    private lateinit var btnCancel: Button

    private var daysAhead = DEFAULT_DAYS_AHEAD

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_options)

        switchHolidays = findViewById(R.id.switchHolidays)
        switchHideFuture = findViewById(R.id.switchHideFuture)
        tvDaysCount = findViewById(R.id.tvDaysCount)
        btnDaysDown = findViewById(R.id.btnDaysDown)
        btnDaysUp = findViewById(R.id.btnDaysUp)
        btnSave = findViewById(R.id.btnSaveOptions)
        btnCancel = findViewById(R.id.btnCancelOptions)

        // Load current settings
        val prefs = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        switchHolidays.isChecked = prefs.getBoolean(KEY_SHOW_HOLIDAYS, false)
        switchHideFuture.isChecked = prefs.getBoolean(KEY_HIDE_FUTURE, false)
        daysAhead = prefs.getInt(KEY_DAYS_AHEAD, DEFAULT_DAYS_AHEAD)
        tvDaysCount.text = daysAhead.toString()

        btnDaysDown.setOnClickListener {
            if (daysAhead > 1) {
                daysAhead--
                tvDaysCount.text = daysAhead.toString()
            }
        }

        btnDaysUp.setOnClickListener {
            if (daysAhead < 60) {
                daysAhead++
                tvDaysCount.text = daysAhead.toString()
            }
        }

        btnSave.setOnClickListener {
            prefs.edit()
                .putBoolean(KEY_SHOW_HOLIDAYS, switchHolidays.isChecked)
                .putBoolean(KEY_HIDE_FUTURE, switchHideFuture.isChecked)
                .putInt(KEY_DAYS_AHEAD, daysAhead)
                .apply()
            setResult(RESULT_OK)
            finish()
        }

        btnCancel.setOnClickListener {
            finish()
        }
    }
}