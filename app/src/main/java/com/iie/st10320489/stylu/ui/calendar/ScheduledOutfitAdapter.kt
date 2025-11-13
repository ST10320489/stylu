package com.iie.st10320489.stylu.ui.calendar

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.iie.st10320489.stylu.R
import com.iie.st10320489.stylu.data.models.calendar.ScheduledOutfit
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class ScheduledOutfitAdapter(
    private val onItemClick: (ScheduledOutfit) -> Unit,
    private val onDeleteClick: (ScheduledOutfit) -> Unit
) : ListAdapter<ScheduledOutfit, ScheduledOutfitAdapter.ViewHolder>(DiffCallback()) {



    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvOutfitName: TextView = itemView.findViewById(R.id.tvOutfitName)
        private val tvScheduledDate: TextView = itemView.findViewById(R.id.tvScheduledDate)
        private val tvWeatherInfo: TextView = itemView.findViewById(R.id.tvWeatherInfo)
        private val outfitPreviewContainer: FrameLayout = itemView.findViewById(R.id.outfitPreviewContainer)
        private val btnDeleteSchedule: ImageButton = itemView.findViewById(R.id.btnDeleteSchedule)
        private val colorDotsContainer: LinearLayout = itemView.findViewById(R.id.colorDotsContainer)

        // ✅ ADD: Keep reference to the outfit image
        private var outfitImageView: ImageView? = null

        fun bind(scheduledOutfit: ScheduledOutfit) {
            // Display outfit name
            tvOutfitName.text = scheduledOutfit.outfit.name

            // Display date
            val dateFormat = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
            tvScheduledDate.text = dateFormat.format(scheduledOutfit.date)

            // Display weather if available
            scheduledOutfit.weatherForecast?.let { weather ->
                tvWeatherInfo.text = "${weather.minTemp}°-${weather.maxTemp}° • ${weather.condition}"
                tvWeatherInfo.visibility = View.VISIBLE
            } ?: run {
                tvWeatherInfo.visibility = View.GONE
            }

            // ✅ FIXED: Only remove the image view, not the delete button!
            outfitImageView?.let { outfitPreviewContainer.removeView(it) }

            // Try to load saved outfit image first
            val savedImagePath = getSavedOutfitImagePath(scheduledOutfit.outfit.outfitId, itemView.context)
            if (savedImagePath.isNotEmpty()) {
                // Display saved bitmap
                outfitImageView = ImageView(itemView.context).apply {
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
                    .into(outfitImageView!!)

                // ✅ Add at index 0 so delete button stays on top
                outfitPreviewContainer.addView(outfitImageView, 0)

            } else if (scheduledOutfit.outfit.items.isNotEmpty()) {
                // Fallback: Show first item's image
                outfitImageView = ImageView(itemView.context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }

                Glide.with(itemView.context)
                    .load(scheduledOutfit.outfit.items.first().imageUrl)
                    .placeholder(R.drawable.cloudy)
                    .error(R.drawable.sunny)
                    .into(outfitImageView!!)

                // ✅ Add at index 0 so delete button stays on top
                outfitPreviewContainer.addView(outfitImageView, 0)
            }

            // Draw color dots with actual colors from items
            colorDotsContainer.removeAllViews()
            scheduledOutfit.outfit.items.take(5).forEach { item ->
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
                            setBackgroundColor(Color.parseColor(colour))
                        } catch (e: Exception) {
                            setBackgroundColor(Color.GRAY)
                        }
                    }
                }
                colorDotsContainer.addView(dot)
            }

            // Click listeners
            itemView.setOnClickListener {
                onItemClick(scheduledOutfit)
            }

            btnDeleteSchedule.setOnClickListener {
                onDeleteClick(scheduledOutfit)
            }
        }

        private fun getSavedOutfitImagePath(outfitId: Int, context: Context): String {
            val file = File(context.filesDir, "outfit_$outfitId.png")
            return if (file.exists()) file.absolutePath else ""
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_scheduled_outfit, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class DiffCallback : DiffUtil.ItemCallback<ScheduledOutfit>() {
        override fun areItemsTheSame(oldItem: ScheduledOutfit, newItem: ScheduledOutfit) =
            oldItem.scheduleId == newItem.scheduleId

        override fun areContentsTheSame(oldItem: ScheduledOutfit, newItem: ScheduledOutfit) =
            oldItem == newItem
    }
}