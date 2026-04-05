package com.openclaw.android

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import io.flutter.plugin.common.EventChannel

/**
 * Foreground service that manages the OpenClaw Gateway lifecycle.
 *
 * Responsibilities:
 *   - Keep the Gateway process running as a foreground service
 *   - Display persistent notification with status
 *   - Manage WakeLock to keep the CPU alive
 *   - Handle process start/stop/restart
 *   - Stream Gateway output to Flutter via EventChannel
 */
class GatewayForegroundService : Service() {

    companion object {
        const val TAG = "GatewayService"

        const val CHANNEL_ID = "openclaw_gateway"
        const val NOTIFICATION_ID = 1001

        // Action constants
        const val ACTION_START = "com.openclaw.android.ACTION_START_GATEWAY"
        const val ACTION_STOP = "com.openclaw.android.ACTION_STOP_GATEWAY"
        const val ACTION_RESTART = "com.openclaw.android.ACTION_RESTART_GATEWAY"

        // Intent extras
        const val EXTRA_NODE_PATH = "node_path"
        const val EXTRA_SERVER_JS = "server_js"
        const val EXTRA_ENV = "env"
        const val EXTRA_PORT = "port"

        // Gateway process ID
        var gatewayPid: Int = -1
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var processManager: ProcessManager? = null
    private var glibcRunner: GlibcRunner? = null
    private val handler = Handler(Looper.getMainLooper())

    // Current status
    private var isRunning = false
    private var gatewayPort = 18789

    // SharedPreferences for persistence
    private val prefs: SharedPreferences by lazy {
        getSharedPreferences("openclaw_prefs", Context.MODE_PRIVATE)
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        glibcRunner = GlibcRunner(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val nodePath = intent.getStringExtra(EXTRA_NODE_PATH)
                    ?: "${filesDir.absolutePath}/node/bin/node"
                val serverJs = intent.getStringExtra(EXTRA_SERVER_JS)
                    ?: "${filesDir.absolutePath}/gateway-server.js"
                val port = intent.getIntExtra(EXTRA_PORT, 18789)
                gatewayPort = port

                // Collect environment variables
                val env = intent.getStringArrayExtra(EXTRA_ENV)
                    ?.associate {
                        val (key, value) = it.split("=", limit = 2)
                        key to value
                    } ?: emptyMap()

                startGateway(nodePath, serverJs, env)
            }

            ACTION_STOP -> {
                stopGateway()
                stopForegroundCompat()
                stopSelf()
            }

            ACTION_RESTART -> {
                stopGateway()
                // Small delay before restarting
                handler.postDelayed({
                    val nodePath = intent.getStringExtra(EXTRA_NODE_PATH)
                        ?: "${filesDir.absolutePath}/node/bin/node"
                    val serverJs = intent.getStringExtra(EXTRA_SERVER_JS)
                        ?: "${filesDir.absolutePath}/gateway-server.js"
                    val port = intent.getIntExtra(EXTRA_PORT, 18789)

                    val env = intent.getStringArrayExtra(EXTRA_ENV)
                        ?.associate {
                            val (key, value) = it.split("=", limit = 2)
                            key to value
                        } ?: emptyMap()

                    startGateway(nodePath, serverJs, env)
                }, 1000)
            }

            else -> {
                // Default: just make it a foreground service (e.g., from BootReceiver)
                val notification = buildNotification("Ready", "Tap to configure")
                startForeground(NOTIFICATION_ID, notification)
            }
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopGateway()
        releaseWakeLock()
        super.onDestroy()
    }

    // === Notification Management ===

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "OpenClaw Gateway",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "OpenClaw Gateway service notifications"
                setShowBadge(false)
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, text: String): android.app.Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Stop action
        val stopIntent = Intent(this, GatewayForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentIntent(pendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop",
                stopPendingIntent
            )
            .setOngoing(true)
            .setShowWhen(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification(title: String, text: String) {
        val notification = buildNotification(title, text)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    // === Gateway Lifecycle ===

    private fun startGateway(nodePath: String, serverJs: String, env: Map<String, String>) {
        if (isRunning) {
            android.util.Log.w(TAG, "Gateway is already running")
            return
        }

        // Acquire wake lock
        acquireWakeLock()

        // Initialize process manager with event channel support
        processManager = ProcessManager(
            context = this,
            eventSinkWrapper = EventSinkWrapper(), // Not connected via EventChannel here
            glibcRunner = glibcRunner!!
        )

        val portArg = "--port=$gatewayPort"

        val pid = processManager!!.startProcess(
            command = nodePath,
            args = listOf(serverJs, portArg),
            env = env,
            workingDir = "${filesDir.absolutePath}",
            onStarted = { _ ->
                gatewayPid = -1 // ProcessManager tracks internally
                isRunning = true
                android.util.Log.d(TAG, "Gateway started, port=$gatewayPort")

                updateNotification(
                    "OpenClaw Gateway",
                    "Running on port $gatewayPort"
                )
            },
            onExit = { _, exitCode ->
                isRunning = false
                android.util.Log.d(TAG, "Gateway exited with code $exitCode")

                updateNotification(
                    "OpenClaw Gateway",
                    "Stopped (exit code: ${exitCode ?: "?"})"
                )

                releaseWakeLock()
            },
            onOutput = { _, line ->
                // Process output for status updates
                if (line.contains("Server running") || line.contains("listening on")) {
                    updateNotification(
                        "OpenClaw Gateway",
                        "Running on port $gatewayPort"
                    )
                }
                if (line.contains("error", ignoreCase = true) || line.contains("Error", ignoreCase = true)) {
                    // Just log errors, don't update notification for every error
                    android.util.Log.e(TAG, "Gateway error: $line")
                }
            }
        )

        if (pid < 0) {
            // Start failed
            releaseWakeLock()
            updateNotification("OpenClaw Gateway", "Failed to start")
        }
    }

    private fun stopGateway() {
        if (!isRunning) {
            android.util.Log.d(TAG, "Gateway is not running")
            return
        }

        android.util.Log.d(TAG, "Stopping gateway...")
        processManager?.stopAll()
        isRunning = false
        releaseWakeLock()

        updateNotification("OpenClaw Gateway", "Stopped")

        // Clear auto-start flag
        prefs.edit().putBoolean("gateway_auto_start", false).apply()
    }

    // === WakeLock Management ===

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) {
            android.util.Log.d(TAG, "WakeLock already held")
            return
        }

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "openclaw:GatewayWakeLock"
        ).apply {
            acquire(10 * 60 * 1000L /* 10 minutes timeout */)
        }

        android.util.Log.d(TAG, "WakeLock acquired")
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
            android.util.Log.d(TAG, "WakeLock released")
        }
        wakeLock = null
    }

    // === Foreground Compatibility ===

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    // === Helper: Start Service from Outside ===

    /**
     * Helper method to start this service from code.
     */
    companion object {
        fun start(context: Context, nodePath: String, serverJs: String, port: Int, env: Map<String, String> = emptyMap()) {
            val intent = Intent(context, GatewayForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_NODE_PATH, nodePath)
                putExtra(EXTRA_SERVER_JS, serverJs)
                putExtra(EXTRA_PORT, port)
                putExtra(EXTRA_ENV, env.map { "${it.key}=${it.value}" }.toTypedArray())
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, GatewayForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
