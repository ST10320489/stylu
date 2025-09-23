package com.iie.st10320489.stylu.ui.item

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.iie.st10320489.stylu.R
import com.iie.st10320489.stylu.ui.item.models.WardrobeItem

class ItemAdapter :
    ListAdapter<WardrobeItem, ItemAdapter.ItemViewHolder>(ItemDiffCallback()) {

    inner class ItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.tvItemName)
        private val imageView: ImageView = itemView.findViewById(R.id.ivItemImage)

        fun bind(item: WardrobeItem) {
            nameText.text = item.name

            // Placeholder drawable for now
            imageView.setImageResource(
                when (item.image) {
                    "white-shirt" -> R.drawable.white_shirt
                    "hoody" -> R.drawable.sweater
                    "sneakers" -> R.drawable.shoes
                    else -> R.drawable.sunny
                }
            )

            // Later, for images from URL:
            // Glide.with(itemView).load(item.image).into(imageView)
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
        oldItem.name == newItem.name && oldItem.category == newItem.category

    override fun areContentsTheSame(oldItem: WardrobeItem, newItem: WardrobeItem) =
        oldItem == newItem
}




