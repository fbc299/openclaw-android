package com.openclaw.android

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Encapsulates glibc linker logic for running Node.js binaries on Android.
 *
 * Strategy:
 *   1. Prefer running via glibc ld-linux-aarch64.so.1 linker with --library-path
 *   2. Fall back to standard ProcessBuilder execution if glibc linker fails
 *
 * glibc linker command:
 *   /data/data/package/files/node/ld-linux-aarch64.so.1 \
 *     --library-path /data/data/package/files/node/lib \
 *     --insecure-rpath \
 *     /data/data/package/files/node/bin/node
 */
class GlibcRunner(private val context: Context) {

    companion object {
        private const val TAG = "GlibcRunner"

        // glibc linker paths relative to the app's files directory
        private const val GLIBC_LINKER = "ld-linux-aarch64.so.1"
        private const val NODE_BIN = "bin/node"
        private const val GLIBC_LIB = "lib"

        private val GLIBC_PRESETS = mapOf(
            "arm64-v8a" to GLIBC_LINKER,
            "armeabi-v7a" to "ld-linux-armhf.so.3",
            "x86_64" to "ld-linux-x86-64.so.2",
            "x86" to "ld-linux.so.2",
        )
    }

    private val nodeDir: File
        get() = File(context.filesDir, "node")

    private val linkerPath: File
        get() = File(nodeDir, GLIBC_LINKER)

    private val nodeBinPath: File
        get() = File(nodeDir, NODE_BIN)

    private val libPath: File
        get() = File(nodeDir, GLIBC_LIB)

    /**
     * Check if glibc linker is available in the files directory.
     */
    fun isGlibcAvailable(): Boolean {
        return linkerPath.exists() && linkerPath.canExecute()
    }

    /**
     * Get the glibc linker command line for running a given binary.
     * Returns null if glibc linker is not available.
     */
    fun getGlibcCommand(binary: String, args: List<String> = emptyList()): List<String>? {
        if (!isGlibcAvailable()) {
            Log.w(TAG, "glibc linker not found at ${linkerPath.absolutePath}")
            return null
        }

        if (!nodeBinPath.exists() && binary != nodeBinPath.absolutePath) {
            // The binary we want to run is not the node binary we know about,
            // but we can still try the glibc linker if the binary exists
            if (!File(binary).exists()) {
                Log.w(TAG, "Binary not found: $binary")
                return null
            }
        }

        val baseDir = nodeDir.absolutePath
        return listOf(
            linkerPath.absolutePath,
            "--library-path", "$baseDir/$GLIBC_LIB",
            "--insecure-rpath",
            binary,
        ) + args
    }

    /**
     * Run a command. First try glibc linker, then fall back to standard execution.
     * Returns a ProcessBuilder configured for the best available method.
     */
    fun createProcessBuilder(
        command: String,
        args: List<String> = emptyList(),
        env: Map<String, String> = emptyMap()
    ): ProcessBuilder {
        // Strategy 1: If running node.js, try glibc linker
        val isNodeCommand = command.contains("node") ||
                command.endsWith("node") ||
                command == nodeBinPath.absolutePath ||
                File(command).name == "node"

        if (isNodeCommand) {
            val targetPath = when {
                File(command).exists() -> command
                else -> nodeBinPath.absolutePath
            }

            val glibcCommand = getGlibcCommand(targetPath, args)
            if (glibcCommand != null) {
                Log.d(TAG, "Using glibc linker: ${glibcCommand.joinToString(" ")}")
                val pb = ProcessBuilder(glibcCommand)
                setupEnvironment(pb, env, File(targetPath).parentFile?.absolutePath)
                return pb
            } else {
                Log.d(TAG, "glibc linker unavailable, falling back to standard execution")
            }
        }

        // Strategy 2: Standard ProcessBuilder
        val fullCommand = listOf(command) + args
        Log.d(TAG, "Standard execution: ${fullCommand.joinToString(" ")}")
        val pb = ProcessBuilder(fullCommand)
        setupEnvironment(pb, env, File(command).parentFile?.absolutePath)
        return pb
    }

    /**
     * Run a shell script: chmod +x then execute.
     * Returns a ProcessBuilder for running the script.
     */
    fun createScriptProcessBuilder(
        scriptPath: String,
        args: List<String> = emptyList(),
        env: Map<String, String> = emptyMap()
    ): ProcessBuilder {
        val script = File(scriptPath)
        if (!script.exists()) {
            throw IllegalArgumentException("Script not found: $scriptPath")
        }

        // chmod +x the script
        try {
            // First try running chmod directly
            val chmodResult = ProcessBuilder("chmod", "+x", script.absolutePath)
                .redirectErrorStream(true)
                .start()
                .waitFor()

            if (chmodResult != 0) {
                // Alternative: use file.setExecutable(true)
                script.setExecutable(true, false)
                Log.d(TAG, "Used setExecutable() for $scriptPath")
            } else {
                Log.d(TAG, "chmod +x succeeded for $scriptPath")
            }
        } catch (e: Exception) {
            // Fallback to setExecutable
            script.setExecutable(true, false)
            Log.w(TAG, "chmod failed, using setExecutable: ${e.message}")
        }

        // Prefer running via glibc sh/bash interpreter if available
        val interpreter = when {
            File("/system/bin/sh").exists() -> "/system/bin/sh"
            File("/system/bin/bash").exists() -> "/system/bin/bash"
            else -> "sh"
        }

        val fullCommand = listOf(interpreter, script.absolutePath) + args
        val pb = ProcessBuilder(fullCommand)
        setupEnvironment(pb, env, script.parentFile?.absolutePath)
        return pb
    }

    /**
     * Setup environment variables for a ProcessBuilder.
     */
    private fun setupEnvironment(
        pb: ProcessBuilder,
        env: Map<String, String>,
        workingDir: String? = null
    ) {
        val envVars = pb.environment()
        env.forEach { (key, value) ->
            envVars[key] = value
        }

        // Set working directory if provided
        if (workingDir != null) {
            pb.directory(File(workingDir))
        }

        // Merge stderr into stdout for easier reading
        pb.redirectErrorStream(true)
    }

    /**
     * Check if the device ABI is supported by our glibc linker.
     */
    fun isSupportedAbi(): Boolean {
        val abis = android.os.Build.SUPPORTED_ABIS
        return abis.any { GLIBC_PRESETS.containsKey(it) }
    }

    /**
     * Get the expected linker name for the current device ABI.
     */
    fun getExpectedLinkerName(): String {
        val abis = android.os.Build.SUPPORTED_ABIS
        for (abi in abis) {
            GLIBC_PRESETS[abi]?.let { return it }
        }
        return GLIBC_LINKER // default
    }
}
