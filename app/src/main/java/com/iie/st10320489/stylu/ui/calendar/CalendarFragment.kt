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
import java.util.*

class CalendarFragment : Fragment() {
    private var _binding: FragmentCalendarBinding? = null
    private val binding get() = _binding!!
    private lateinit var calendarRepository: CalendarRepository
    private lateinit var apiService: ApiService
    private lateinit var adapter: ScheduledOutfitAdapter
    private var selectedDate: Date = Date()

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
        loadScheduledOutfits()
    }

    private fun setupCalendarView() {
        binding.calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            val calendar = Calendar.getInstance()
            calendar.set(year, month, dayOfMonth)
            selectedDate = calendar.time
            Log.d(TAG, "Date selected: $selectedDate")
            loadScheduledOutfits()
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
            showOutfitSelectionDialog()
        }
    }

    private fun showOutfitSelectionDialog() {
        lifecycleScope.launch {
            try {
                binding.progressBar.visibility = View.VISIBLE

                val result = apiService.getUserOutfits()

                result.onSuccess { outfits ->
                    binding.progressBar.visibility = View.GONE

                    if (outfits.isEmpty()) {
                        Toast.makeText(
                            requireContext(),
                            "No outfits available. Create an outfit first!",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@onSuccess
                    }

                    val outfitNames = outfits.map { it.name }.toTypedArray()

                    AlertDialog.Builder(requireContext())
                        .setTitle("Select Outfit to Schedule")
                        .setItems(outfitNames) { _, which ->
                            val selectedOutfit = outfits[which]
                            showScheduleDialog(selectedOutfit.outfitId, selectedOutfit.name)
                        }
                        .setNegativeButton("Cancel", null)
                        .show()

                }.onFailure { error ->
                    binding.progressBar.visibility = View.GONE

                    val message = when {
                        error.message?.contains("Authentication failed") == true ||
                                error.message?.contains("Session expired") == true -> {
                            "Your session has expired. Please login again."
                        }
                        error.message?.contains("timed out") == true ||
                                error.message?.contains("cold start") == true -> {
                            "Server is starting up. Please try again in a moment."
                        }
                        error.message?.contains("connect") == true -> {
                            "Cannot connect to server. Please check your internet connection."
                        }
                        else -> "Failed to load outfits: ${error.message}"
                    }

                    Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                Log.e(TAG, "Error showing outfit selection", e)
                Toast.makeText(
                    requireContext(),
                    "Unexpected error: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun showScheduleDialog(outfitId: Int, outfitName: String) {
        val dialog = ScheduleOutfitDialog(
            outfitId = outfitId,
            outfitName = outfitName,
            initialDate = selectedDate,
            onScheduled = {
                loadScheduledOutfits()
            }
        )
        dialog.show(parentFragmentManager, "ScheduleOutfitDialog")
    }

    private fun loadScheduledOutfits() {
        lifecycleScope.launch {
            try {
                binding.progressBar.visibility = View.VISIBLE

                val calendar = Calendar.getInstance()
                calendar.time = selectedDate
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                val startDate = calendar.time

                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                val endDate = calendar.time

                Log.d(TAG, "Loading schedules from $startDate to $endDate")

                val result = calendarRepository.getScheduledOutfits(startDate, endDate)

                result.onSuccess { outfits ->
                    Log.d(TAG, "Successfully loaded ${outfits.size} scheduled outfits")

                    adapter.submitList(outfits)

                    if (outfits.isEmpty()) {
                        binding.tvNoScheduled.visibility = View.VISIBLE
                        binding.rvScheduledOutfits.visibility = View.GONE
                        Log.d(TAG, "No outfits scheduled for selected date")
                    } else {
                        binding.tvNoScheduled.visibility = View.GONE
                        binding.rvScheduledOutfits.visibility = View.VISIBLE
                    }

                }.onFailure { error ->
                    Log.e(TAG, "Failed to load scheduled outfits", error)

                    val message = when {
                        error.message?.contains("Authentication failed") == true ||
                                error.message?.contains("Session expired") == true -> {
                            "Your session has expired. Please login again."
                        }
                        error.message?.contains("timed out") == true ||
                                error.message?.contains("cold start") == true -> {
                            "Server is starting up. Please wait a moment and try again."
                        }
                        error.message?.contains("connect") == true -> {
                            "Cannot connect to server. Please check your internet connection."
                        }
                        else -> "Failed to load schedule: ${error.message}"
                    }

                    Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                Log.e(TAG, "Exception loading scheduled outfits", e)
                Toast.makeText(
                    requireContext(),
                    "Unexpected error: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                binding.progressBar.visibility = View.GONE
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

    private fun deleteSchedule(scheduleId: Int) {
        lifecycleScope.launch {
            try {
                binding.progressBar.visibility = View.VISIBLE

                val result = calendarRepository.deleteScheduledOutfit(scheduleId)

                result.onSuccess {
                    Toast.makeText(
                        requireContext(),
                        "Removed from calendar",
                        Toast.LENGTH_SHORT
                    ).show()
                    loadScheduledOutfits()
                }.onFailure { error ->
                    val message = when {
                        error.message?.contains("Authentication failed") == true ||
                                error.message?.contains("Session expired") == true -> {
                            "Your session has expired. Please login again."
                        }
                        error.message?.contains("not found") == true ||
                                error.message?.contains("already deleted") == true -> {
                            "Schedule not found or already deleted."
                        }
                        error.message?.contains("timed out") == true -> {
                            "Request timed out. Please try again."
                        }
                        error.message?.contains("connect") == true -> {
                            "Cannot connect to server. Please check your internet connection."
                        }
                        else -> "Failed to remove: ${error.message}"
                    }

                    Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    "Unexpected error: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume - reloading scheduled outfits")
        loadScheduledOutfits()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}