package com.iie.st10320489.stylu.ui.wardrobe

import android.os.Bundle
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
import com.iie.st10320489.stylu.network.ApiService
import kotlinx.coroutines.launch

/**
 * ✅ FIXED: Handles empty outfits gracefully
 * - Shows message if outfit has no items
 * - Prevents crashes on empty outfits
 */
class OutfitDetailFragment : Fragment() {

    private lateinit var apiService: ApiService
    private var outfitId: Int = -1

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
        outfitId = arguments?.getInt("outfitId") ?: -1

        if (outfitId == -1) {
            Toast.makeText(requireContext(), "Invalid outfit", Toast.LENGTH_SHORT).show()
            findNavController().navigateUp()
            return
        }

        view.findViewById<Button>(R.id.btnDeleteOutfit).setOnClickListener {
            confirmDelete()
        }



        loadOutfitDetail()
    }

    private fun loadOutfitDetail() {
        lifecycleScope.launch {
            try {
                val result = apiService.getUserOutfits()
                result.onSuccess { outfits ->
                    val outfit = outfits.find { it.outfitId == outfitId }

                    if (outfit == null) {
                        Toast.makeText(
                            requireContext(),
                            "Outfit not found",
                            Toast.LENGTH_SHORT
                        ).show()
                        findNavController().navigateUp()
                        return@onSuccess
                    }

                    view?.findViewById<Button>(R.id.btnDeleteOutfit2)?.setOnClickListener {
                        val bundle = Bundle().apply {
                            putInt("outfitId", outfitId)
                            putString("outfitName", outfit.name)
                        }
                        findNavController().navigate(
                            R.id.action_outfit_detail_to_edit_outfit,
                            bundle
                        )
                    }

                    // ✅ Check if outfit is empty
                    if (outfit.items.isEmpty()) {
                        showEmptyOutfitMessage(outfit.name)
                    } else {
                        displayOutfit(outfit)
                    }

                }.onFailure { error ->
                    Toast.makeText(
                        requireContext(),
                        "Failed to load outfit: ${error.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    findNavController().navigateUp()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    "Error: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
                findNavController().navigateUp()
            }
        }
    }

    /**
     * ✅ Show message for empty outfits
     */
    private fun showEmptyOutfitMessage(outfitName: String) {
        view?.findViewById<TextView>(R.id.tvOutfitName)?.text = outfitName

        // Hide the canvas
        view?.findViewById<FrameLayout>(R.id.outfitCanvas)?.visibility = View.GONE

        // Show empty state message
        Toast.makeText(
            requireContext(),
            "This outfit has no items.\nAdd items to see it displayed.",
            Toast.LENGTH_LONG
        ).show()

        // Optionally, you can add a TextView to show the message in the UI
        // For now, we'll just show a toast and allow deletion
    }

    private fun displayOutfit(outfit: ApiService.OutfitDetail) {
        view?.findViewById<TextView>(R.id.tvOutfitName)?.text = outfit.name

        val canvas = view?.findViewById<FrameLayout>(R.id.outfitCanvas)
        canvas?.removeAllViews()
        canvas?.visibility = View.VISIBLE

        // ✅ Double-check items exist before rendering
        if (outfit.items.isEmpty()) {
            showEmptyOutfitMessage(outfit.name)
            return
        }

        canvas?.post {
            outfit.items.forEach { item ->
                val imageView = ImageView(requireContext())

                val layoutData = item.layoutData
                if (layoutData != null) {
                    val actualX = layoutData.x * canvas.width
                    val actualY = layoutData.y * canvas.height

                    imageView.layoutParams = FrameLayout.LayoutParams(
                        layoutData.width,
                        layoutData.height
                    )
                    imageView.x = actualX
                    imageView.y = actualY
                    imageView.scaleX = layoutData.scale
                    imageView.scaleY = layoutData.scale
                } else {
                    imageView.layoutParams = FrameLayout.LayoutParams(
                        200,
                        200
                    )
                }

                imageView.scaleType = ImageView.ScaleType.FIT_CENTER

                Glide.with(requireContext())
                    .load(item.imageUrl)
                    .fitCenter()
                    .placeholder(R.drawable.default_img)
                    .error(R.drawable.default_img)
                    .into(imageView)

                canvas.addView(imageView)
            }
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

    private fun deleteOutfit() {
        lifecycleScope.launch {
            try {
                val result = apiService.deleteOutfit(outfitId)
                result.onSuccess {
                    Toast.makeText(
                        requireContext(),
                        "Outfit deleted",
                        Toast.LENGTH_SHORT
                    ).show()
                    findNavController().navigateUp()
                }.onFailure { error ->
                    Toast.makeText(
                        requireContext(),
                        "Failed to delete: ${error.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    "Error: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}