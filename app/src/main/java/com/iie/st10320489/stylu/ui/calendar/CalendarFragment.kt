package com.iie.st10320489.stylu.ui.calendar

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.iie.st10320489.stylu.R
import com.iie.st10320489.stylu.network.ApiService
import com.iie.st10320489.stylu.ui.wardrobe.OutfitAdapter
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class CalendarFragment : Fragment() {

    private lateinit var calendarView: CalendarView
    private lateinit var rvScheduledOutfits: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyStateLayout: LinearLayout
    private lateinit var btnBack: ImageButton

    private lateinit var apiService: ApiService
    private lateinit var outfitAdapter: OutfitAdapter
    private var scheduledOutfits: List<ApiService.OutfitDetail> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_calendar, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        apiService = ApiService(requireContext())
        initializeViews(view)
        setupRecyclerView()
        setupClickListeners()
        loadScheduledOutfits()
    }

    private fun initializeViews(view: View) {
        calendarView = view.findViewById(R.id.calendarView)
        rvScheduledOutfits = view.findViewById(R.id.rvScheduledOutfits)
        progressBar = view.findViewById(R.id.progressBar)
        emptyStateLayout = view.findViewById(R.id.emptyStateLayout)
        btnBack = view.findViewById(R.id.btnBack)
    }

    private fun setupRecyclerView() {
        outfitAdapter = OutfitAdapter(
            onOutfitClick = { outfit ->
                val bundle = Bundle().apply {
                    putInt("outfitId", outfit.outfitId)
                }
                findNavController().navigate(R.id.action_calendar_to_outfit_detail, bundle)
            },
            onScheduleClick = { outfit ->
                showDatePickerForReschedule(outfit)
            }
        )

        rvScheduledOutfits.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = outfitAdapter
        }
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            val selectedDate = formatDate(year, month, dayOfMonth)
            filterOutfitsByDate(selectedDate)
        }
    }

    private fun loadScheduledOutfits() {
        lifecycleScope.launch {
            try {
                progressBar.visibility = View.VISIBLE

                val result = apiService.getScheduledOutfits()
                result.onSuccess { outfits ->
                    scheduledOutfits = outfits
                    outfitAdapter.submitList(outfits)

                    if (outfits.isEmpty()) {
                        emptyStateLayout.visibility = View.VISIBLE
                        rvScheduledOutfits.visibility = View.GONE
                    } else {
                        emptyStateLayout.visibility = View.GONE
                        rvScheduledOutfits.visibility = View.VISIBLE
                    }
                }.onFailure { error ->
                    Toast.makeText(
                        requireContext(),
                        "Failed to load scheduled outfits: ${error.message}",
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
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun filterOutfitsByDate(date: String) {
        val filtered = scheduledOutfits.filter { it.schedule == date }

        if (filtered.isEmpty()) {
            Toast.makeText(
                requireContext(),
                "No outfits scheduled for this date",
                Toast.LENGTH_SHORT
            ).show()
            outfitAdapter.submitList(scheduledOutfits)
        } else {
            outfitAdapter.submitList(filtered)
        }
    }

    private fun showDatePickerForReschedule(outfit: ApiService.OutfitDetail) {
        val calendar = Calendar.getInstance()

        outfit.schedule?.let { scheduleStr ->
            try {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                calendar.time = sdf.parse(scheduleStr) ?: Date()
            } catch (e: Exception) {
                // Use current date if parsing fails
            }
        }

        DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                val newDate = formatDate(year, month, dayOfMonth)
                rescheduleOutfit(outfit.outfitId, newDate)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).apply {
            datePicker.minDate = System.currentTimeMillis()
            show()
        }
    }

    private fun rescheduleOutfit(outfitId: Int, newDate: String) {
        lifecycleScope.launch {
            try {
                progressBar.visibility = View.VISIBLE

                val result = apiService.scheduleOutfit(outfitId, newDate)
                result.onSuccess {
                    Toast.makeText(
                        requireContext(),
                        "Outfit rescheduled successfully",
                        Toast.LENGTH_SHORT
                    ).show()
                    loadScheduledOutfits()
                }.onFailure { error ->
                    Toast.makeText(
                        requireContext(),
                        "Failed to reschedule: ${error.message}",
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
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun formatDate(year: Int, month: Int, dayOfMonth: Int): String {
        val calendar = Calendar.getInstance()
        calendar.set(year, month, dayOfMonth)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(calendar.time)
    }
}