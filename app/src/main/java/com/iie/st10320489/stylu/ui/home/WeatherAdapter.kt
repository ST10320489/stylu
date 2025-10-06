package com.iie.st10320489.stylu.ui.home

import android.util.Log
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

    companion object {
        private const val TAG = "WeatherAdapter"
    }

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

        // Debug: Log what we're receiving
        Log.d(TAG, "Position $position: Day=${weather.day}, Condition='${weather.condition}', Background='${weather.background}'")

        // Set icon based on condition
        val iconRes = when(weather.condition) {
            "sun" -> {
                Log.d(TAG, "  -> Setting SUNNY icon")
                R.drawable.sunny
            }
            "cloudy" -> {
                Log.d(TAG, "  -> Setting CLOUDY icon")
                R.drawable.cloudy
            }
            "rain" -> {
                Log.d(TAG, "  -> Setting RAIN icon")
                R.drawable.rain
            }
            "thunder" -> {
                Log.d(TAG, "  -> Setting THUNDER icon")
                R.drawable.thunder
            }
            else -> {
                Log.e(TAG, "  -> UNKNOWN condition: '${weather.condition}' - defaulting to sunny")
                R.drawable.sunny
            }
        }
        holder.ivWeatherIcon.setImageResource(iconRes)

        // Set gradient background based on condition
        val gradientRes = when(weather.background) {
            "sun" -> {
                Log.d(TAG, "  -> Setting SUN gradient")
                R.drawable.gradient_sun
            }
            "cloudy" -> {
                Log.d(TAG, "  -> Setting CLOUDY gradient")
                R.drawable.gradient_cloud
            }
            "rain" -> {
                Log.d(TAG, "  -> Setting RAIN gradient")
                R.drawable.gradient_rain
            }
            else -> {
                Log.e(TAG, "  -> UNKNOWN background: '${weather.background}' - defaulting to sun")
                R.drawable.gradient_sun
            }
        }
        holder.gradientContainer.background =
            ContextCompat.getDrawable(holder.itemView.context, gradientRes)
    }
}