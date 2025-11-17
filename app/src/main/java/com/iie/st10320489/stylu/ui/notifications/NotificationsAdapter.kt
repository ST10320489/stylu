package com.iie.st10320489.stylu.ui.notifications

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.iie.st10320489.stylu.R
import com.iie.st10320489.stylu.data.models.notifications.Notification
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class NotificationsAdapter(private val notifications: List<Notification>) :
    RecyclerView.Adapter<NotificationsAdapter.NotificationViewHolder>() {

    class NotificationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTitle: TextView = itemView.findViewById(R.id.tvNotificationTitle)
        val tvMessage: TextView = itemView.findViewById(R.id.tvNotificationMessage)
        val tvTime: TextView = itemView.findViewById(R.id.tvNotificationTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notification, parent, false)
        return NotificationViewHolder(view)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        val notification = notifications[position]

        // Add emoji based on notification type
        val titleWithEmoji = when (notification.type) {
            "new_drop" -> " ${notification.title}"
            "outfit_liked" -> "${notification.title}"
            "reminder" -> " ${notification.title}"
            "social" -> "${notification.title}"
            else -> notification.title
        }

        holder.tvTitle.text = titleWithEmoji
        holder.tvMessage.text = notification.message
        holder.tvTime.text = formatRelativeTime(notification.sentAt ?: notification.scheduledAt)
    }

    override fun getItemCount(): Int = notifications.size

    /**
     * Format timestamp as relative time
     */
    private fun formatRelativeTime(timestamp: String): String {
        try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
            val date = sdf.parse(timestamp) ?: return timestamp

            val now = Date()
            val diff = now.time - date.time

            return when {
                diff < TimeUnit.MINUTES.toMillis(1) -> "Just now"
                diff < TimeUnit.HOURS.toMillis(1) -> {
                    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
                    "$minutes minute${if (minutes > 1) "s" else ""} ago"
                }
                diff < TimeUnit.DAYS.toMillis(1) -> {
                    val hours = TimeUnit.MILLISECONDS.toHours(diff)
                    "$hours hour${if (hours > 1) "s" else ""} ago"
                }
                diff < TimeUnit.DAYS.toMillis(7) -> {
                    val days = TimeUnit.MILLISECONDS.toDays(diff)
                    "$days day${if (days > 1) "s" else ""} ago"
                }
                else -> {
                    // Format as date for older notifications
                    SimpleDateFormat("MMM dd, yyyy", Locale.US).format(date)
                }
            }
        } catch (e: Exception) {
            return timestamp
        }
    }
}