package com.iie.st10320489.stylu.ui.notifications

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.iie.st10320489.stylu.R
import com.iie.st10320489.stylu.data.models.notifications.Notification
import com.iie.st10320489.stylu.repository.NotificationRepository
import kotlinx.coroutines.launch

class NotificationsFragment : Fragment() {

    private var rvNotifications: RecyclerView? = null
    private var tvEmptyState: LinearLayout? = null  // ✅ CHANGED FROM TextView to LinearLayout
    private var progressBar: ProgressBar? = null
    private lateinit var adapter: NotificationsAdapter
    private val notificationsList = mutableListOf<Notification>()
    private var repository: NotificationRepository? = null

    companion object {
        private const val TAG = "NotificationsFragment"
    }

    // BroadcastReceiver to listen for new notifications
    private val notificationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.iie.st10320489.stylu.NEW_NOTIFICATION") {
                Log.d(TAG, "📬 Received broadcast: NEW_NOTIFICATION")
                fetchNotifications()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.d(TAG, "onCreateView called")

        return try {
            val view = inflater.inflate(R.layout.fragment_notifications, container, false)

            // Initialize views - ✅ FIXED: tvEmptyState is a LinearLayout, not TextView
            rvNotifications = view.findViewById(R.id.rvNotifications)
            tvEmptyState = view.findViewById(R.id.tvEmptyState)  // This is now LinearLayout
            progressBar = view.findViewById(R.id.progressBar)

            // Setup RecyclerView
            rvNotifications?.layoutManager = LinearLayoutManager(requireContext())
            adapter = NotificationsAdapter(notificationsList)
            rvNotifications?.adapter = adapter

            // ✅ FIXED: Initialize repository in onCreateView, not later
            repository = NotificationRepository(requireContext())

            Log.d(TAG, "✅ Views initialized successfully")

            view
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in onCreateView: ${e.message}", e)
            Toast.makeText(context, "Error loading notifications", Toast.LENGTH_SHORT).show()
            inflater.inflate(R.layout.fragment_notifications, container, false)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Load notifications after view is created
        fetchNotifications()
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume called")

        try {
            // Register broadcast receiver
            val filter = IntentFilter("com.iie.st10320489.stylu.NEW_NOTIFICATION")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requireContext().registerReceiver(
                    notificationReceiver,
                    filter,
                    Context.RECEIVER_NOT_EXPORTED
                )
            } else {
                ContextCompat.registerReceiver(
                    requireContext(),
                    notificationReceiver,
                    filter,
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )
            }

            Log.d(TAG, "✅ Broadcast receiver registered")

            // Refresh when returning to fragment
            fetchNotifications()

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in onResume: ${e.message}", e)
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause called")

        // Unregister broadcast receiver
        try {
            requireContext().unregisterReceiver(notificationReceiver)
            Log.d(TAG, "✅ Broadcast receiver unregistered")
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver: ${e.message}")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG, "onDestroyView called")

        // Clean up references
        rvNotifications = null
        tvEmptyState = null
        progressBar = null
        repository = null
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun fetchNotifications() {
        lifecycleScope.launch {
            try {
                Log.d(TAG, "📥 Fetching notifications...")
                showLoading(true)

                // Check if repository is initialized
                val repo = repository
                if (repo == null) {
                    Log.e(TAG, "Repository is null")
                    showLoading(false)
                    return@launch
                }

                val notifications = repo.getUserNotifications()

                notificationsList.clear()
                notificationsList.addAll(notifications)
                adapter.notifyDataSetChanged()

                Log.d(TAG, "✅ Loaded ${notifications.size} notifications")

                // Show empty state if no notifications
                if (notifications.isEmpty()) {
                    showEmptyState(true)
                    Log.d(TAG, "📭 No notifications found")
                } else {
                    showEmptyState(false)
                    Log.d(TAG, "📬 ${notifications.size} notifications displayed")
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error fetching notifications: ${e.message}", e)

                // Show error to user only if fragment is still attached
                if (isAdded && context != null) {
                    Toast.makeText(
                        requireContext(),
                        "Error loading notifications: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } finally {
                showLoading(false)
            }
        }
    }

    private fun showLoading(isLoading: Boolean) {
        try {
            progressBar?.visibility = if (isLoading) View.VISIBLE else View.GONE
            rvNotifications?.visibility = if (isLoading) View.GONE else View.VISIBLE
        } catch (e: Exception) {
            Log.e(TAG, "Error in showLoading: ${e.message}")
        }
    }

    private fun showEmptyState(isEmpty: Boolean) {
        try {
            tvEmptyState?.visibility = if (isEmpty) View.VISIBLE else View.GONE
            rvNotifications?.visibility = if (isEmpty) View.GONE else View.VISIBLE
        } catch (e: Exception) {
            Log.e(TAG, "Error in showEmptyState: ${e.message}")
        }
    }
}