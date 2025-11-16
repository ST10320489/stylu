package com.iie.st10320489.stylu.utils

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * ✅ Centralized Snapshot Management
 * Handles saving, loading, and cleaning up outfit snapshot images
 */
object SnapshotManager {
    private const val TAG = "SnapshotManager"
    private const val SNAPSHOT_PREFIX = "outfit_"
    private const val SNAPSHOT_EXTENSION = ".png"

    /**
     * Save outfit snapshot bitmap
     */
    fun saveSnapshot(context: Context, outfitId: Int, bitmap: Bitmap): Boolean {
        return try {
            val file = getSnapshotFile(context, outfitId)

            // Delete old file if exists
            if (file.exists()) {
                file.delete()
                Log.d(TAG, "Deleted old snapshot for outfit $outfitId")
            }

            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.flush()
            }

            Log.d(TAG, "✅ Saved snapshot for outfit $outfitId: ${file.length()} bytes")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to save snapshot for outfit $outfitId", e)
            false
        }
    }

    /**
     * Get snapshot file for outfit
     */
    fun getSnapshotFile(context: Context, outfitId: Int): File {
        return File(context.filesDir, "$SNAPSHOT_PREFIX$outfitId$SNAPSHOT_EXTENSION")
    }

    /**
     * Check if snapshot exists
     */
    fun snapshotExists(context: Context, outfitId: Int): Boolean {
        val file = getSnapshotFile(context, outfitId)
        return file.exists() && file.length() > 0
    }

    /**
     * Delete snapshot for specific outfit
     */
    fun deleteSnapshot(context: Context, outfitId: Int): Boolean {
        return try {
            val file = getSnapshotFile(context, outfitId)
            val deleted = file.delete()
            if (deleted) {
                Log.d(TAG, "✅ Deleted snapshot for outfit $outfitId")
            }
            deleted
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to delete snapshot for outfit $outfitId", e)
            false
        }
    }

    /**
     * ✅ Clean up orphaned snapshots (snapshots for deleted outfits)
     */
    fun cleanupOrphanedSnapshots(context: Context, validOutfitIds: List<Int>): Int {
        var deletedCount = 0

        try {
            val validIds = validOutfitIds.toSet()
            val snapshotFiles = context.filesDir.listFiles { file ->
                file.name.startsWith(SNAPSHOT_PREFIX) && file.name.endsWith(SNAPSHOT_EXTENSION)
            } ?: emptyArray()

            Log.d(TAG, "🧹 Cleanup: Found ${snapshotFiles.size} snapshot files")
            Log.d(TAG, "🧹 Valid outfit IDs: $validIds")

            snapshotFiles.forEach { file ->
                try {
                    // Extract outfit ID from filename
                    val fileName = file.nameWithoutExtension
                    val outfitIdStr = fileName.removePrefix(SNAPSHOT_PREFIX)
                    val outfitId = outfitIdStr.toIntOrNull()

                    if (outfitId == null || outfitId !in validIds) {
                        if (file.delete()) {
                            deletedCount++
                            Log.d(TAG, "🗑️ Deleted orphaned snapshot: ${file.name}")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to process file: ${file.name}", e)
                }
            }

            Log.d(TAG, "✅ Cleanup complete: Deleted $deletedCount orphaned snapshots")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Cleanup failed", e)
        }

        return deletedCount
    }

    /**
     * ✅ Delete ALL snapshots (for cache reset)
     */
    fun deleteAllSnapshots(context: Context): Int {
        var deletedCount = 0

        try {
            val snapshotFiles = context.filesDir.listFiles { file ->
                file.name.startsWith(SNAPSHOT_PREFIX) && file.name.endsWith(SNAPSHOT_EXTENSION)
            } ?: emptyArray()

            snapshotFiles.forEach { file ->
                if (file.delete()) {
                    deletedCount++
                }
            }

            Log.d(TAG, "🗑️ Deleted ALL snapshots: $deletedCount files")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to delete all snapshots", e)
        }

        return deletedCount
    }

    /**
     * Get all snapshot files
     */
    fun getAllSnapshots(context: Context): List<File> {
        return context.filesDir.listFiles { file ->
            file.name.startsWith(SNAPSHOT_PREFIX) && file.name.endsWith(SNAPSHOT_EXTENSION)
        }?.toList() ?: emptyList()
    }

    /**
     * Get total size of all snapshots
     */
    fun getTotalSnapshotSize(context: Context): Long {
        return getAllSnapshots(context).sumOf { it.length() }
    }
}