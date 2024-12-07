package com.richardluo.globalIconPack.iconPack

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat

private const val CHANNEL_ID: String = "IconPackProvider"

class KeepAliveService : Service() {

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    createNotificationChannel()
    val builder: NotificationCompat.Builder =
      NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("The app is providing icon pack to hooked apps")
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setAutoCancel(true)
    startForeground(1, builder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
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
    if (intent?.action == Intent.ACTION_BOOT_COMPLETED)
      context.startForegroundService(Intent(context, KeepAliveService::class.java))
  }
}
