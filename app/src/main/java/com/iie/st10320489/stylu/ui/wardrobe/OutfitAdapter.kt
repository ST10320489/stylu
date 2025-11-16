package com.iie.st10320489.stylu.ui.wardrobe

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.palette.graphics.Palette
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.signature.ObjectKey
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.iie.st10320489.stylu.R
import com.iie.st10320489.stylu.network.ApiService
import java.io.File

/**
 * ✅ FIXED: Glide cache busting for updated snapshots
 */
class OutfitAdapter(
    private val onOutfitClick: (ApiService.OutfitDetail) -> Unit
) : ListAdapter<ApiService.OutfitDetail, OutfitAdapter.OutfitViewHolder>(OutfitDiffCallback()) {

    inner class OutfitViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvOutfitName: TextView = itemView.findViewById(R.id.tvOutfitName)
        private val outfitPreviewContainer: FrameLayout = itemView.findViewById(R.id.outfitPreviewContainer)
        private val colorDotsContainer: LinearLayout = itemView.findViewById(R.id.colorDotsContainer)

        init {
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onOutfitClick(getItem(position))
                }
            }
        }

        fun bind(outfit: ApiService.OutfitDetail) {
            tvOutfitName.text = outfit.name
            outfitPreviewContainer.removeAllViews()
            colorDotsContainer.removeAllViews()

            val snapshotFile = File(itemView.context.filesDir, "outfit_${outfit.outfitId}.png")

            if (snapshotFile.exists()) {
                // Display saved bitmap AND extract colors using Palette API
                val imageView = ImageView(itemView.context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }

                // ✅ FIX: Load image with cache busting
                Glide.with(itemView.context)
                    .asBitmap()
                    .load(snapshotFile)
                    .diskCacheStrategy(DiskCacheStrategy.NONE) // ✅ Don't cache to disk
                    .skipMemoryCache(true) // ✅ Don't cache in memory
                    .signature(ObjectKey(snapshotFile.lastModified())) // ✅ Cache key = file timestamp
                    .placeholder(R.drawable.default_img)
                    .error(R.drawable.default_img)
                    .into(object : CustomTarget<Bitmap>() {
                        override fun onResourceReady(
                            bitmap: Bitmap,
                            transition: Transition<in Bitmap>?
                        ) {
                            imageView.setImageBitmap(bitmap)

                            // ✅ Extract colors from bitmap using Palette API
                            extractColorsFromBitmap(bitmap)
                        }

                        override fun onLoadCleared(placeholder: Drawable?) {
                            // Optional: handle cleanup
                        }
                    })

                outfitPreviewContainer.addView(imageView)
            } else {
                // Fallback: build canvas from layoutData
                buildCanvasPreview(outfit)

                // Extract colors from item metadata as fallback
                extractColorsFromItems(outfit.items)
            }
        }

        // ✅ Extract colors using Palette API
        private fun extractColorsFromBitmap(bitmap: Bitmap) {
            Palette.from(bitmap).generate { palette ->
                palette?.let {
                    val colors = mutableListOf<Int>()

                    // Get vibrant, light, dark, and muted colors
                    it.vibrantSwatch?.rgb?.let { color -> colors.add(color) }
                    it.lightVibrantSwatch?.rgb?.let { color -> colors.add(color) }
                    it.darkVibrantSwatch?.rgb?.let { color -> colors.add(color) }
                    it.mutedSwatch?.rgb?.let { color -> colors.add(color) }

                    // If we still don't have 4 colors, add dominant swatch
                    if (colors.size < 4) {
                        it.dominantSwatch?.rgb?.let { color ->
                            if (!colors.contains(color)) {
                                colors.add(color)
                            }
                        }
                    }

                    // Take only first 4 unique colors and display them
                    colors.distinct().take(4).forEach { color ->
                        addColorDot(color)
                    }
                }
            }
        }

        // ✅ Fallback - extract colors from item metadata
        private fun extractColorsFromItems(items: List<ApiService.OutfitItemDetail>) {
            items.take(4).forEach { item ->
                item.colour?.let { colourString ->
                    try {
                        val color = android.graphics.Color.parseColor(colourString)
                        addColorDot(color)
                    } catch (e: Exception) {
                        // If parsing fails, use a subtle gray
                        addColorDot(android.graphics.Color.LTGRAY)
                    }
                }
            }
        }

        // ✅ Helper to add color dot
        private fun addColorDot(color: Int) {
            val dot = View(itemView.context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    (16 * itemView.resources.displayMetrics.density).toInt(),
                    (16 * itemView.resources.displayMetrics.density).toInt()
                ).apply {
                    setMargins(
                        (4 * itemView.resources.displayMetrics.density).toInt(),
                        0,
                        (4 * itemView.resources.displayMetrics.density).toInt(),
                        0
                    )
                }

                setBackgroundResource(R.drawable.color_dot_bg)
                setBackgroundColor(color)
            }
            colorDotsContainer.addView(dot)
        }

        private fun buildCanvasPreview(outfit: ApiService.OutfitDetail) {
            outfitPreviewContainer.post {
                outfit.items.forEach { item ->
                    val imageView = ImageView(itemView.context)
                    val layoutData = item.layoutData

                    if (layoutData != null) {
                        val actualX = layoutData.x * outfitPreviewContainer.width
                        val actualY = layoutData.y * outfitPreviewContainer.height

                        imageView.layoutParams = FrameLayout.LayoutParams(
                            (layoutData.width * 0.6f).toInt(),
                            (layoutData.height * 0.6f).toInt()
                        )
                        imageView.x = actualX
                        imageView.y = actualY
                        imageView.scaleX = layoutData.scale * 0.6f
                        imageView.scaleY = layoutData.scale * 0.6f
                    } else {
                        imageView.layoutParams = FrameLayout.LayoutParams(120, 120)
                    }

                    imageView.scaleType = ImageView.ScaleType.FIT_CENTER

                    Glide.with(itemView.context)
                        .load(item.imageUrl)
                        .fitCenter()
                        .placeholder(R.drawable.cloudy)
                        .error(R.drawable.sunny)
                        .into(imageView)

                    outfitPreviewContainer.addView(imageView)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OutfitViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_outfit, parent, false)
        return OutfitViewHolder(view)
    }

    override fun onBindViewHolder(holder: OutfitViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}

class OutfitDiffCallback : DiffUtil.ItemCallback<ApiService.OutfitDetail>() {
    override fun areItemsTheSame(
        oldItem: ApiService.OutfitDetail,
        newItem: ApiService.OutfitDetail
    ) = oldItem.outfitId == newItem.outfitId

    override fun areContentsTheSame(
        oldItem: ApiService.OutfitDetail,
        newItem: ApiService.OutfitDetail
    ) = oldItem == newItem
}