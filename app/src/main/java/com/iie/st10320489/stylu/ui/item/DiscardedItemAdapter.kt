package com.iie.st10320489.stylu.ui.item

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.iie.st10320489.stylu.R
import com.iie.st10320489.stylu.ui.item.models.DiscardedItem

class DiscardedItemAdapter(
    private val onRestoreClick: (DiscardedItem) -> Unit,
    private val onDeleteClick: (DiscardedItem) -> Unit
) : ListAdapter<DiscardedItem, DiscardedItemAdapter.DiscardedItemViewHolder>(DiscardedItemDiffCallback()) {

    inner class DiscardedItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.tvItemName)
        private val imageView: ImageView = itemView.findViewById(R.id.ivItemImage)
        private val btnRestore: ImageButton = itemView.findViewById(R.id.btnRestore)
        private val btnDelete: ImageButton = itemView.findViewById(R.id.btnDelete)
        private val discardedOverlay: View = itemView.findViewById(R.id.discardedOverlay)

        fun bind(discardedItem: DiscardedItem) {
            val item = discardedItem.item

            // Display the item name
            nameText.text = if (!item.name.isNullOrEmpty()) {
                item.name
            } else if (!item.colour.isNullOrEmpty()) {
                "${item.colour} ${item.subcategory}"
            } else {
                item.subcategory
            }

            // Load image with a discarded overlay effect
            Glide.with(itemView.context)
                .load(item.imageUrl)
                .placeholder(R.drawable.default_img)
                .error(R.drawable.default_img)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .fitCenter()
                .into(imageView)

            // Show overlay to indicate discarded state
            discardedOverlay.visibility = View.VISIBLE

            // Set up action buttons
            btnRestore.setOnClickListener {
                onRestoreClick(discardedItem)
            }

            btnDelete.setOnClickListener {
                onDeleteClick(discardedItem)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DiscardedItemViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_discarded_wardrobe, parent, false)
        return DiscardedItemViewHolder(view)
    }

    override fun onBindViewHolder(holder: DiscardedItemViewHolder, position: Int) {
        val discardedItem = getItem(position)
        holder.bind(discardedItem)
    }
}

class DiscardedItemDiffCallback : DiffUtil.ItemCallback<DiscardedItem>() {
    override fun areItemsTheSame(oldItem: DiscardedItem, newItem: DiscardedItem) =
        oldItem.item.itemId == newItem.item.itemId

    override fun areContentsTheSame(oldItem: DiscardedItem, newItem: DiscardedItem) =
        oldItem == newItem
}