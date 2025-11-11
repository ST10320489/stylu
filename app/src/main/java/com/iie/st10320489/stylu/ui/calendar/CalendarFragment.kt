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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    private var observerJob: Job? = null

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

        // Initial load from API (with cache fallback)
        loadScheduledOutfitsFromApi()

        // Then observe cache for real-time updates
        observeScheduledOutfitsCache()
    }

    private fun setupCalendarView() {
        binding.calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            val calendar = Calendar.getInstance()
            calendar.set(year, month, dayOfMonth)
            selectedDate = calendar.time
            Log.d(TAG, "📅 Date selected: $selectedDate")

            // Reload for new date
            loadScheduledOutfitsFromApi()
            observeScheduledOutfitsCache()
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
                    // Reload after scheduling
                    loadScheduledOutfitsFromApi()
                },
                initialDate = selectedDate
            )
            bottomSheet.show(parentFragmentManager, "ScheduleOutfitBottomSheet")
        }
    }

    /**
     * ✅ ONLINE-FIRST: Load from API, which will cache to Room
     * This triggers once, then Flow observes the cache for updates
     */
    private fun loadScheduledOutfitsFromApi() {
        lifecycleScope.launch {
            try {
                binding.progressBar.visibility = View.VISIBLE

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
                Log.d(TAG, "📅 Selected date: ${df.format(selectedDate)}")
                Log.d(TAG, "📅 Query range: ${df.format(startDate)} to ${df.format(endDate)}")

                // This will try API first, cache the results, then return
                val result = calendarRepository.getScheduledOutfits(startDate, endDate)

                result.onSuccess { outfits ->
                    Log.d(TAG, "✅ Loaded ${outfits.size} scheduled outfits")

                    // Log for debugging
                    outfits.forEachIndexed { index, outfit ->
                        Log.d(TAG, "📦 Outfit $index:")
                        Log.d(TAG, "  - Schedule ID: ${outfit.scheduleId}")
                        Log.d(TAG, "  - Name: ${outfit.outfit.name}")
                        Log.d(TAG, "  - Outfit ID: ${outfit.outfit.outfitId}")
                        Log.d(TAG, "  - Date: ${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(outfit.date)}")
                        Log.d(TAG, "  - Items: ${outfit.outfit.items.size}")
                    }

                    // Note: Flow will update UI, but we show initial state
                    if (outfits.isEmpty()) {
                        binding.tvNoScheduled.visibility = View.VISIBLE
                        binding.rvScheduledOutfits.visibility = View.GONE
                    } else {
                        binding.tvNoScheduled.visibility = View.GONE
                        binding.rvScheduledOutfits.visibility = View.VISIBLE
                    }

                }.onFailure { error ->
                    Log.e(TAG, "❌ Failed to load scheduled outfits", error)

                    val message = when {
                        error.message?.contains("Authentication failed") == true ||
                                error.message?.contains("Session expired") == true -> {
                            "Session expired. Showing cached data."
                        }
                        error.message?.contains("cached data") == true -> {
                            "📱 Offline mode - showing cached data"
                        }
                        error.message?.contains("timed out") == true -> {
                            "Server timeout. Showing cached data."
                        }
                        error.message?.contains("connect") == true -> {
                            "📱 No internet - showing cached data"
                        }
                        else -> "Using cached data: ${error.message}"
                    }

                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Exception loading scheduled outfits", e)
                Toast.makeText(
                    requireContext(),
                    "Error: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    /**
     * ✅ Observe cache for real-time updates
     * This keeps UI in sync with database (which is synced with API when online)
     */
    private fun observeScheduledOutfitsCache() {
        // Cancel previous observer
        observerJob?.cancel()

        observerJob = lifecycleScope.launch {
            try {
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

                // Observe cache for real-time updates
                calendarRepository.getScheduledOutfitsFlow(startDate, endDate)
                    .collect { outfits ->
                        Log.d(TAG, "🔄 Cache update: ${outfits.size} scheduled outfits")

                        adapter.submitList(outfits)

                        if (outfits.isEmpty()) {
                            binding.tvNoScheduled.visibility = View.VISIBLE
                            binding.rvScheduledOutfits.visibility = View.GONE
                        } else {
                            binding.tvNoScheduled.visibility = View.GONE
                            binding.rvScheduledOutfits.visibility = View.VISIBLE
                        }
                    }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Exception observing cache", e)
            }
        }
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

    /**
     * ✅ ONLINE-FIRST: Delete via API, which will also remove from cache
     * Falls back to cache-only delete if offline
     */
    private fun deleteSchedule(scheduleId: Int) {
        lifecycleScope.launch {
            try {
                binding.progressBar.visibility = View.VISIBLE

                val result = calendarRepository.deleteScheduledOutfit(scheduleId)

                result.onSuccess {
                    Toast.makeText(
                        requireContext(),
                        "✅ Removed from calendar",
                        Toast.LENGTH_SHORT
                    ).show()
                    // Flow will automatically update UI
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
                            "📱 Offline - removed from local cache"
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
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume - refreshing from API")
        loadScheduledOutfitsFromApi()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        observerJob?.cancel()
        _binding = null
    }
}