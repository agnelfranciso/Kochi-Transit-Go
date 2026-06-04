package app.agneldev.kochitransitgo

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.color.DynamicColors

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        val toolbar = findViewById<Toolbar>(R.id.settingsToolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        val prefs = getSharedPreferences("KochiTransitPrefs", Context.MODE_PRIVATE)

        // Theme
        val themeSpinner = findViewById<Spinner>(R.id.themeSpinner)
        val themes = arrayOf("System Default", "Light", "Dark")
        themeSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, themes)
        
        val savedTheme = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        themeSpinner.setSelection(
            when (savedTheme) {
                AppCompatDelegate.MODE_NIGHT_NO -> 1
                AppCompatDelegate.MODE_NIGHT_YES -> 2
                else -> 0
            }
        )

        var isInitialSelection = true
        themeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (isInitialSelection) {
                    isInitialSelection = false
                    return
                }
                val mode = when (position) {
                    1 -> AppCompatDelegate.MODE_NIGHT_NO
                    2 -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
                prefs.edit().putInt("theme_mode", mode).apply()
                AppCompatDelegate.setDefaultNightMode(mode)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Time Format
        val timeFormatSwitch = findViewById<SwitchMaterial>(R.id.timeFormatSwitch)
        val isSystem24Hour = android.text.format.DateFormat.is24HourFormat(this)
        timeFormatSwitch.isChecked = prefs.getBoolean("use_24_hour", isSystem24Hour)
        timeFormatSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("use_24_hour", isChecked).apply()
        }

        // Legal
        val legalBtn = findViewById<View>(R.id.legalBtnCard)
        legalBtn.setOnClickListener {
            startActivity(Intent(this, LegalActivity::class.java))
        }
    }
}
