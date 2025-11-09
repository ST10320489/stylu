package com.iie.st10320489.stylu.ui.calendar

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
import com.iie.st10320489.stylu.R
import com.iie.st10320489.stylu.data.models.calendar.ScheduledOutfit
import java.text.SimpleDateFormat
import java.util.*

class ScheduledOutfitAdapter(
    private val onItemClick: (ScheduledOutfit) -> Unit,
    private val onDeleteClick: (ScheduledOutfit) -> Unit
) : ListAdapter<ScheduledOutfit, ScheduledOutfitAdapter.ViewHolder>(DiffCallback()) {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivOutfit: ImageView = itemView.findViewById(R.id.ivScheduledOutfit)
        private val tvOutfitName: TextView = itemView.findViewById(R.id.tvOutfitName)
        private val tvDate: TextView = itemView.findViewById(R.id.tvScheduledDate)
        private val tvWeather: TextView = itemView.findViewById(R.id.tvWeatherInfo)
        private val btnDelete: ImageButton = itemView.findViewById(R.id.btnDeleteSchedule)

        fun bind(scheduledOutfit: ScheduledOutfit) {
            tvOutfitName.text = scheduledOutfit.outfit.name

            val dateFormat = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
            tvDate.text = dateFormat.format(scheduledOutfit.date)

            scheduledOutfit.weatherForecast?.let { weather ->
                tvWeather.text = "${weather.minTemp}°-${weather.maxTemp}° • ${weather.condition}"
                tvWeather.visibility = View.VISIBLE
            } ?: run {
                tvWeather.visibility = View.GONE
            }

            // Load outfit preview image
            if (scheduledOutfit.outfit.items.isNotEmpty()) {
                Glide.with(itemView.context)
                    .load(scheduledOutfit.outfit.items.first().imageUrl)
                    .placeholder(R.drawable.default_outfit)
                    .into(ivOutfit)
            }

            itemView.setOnClickListener {
                onItemClick(scheduledOutfit)
            }

            btnDelete.setOnClickListener {
                onDeleteClick(scheduledOutfit)
            }
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