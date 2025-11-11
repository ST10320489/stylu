package com.iie.st10320489.stylu.ui.calendar

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.iie.st10320489.stylu.R
import java.time.LocalDate

class CalendarAdapter(
    private val days: List<LocalDate>,
    private var selectedDate: LocalDate,
    private val onDateClick: (LocalDate) -> Unit
) : RecyclerView.Adapter<CalendarAdapter.DayViewHolder>() {

    private var scheduledDates: Set<LocalDate> = emptySet()

    inner class DayViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val dayText: TextView = itemView.findViewById(R.id.tvDay)
        val indicator: View? = itemView.findViewById(R.id.vIndicator)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_calendar_day, parent, false)
        return DayViewHolder(view)
    }

    override fun onBindViewHolder(holder: DayViewHolder, position: Int) {
        val date = days[position]
        val dayOfMonth = date.dayOfMonth

        holder.dayText.text = dayOfMonth.toString()

        // Style for selected date
        if (date == selectedDate) {
            holder.dayText.setBackgroundResource(R.drawable.bg_indicator)
            holder.dayText.setTextColor(Color.WHITE)
        } else {
            holder.dayText.background = null
            holder.dayText.setTextColor(Color.BLACK)
        }

        // Show indicator for scheduled dates
        holder.indicator?.visibility = if (scheduledDates.contains(date)) {
            View.VISIBLE
        } else {
            View.GONE
        }

        // Dim dates from other months
        val currentMonth = selectedDate.month
        if (date.month != currentMonth) {
            holder.dayText.alpha = 0.3f
        } else {
            holder.dayText.alpha = 1.0f
        }

        holder.itemView.setOnClickListener {
            onDateClick(date)
        }
    }

    override fun getItemCount(): Int = days.size

    fun setSelectedDate(date: LocalDate) {
        selectedDate = date
        notifyDataSetChanged()
    }

    fun setScheduledDates(dates: List<LocalDate>) {
        scheduledDates = dates.toSet()
        notifyDataSetChanged()
    }
}