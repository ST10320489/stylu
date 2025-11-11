package com.iie.st10320489.stylu.ui.calendar

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.iie.st10320489.stylu.R
import com.iie.st10320489.stylu.data.models.outfit.Outfit

class OutfitAdapter(
    private var outfits: List<Outfit>,
    private val onItemClick: (Outfit) -> Unit,
    private val onEditClick: (Outfit) -> Unit,
    private val onDeleteClick: (Outfit) -> Unit
) : RecyclerView.Adapter<OutfitAdapter.OutfitViewHolder>() {

    inner class OutfitViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvOutfitName: TextView = itemView.findViewById(R.id.tvOutfitName)
        val tvItemCount: TextView = itemView.findViewById(R.id.tvItemCount)
        val ivOutfitPreview: ImageView = itemView.findViewById(R.id.ivOutfitPreview)
        val btnEdit: ImageButton = itemView.findViewById(R.id.btnEdit)
        val btnDelete: ImageButton = itemView.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OutfitViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_scheduled_outfit, parent, false)
        return OutfitViewHolder(view)
    }

    override fun onBindViewHolder(holder: OutfitViewHolder, position: Int) {
        val outfit = outfits[position]

        holder.tvOutfitName.text = outfit.name
        holder.tvItemCount.text = "${outfit.items.size} items"

        // Load first item's image as preview
        if (outfit.items.isNotEmpty()) {
            Glide.with(holder.itemView.context)
                .load(outfit.items[0].imageUrl)
                .placeholder(R.drawable.default_img)
                .error(R.drawable.default_img)
                .into(holder.ivOutfitPreview)
        } else {
            holder.ivOutfitPreview.setImageResource(R.drawable.default_img)
        }

        holder.itemView.setOnClickListener {
            onItemClick(outfit)
        }

        holder.btnEdit.setOnClickListener {
            onEditClick(outfit)
        }

        holder.btnDelete.setOnClickListener {
            onDeleteClick(outfit)
        }
    }

    override fun getItemCount(): Int = outfits.size

    fun updateOutfits(newOutfits: List<Outfit>) {
        outfits = newOutfits
        notifyDataSetChanged()
    }
}