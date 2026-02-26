package com.kilu.pocketagent.core.hub.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.kilu.pocketagent.MainActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class HubRuntimeService : LifecycleService() {

    companion object {
        const val CHANNEL_ID = "HubRuntimeChannel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_RESUME = "ACTION_RESUME"
    }

    private var isRunning = false
    private var isPaused = false
    private var loopJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        
        when (intent?.action) {
            ACTION_START -> startService()
            ACTION_STOP -> stopService()
            ACTION_PAUSE -> pauseService()
            ACTION_RESUME -> resumeService()
        }
        
        return START_STICKY
    }

    private fun startService() {
        if (isRunning) return
        isRunning = true
        isPaused = false
        Log.d("HubRuntimeService", "Starting Hub Always-On Service")
        
        startForeground(NOTIFICATION_ID, buildNotification("Hub is running 24/7", "IDLE"))
        
        val store = com.kilu.pocketagent.core.storage.DeviceProfileStore(this)
        val apiClient = com.kilu.pocketagent.core.network.ApiClient(store)
        val loop = HubRuntimeLoop(this, apiClient)
        loop.onStateChanged = { state, msg ->
            if (!isPaused) {
                updateNotification(msg, state.name)
            }
        }
        
        loopJob = lifecycleScope.launch {
            loop.startLoop { isRunning && !isPaused }
        }
    }

    private fun pauseService() {
        isPaused = true
        updateNotification("Hub is paused")
        Log.d("HubRuntimeService", "Service PAUSED")
    }

    private fun resumeService() {
        isPaused = false
        updateNotification("Hub is running 24/7")
        Log.d("HubRuntimeService", "Service RESUMED")
    }

    private fun stopService() {
        Log.d("HubRuntimeService", "Stopping Hub Always-On Service")
        isRunning = false
        loopJob?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun updateNotification(text: String, stateText: String = "Active") {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification(text, stateText))
    }

    private fun buildNotification(text: String, stateText: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val pauseIntent = PendingIntent.getService(this, 1, Intent(this, HubRuntimeService::class.java).apply { action = ACTION_PAUSE }, PendingIntent.FLAG_IMMUTABLE)
        val resumeIntent = PendingIntent.getService(this, 2, Intent(this, HubRuntimeService::class.java).apply { action = ACTION_RESUME }, PendingIntent.FLAG_IMMUTABLE)
        val stopIntent = PendingIntent.getService(this, 3, Intent(this, HubRuntimeService::class.java).apply { action = ACTION_STOP }, PendingIntent.FLAG_IMMUTABLE)
        
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("KiLu Hub")
            .setContentText("$text [$stateText]")
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            
        if (isPaused) {
            builder.addAction(android.R.drawable.ic_media_play, "Resume", resumeIntent)
        } else {
            builder.addAction(android.R.drawable.ic_media_pause, "Pause", pauseIntent)
        }
        builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopIntent)
            
        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Hub Runtime",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Always-On executions for the Hub device"
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }
}
