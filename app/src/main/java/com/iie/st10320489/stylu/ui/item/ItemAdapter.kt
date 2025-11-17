package com.iie.st10320489.stylu.ui.item

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.iie.st10320489.stylu.R
import com.iie.st10320489.stylu.ui.item.models.WardrobeItem

class ItemAdapter(
    private val onItemClick: ((WardrobeItem) -> Unit)? = null
) : ListAdapter<WardrobeItem, ItemAdapter.ItemViewHolder>(ItemDiffCallback()) {

    inner class ItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.tvItemName)
        private val imageView: ImageView = itemView.findViewById(R.id.ivItemImage)

        init {
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick?.invoke(getItem(position))
                }
            }
        }

        fun bind(item: WardrobeItem) {
            // Display the item name first
            nameText.text = if (!item.name.isNullOrEmpty()) {
                item.name
            } else if (!item.colour.isNullOrEmpty()) {
                "${item.colour} ${item.subcategory}"
            } else {
                item.subcategory
            }

            // Load image from Supabase using Glide
            Glide.with(itemView.context)
                .load(item.imageUrl)
                .placeholder(R.drawable.default_img)
                .error(R.drawable.default_img)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .fitCenter()
                .into(imageView)

        }

    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_wardrobe, parent, false)
        return ItemViewHolder(view)
    }

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }
}

class ItemDiffCallback : DiffUtil.ItemCallback<WardrobeItem>() {
    override fun areItemsTheSame(oldItem: WardrobeItem, newItem: WardrobeItem) =
        oldItem.itemId == newItem.itemId

    override fun areContentsTheSame(oldItem: WardrobeItem, newItem: WardrobeItem) =
        oldItem == newItem
}