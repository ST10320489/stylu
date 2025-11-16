package com.iie.st10320489.stylu.ui.wardrobe

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.signature.ObjectKey
import com.iie.st10320489.stylu.R
import com.iie.st10320489.stylu.data.local.StyluDatabase
import com.iie.st10320489.stylu.network.ApiService
import com.iie.st10320489.stylu.repository.OutfitRepository
import kotlinx.coroutines.launch
import java.io.File

/**
 * ✅ FIXED: Shows snapshot image with cache busting and refreshes on resume
 */
class OutfitDetailFragment : Fragment() {

    private lateinit var apiService: ApiService
    private lateinit var outfitRepository: OutfitRepository
    private var outfitId: Int = -1
    private var outfitName: String = ""

    companion object {
        private const val TAG = "OutfitDetail"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_outfit_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        apiService = ApiService(requireContext())
        outfitRepository = OutfitRepository(requireContext())
        outfitId = arguments?.getInt("outfitId") ?: -1

        Log.d(TAG, "Opening outfit detail for ID: $outfitId")

        if (outfitId == -1) {
            Toast.makeText(requireContext(), "Invalid outfit", Toast.LENGTH_SHORT).show()
            findNavController().navigateUp()
            return
        }

        setupButtons(view)
        loadOutfitDetail()
    }

    override fun onResume() {
        super.onResume()
        // ✅ Reload outfit when returning from edit
        if (outfitId != -1) {
            Log.d(TAG, "onResume - reloading outfit detail")
            loadOutfitDetail()
        }
    }

    private fun setupButtons(view: View) {
        // Delete button
        view.findViewById<Button>(R.id.btnDeleteOutfit).setOnClickListener {
            confirmDelete()
        }

        // Edit button
        view.findViewById<Button>(R.id.btnDeleteOutfit2).setOnClickListener {
            val bundle = Bundle().apply {
                putInt("outfitId", outfitId)
                putString("outfitName", outfitName)
            }
            findNavController().navigate(
                R.id.action_outfit_detail_to_edit_outfit,
                bundle
            )
        }
    }

