package com.tools.gamecontroller

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.annotation.RequiresApi

class GameControllerService : Service() {
    private val TAG = "GameControllerService"
    private lateinit var notificationManager: NotificationManager
    private val notificationId = 1

    // 前台服务类型常量
    companion object {
        const val FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE_VALUE = 1
        const val FOREGROUND_SERVICE_TYPE_DATA_SYNC_VALUE = 2
        const val FOREGROUND_SERVICE_TYPE_GAME_CONTROLLER =
            FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE_VALUE or FOREGROUND_SERVICE_TYPE_DATA_SYNC_VALUE
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")

        // 显示前台通知
        val notification = createNotification("正在连接游戏手柄...")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ 使用前台服务类型
            startForeground(notificationId, notification, FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            // Android 11 使用前台服务（无类型）
            startForeground(notificationId, notification)
        }

        // 返回 START_STICKY 以便服务被杀死后可以自动重启
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
    }

    override fun onBind(intent: Intent): IBinder? {
        return ServiceBinder()
    }

    inner class ServiceBinder : Binder(), IBinder {
        fun getService(): GameControllerService = this@GameControllerService
    }

    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            "game_controller_channel",
            "Game Controller Service",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(serviceChannel)
    }

    fun updateNotification(text: String) {
        val notification = createNotification(text)
        notificationManager.notify(notificationId, notification)
    }

    private fun createNotification(text: String): Notification {
        return NotificationCompat.Builder(this, "game_controller_channel")
            .setContentTitle("蓝牙手柄模拟器")
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()
    }
}