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
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.iie.st10320489.stylu.R
import com.iie.st10320489.stylu.data.models.notifications.Notification
import com.iie.st10320489.stylu.repository.NotificationRepository
import kotlinx.coroutines.launch

class NotificationsFragment : Fragment() {

    private var rvNotifications: RecyclerView? = null
    private var tvEmptyState: LinearLayout? = null
    private var progressBar: ProgressBar? = null
    private var swipeRefresh: SwipeRefreshLayout? = null
    private lateinit var adapter: NotificationsAdapter
    private val notificationsList = mutableListOf<Notification>()
    private var repository: NotificationRepository? = null

    // Track if this is the first load
    private var isFirstLoad = true

    companion object {
        private const val TAG = "NotificationsFragment"
    }

    // BroadcastReceiver to listen for new notifications
    private val notificationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.iie.st10320489.stylu.NEW_NOTIFICATION") {
                Log.d(TAG, "Received broadcast: NEW_NOTIFICATION")
                isFirstLoad = false
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

            // Initialize views
            rvNotifications = view.findViewById(R.id.rvNotifications)
            tvEmptyState = view.findViewById(R.id.tvEmptyState)
            progressBar = view.findViewById(R.id.progressBar)
            swipeRefresh = view.findViewById(R.id.swipeRefresh)

            // Setup RecyclerView
            rvNotifications?.layoutManager = LinearLayoutManager(requireContext())
            adapter = NotificationsAdapter(notificationsList)
            rvNotifications?.adapter = adapter

            // Initialize repository in onCreateView
            repository = NotificationRepository(requireContext())


            setupSwipeRefresh()

            Log.d(TAG, "Views initialized successfully")

            view
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreateView: ${e.message}", e)
            Toast.makeText(context, "Error loading notifications", Toast.LENGTH_SHORT).show()
            inflater.inflate(R.layout.fragment_notifications, container, false)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Load notifications after view is created
        fetchNotifications()
    }


    private fun setupSwipeRefresh() {
        swipeRefresh?.setOnRefreshListener {
            isFirstLoad = false
            fetchNotifications(forceRefresh = true)
        }

        swipeRefresh?.setColorSchemeResources(
            R.color.purple_primary,
            R.color.orange_secondary
        )
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

            Log.d(TAG, "Broadcast receiver registered")

            // Refresh when returning to fragment (but don't show first load message)
            isFirstLoad = false
            fetchNotifications()

        } catch (e: Exception) {
            Log.e(TAG, "Error in onResume: ${e.message}", e)
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause called")

        // Unregister broadcast receiver
        try {
            requireContext().unregisterReceiver(notificationReceiver)
            Log.d(TAG, "Broadcast receiver unregistered")
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
        swipeRefresh = null
        repository = null
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun fetchNotifications(forceRefresh: Boolean = false) {
        lifecycleScope.launch {
            try {
                Log.d(TAG, "Fetching notifications...")

                // Show loading message only on first load
                if (isFirstLoad && !forceRefresh) {
                    showLoadingWithMessage(
                        show = true,
                        message = "Loading notifications...\n\n" +
                                "First load may take up to 60 seconds if the server is starting up."
                    )
                } else {
                    showLoading(true)
                }

                // Check if repository is initialized
                val repo = repository
                if (repo == null) {
                    Log.e(TAG, "Repository is null")
                    showLoading(false)
                    showLoadingWithMessage(show = false, message = "")
                    swipeRefresh?.isRefreshing = false
                    return@launch
                }

                val notifications = repo.getUserNotifications()

                notificationsList.clear()
                notificationsList.addAll(notifications)
                adapter.notifyDataSetChanged()

                Log.d(TAG, "Loaded ${notifications.size} notifications")

                // Show empty state if no notifications
                if (notifications.isEmpty()) {
                    showEmptyState(true)
                    Log.d(TAG, "No notifications found")
                } else {
                    showEmptyState(false)
                    Log.d(TAG, "${notifications.size} notifications displayed")
                }

                // Mark first load as complete
                isFirstLoad = false

            } catch (e: Exception) {
                Log.e(TAG, "Error fetching notifications: ${e.message}", e)
                handleLoadError(e)
            } finally {
                showLoading(false)
                showLoadingWithMessage(show = false, message = "")
                swipeRefresh?.isRefreshing = false
            }
        }
    }


    private fun handleLoadError(error: Throwable) {
        val errorMessage = when {
            error.message?.contains("timed out", ignoreCase = true) == true -> {
                "Server is starting up. This can take up to 60 seconds on first request.\n\n" +
                        "Please try again in a moment."
            }
            error.message?.contains("starting up", ignoreCase = true) == true -> {
                error.message ?: "Server is starting..."
            }
            error.message?.contains("authentication", ignoreCase = true) == true -> {
                "Authentication failed. Please log in again."
            }
            else -> "Error loading notifications: ${error.message}"
        }

        // Show error to user only if fragment is still attached
        if (isAdded && context != null) {
            Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show()
        }
    }


    private fun showLoadingWithMessage(show: Boolean, message: String) {
        try {
            if (show) {
                progressBar?.visibility = View.VISIBLE
                swipeRefresh?.visibility = View.GONE
                if (message.isNotEmpty() && isAdded && context != null) {
                    Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                }
            } else {
                progressBar?.visibility = View.GONE
                swipeRefresh?.visibility = View.VISIBLE
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in showLoadingWithMessage: ${e.message}")
        }
    }

    private fun showLoading(isLoading: Boolean) {
        try {
            progressBar?.visibility = if (isLoading) View.VISIBLE else View.GONE
            // Don't hide content for quick refreshes
            if (!isLoading) {
                swipeRefresh?.visibility = View.VISIBLE
            }
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