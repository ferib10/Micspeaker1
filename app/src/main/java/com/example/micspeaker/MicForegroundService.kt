package com.example.micspeaker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class MicForegroundService : Service() {

    @Volatile private var isRunning = false
    private var workerThread: Thread? = null

    companion object {
        const val CHANNEL_ID = "mic_live_channel"
        const val NOTIF_ID = 1
        const val ACTION_STOP = "com.example.micspeaker.STOP"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopStreaming()
            stopSelf()
            return START_NOT_STICKY
        }
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
        startForeground(NOTIF_ID, buildNotification(), type)
        startStreaming()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopStreaming()
        super.onDestroy()
    }

    private fun startStreaming() {
        if (isRunning) return
        isRunning = true
        workerThread = Thread {
            val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

            // اول SCO رو امتحان کن (برای هدست و اسپیکرهای دو طرفه)
            val useSco = audioManager.isBluetoothScoAvailableOffCall
            if (useSco) {
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                @Suppress("DEPRECATION")
                audioManager.startBluetoothSco()
                @Suppress("DEPRECATION")
                audioManager.isBluetoothScoOn = true
            } else {
                // اگه SCO نبود از A2DP استفاده کن (اسپیکر معمولی)
                audioManager.mode = AudioManager.MODE_NORMAL
            }

            val sampleRate = 44100
            val minBufIn = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val minBufOut = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = maxOf(minBufIn, minBufOut, 4096)

            val recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            val usage = if (useSco)
                AudioAttributes.USAGE_VOICE_COMMUNICATION
            else
                AudioAttributes.USAGE_MEDIA

            val track = AudioTrack(
                AudioAttributes.Builder()
                    .setUsage(usage)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build(),
                bufferSize,
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE
            )

            try {
                recorder.startRecording()
                track.play()
                val buffer = ShortArray(bufferSize / 2)
                while (isRunning) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read > 0) track.write(buffer, 0, read)
                }
            } finally {
                try { recorder.stop() } catch (e: Exception) { }
                try { recorder.release() } catch (e: Exception) { }
                try { track.stop() } catch (e: Exception) { }
                try { track.release() } catch (e: Exception) { }
                if (useSco) {
                    @Suppress("DEPRECATION")
                    audioManager.stopBluetoothSco()
                    @Suppress("DEPRECATION")
                    audioManager.isBluetoothScoOn = false
                }
                audioManager.mode = AudioManager.MODE_NORMAL
            }
        }
        workerThread?.start()
    }

    private fun stopStreaming() {
        isRunning = false
        workerThread?.join(500)
        workerThread = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "میکروفون زنده",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, MicForegroundService::class.java)
        stopIntent.action = ACTION_STOP
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("میکروفون زنده فعال است")
            .setContentText("صدا به اسپیکر متصل پخش می‌شود")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .addAction(android.R.drawable.ic_media_pause, "توقف", stopPendingIntent)
            .setSubText("by Farshad Babanejhad | farshad1069@msn.com")
            .setOngoing(true)
            .build()
    }
}