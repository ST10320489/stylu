package com.iie.st10320489.stylu.ui.calendar

import android.app.DatePickerDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.iie.st10320489.stylu.R
import com.iie.st10320489.stylu.databinding.FragmentCalendarBinding
import com.iie.st10320489.stylu.data.models.outfit.Outfit
import com.iie.st10320489.stylu.repository.OutfitRepository
import com.iie.st10320489.stylu.utils.NotificationHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar

class CalendarFragment : Fragment() {

    private var _binding: FragmentCalendarBinding? = null
    private val binding get() = _binding!!

    private lateinit var calendarAdapter: CalendarAdapter
    private lateinit var outfitAdapter: OutfitAdapter
    private lateinit var outfitRepository: OutfitRepository

    private var selectedDate: LocalDate = LocalDate.now()
    private var allOutfits: List<Outfit> = emptyList()
    private var scheduledOutfits: List<Outfit> = emptyList()
    private lateinit var fabAddSchedule: FloatingActionButton

    // Job management
    private var syncJob: Job? = null

    companion object {
        private const val TAG = "CalendarFragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCalendarBinding.inflate(inflater, container, false)
        outfitRepository = OutfitRepository(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViews()
        setupCalendar()
        setupOutfitList()
        observeOutfits()
        syncData()
    }

    private fun setupViews() {
        Log.d(TAG, "setupViews() called")

        // Add FAB for creating/scheduling outfit
        fabAddSchedule = FloatingActionButton(requireContext()).apply {
            setImageResource(android.R.drawable.ic_input_add)
            setOnClickListener {
                Log.d(TAG, "FAB clicked - showing schedule options dialog")
                showScheduleOptionsDialog()
            }
        }

        // Add FAB to the layout programmatically
        if (binding.root is android.widget.FrameLayout) {
            val params = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.BOTTOM or android.view.Gravity.END
                setMargins(0, 0, 32, 32)
            }
            fabAddSchedule.layoutParams = params
            (binding.root as android.widget.FrameLayout).addView(fabAddSchedule)
        }

        // Update month/year display
        updateMonthYearDisplay()

        // Add navigation buttons for months
        setupMonthNavigation()
    }

    private fun setupMonthNavigation() {
        // You'll need to add these buttons to your layout
        // For now, handle with swipe gestures or click on month header
        binding.tvSelectedDate.setOnClickListener {
            showMonthYearPicker()
        }
    }

    private fun showMonthYearPicker() {
        val year = selectedDate.year
        val month = selectedDate.monthValue - 1

        DatePickerDialog(
            requireContext(),
            { _, selectedYear, selectedMonth, _ ->
                selectedDate = LocalDate.of(selectedYear, selectedMonth + 1, 1)
                setupCalendar()
                updateMonthYearDisplay()
            },
            year,
            month,
            1
        ).show()
    }

    private fun updateMonthYearDisplay() {
        val formatter = DateTimeFormatter.ofPattern("MMMM yyyy")
        binding.tvSelectedDate.text = selectedDate.format(formatter)
    }

    private fun setupCalendar() {
        val days = generateCalendarDays()

        calendarAdapter = CalendarAdapter(days, selectedDate) { date ->
            selectedDate = date
            calendarAdapter.setSelectedDate(date)
            filterOutfitsForDate(date)

            // Update display to show selected date
            val formatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")
            binding.tvSelectedDate.text = date.format(formatter)
        }

        binding.rvCalendar.apply {
            layoutManager = GridLayoutManager(requireContext(), 7)
            adapter = calendarAdapter
        }

        // Add day labels
        addDayLabels()
    }

    private fun addDayLabels() {
        // This would be added to your layout XML, but here's how you'd do it programmatically
        val dayLabels = listOf("S", "M", "T", "W", "T", "F", "S")
        // Add a header row to your calendar RecyclerView or use a separate layout
    }

