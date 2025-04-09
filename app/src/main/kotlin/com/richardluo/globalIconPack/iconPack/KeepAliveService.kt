package com.richardluo.globalIconPack.iconPack

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import com.richardluo.globalIconPack.R
import com.richardluo.globalIconPack.utils.log

private const val CHANNEL_ID: String = "IconPackProvider"

class KeepAliveService : Service() {

  companion object {
    fun startForeground(context: Context) {
      try {
        context.startForegroundService(Intent(context, KeepAliveService::class.java))
      } catch (e: Exception) {
        log(e)
      }
    }

    @SuppressLint("ImplicitSamInstance")
    fun stopForeground(context: Context) {
      context.stopService(Intent(context, KeepAliveService::class.java))
    }
  }

  override fun onCreate() {
    createNotificationChannel()
    val builder: Notification.Builder =
      Notification.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle("Providing icon pack to hooked apps")
        .setAutoCancel(true)
    startForeground(1, builder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST)
  }

  private fun createNotificationChannel() {
    val name = "Icon pack provider"
    val importance = NotificationManager.IMPORTANCE_NONE
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
