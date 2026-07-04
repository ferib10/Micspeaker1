}

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, MicForegroundService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("میکروفون زنده فعال است")
            .setContentText("صدا به اسپیکر متصل پخش می‌شود")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .addAction(android.R.drawable.ic_media_pause, "توقف", stopPendingIntent)
            .setOngoing(true)
            .build()
    }
}