package com.iie.st10320489.stylu.ui.calendar

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.iie.st10320489.stylu.databinding.FragmentCalendarBinding
import com.iie.st10320489.stylu.repository.CalendarRepository
import com.iie.st10320489.stylu.network.ApiService
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class CalendarFragment : Fragment() {

    private var _binding: FragmentCalendarBinding? = null
    private val binding get() = _binding!!

    private lateinit var calendarRepository: CalendarRepository
    private lateinit var apiService: ApiService
    private lateinit var adapter: ScheduledOutfitAdapter

    private var selectedDate: Date = Date()
    private var isFirstLoad = true

    companion object {
        private const val TAG = "CalendarFragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCalendarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        calendarRepository = CalendarRepository(requireContext())
        apiService = ApiService(requireContext())

        setupCalendarView()
        setupRecyclerView()
        setupFab()
        setupSwipeRefresh()

        loadScheduledOutfitsWithCaching()
    }

    private fun setupCalendarView() {
        binding.calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            val calendar = Calendar.getInstance()
            calendar.set(year, month, dayOfMonth)
            selectedDate = calendar.time
            Log.d(TAG, "Date selected: $selectedDate")

            isFirstLoad = false
            loadScheduledOutfitsWithCaching()
        }
    }

    private fun setupRecyclerView() {
        adapter = ScheduledOutfitAdapter(
            onItemClick = { scheduledOutfit ->
                Toast.makeText(
                    requireContext(),
                    "Outfit: ${scheduledOutfit.outfit.name}",
                    Toast.LENGTH_SHORT
                ).show()
            },
            onDeleteClick = { scheduledOutfit ->
                confirmDelete(scheduledOutfit)
            }
        )

        binding.rvScheduledOutfits.layoutManager = LinearLayoutManager(requireContext())
        binding.rvScheduledOutfits.adapter = adapter
    }

    private fun setupFab() {
        binding.fabAddSchedule.setOnClickListener {
            val bottomSheet = ScheduleOutfitBottomSheet(
                onScheduled = {
                    isFirstLoad = false
                    loadScheduledOutfitsWithCaching(forceRefresh = true)
                },
                initialDate = selectedDate
            )
            bottomSheet.show(parentFragmentManager, "ScheduleOutfitBottomSheet")
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh?.setOnRefreshListener {
            isFirstLoad = false
            loadScheduledOutfitsWithCaching(forceRefresh = true)
        }

        binding.swipeRefresh?.setColorSchemeResources(
            com.iie.st10320489.stylu.R.color.purple_primary,
            com.iie.st10320489.stylu.R.color.orange_secondary
        )
    }

    private fun loadScheduledOutfitsWithCaching(forceRefresh: Boolean = false) {
        lifecycleScope.launch {
            try {
                if (isFirstLoad && !forceRefresh) {
                    showLoadingWithMessage(
                        show = true,
                        message = "Loading scheduled outfits...\n\n" +
                                "First load may take up to 60 seconds if the server is starting up."
                    )
                } else {
                    showLoading(true)
                }

                val calendar = Calendar.getInstance()
                calendar.time = selectedDate
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startDate = calendar.time

                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                calendar.set(Calendar.MILLISECOND, 999)
                val endDate = calendar.time

                val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                Log.d(TAG, "Selected date: ${df.format(selectedDate)}")
                Log.d(TAG, "Query range: ${df.format(startDate)} to ${df.format(endDate)}")

                val result = calendarRepository.getScheduledOutfits(startDate, endDate)

                result.onSuccess { outfits ->
                    Log.d(TAG, "Loaded ${outfits.size} scheduled outfits")

                    adapter.submitList(outfits)

                    if (outfits.isEmpty()) {
                        binding.tvNoScheduled.visibility = View.VISIBLE
                        binding.rvScheduledOutfits.visibility = View.GONE
                    } else {
                        binding.tvNoScheduled.visibility = View.GONE
                        binding.rvScheduledOutfits.visibility = View.VISIBLE
                    }

                    isFirstLoad = false

                }.onFailure { error ->
                    handleLoadError(error)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception loading scheduled outfits", e)
                Toast.makeText(
                    requireContext(),
                    "Error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                showLoading(false)
                showLoadingWithMessage(show = false, message = "")
                binding.swipeRefresh?.isRefreshing = false
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
            error.message?.contains("Authentication failed") == true ||
                    error.message?.contains("Session expired") == true -> {
                "Session expired. Showing cached data."
            }
            error.message?.contains("cached data") == true -> {
                "Offline mode - showing cached data"
            }
            error.message?.contains("connect") == true -> {
                "No internet - showing cached data"
            }
            else -> "Using cached data: ${error.message}"
        }

        Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT).show()
    }

    private fun confirmDelete(scheduledOutfit: com.iie.st10320489.stylu.data.models.calendar.ScheduledOutfit) {
        AlertDialog.Builder(requireContext())
            .setTitle("Remove Schedule")
            .setMessage("Remove '${scheduledOutfit.outfit.name}' from your calendar?")
            .setPositiveButton("Remove") { _, _ ->
                deleteSchedule(scheduledOutfit.scheduleId)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteSchedule(scheduleId: Int) {
        lifecycleScope.launch {
            try {
                showLoading(true)

                val result = calendarRepository.deleteScheduledOutfit(scheduleId)

                result.onSuccess {
                    Toast.makeText(
                        requireContext(),
                        "Removed from calendar",
                        Toast.LENGTH_SHORT
                    ).show()
                    loadScheduledOutfitsWithCaching(forceRefresh = true)
                }.onFailure { error ->
                    val message = when {
                        error.message?.contains("Authentication failed") == true ||
                                error.message?.contains("Session expired") == true -> {
                            "Session expired. Schedule removed locally."
                        }
                        error.message?.contains("not found") == true ||
                                error.message?.contains("already deleted") == true -> {
                            "Schedule not found or already deleted."
                        }
                        error.message?.contains("timed out") == true -> {
                            "Request timed out. Removed locally."
                        }
                        error.message?.contains("connect") == true -> {
                            "Offline - removed from local cache"
                        }
                        else -> "Failed to remove: ${error.message}"
                    }

                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    "Error: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                showLoading(false)
            }
        }
    }

    private fun showLoadingWithMessage(show: Boolean, message: String) {
        _binding?.let { binding ->
            if (show) {
                binding.progressBar.visibility = View.VISIBLE
                binding.swipeRefresh?.visibility = View.GONE
                if (message.isNotEmpty()) {
                    Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                }
            } else {
                binding.progressBar.visibility = View.GONE
                binding.swipeRefresh?.visibility = View.VISIBLE
            }
        }
    }

    private fun showLoading(show: Boolean) {
        _binding?.let { binding ->
            binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
            if (!show) {
                binding.swipeRefresh?.visibility = View.VISIBLE
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume - refreshing from API")
        isFirstLoad = false
        loadScheduledOutfitsWithCaching(forceRefresh = false)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}