    private fun setupOutfitList() {
        outfitAdapter = OutfitAdapter(
            emptyList(),
            onItemClick = { outfit ->
                navigateToOutfitDetail(outfit)
            },
            onEditClick = { outfit ->
                showEditScheduleDialog(outfit)
            },
            onDeleteClick = { outfit ->
                confirmDeleteOutfit(outfit)
            }
        )

        binding.rvOutfits.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = outfitAdapter
        }
    }

    private fun generateCalendarDays(): List<LocalDate> {
        val days = mutableListOf<LocalDate>()
        val firstDayOfMonth = selectedDate.withDayOfMonth(1)
        val lastDayOfMonth = selectedDate.withDayOfMonth(selectedDate.lengthOfMonth())

        // Get the first day of the week (Sunday = 0)
        val firstDayOfWeek = (firstDayOfMonth.dayOfWeek.value % 7)
        val startDate = firstDayOfMonth.minusDays(firstDayOfWeek.toLong())

        // Generate 6 weeks (42 days) for consistent calendar size
        var currentDate = startDate
        repeat(42) {
            days.add(currentDate)
            currentDate = currentDate.plusDays(1)
        }

        return days
    }

    private fun observeOutfits() {
        // Observe all outfits from cache
        viewLifecycleOwner.lifecycleScope.launch {
            outfitRepository.getAllOutfits().collect { outfits ->
                allOutfits = outfits
                scheduledOutfits = outfits.filter { it.schedule != null }

                // Update calendar indicators
                val scheduledDates = scheduledOutfits.mapNotNull { outfit ->
                    outfit.schedule?.let {
                        try {
                            LocalDate.parse(it)
                        } catch (e: Exception) {
                            null
                        }
                    }
                }
                calendarAdapter.setScheduledDates(scheduledDates)

                // Filter for current selected date
                filterOutfitsForDate(selectedDate)

                // Hide loading
                binding.progressBar.visibility = View.GONE
            }
        }

        // Observe scheduled dates for indicators
        viewLifecycleOwner.lifecycleScope.launch {
            outfitRepository.getScheduledDates().collect { dates ->
                val localDates = dates.mapNotNull { dateString ->
                    try {
                        LocalDate.parse(dateString)
                    } catch (e: Exception) {
                        null
                    }
                }
                calendarAdapter.setScheduledDates(localDates)
            }
        }
    }

    private fun syncData() {
        syncJob?.cancel()

        syncJob = viewLifecycleOwner.lifecycleScope.launch {
            // Show sync indicator (optional)
            binding.progressBar.visibility = View.VISIBLE

            try {
                // Attempt to sync with server
                val result = outfitRepository.syncOutfits()

                result.onFailure { error ->
                    // Silently fail - we have cached data
                    Log.e(TAG, "Sync failed, using cached data", error)

                    // Only show toast for network errors, not auth issues
                    if (error.message?.contains("network", true) == true) {
                        Toast.makeText(
                            requireContext(),
                            "Offline mode - changes will sync when online",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun filterOutfitsForDate(date: LocalDate) {
        val dateString = date.format(DateTimeFormatter.ISO_LOCAL_DATE)

        viewLifecycleOwner.lifecycleScope.launch {
            outfitRepository.getOutfitsByDate(dateString).first().let { outfits ->
                outfitAdapter.updateOutfits(outfits)
                binding.tvNoOutfits.visibility = if (outfits.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun showScheduleOptionsDialog() {
        Log.d(TAG, "showScheduleOptionsDialog() called for date: $selectedDate")

        val options = arrayOf(
            "Create new outfit for ${selectedDate.format(DateTimeFormatter.ofPattern("MMM d"))}",
            "Choose existing outfit for this date",
            "View all outfits"
        )

        AlertDialog.Builder(requireContext())
            .setTitle("Schedule Outfit")
            .setItems(options) { _, which ->
                Log.d(TAG, "Option selected: $which")
                when (which) {
                    0 -> {
                        Log.d(TAG, "Navigate to create outfit")
                        navigateToCreateOutfit(selectedDate)
                    }
                    1 -> {
                        Log.d(TAG, "Show outfit selection dialog")
                        showOutfitSelectionDialog()
                    }
                    2 -> {
                        Log.d(TAG, "Navigate to outfit list")
                        navigateToOutfitList()
                    }
                }
            }
            .show()
    }

    private fun showOutfitSelectionDialog() {
        val unscheduledOutfits = allOutfits.filter { it.schedule == null }

        if (unscheduledOutfits.isEmpty()) {
            Toast.makeText(requireContext(), "No unscheduled outfits available", Toast.LENGTH_SHORT).show()
            return
        }

        val outfitNames = unscheduledOutfits.map { it.name }.toTypedArray()

        AlertDialog.Builder(requireContext())
            .setTitle("Choose Outfit for ${selectedDate.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))}")
            .setItems(outfitNames) { _, which ->
                val selectedOutfit = unscheduledOutfits[which]
                scheduleOutfit(selectedOutfit, selectedDate)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun scheduleOutfit(outfit: Outfit, date: LocalDate) {
        Log.d(TAG, "scheduleOutfit() called")
        Log.d(TAG, "  Outfit: ${outfit.name} (ID: ${outfit.outfitId})")
        Log.d(TAG, "  Date: $date")

        viewLifecycleOwner.lifecycleScope.launch {
            val dateString = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
            Log.d(TAG, "  Date string: $dateString")

            val result = outfitRepository.updateOutfitSchedule(outfit.outfitId.toString(), dateString)

            result.onSuccess {
                Log.d(TAG, "scheduleOutfit: SUCCESS")

                // ✅ SAVE NOTIFICATION
                NotificationHelper.saveLocalNotification(
                    context = requireContext(),
                    title = "Outfit Scheduled ⏰",
                    message = "\"${outfit.name}\" scheduled for ${date.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))}",
                    type = "reminder"
                )

                Toast.makeText(
                    requireContext(),
                    "Outfit scheduled for ${date.format(DateTimeFormatter.ofPattern("MMM d"))}",
                    Toast.LENGTH_SHORT
                ).show()
            }.onFailure { error ->
                Log.e(TAG, "scheduleOutfit: FAILED - ${error.message}", error)
                Toast.makeText(
                    requireContext(),
                    "Failed to schedule outfit: ${error.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    private fun showEditScheduleDialog(outfit: Outfit) {
        val options = mutableListOf<String>()

        if (outfit.schedule != null) {
            options.add("Remove schedule")
            options.add("Change date")
        } else {
            options.add("Schedule for ${selectedDate.format(DateTimeFormatter.ofPattern("MMM d"))}")
            options.add("Choose different date")
        }
        options.add("Cancel")

        AlertDialog.Builder(requireContext())
            .setTitle("Edit Schedule: ${outfit.name}")
            .setItems(options.toTypedArray()) { _, which ->
                when (which) {
                    0 -> {
                        if (outfit.schedule != null) {
                            // Remove schedule
                            removeSchedule(outfit)
                        } else {
                            // Schedule for selected date
                            scheduleOutfit(outfit, selectedDate)
                        }
                    }
                    1 -> {
                        // Show date picker
                        showDatePickerForOutfit(outfit)
                    }
                }
            }
            .show()
    }

    private fun showDatePickerForOutfit(outfit: Outfit) {
        val calendar = Calendar.getInstance()

        DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                val newDate = LocalDate.of(year, month + 1, dayOfMonth)
                scheduleOutfit(outfit, newDate)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun removeSchedule(outfit: Outfit) {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = outfitRepository.updateOutfitSchedule(outfit.outfitId.toString(), null)

            result.onSuccess {
                Toast.makeText(requireContext(), "Schedule removed", Toast.LENGTH_SHORT).show()
            }.onFailure { error ->
                Toast.makeText(
                    requireContext(),
                    "Failed to remove schedule: ${error.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun confirmDeleteOutfit(outfit: Outfit) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Outfit")
            .setMessage("Are you sure you want to delete '${outfit.name}'?")
            .setPositiveButton("Delete") { _, _ ->
                deleteOutfit(outfit)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteOutfit(outfit: Outfit) {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = outfitRepository.deleteOutfit(outfit.outfitId.toString())

            result.onSuccess {
                Toast.makeText(requireContext(), "Outfit deleted", Toast.LENGTH_SHORT).show()
            }.onFailure { error ->
                Toast.makeText(
                    requireContext(),
                    "Failed to delete: ${error.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun navigateToCreateOutfit(date: LocalDate? = null) {
        val bundle = Bundle().apply {
            date?.let {
                putString("scheduled_date", it.format(DateTimeFormatter.ISO_LOCAL_DATE))
            }
        }
        findNavController().navigate(R.id.action_calendar_to_create_outfit, bundle)
    }

    private fun navigateToOutfitDetail(outfit: Outfit) {
        val bundle = Bundle().apply {
            putInt("outfit_id", outfit.outfitId)
        }
        findNavController().navigate(R.id.action_calendar_to_outfit_detail, bundle)
    }

    private fun navigateToOutfitList() {
        findNavController().navigate(R.id.action_calendar_to_wardrobe)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        syncJob?.cancel()
        _binding = null
    }
}