package com.iie.st10320489.stylu.ui.calendar

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.iie.st10320489.stylu.R
import com.iie.st10320489.stylu.network.ApiService
import java.io.File

class OutfitSelectionAdapter(
    private val onOutfitSelected: (ApiService.OutfitDetail) -> Unit
) : ListAdapter<OutfitSelectionAdapter.OutfitItem, OutfitSelectionAdapter.ViewHolder>(DiffCallback()) {

    data class OutfitItem(
        val outfit: ApiService.OutfitDetail,
        val isSelected: Boolean = false
    )

    private var selectedPosition = -1

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardOutfit: View = itemView.findViewById(R.id.cardOutfit)
        private val outfitPreviewContainer: FrameLayout = itemView.findViewById(R.id.outfitPreviewContainer)
        private val selectionOverlay: View = itemView.findViewById(R.id.selectionOverlay)
        private val ivCheck: ImageView = itemView.findViewById(R.id.ivCheck)
        private val tvOutfitName: TextView = itemView.findViewById(R.id.tvOutfitName)
        private val colorDotsContainer: LinearLayout = itemView.findViewById(R.id.colorDotsContainer)

        init {
            cardOutfit.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val previousSelected = selectedPosition
                    selectedPosition = position

                    // Update UI for both items
                    if (previousSelected != -1) {
                        notifyItemChanged(previousSelected)
                    }
                    notifyItemChanged(position)

                    onOutfitSelected(getItem(position).outfit)
                }
            }
        }

        fun bind(outfitItem: OutfitItem) {
            val outfit = outfitItem.outfit
            val isSelected = adapterPosition == selectedPosition

            tvOutfitName.text = outfit.name

            // Update selection state
            selectionOverlay.visibility = if (isSelected) View.VISIBLE else View.GONE
            ivCheck.visibility = if (isSelected) View.VISIBLE else View.GONE

            // Clear previous views
            outfitPreviewContainer.removeAllViews()

            // Try to load saved outfit image first
            val savedImagePath = getSavedOutfitImagePath(outfit.outfitId, itemView.context)
            if (savedImagePath.isNotEmpty()) {
                val imageView = ImageView(itemView.context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }

                Glide.with(itemView.context)
                    .load(savedImagePath)
                    .placeholder(R.drawable.cloudy)
                    .error(R.drawable.sunny)
                    .into(imageView)

                outfitPreviewContainer.addView(imageView)
            } else if (outfit.items.isNotEmpty()) {
                // Fallback: Show first item's image
                val imageView = ImageView(itemView.context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }

                Glide.with(itemView.context)
                    .load(outfit.items.first().imageUrl)
                    .placeholder(R.drawable.cloudy)
                    .error(R.drawable.sunny)
                    .into(imageView)

                outfitPreviewContainer.addView(imageView)
            }

            // Draw color dots
            colorDotsContainer.removeAllViews()
            outfit.items.take(4).forEach { item ->
                val dot = View(itemView.context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        (8 * resources.displayMetrics.density).toInt(),
                        (8 * resources.displayMetrics.density).toInt()
                    ).apply {
                        setMargins(
                            (1 * resources.displayMetrics.density).toInt(),
                            0,
                            (1 * resources.displayMetrics.density).toInt(),
                            0
                        )
                    }

                    setBackgroundResource(R.drawable.color_dot_bg)
                    item.colour?.let { colour ->
                        try {
                            setBackgroundColor(Color.parseColor(colour))
                        } catch (e: Exception) {
                            setBackgroundColor(Color.GRAY)
                        }
                    }
                }
                colorDotsContainer.addView(dot)
            }
        }

        private fun getSavedOutfitImagePath(outfitId: Int, context: Context): String {
            val file = File(context.filesDir, "outfit_$outfitId.png")
            return if (file.exists()) file.absolutePath else ""
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_schedule_outfit_selection, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    fun clearSelection() {
        val previous = selectedPosition
        selectedPosition = -1
        if (previous != -1) {
            notifyItemChanged(previous)
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<OutfitItem>() {
        override fun areItemsTheSame(oldItem: OutfitItem, newItem: OutfitItem) =
            oldItem.outfit.outfitId == newItem.outfit.outfitId

        override fun areContentsTheSame(oldItem: OutfitItem, newItem: OutfitItem) =
            oldItem == newItem
    }
}