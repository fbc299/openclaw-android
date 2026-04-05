package com.openclaw.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.util.Log

/**
 * BroadcastReceiver that starts the GatewayForegroundService on device boot.
 *
 * Only starts the service if the user had previously enabled auto-start
 * (stored in SharedPreferences).
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
        private const val PREFS_NAME = "openclaw_prefs"
        private const val KEY_AUTO_START = "gateway_auto_start"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                Log.d(TAG, "Received BOOT_COMPLETED")
                handleBoot(context)
            }
            Intent.ACTION_LOCKED_BOOT_COMPLETED -> {
                Log.d(TAG, "Received LOCKED_BOOT_COMPLETED")
                handleBoot(context)
            }
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                // App was updated - restart service if it should be running
                Log.d(TAG, "Received MY_PACKAGE_REPLACED")
                handleBoot(context)
            }
        }
    }

    private fun handleBoot(context: Context) {
        val prefs: SharedPreferences =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val autoStart = prefs.getBoolean(KEY_AUTO_START, false)

        if (!autoStart) {
            Log.d(TAG, "Auto-start not enabled, skipping")
            return
        }

        Log.d(TAG, "Starting GatewayForegroundService on boot")
        val serviceIntent = Intent(context, GatewayForegroundService::class.java).apply {
            putExtra("auto_start", true)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start service on boot", e)
        }
    }

    /**
     * Enable auto-start for the gateway service.
     * Call this when the user starts the gateway.
     */
    fun setAutoStart(context: Context, enabled: Boolean) {
        val prefs: SharedPreferences =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_AUTO_START, enabled).apply()
        Log.d(TAG, "Auto-start ${if (enabled) "enabled" else "disabled"}")
    }
}
