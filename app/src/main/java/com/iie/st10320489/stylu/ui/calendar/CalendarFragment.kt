package com.iie.st10320489.stylu.ui.calendar

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.iie.st10320489.stylu.R
import com.iie.st10320489.stylu.databinding.FragmentCalendarBinding
import com.iie.st10320489.stylu.repository.CalendarRepository
import kotlinx.coroutines.launch
import java.util.*

class CalendarFragment : Fragment() {

    private var _binding: FragmentCalendarBinding? = null
    private val binding get() = _binding!!

    private lateinit var calendarRepository: CalendarRepository
    private lateinit var adapter: ScheduledOutfitAdapter

    private var selectedDate: Date = Date()

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

        setupCalendarView()
        setupRecyclerView()
        loadScheduledOutfits()
    }

    private fun setupCalendarView() {
        binding.calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            val calendar = Calendar.getInstance()
            calendar.set(year, month, dayOfMonth)
            selectedDate = calendar.time

            loadScheduledOutfits()
        }
    }

    private fun setupRecyclerView() {
        adapter = ScheduledOutfitAdapter(
            onItemClick = { scheduledOutfit ->
                showOutfitDetails(scheduledOutfit)
            },
            onDeleteClick = { scheduledOutfit ->
                confirmDelete(scheduledOutfit)
            }
        )

        binding.rvScheduledOutfits.layoutManager = LinearLayoutManager(requireContext())
        binding.rvScheduledOutfits.adapter = adapter
    }

    private fun loadScheduledOutfits() {
        lifecycleScope.launch {
            try {
                binding.progressBar.visibility = View.VISIBLE

                val calendar = Calendar.getInstance()
                calendar.time = selectedDate
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                val startDate = calendar.time

                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                val endDate = calendar.time

                val result = calendarRepository.getScheduledOutfits(startDate, endDate)

                result.onSuccess { outfits ->
                    adapter.submitList(outfits)

                    binding.tvNoScheduled.visibility = if (outfits.isEmpty()) {
                        View.VISIBLE
                    } else {
                        View.GONE
                    }
                }.onFailure { error ->
                    Toast.makeText(
                        requireContext(),
                        "Failed to load: ${error.message}",
                        Toast.LENGTH_SHORT
                    ).show()
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

    private fun showOutfitDetails(scheduledOutfit: com.iie.st10320489.stylu.data.models.calendar.ScheduledOutfit) {
        // Navigate to outfit detail or show dialog
        Toast.makeText(
            requireContext(),
            "Outfit: ${scheduledOutfit.outfit.name}",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun confirmDelete(scheduledOutfit: com.iie.st10320489.stylu.data.models.calendar.ScheduledOutfit) {
        AlertDialog.Builder(requireContext())
            .setTitle("Remove Schedule")
            .setMessage("Remove this outfit from your calendar?")
            .setPositiveButton("Remove") { _, _ ->
                deleteSchedule(scheduledOutfit.scheduleId)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteSchedule(scheduleId: Int) {
        lifecycleScope.launch {
            val result = calendarRepository.deleteScheduledOutfit(scheduleId)

            result.onSuccess {
                Toast.makeText(requireContext(), "Removed from calendar", Toast.LENGTH_SHORT).show()
                loadScheduledOutfits()
            }.onFailure {
                Toast.makeText(requireContext(), "Failed to remove", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}