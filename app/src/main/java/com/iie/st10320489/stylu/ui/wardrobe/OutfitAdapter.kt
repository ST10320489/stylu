package com.iie.st10320489.stylu.ui.wardrobe

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
                        imageView.layoutParams = FrameLayout.LayoutParams(
                            120,
                            120
                        )
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