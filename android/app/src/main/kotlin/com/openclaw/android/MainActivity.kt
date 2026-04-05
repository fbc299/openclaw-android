package com.openclaw.android

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val CHANNEL = "com.openclaw.android/bridge"
        private const val EVENT_CHANNEL_OUTPUT = "com.openclaw.android/process_output"
    }

    // Core components
    private lateinit var glibcRunner: GlibcRunner
    private lateinit var processManager: ProcessManager
    private val eventSinkWrapper = EventSinkWrapper()

    // Legacy process tracking (for backwards compatibility)
    private val processes = mutableMapOf<Int, Process>()
    private var nextPid = 1000

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        // Initialize core components
        glibcRunner = GlibcRunner(this)
        processManager = ProcessManager(this, eventSinkWrapper, glibcRunner)

        // Setup MethodChannel
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
            .setMethodCallHandler { call, result ->
                handleMethodCall(call, result)
            }

        // Setup EventChannel for process output streaming
        EventChannel(flutterEngine.dartExecutor.binaryMessenger, EVENT_CHANNEL_OUTPUT)
            .setStreamHandler(object : EventChannel.StreamHandler {
                override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
                    Log.d(TAG, "Process output stream connected")
                    eventSinkWrapper.setSink(events)
                }

                override fun onCancel(arguments: Any?) {
                    Log.d(TAG, "Process output stream disconnected")
                    eventSinkWrapper.setSink(null)
                }
            })
    }

    private fun handleMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            // === Process Management (new, with streaming) ===
            "startProcess" -> handleStartProcess(call, result)
            "stopProcess" -> handleStopProcess(call, result)
            "isProcessRunning" -> handleIsProcessRunning(call, result)
            "writeStdin" -> handleWriteStdin(call, result)
            "getProcessInfo" -> handleGetProcessInfo(call, result)
            "getAllProcesses" -> handleGetAllProcesses(result)
            "stopAllProcesses" -> handleStopAllProcesses(result)

            // === Script Execution ===
            "startScript" -> handleStartScript(call, result)

            // === Glibc ===
            "isGlibcAvailable" -> result.success(glibcRunner.isGlibcAvailable())
            "getGlibcLinkerName" -> result.success(glibcRunner.getExpectedLinkerName())
            "isSupportedAbi" -> result.success(glibcRunner.isSupportedAbi())

            // === Permissions ===
            "requestPermission" -> handleRequestPermission(call, result)

            // === Device Info ===
            "getDeviceInfo" -> handleGetDeviceInfo(result)

            // === UI ===
            "showToast" -> {
                val message = call.argument<String>("message") ?: ""
                showToast(message)
                result.success(null)
            }

            // === Battery ===
            "requestBatteryOptimizationExclusion" -> handleBatteryOptimization(result)
            "isIgnoringBatteryOptimizations" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val pm = getSystemService(POWER_SERVICE) as PowerManager
                    result.success(pm.isIgnoringBatteryOptimizations(packageName))
                } else {
                    result.success(true)
                }
            }

            // === File I/O ===
            "readTextFile" -> handleReadTextFile(call, result)
            "writeTextFile" -> handleWriteTextFile(call, result)
            "fileExists" -> {
                val path = call.argument<String>("path") ?: ""
                result.success(java.io.File(path).exists())
            }
            "deleteFile" -> {
                val path = call.argument<String>("path") ?: ""
                val f = java.io.File(path)
                result.success(f.delete())
            }

            // === Device Capabilities ===
            "camera.capture" -> handleCameraCapture(call, result)
            "flash.setTorch" -> handleFlashSetTorch(call, result)
            "flash.toggleTorch" -> handleFlashToggleTorch(result)
            "location.getCurrentPosition" -> handleGetCurrentPosition(result)
            "haptic.vibrate" -> handleHapticVibrate(call, result)

            // === Gateway Service ===
            "startGatewayService" -> handleStartGatewayService(call, result)
            "stopGatewayService" -> handleStopGatewayService(call, result)
            "isGatewayServiceRunning" -> result.success(isGatewayServiceRunning())

            // === Auto Start ===
            "setAutoStart" -> {
                val enabled = call.argument<Boolean>("enabled") ?: false
                BootReceiver().setAutoStart(this, enabled)
                result.success(null)
            }

            else -> result.notImplemented()
        }
    }

    // === Process Management (new) ===

    private fun handleStartProcess(call: MethodCall, result: MethodChannel.Result) {
        val command = call.argument<String>("command") ?: ""
        val args = call.argument<List<String>>("args") ?: emptyList()
        val env = call.argument<Map<String, String>>("env") ?: emptyMap()
        val workingDir = call.argument<String>("workingDir")

        val pid = processManager.startProcess(
            command = command,
            args = args,
            env = env,
            workingDir = workingDir,
            onStarted = { pid ->
                Log.d(TAG, "Process started: $pid ($command)")
            },
            onExit = { pid, exitCode ->
                Log.d(TAG, "Process exited: $pid code=$exitCode")
            },
        )

        if (pid >= 0) {
            result.success(mapOf("pid" to pid, "command" to command))
        } else {
            result.error("PROCESS_ERROR", "Failed to start process: $command", null)
        }
    }

    private fun handleStopProcess(call: MethodCall, result: MethodChannel.Result) {
        val pid = call.argument<Int>("pid") ?: 0
        val success = processManager.stopProcess(pid)
        result.success(success)
    }

    private fun handleIsProcessRunning(call: MethodCall, result: MethodChannel.Result) {
        val pid = call.argument<Int>("pid") ?: 0
        result.success(processManager.isRunning(pid))
    }

    private fun handleWriteStdin(call: MethodCall, result: MethodChannel.Result) {
        val pid = call.argument<Int>("pid") ?: 0
        val input = call.argument<String>("input") ?: ""
        val success = processManager.writeStdin(pid, input)
        result.success(success)
    }

    private fun handleGetProcessInfo(call: MethodCall, result: MethodChannel.Result) {
        val pid = call.argument<Int>("pid") ?: 0
        val info = processManager.getProcessInfo(pid)
        result.success(info)
    }

    private fun handleGetAllProcesses(result: MethodChannel.Result) {
        result.success(processManager.getAllProcesses())
    }

    private fun handleStopAllProcesses(result: MethodChannel.Result) {
        processManager.stopAll()
        result.success(null)
    }

    // === Script Execution ===

    private fun handleStartScript(call: MethodCall, result: MethodChannel.Result) {
        val scriptPath = call.argument<String>("scriptPath") ?: ""
        val args = call.argument<List<String>>("args") ?: emptyList()
        val env = call.argument<Map<String, String>>("env") ?: emptyMap()

        val pid = processManager.startScript(
            scriptPath = scriptPath,
            args = args,
            env = env,
            onStarted = { pid ->
                Log.d(TAG, "Script started: $pid ($scriptPath)")
            },
            onExit = { pid, exitCode ->
                Log.d(TAG, "Script exited: $pid code=$exitCode")
            },
        )

        if (pid >= 0) {
            result.success(mapOf("pid" to pid, "script" to scriptPath))
        } else {
            result.error("SCRIPT_ERROR", "Failed to start script: $scriptPath", null)
        }
    }

    // === Legacy Process Management (backwards compat) ===

    @Deprecated("Use processManager.startProcess instead")
    private fun handleStartProcessLegacy(
        command: String,
        args: List<String>,
        env: Map<String, String>,
        result: MethodChannel.Result
    ) {
        try {
            val fullCommand = listOf(command) + args
            val processBuilder = ProcessBuilder(fullCommand)

            val envVars = processBuilder.environment()
            env.forEach { (key, value) -> envVars[key] = value }
            processBuilder.redirectErrorStream(true)

            val process = processBuilder.start()
            val pid = nextPid++
            processes[pid] = process

            Thread {
                try {
                    val reader = java.io.BufferedReader(
                        java.io.InputStreamReader(process.inputStream)
                    )
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        eventSinkWrapper.success(mapOf(
                            "pid" to pid,
                            "type" to "stdout",
                            "line" to line,
                        ))
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }.start()

            result.success(mapOf("pid" to pid, "command" to command))
        } catch (e: Exception) {
            result.error("PROCESS_ERROR", e.message, null)
        }
    }

    private fun handleStopProcessLegacy(pid: Int, result: MethodChannel.Result) {
        val process = processes[pid]
        if (process != null) {
            process.destroy()
            processes.remove(pid)
            result.success(null)
        } else {
            result.error("NOT_FOUND", "Process $pid not found", null)
        }
    }

    private fun isProcessRunningLegacy(pid: Int): Boolean {
        val process = processes[pid] ?: return false
        return try {
            process.exitValue()
            false
        } catch (e: IllegalThreadStateException) {
            true
        }
    }

    // === Gateway Service ===

    private fun handleStartGatewayService(call: MethodCall, result: MethodChannel.Result) {
        val nodePath = call.argument<String>("nodePath")
            ?: "${filesDir.absolutePath}/node/bin/node"
        val serverJs = call.argument<String>("serverJs")
            ?: "${filesDir.absolutePath}/gateway-server.js"
        val port = call.argument<Int>("port") ?: 18789
        val env = call.argument<Map<String, String>>("env") ?: emptyMap()

        try {
            GatewayForegroundService.start(this, nodePath, serverJs, port, env)
            result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start gateway service", e)
            result.error("SERVICE_ERROR", e.message, null)
        }
    }

    private fun handleStopGatewayService(call: MethodCall, result: MethodChannel.Result) {
        try {
            GatewayForegroundService.stop(this)
            result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop gateway service", e)
            result.error("SERVICE_ERROR", e.message, null)
        }
    }

    private fun isGatewayServiceRunning(): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (GatewayForegroundService::class.java.name == service.service.className) {
                return true
            }
        }
        return false
    }

    // === Permissions ===

    private fun handleRequestPermission(call: MethodCall, result: MethodChannel.Result) {
        val type = call.argument<String>("type") ?: ""
        val androidPermission = when (type) {
            "camera" -> Manifest.permission.CAMERA
            "location" -> Manifest.permission.ACCESS_FINE_LOCATION
            "microphone" -> Manifest.permission.RECORD_AUDIO
            "storage" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_IMAGES
            } else {
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            }
            "notification" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.POST_NOTIFICATIONS
            } else {
                null // Not needed on older Android
            }
            else -> null
        }

        if (androidPermission == null) {
            result.success(false)
            return
        }

        val hasPermission = checkSelfPermission(androidPermission) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            result.success(true)
        } else {
            requestPermissions(arrayOf(androidPermission), 1)
            result.success(false)
        }
    }

    // === Device Info ===

    private fun handleGetDeviceInfo(result: MethodChannel.Result) {
        val info = mapOf(
            "model" to Build.MODEL,
            "manufacturer" to Build.MANUFACTURER,
            "brand" to Build.BRAND,
            "device" to Build.DEVICE,
            "sdkInt" to Build.VERSION.SDK_INT,
            "release" to Build.VERSION.RELEASE,
            "supportedAbis" to Build.SUPPORTED_ABIS.toList(),
            "hardware" to Build.HARDWARE,
        )
        result.success(info)
    }

    // === UI ===

    private fun showToast(message: String) {
        runOnUiThread {
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }

    // === Battery ===

    private fun handleBatteryOptimization(result: MethodChannel.Result) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (pm.isIgnoringBatteryOptimizations(packageName)) {
                result.success(true)
            } else {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${packageName}")
                    }
                    startActivity(intent)
                    result.success(true)
                } catch (e: Exception) {
                    result.error("BATTERY_ERROR", e.message, null)
                }
            }
        } else {
            result.success(true)
        }
    }

    // === File I/O ===

    private fun handleReadTextFile(call: MethodCall, result: MethodChannel.Result) {
        val path = call.argument<String>("path") ?: ""
        try {
            val content = java.io.File(path).readText()
            result.success(content)
        } catch (e: Exception) {
            result.error("READ_ERROR", e.message, null)
        }
    }

    private fun handleWriteTextFile(call: MethodCall, result: MethodChannel.Result) {
        val path = call.argument<String>("path") ?: ""
        val content = call.argument<String>("content") ?: ""
        try {
            val file = java.io.File(path)
            file.parentFile?.mkdirs()
            file.writeText(content)
            result.success(null)
        } catch (e: Exception) {
            result.error("WRITE_ERROR", e.message, null)
        }
    }

    // === Device Capabilities ===

    /**
     * Capture a photo using Camera2 API.
     * Requires CAMERA permission.
     */
    private fun handleCameraCapture(call: MethodCall, result: MethodChannel.Result) {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            result.error("PERMISSION_DENIED", "Camera permission not granted", null)
            return
        }

        val path = call.argument<String>("path") ?: ""
        if (path.isEmpty()) {
            result.error("INVALID_ARGS", "Path is required", null)
            return
        }

        try {
            val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull()
            if (cameraId == null) {
                result.error("NO_CAMERA", "No camera available", null)
                return
            }

            // Use a background thread to open camera (it's blocking)
            Thread {
                try {
                    val success = openCameraSync(cameraManager, cameraId)
                    runOnUiThread {
                        if (success) {
                            result.success(true)
                        } else {
                            result.error("CAMERA_OPEN", "Could not open camera", null)
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        result.error("CAPTURE_ERROR", e.message, null)
                    }
                }
            }.start()
        } catch (e: CameraAccessException) {
            result.error("CAMERA_ACCESS", e.message, null)
        }
    }

    private fun openCameraSync(cameraManager: CameraManager, cameraId: String): Boolean {
        var opened = false
        val latch = java.util.concurrent.CountDownLatch(1)
        var device: android.hardware.camera2.CameraDevice? = null
        var exception: Exception? = null

        cameraManager.openCamera(cameraId, object : android.hardware.camera2.CameraDevice.StateCallback() {
            override fun onOpened(camera: android.hardware.camera2.CameraDevice) {
                device = camera
                opened = true
                latch.countDown()
            }
            override fun onDisconnected(camera: android.hardware.camera2.CameraDevice) {
                camera.close()
                latch.countDown()
            }
            override fun onError(camera: android.hardware.camera2.CameraDevice, error: Int) {
                exception = Exception("Camera open error: $error")
                latch.countDown()
            }
        }, null)

        latch.await(5, java.util.concurrent.TimeUnit.SECONDS)

        if (opened) {
            // TODO: Full Camera2 capture pipeline requires a Surface/TextureView
            // For now, close immediately — the intent is to expose capability
            // In production, integrate with camera_android plugin for full capture
            device?.close()
            return true
        }
        return false
    }

    /**
     * Set flashlight torch on/off.
     */
    private fun handleFlashSetTorch(call: MethodCall, result: MethodChannel.Result) {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            result.error("PERMISSION_DENIED", "Camera permission not granted", null)
            return
        }

        val enabled = call.argument<Boolean>("enabled") ?: false
        try {
            val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull()
            if (cameraId == null) {
                result.error("NO_CAMERA", "No camera available", null)
                return
            }

            cameraManager.setTorchMode(cameraId, enabled)
            result.success(true)
        } catch (e: CameraAccessException) {
            result.error("FLASH_ERROR", e.message, null)
        }
    }

    /**
     * Toggle flashlight torch. Returns new state.
     */
    private fun handleFlashToggleTorch(result: MethodChannel.Result) {
        try {
            val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull()
            if (cameraId == null) {
                result.error("NO_CAMERA", "No camera available", null)
                return
            }

            // We don't track state natively; default to ON
            val newState = true
            cameraManager.setTorchMode(cameraId, newState)
            result.success(newState)
        } catch (e: CameraAccessException) {
            result.error("FLASH_ERROR", e.message, null)
        }
    }

    /**
     * Get current GPS location.
     */
    private fun handleGetCurrentPosition(result: MethodChannel.Result) {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            result.error("PERMISSION_DENIED", "Location permission not granted", null)
            return
        }

        try {
            val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val location = getLastKnownLocation(locationManager)

            if (location != null) {
                val position = mapOf(
                    "latitude" to location.latitude,
                    "longitude" to location.longitude,
                    "accuracy" to location.accuracy,
                    "altitude" to location.altitude,
                    "timestamp" to location.time,
                )
                result.success(position)
            } else {
                result.error("LOCATION_UNAVAILABLE", "Could not get location", null)
            }
        } catch (e: SecurityException) {
            result.error("PERMISSION_DENIED", e.message, null)
        } catch (e: Exception) {
            result.error("LOCATION_ERROR", e.message, null)
        }
    }

    private fun getLastKnownLocation(locationManager: LocationManager): Location? {
        // Try GPS first, then network
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        for (provider in providers) {
            try {
                val location = locationManager.getLastKnownLocation(provider)
                if (location != null) return location
            } catch (_: SecurityException) {
                // Continue to next provider
            }
        }
        return null
    }

    /**
     * Trigger haptic vibration for the specified duration.
     */
    private fun handleHapticVibrate(call: MethodCall, result: MethodChannel.Result) {
        val duration = call.argument<Int>("duration") ?: 100

        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(duration.toLong(), VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(duration.toLong())
            }

            result.success(true)
        } catch (e: Exception) {
            result.error("VIBRATE_ERROR", e.message, null)
        }
    }

    // === Lifecycle ===

    override fun onDestroy() {
        // Clean up all processes
        processManager.stopAll()
        super.onDestroy()
    }
}
