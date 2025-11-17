package com.iie.st10320489.stylu.utils

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import java.io.File

object SnapshotDebugger {
    private const val TAG = "SnapshotDebug"

    /**
     * Verify a snapshot file and log detailed information
     */
    fun verifySnapshot(context: Context, outfitId: Int): Boolean {
        Log.d(TAG, "")
        Log.d(TAG, "╔═══════════════════════════════════════════════════════")
        Log.d(TAG, "║ VERIFYING SNAPSHOT FOR OUTFIT $outfitId")
        Log.d(TAG, "╚═══════════════════════════════════════════════════════")
        Log.d(TAG, "")

        val file = File(context.filesDir, "outfit_$outfitId.png")

        Log.d(TAG, "FILE INFO:")
        Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.d(TAG, "Path: ${file.absolutePath}")
        Log.d(TAG, "Exists: ${file.exists()}")

        if (!file.exists()) {
            Log.e(TAG, "FILE DOES NOT EXIST")
            Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Log.d(TAG, "")
            return false
        }

        Log.d(TAG, "Size: ${file.length()} bytes")
        Log.d(TAG, "Can Read: ${file.canRead()}")
        Log.d(TAG, "Last Modified: ${java.util.Date(file.lastModified())}")
        Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.d(TAG, "")

        // Try to decode bitmap
        try {
            Log.d(TAG, "DECODING BITMAP...")
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)

            if (bitmap == null) {
                Log.e(TAG, "FAILED TO DECODE BITMAP - File may be corrupted")
                Log.d(TAG, "")
                return false
            }

            Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Log.d(TAG, "BITMAP DECODED SUCCESSFULLY")
            Log.d(TAG, "Width: ${bitmap.width}px")
            Log.d(TAG, "Height: ${bitmap.height}px")
            Log.d(TAG, "Config: ${bitmap.config}")
            Log.d(TAG, "Byte Count: ${bitmap.byteCount}")
            Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Log.d(TAG, "")

            bitmap.recycle()
            return true

        } catch (e: Exception) {
            Log.e(TAG, "ERROR DECODING BITMAP", e)
            Log.e(TAG, "Exception: ${e.message}")
            Log.e(TAG, "Stack trace: ${e.stackTraceToString()}")
            Log.d(TAG, "")
            return false
        }
    }

    /**
     * List all snapshot files in the app directory
     */
    fun listAllSnapshots(context: Context) {
        Log.d(TAG, "")
        Log.d(TAG, "╔═══════════════════════════════════════════════════════")
        Log.d(TAG, "║ ALL SNAPSHOT FILES")
        Log.d(TAG, "╚═══════════════════════════════════════════════════════")
        Log.d(TAG, "")

        val snapshots = context.filesDir.listFiles { file ->
            file.name.startsWith("outfit_") && file.name.endsWith(".png")
        }

        if (snapshots == null || snapshots.isEmpty()) {
            Log.d(TAG, "No snapshot files found")
            Log.d(TAG, "")
            return
        }

        Log.d(TAG, "Found ${snapshots.size} snapshot file(s):")
        Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

        snapshots.sortedBy { it.name }.forEach { file ->
            val outfitId = file.name.removePrefix("outfit_").removeSuffix(".png")
            Log.d(TAG, "")
            Log.d(TAG, "Outfit ID: $outfitId")
            Log.d(TAG, "  ├─ File: ${file.name}")
            Log.d(TAG, "  ├─ Size: ${file.length()} bytes")
            Log.d(TAG, "  └─ Modified: ${java.util.Date(file.lastModified())}")
        }

        Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.d(TAG, "")
    }

    /**
     * Compare two snapshots
     */
    fun compareSnapshots(context: Context, outfitId: Int) {
        Log.d(TAG, "")
        Log.d(TAG, "╔═══════════════════════════════════════════════════════")
        Log.d(TAG, "║ SNAPSHOT HISTORY FOR OUTFIT $outfitId")
        Log.d(TAG, "╚═══════════════════════════════════════════════════════")
        Log.d(TAG, "")

        val currentFile = File(context.filesDir, "outfit_$outfitId.png")

        if (!currentFile.exists()) {
            Log.d(TAG, "Current snapshot does not exist")
            Log.d(TAG, "")
            return
        }

        Log.d(TAG, "CURRENT SNAPSHOT:")
        Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.d(TAG, "Size: ${currentFile.length()} bytes")
        Log.d(TAG, "Modified: ${java.util.Date(currentFile.lastModified())}")

        val bitmap = BitmapFactory.decodeFile(currentFile.absolutePath)
        if (bitmap != null) {
            Log.d(TAG, "Dimensions: ${bitmap.width}x${bitmap.height}")
            bitmap.recycle()
        }

        Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.d(TAG, "")
    }
}