package com.richardluo.globalIconPack.iconPack

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo

private const val CHANNEL_ID: String = "IconPackProvider"
private const val STOP_FOREGROUND: String = "STOP_FOREGROUND"

class KeepAliveService : Service() {

  companion object {
    private var isServiceRunning = false

    fun startForeground(context: Context) {
      if (isServiceRunning) return
      runCatching { context.startForegroundService(Intent(context, KeepAliveService::class.java)) }
    }

    fun stopForeground(context: Context) {
      if (!isServiceRunning) return
      context.startService(
        Intent(context, KeepAliveService::class.java).apply { action = STOP_FOREGROUND }
      )
    }
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      STOP_FOREGROUND -> {
        stopForeground(STOP_FOREGROUND_REMOVE)
        isServiceRunning = false
      }
      else -> {
        createNotificationChannel()
        val builder: Notification.Builder =
          Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Providing icon pack to hooked apps")
            .setAutoCancel(true)
        startForeground(1, builder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST)
        isServiceRunning = true
      }
    }
    return START_STICKY
  }

  private fun createNotificationChannel() {
    val name = "Icon pack provider"
    val importance = NotificationManager.IMPORTANCE_DEFAULT
    val channel = NotificationChannel(CHANNEL_ID, name, importance)
    val notificationManager = getSystemService(NotificationManager::class.java)
    notificationManager.createNotificationChannel(channel)
  }

  override fun onBind(intent: Intent?) = null
}

class BootReceiver : BroadcastReceiver() {

  override fun onReceive(context: Context, intent: Intent?) {
    when (intent?.action) {
      Intent.ACTION_BOOT_COMPLETED,
      Intent.ACTION_LOCKED_BOOT_COMPLETED -> KeepAliveService.startForeground(context)
    }
  }
}
