package com.example.micspeaker

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private var isActive = false
    private lateinit var statusText: TextView
    private lateinit var toggleButton: Button
    private lateinit var audioManager: AudioManager

    private val requestPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            startMic()
        } else {
            Toast.makeText(this, "برای کار کردن برنامه باید مجوز میکروفون را بدهی", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        toggleButton = findViewById(R.id.toggleButton)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val infoButton = findViewById<Button>(R.id.infoButton)
        infoButton.setOnClickListener {
            android.app.AlertDialog.Builder(this)
                .setTitle("اطلاعات سازنده")
                .setMessage("نام: Farshad Babanejhad\nایمیل: farshad1069@msn.com")
                .setPositiveButton("باشه", null)
                .show()
        }

        toggleButton.setOnClickListener {
            if (!isActive) {
                checkPermissionsAndStart()
            } else {
                stopMic()
            }
        }
    }

    private fun checkPermissionsAndStart() {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.RECORD_AUDIO)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (needed.isNotEmpty()) {
            requestPermissionLauncher.launch(needed.toTypedArray())
        } else {
            startMic()
        }
    }

    private fun startMic() {
        audioManager.mode = AudioManager.MODE_NORMAL
        audioManager.isSpeakerphoneOn = false

        val serviceIntent = Intent(this, MicForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        isActive = true
        statusText.text = "روشن — در حال پخش زنده"
        toggleButton.text = "توقف"
    }

    private fun stopMic() {
        val stopIntent = Intent(this, MicForegroundService::class.java).apply {
            action = MicForegroundService.ACTION_STOP
        }
        startService(stopIntent)

        isActive = false
        statusText.text = "خاموش"
        toggleButton.text = "شروع"
    }

    override fun onDestroy() {
        if (isActive) stopMic()
        super.onDestroy()
    }
}