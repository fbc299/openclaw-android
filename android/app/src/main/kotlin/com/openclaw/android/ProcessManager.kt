package com.openclaw.android

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import io.flutter.plugin.common.EventChannel
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Manages process lifecycle with real-time output streaming back to Dart
 * via EventChannel.
 *
 * Features:
 *   - Track multiple concurrent processes by ID
 *   - Stream stdout/stderr lines to Flutter in real-time
 *   - Support writing stdin to running processes
 *   - Graceful and forceful termination
 *   - Exit code notification
 */
class ProcessManager(
    private val context: Context,
    private val eventSinkWrapper: EventSinkWrapper,
    private val glibcRunner: GlibcRunner
) {

    companion object {
        private const val TAG = "ProcessManager"
    }

    /** Holds running process and its metadata */
    data class ManagedProcess(
        val id: Int,
        val process: Process,
        val command: String,
        val startTime: Long = System.currentTimeMillis(),
        var exitCode: Int? = null,
    )

    private val processes = ConcurrentHashMap<Int, ManagedProcess>()
    private val pidCounter = AtomicInteger(1000)

    /**
     * Start a process and begin streaming output.
     *
     * Returns the process ID on success, or -1 on failure.
     */
    fun startProcess(
        command: String,
        args: List<String> = emptyList(),
        env: Map<String, String> = emptyMap(),
        workingDir: String? = null,
        redirectErrorStream: Boolean = true,
        onStarted: (Int) -> Unit = {},
        onExit: (Int, Int?) -> Unit = { _, _ -> },
        onStdout: (Int, String) -> Unit = { _, _ -> },
        onStderr: (Int, String) -> Unit = { _, _ -> },
        onOutput: (Int, String) -> Unit = { _, _ -> },
    ): Int {
        try {
            val processBuilder = glibcRunner.createProcessBuilder(command, args, env)
            processBuilder.redirectErrorStream(redirectErrorStream)

            if (workingDir != null) {
                processBuilder.directory(java.io.File(workingDir))
            }

            // Don't re-apply workingDir (glibcRunner already sets it)
            // but if workingDir is passed explicitly here, override it
            if (workingDir != null) {
                processBuilder.directory(java.io.File(workingDir))
            }

            val process = processBuilder.start()
            val pid = pidCounter.getAndIncrement()

            val managedProcess = ManagedProcess(
                id = pid,
                process = process,
                command = command,
            )
            processes[pid] = managedProcess

            Log.d(TAG, "Started process $pid: $command ${args.joinToString(" ")}")

            // Start output reader threads
            startOutputReaders(pid, process, redirectErrorStream, onOutput, onStdout, onStderr)

            // Watch for exit
            startExitWatcher(pid, process, onExit)

            onStarted(pid)
            return pid
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start process: $command", e)
            return -1
        }
    }

    /**
     * Start a shell script (handles chmod +x automatically).
     */
    fun startScript(
        scriptPath: String,
        args: List<String> = emptyList(),
        env: Map<String, String> = emptyMap(),
        onStarted: (Int) -> Unit = {},
        onExit: (Int, Int?) -> Unit = { _, _ -> },
        onOutput: (Int, String) -> Unit = { _, _ -> },
    ): Int {
        return try {
            val processBuilder = glibcRunner.createScriptProcessBuilder(
                scriptPath = scriptPath,
                args = args,
                env = env
            )

            val process = processBuilder.start()
            val pid = pidCounter.getAndIncrement()

            processes[pid] = ManagedProcess(
                id = pid,
                process = process,
                command = scriptPath,
            )

            Log.d(TAG, "Started script $pid: $scriptPath")

            startOutputReaders(pid, process, true, onOutput)
            startExitWatcher(pid, process, onExit)

            onStarted(pid)
            pid
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start script: $scriptPath", e)
            -1
        }
    }

    /**
     * Send input to a running process's stdin.
     */
    fun writeStdin(pid: Int, input: String): Boolean {
        val managedProcess = processes[pid] ?: return false
        return try {
            managedProcess.process.outputStream.use { os ->
                OutputStreamWriter(os).use { writer ->
                    writer.write(input)
                    writer.flush()
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write stdin to process $pid", e)
            false
        }
    }

    /**
     * Check if a process is still running.
     */
    fun isRunning(pid: Int): Boolean {
        val managedProcess = processes[pid] ?: return false
        return try {
            managedProcess.process.exitValue()
            false // exited
        } catch (e: IllegalThreadStateException) {
            true // still running
        }
    }

    /**
     * Get process info.
     */
    fun getProcessInfo(pid: Int): Map<String, Any?>? {
        val managedProcess = processes[pid] ?: return null
        return mapOf(
            "pid" to managedProcess.id,
            "command" to managedProcess.command,
            "running" to isRunning(pid),
            "exitCode" to managedProcess.exitCode,
            "startTime" to managedProcess.startTime,
            "uptimeMillis" to (System.currentTimeMillis() - managedProcess.startTime),
        )
    }

    /**
     * Get all running processes.
     */
    fun getAllProcesses(): List<Map<String, Any?>> {
        return processes.values.map {
            getProcessInfo(it.id)!!
        }
    }

    /**
     * Gracefully stop a process (SIGTERM).
     */
    fun stopProcess(pid: Int): Boolean {
        val managedProcess = processes[pid] ?: return false
        return try {
            managedProcess.process.destroy()
            processes.remove(pid)
            Log.d(TAG, "Stopped process $pid (destroy)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop process $pid", e)
            false
        }
    }

    /**
     * Forcefully kill a process (SIGKEM).
     */
    fun forceKill(pid: Int): Boolean {
        val managedProcess = processes[pid] ?: return false
        return try {
            managedProcess.process.destroyForcibly()
            processes.remove(pid)
            Log.d(TAG, "Force killed process $pid")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to force kill process $pid", e)
            false
        }
    }

    /**
     * Stop all running processes.
     */
    fun stopAll() {
        val pids = processes.keys.toList()
        pids.forEach { pid ->
            stopProcess(pid)
        }
        Log.d(TAG, "Stopped all ${pids.size} processes")
    }

    // === Internal: Output Streaming ===

    /**
     * Start threads to read stdout and stderr from the process.
     * Lines are streamed to Dart via EventChannel.
     */
    private fun startOutputReaders(
        pid: Int,
        process: Process,
        mergeStderr: Boolean,
        onOutput: (Int, String) -> Unit = { _, _ -> },
        onStdout: (Int, String) -> Unit = { _, _ -> },
        onStderr: (Int, String) -> Unit = { _, _ -> },
    ) {
        // Read stdout
        val stdoutThread = Thread {
            var reader: BufferedReader? = null
            try {
                reader = BufferedReader(InputStreamReader(process.inputStream))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val outputLine = line!!
                    Handler(Looper.getMainLooper()).post {
                        // Emit to EventChannel if available
                        eventSinkWrapper.success(mapOf(
                            "pid" to pid,
                            "type" to "stdout",
                            "line" to outputLine,
                        ))

                        // Also call Kotlin callbacks
                        onOutput(pid, outputLine)
                        onStdout(pid, outputLine)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reading stdout for $pid", e)
                if (e !is java.io.IOException || !e.message.isNullOrBlank() && e.message!!.contains("Stream closed").not()) {
                    Handler(Looper.getMainLooper()).post {
                        eventSinkWrapper.success(mapOf(
                            "pid" to pid,
                            "type" to "error",
                            "line" to "Read error: ${e.message}",
                        ))
                    }
                }
            } finally {
                reader?.close()
            }
        }.apply { isDaemon = true; name = "process-stdout-$pid" }
        stdoutThread.start()

        // Read stderr (only if not merged)
        if (!mergeStderr) {
            val stderrThread = Thread {
                var reader: BufferedReader? = null
                try {
                    reader = BufferedReader(InputStreamReader(process.errorStream))
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val errorLine = line!!
                        Handler(Looper.getMainLooper()).post {
                            eventSinkWrapper.success(mapOf(
                                "pid" to pid,
                                "type" to "stderr",
                                "line" to errorLine,
                            ))
                            onStderr(pid, errorLine)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading stderr for $pid", e)
                } finally {
                    reader?.close()
                }
            }.apply { isDaemon = true; name = "process-stderr-$pid" }
            stderrThread.start()
        }
    }

    /**
     * Watch for process exit and emit exit code.
     */
    private fun startExitWatcher(
        pid: Int,
        process: Process,
        onExit: (Int, Int?) -> Unit,
    ) {
        Thread {
            try {
                val exitCode = process.waitFor()
                val managedProcess = processes[pid]
                if (managedProcess != null) {
                    managedProcess.exitCode = exitCode
                }

                Handler(Looper.getMainLooper()).post {
                    // Emit exit event to EventChannel
                    eventSinkWrapper.success(mapOf(
                        "pid" to pid,
                        "type" to "exit",
                        "exitCode" to exitCode,
                    ))

                    // Call Kotlin callback
                    onExit(pid, exitCode)
                }

                Log.d(TAG, "Process $pid exited with code $exitCode")
            } catch (e: InterruptedException) {
                Log.w(TAG, "Process $pid interrupted", e)
                Thread.currentThread().interrupt()
            } finally {
                // Clean up from map
                processes.remove(pid)
            }
        }.apply { isDaemon = true; name = "process-exit-watcher-$pid" }.start()
    }
}

/**
 * Wrapper around EventChannel.EventSink that safely handles thread-bound disposal.
 * We store a reference that can be updated when the Dart side reconnects.
 */
class EventSinkWrapper {
    private var eventSink: EventChannel.EventSink? = null

    fun setSink(sink: EventChannel.EventSink?) {
        eventSink = sink
    }

    fun success(data: Any) {
        try {
            eventSink?.success(data)
        } catch (e: Exception) {
            Log.w("EventSinkWrapper", "Failed to emit event: ${e.message}")
        }
    }
}
