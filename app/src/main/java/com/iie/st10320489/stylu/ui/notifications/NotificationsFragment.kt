package com.iie.st10320489.stylu.ui.notifications

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.iie.st10320489.stylu.R
import com.iie.st10320489.stylu.data.models.notifications.Notification

import com.iie.st10320489.stylu.repository.NotificationRepository
import kotlinx.coroutines.launch

class NotificationsFragment : Fragment() {

    private lateinit var rvNotifications: RecyclerView
    private lateinit var adapter: NotificationsAdapter
    private val notificationsList = mutableListOf<Notification>()
    private lateinit var repository: NotificationRepository

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_notifications, container, false)
        rvNotifications = view.findViewById(R.id.rvNotifications)
        rvNotifications.layoutManager = LinearLayoutManager(requireContext())
        adapter = NotificationsAdapter(notificationsList)
        rvNotifications.adapter = adapter

        repository = NotificationRepository(requireContext())

        fetchNotifications()

        return view
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun fetchNotifications() {
        lifecycleScope.launch {
            try {

                val prefs = requireContext().getSharedPreferences("stylu_prefs", 0)
                val accessToken = prefs.getString("access_token", null) ?: return@launch

                val client = okhttp3.OkHttpClient()
                val request = okhttp3.Request.Builder()
                    .url("https://fkmhmtioehokrukqwano.supabase.co/rest/v1/notifications?user_id=eq.${prefs.getInt("user_id",0)}&order=scheduled_at.desc")
                    .get()
                    .addHeader("apikey", repository.SUPABASE_ANON_KEY)
                    .addHeader("Authorization", "Bearer $accessToken")
                    .addHeader("Content-Type", "application/json")
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    val jsonArray = org.json.JSONArray(body ?: "[]")
                    notificationsList.clear()
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        notificationsList.add(
                            Notification(
                                id = obj.getInt("notifications_id"),
                                userId = obj.getInt("user_id"),
                                title = obj.getString("title"),
                                message = obj.getString("message"),
                                type = obj.optString("type", "general"),
                                scheduledAt = obj.getString("scheduled_at"),
                                sentAt = obj.optString("sent_at", null),
                                status = obj.optString("status", "queued")
                            )
                        )
                    }
                    adapter.notifyDataSetChanged()
                } else {
                    Toast.makeText(requireContext(), "Failed to fetch notifications", Toast.LENGTH_SHORT).show()
                }
                response.close()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
