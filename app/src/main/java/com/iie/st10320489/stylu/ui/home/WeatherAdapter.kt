package com.iie.st10320489.stylu.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.iie.st10320489.stylu.R
import com.iie.st10320489.stylu.ui.home.models.DailyWeather

class WeatherAdapter(private val weatherList: List<DailyWeather>) :
    RecyclerView.Adapter<WeatherAdapter.WeatherViewHolder>() {

    class WeatherViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvWeekDay: TextView = itemView.findViewById(R.id.tvWeekDay)
        val ivWeatherIcon: ImageView = itemView.findViewById(R.id.ivWeatherIcon)
        val tvTempMinMax: TextView = itemView.findViewById(R.id.tvTempMinMax)
        val gradientContainer: FrameLayout = itemView.findViewById(R.id.gradientContainer)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WeatherViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_weather_day, parent, false)
        return WeatherViewHolder(view)
    }

    override fun getItemCount() = weatherList.size

    override fun onBindViewHolder(holder: WeatherViewHolder, position: Int) {
        val weather = weatherList[position]

        holder.tvWeekDay.text = weather.day
        holder.tvTempMinMax.text = "${weather.minTemp}°C - ${weather.maxTemp}°C"

        // Set icon based on condition
        val iconRes = when(weather.condition) {
            "sun" -> R.drawable.sunny
            "cloudy" -> R.drawable.cloudy
            "rain" -> R.drawable.rain
            "thunder" -> R.drawable.thunder
            else -> R.drawable.sunny
        }
        holder.ivWeatherIcon.setImageResource(iconRes)

        // Set gradient background based on condition
        val gradientRes = when(weather.background) {
            "sun" -> R.drawable.gradient_sun
            "cloudy" -> R.drawable.gradient_cloud
            "rain" -> R.drawable.gradient_rain
            else -> R.drawable.gradient_sun
        }
        holder.gradientContainer.background =
            ContextCompat.getDrawable(holder.itemView.context, gradientRes)
    }
}