    private fun loadOutfitDetail() {
        lifecycleScope.launch {
            try {
                Log.d(TAG, "Loading outfit from API...")

                val result = apiService.getUserOutfits()
                result.onSuccess { outfits ->
                    val outfit = outfits.find { it.outfitId == outfitId }

                    if (outfit == null) {
                        Log.e(TAG, "Outfit $outfitId not found")
                        Toast.makeText(requireContext(), "Outfit not found", Toast.LENGTH_SHORT).show()
                        findNavController().navigateUp()
                        return@onSuccess
                    }

                    outfitName = outfit.name
                    Log.d(TAG, "Loaded outfit: ${outfit.name} with ${outfit.items.size} items")

                    displayOutfitSnapshot(outfit)

                }.onFailure { error ->
                    Log.e(TAG, "Failed to load outfit: ${error.message}")
                    Toast.makeText(
                        requireContext(),
                        "Failed to load outfit: ${error.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    findNavController().navigateUp()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception: ${e.message}", e)
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                findNavController().navigateUp()
            }
        }
    }

    /**
     * ✅ FIXED: Display snapshot with Glide cache busting
     */
    private fun displayOutfitSnapshot(outfit: ApiService.OutfitDetail) {
        view?.findViewById<TextView>(R.id.tvOutfitName)?.text = outfit.name

        val canvas = view?.findViewById<FrameLayout>(R.id.outfitCanvas)
        canvas?.removeAllViews()
        canvas?.visibility = View.VISIBLE

        val snapshotFile = File(requireContext().filesDir, "outfit_${outfit.outfitId}.png")

        Log.d(TAG, "")
        Log.d(TAG, "📸 DISPLAYING SNAPSHOT")
        Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.d(TAG, "Expected file: ${snapshotFile.absolutePath}")
        Log.d(TAG, "File exists: ${snapshotFile.exists()}")

        if (snapshotFile.exists()) {
            Log.d(TAG, "File size: ${snapshotFile.length()} bytes")
            Log.d(TAG, "Last modified: ${java.util.Date(snapshotFile.lastModified())}")
            Log.d(TAG, "Timestamp: ${snapshotFile.lastModified()}")
            Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Log.d(TAG, "")

            val imageView = ImageView(requireContext()).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                scaleType = ImageView.ScaleType.FIT_CENTER
            }

            Log.d(TAG, "🖼️ LOADING IMAGE WITH GLIDE (cache busting enabled)...")

            // ✅ FIX: Cache busting with file timestamp
            Glide.with(requireContext())
                .load(snapshotFile)
                .diskCacheStrategy(DiskCacheStrategy.NONE) // ✅ Don't cache to disk
                .skipMemoryCache(true) // ✅ Don't cache in memory
                .signature(ObjectKey(snapshotFile.lastModified())) // ✅ Cache key = timestamp
                .placeholder(R.drawable.default_img)
                .error(R.drawable.default_img)
                .listener(object : com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable> {
                    override fun onLoadFailed(
                        e: com.bumptech.glide.load.engine.GlideException?,
                        model: Any?,
                        target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>?,
                        isFirstResource: Boolean
                    ): Boolean {
                        Log.e(TAG, "❌ Glide failed to load snapshot", e)
                        e?.logRootCauses(TAG)
                        return false
                    }

                    override fun onResourceReady(
                        resource: android.graphics.drawable.Drawable?,
                        model: Any?,
                        target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>?,
                        dataSource: com.bumptech.glide.load.DataSource?,
                        isFirstResource: Boolean
                    ): Boolean {
                        Log.d(TAG, "✅ Glide loaded snapshot successfully")
                        Log.d(TAG, "Data source: $dataSource")
                        return false
                    }
                })
                .into(imageView)

            canvas?.addView(imageView)
            Log.d(TAG, "✅ ImageView added to canvas")

        } else {
            Log.w(TAG, "⚠️ NO SNAPSHOT FILE FOUND")
            Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Log.d(TAG, "")

            // List what files DO exist
            Log.d(TAG, "📁 FILES THAT DO EXIST:")
            requireContext().filesDir.listFiles()?.filter {
                it.name.startsWith("outfit_")
            }?.forEach {
                Log.d(TAG, "  • ${it.name} (${it.length()} bytes)")
            }
            Log.d(TAG, "")

            Toast.makeText(
                requireContext(),
                "No preview available. Edit to regenerate.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun confirmDelete() {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Outfit")
            .setMessage("Are you sure you want to delete \"$outfitName\"?")
            .setPositiveButton("Delete") { _, _ ->
                deleteOutfit()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * ✅ Delete outfit and its snapshot
     */
    private fun deleteOutfit() {
        lifecycleScope.launch {
            try {
                Log.d(TAG, "Deleting outfit $outfitId...")

                // Delete from API
                val result = outfitRepository.deleteOutfit(outfitId.toString())

                result.onSuccess {
                    Log.d(TAG, "✅ API deletion successful")

                    // Delete the snapshot file
                    val snapshotFile = File(requireContext().filesDir, "outfit_$outfitId.png")
                    if (snapshotFile.exists()) {
                        val deleted = snapshotFile.delete()
                        Log.d(TAG, "Snapshot deleted: $deleted")
                    }

                    // Clear cache to force refresh
                    try {
                        val database = StyluDatabase.getDatabase(requireContext())
                        database.outfitDao().deleteOutfitComplete(outfitId)
                        Log.d(TAG, "Cache cleared")
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to clear cache: ${e.message}")
                    }

                    Toast.makeText(requireContext(), "Outfit deleted ✅", Toast.LENGTH_SHORT).show()
                    findNavController().navigateUp()

                }.onFailure { error ->
                    Log.e(TAG, "Failed to delete: ${error.message}")
                    Toast.makeText(
                        requireContext(),
                        "Failed to delete: ${error.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception during delete", e)
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}