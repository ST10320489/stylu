package com.iie.st10320489.stylu.ui.calendar

import android.content.Context
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
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.google.android.material.card.MaterialCardView
import com.iie.st10320489.stylu.R
import com.iie.st10320489.stylu.network.ApiService
import java.io.File

class OutfitGridAdapter(
    private val onOutfitClick: (ApiService.OutfitDetail) -> Unit
) : ListAdapter<ApiService.OutfitDetail, OutfitGridAdapter.ViewHolder>(DiffCallback()) {

    private var selectedPosition = RecyclerView.NO_POSITION

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardOutfit: MaterialCardView = itemView.findViewById(R.id.cardOutfit)
        private val tvOutfitName: TextView = itemView.findViewById(R.id.tvOutfitName)
        private val outfitPreviewContainer: FrameLayout = itemView.findViewById(R.id.outfitPreviewContainer)
        private val ivOutfitPreview: ImageView = itemView.findViewById(R.id.ivOutfitPreview)
        private val selectionOverlay: View = itemView.findViewById(R.id.selectionOverlay)
        private val ivCheckmark: ImageView = itemView.findViewById(R.id.ivCheckmark)
        private val colorDotsContainer: LinearLayout = itemView.findViewById(R.id.colorDotsContainer)

        init {
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val previousSelected = selectedPosition
                    selectedPosition = position

                    if (previousSelected != RecyclerView.NO_POSITION) {
                        notifyItemChanged(previousSelected)
                    }
                    notifyItemChanged(position)

                    onOutfitClick(getItem(position))
                }
            }
        }

        fun bind(outfit: ApiService.OutfitDetail, isSelected: Boolean) {
            tvOutfitName.text = outfit.name

            if (isSelected) {
                cardOutfit.strokeWidth = (3 * itemView.resources.displayMetrics.density).toInt()
                selectionOverlay.visibility = View.VISIBLE
                ivCheckmark.visibility = View.VISIBLE
            } else {
                cardOutfit.strokeWidth = 0
                selectionOverlay.visibility = View.GONE
                ivCheckmark.visibility = View.GONE
            }

            // Optimized image loading
            val savedImagePath = getSavedOutfitImagePath(outfit.outfitId, itemView.context)

            val requestOptions = RequestOptions()
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .override(300, 300)
                .centerCrop()
                .placeholder(R.drawable.cloudy)
                .error(R.drawable.sunny)
                .timeout(10000)

            if (savedImagePath.isNotEmpty()) {
                Glide.with(itemView.context)
                    .load(savedImagePath)
                    .apply(requestOptions)
                    .into(ivOutfitPreview)
            } else if (outfit.items.isNotEmpty()) {
                Glide.with(itemView.context)
                    .load(outfit.items.first().imageUrl)
                    .apply(requestOptions)
                    .into(ivOutfitPreview)
            } else {
                ivOutfitPreview.setImageResource(R.drawable.cloudy)
            }

            // Draw color dots
            colorDotsContainer.removeAllViews()
            outfit.items.take(5).forEach { item ->
                val dot = View(itemView.context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        (12 * resources.displayMetrics.density).toInt(),
                        (12 * resources.displayMetrics.density).toInt()
                    ).apply {
                        setMargins(
                            (2 * resources.displayMetrics.density).toInt(),
                            0,
                            (2 * resources.displayMetrics.density).toInt(),
                            0
                        )
                    }

                    setBackgroundResource(R.drawable.color_dot_bg)
                    item.colour?.let { colour ->
                        try {
                            setBackgroundColor(android.graphics.Color.parseColor(colour))
                        } catch (e: Exception) {
                            setBackgroundColor(android.graphics.Color.GRAY)
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
            .inflate(R.layout.item_outfit_grid, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), position == selectedPosition)
    }

    class DiffCallback : DiffUtil.ItemCallback<ApiService.OutfitDetail>() {
        override fun areItemsTheSame(
            oldItem: ApiService.OutfitDetail,
            newItem: ApiService.OutfitDetail
        ) = oldItem.outfitId == newItem.outfitId

        override fun areContentsTheSame(
            oldItem: ApiService.OutfitDetail,
            newItem: ApiService.OutfitDetail
        ) = oldItem == newItem
    }
}