package com.example.vibrarelax

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider

class MainActivity : AppCompatActivity() {

    private lateinit var slider: Slider
    private lateinit var intensityLabel: TextView
    private lateinit var patternGroup: RadioGroup
    private lateinit var timerSpinner: Spinner
    private lateinit var startStop: MaterialButton
    private lateinit var status: TextView
    private lateinit var targetSpinner: Spinner
    private lateinit var controllerStatus: TextView

    private val timerLabels = listOf("No timer", "5 min", "10 min", "15 min", "30 min", "60 min")
    private val timerMinutes = listOf(0, 5, 10, 15, 30, 60)

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        slider = findViewById(R.id.intensitySlider)
        intensityLabel = findViewById(R.id.intensityLabel)
        patternGroup = findViewById(R.id.patternGroup)
        timerSpinner = findViewById(R.id.timerSpinner)
        startStop = findViewById(R.id.startStop)
        status = findViewById(R.id.status)
        targetSpinner = findViewById(R.id.targetSpinner)
        controllerStatus = findViewById(R.id.controllerStatus)
        targetSpinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            listOf("Phone", "Controller (PS4 / PS5)", "Phone + controller")
        )
        targetSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: android.view.View?, pos: Int, id: Long) {
                updateControllerStatus()
                if (Session.running) send(VibrationService.ACTION_UPDATE)
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        Patterns.all.keys.forEachIndexed { i, name ->
            val rb = RadioButton(this).apply { id = 1000 + i; text = name; textSize = 16f }
            patternGroup.addView(rb)
        }
        patternGroup.check(1000)

        timerSpinner.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, timerLabels)

        slider.addOnChangeListener { _, v, _ ->
            intensityLabel.text = "Intensity: ${v.toInt()}%"
            if (Session.running) send(VibrationService.ACTION_UPDATE)
        }
        patternGroup.setOnCheckedChangeListener { _, _ ->
            if (Session.running) send(VibrationService.ACTION_UPDATE)
        }

        startStop.setOnClickListener {
            if (Session.running) {
                startService(Intent(this, VibrationService::class.java)
                    .setAction(VibrationService.ACTION_STOP))
            } else {
                requestNotifPermissionIfNeeded()
                send(VibrationService.ACTION_START)
            }
        }
        intensityLabel.text = "Intensity: ${slider.value.toInt()}%"
    }

    private fun currentPattern(): String {
        val idx = patternGroup.checkedRadioButtonId - 1000
        return Patterns.all.keys.elementAtOrNull(idx) ?: "Continuous"
    }

    private fun send(action: String) {
        val i = Intent(this, VibrationService::class.java).setAction(action)
            .putExtra(VibrationService.EXTRA_PATTERN, currentPattern())
            .putExtra(VibrationService.EXTRA_INTENSITY, slider.value.toInt())
            .putExtra(VibrationService.EXTRA_TARGET, targetSpinner.selectedItemPosition)
            .putExtra(VibrationService.EXTRA_MINUTES, timerMinutes[timerSpinner.selectedItemPosition])
        if (action == VibrationService.ACTION_START) ContextCompat.startForegroundService(this, i)
        else startService(i)
    }

    private fun requestNotifPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun updateControllerStatus() {
        val n = Controllers.find().size
        controllerStatus.text = when {
            android.os.Build.VERSION.SDK_INT < 31 -> "Controller vibration needs Android 12 or newer"
            n > 0 -> "Controllers with vibration found: $n"
            else -> "No controller with vibration found. Pair it in Bluetooth settings, press the PS button, then reopen the app."
        }
    }

    private fun refresh() {
        updateControllerStatus()
        startStop.text = if (Session.running) "Stop" else "Start"
        timerSpinner.isEnabled = !Session.running
        targetSpinner.isEnabled = true
        status.text = when {
            !Session.running -> "Ready"
            Session.remainingSec >= 0 ->
                "Running — %d:%02d left".format(Session.remainingSec / 60, Session.remainingSec % 60)
            else -> "Running"
        }
    }

    override fun onStart() {
        super.onStart()
        Session.listener = { refresh() }
        refresh()
    }

    override fun onStop() {
        Session.listener = null
        super.onStop()
    }
}
