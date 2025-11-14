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
import com.iie.st10320489.stylu.R
import com.iie.st10320489.stylu.data.local.StyluDatabase
import com.iie.st10320489.stylu.network.ApiService
import com.iie.st10320489.stylu.repository.OutfitRepository
import kotlinx.coroutines.launch
import java.io.File

/**
 * ✅ FIXED: Shows snapshot image and refreshes cache after delete
 */
class OutfitDetailFragment : Fragment() {

    private lateinit var apiService: ApiService
    private lateinit var outfitRepository: OutfitRepository
    private var outfitId: Int = -1
    private var outfitName: String = ""

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

        if (outfitId == -1) {
            Toast.makeText(requireContext(), "Invalid outfit", Toast.LENGTH_SHORT).show()
            findNavController().navigateUp()
            return
        }

        setupButtons(view)
        loadOutfitDetail()
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
                val result = apiService.getUserOutfits()
                result.onSuccess { outfits ->
                    val outfit = outfits.find { it.outfitId == outfitId }

                    if (outfit == null) {
                        Toast.makeText(requireContext(), "Outfit not found", Toast.LENGTH_SHORT).show()
                        findNavController().navigateUp()
                        return@onSuccess
                    }

                    outfitName = outfit.name
                    displayOutfitSnapshot(outfit)

                }.onFailure { error ->
                    Toast.makeText(
                        requireContext(),
                        "Failed to load outfit: ${error.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    findNavController().navigateUp()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                findNavController().navigateUp()
            }
        }
    }

    /**
     * ✅ FIX 2: Display the saved snapshot image instead of rendering items
     */
// ✅ Add this to OutfitDetailFragment to debug snapshot loading

    private fun displayOutfitSnapshot(outfit: ApiService.OutfitDetail) {
        view?.findViewById<TextView>(R.id.tvOutfitName)?.text = outfit.name

        val canvas = view?.findViewById<FrameLayout>(R.id.outfitCanvas)
        canvas?.removeAllViews()
        canvas?.visibility = View.VISIBLE

        // ✅ Debug: Check for snapshot file
        val snapshotFile = File(requireContext().filesDir, "outfit_${outfit.outfitId}.png")

        Log.d("OutfitDetail", "=== LOOKING FOR SNAPSHOT ===")
        Log.d("OutfitDetail", "Outfit ID: ${outfit.outfitId}")
        Log.d("OutfitDetail", "Expected file: ${snapshotFile.absolutePath}")
        Log.d("OutfitDetail", "File exists: ${snapshotFile.exists()}")

        if (snapshotFile.exists()) {
            Log.d("OutfitDetail", "File size: ${snapshotFile.length()} bytes")
        } else {
            // ✅ List all outfit snapshots in the directory
            Log.d("OutfitDetail", "❌ File NOT FOUND!")
            Log.d("OutfitDetail", "Listing all files in directory:")
            requireContext().filesDir.listFiles()?.filter { it.name.startsWith("outfit_") }?.forEach {
                Log.d("OutfitDetail", "  Found: ${it.name} (${it.length()} bytes)")
            }
        }

        if (snapshotFile.exists()) {
            // Show the snapshot
            val imageView = ImageView(requireContext()).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                scaleType = ImageView.ScaleType.FIT_CENTER
            }

            Glide.with(requireContext())
                .load(snapshotFile)
                .placeholder(R.drawable.default_img)
                .error(R.drawable.default_img)
                .listener(object : com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable> {
                    override fun onLoadFailed(
                        e: com.bumptech.glide.load.engine.GlideException?,
                        model: Any?,
                        target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>?,
                        isFirstResource: Boolean
                    ): Boolean {
                        Log.e("OutfitDetail", "❌ Glide failed to load image", e)
                        return false
                    }

                    override fun onResourceReady(
                        resource: android.graphics.drawable.Drawable?,
                        model: Any?,
                        target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>?,
                        dataSource: com.bumptech.glide.load.DataSource?,
                        isFirstResource: Boolean
                    ): Boolean {
                        Log.d("OutfitDetail", "✅ Glide successfully loaded image")
                        return false
                    }
                })
                .into(imageView)

            canvas?.addView(imageView)
            Log.d("OutfitDetail", "✅ ImageView added to canvas")
        } else {
            // No snapshot - show message
            Log.w("OutfitDetail", "⚠️ No snapshot available")
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
            .setMessage("Are you sure you want to delete this outfit?")
            .setPositiveButton("Delete") { _, _ ->
                deleteOutfit()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * ✅ FIX 1: Refresh cache after delete to remove from UI
     */
    private fun deleteOutfit() {
        lifecycleScope.launch {
            try {
                // Delete from API
                val result = outfitRepository.deleteOutfit(outfitId.toString())

                result.onSuccess {
                    // ✅ Delete the snapshot file
                    val snapshotFile = File(requireContext().filesDir, "outfit_$outfitId.png")
                    if (snapshotFile.exists()) {
                        snapshotFile.delete()
                    }

                    // ✅ Clear cache to force refresh
                    val database = StyluDatabase.getDatabase(requireContext())
                    database.outfitDao().deleteOutfitComplete(outfitId)

                    Toast.makeText(requireContext(), "Outfit deleted ✅", Toast.LENGTH_SHORT).show()
                    findNavController().navigateUp()

                }.onFailure { error ->
                    Toast.makeText(
                        requireContext(),
                        "Failed to delete: ${error.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}