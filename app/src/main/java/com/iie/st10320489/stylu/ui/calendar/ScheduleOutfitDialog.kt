package com.iie.st10320489.stylu.ui.calendar

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.iie.st10320489.stylu.databinding.DialogScheduleOutfitBinding
import com.iie.st10320489.stylu.network.ApiService
import com.iie.st10320489.stylu.repository.CalendarRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class ScheduleOutfitDialog(
    private val onScheduled: () -> Unit,
    initialDate: Date = Date()
) : DialogFragment() {

    private var _binding: DialogScheduleOutfitBinding? = null
    private val binding get() = _binding!!

    private lateinit var calendarRepository: CalendarRepository
    private lateinit var apiService: ApiService
    private lateinit var outfitAdapter: OutfitSelectionAdapter

    private var selectedDate: Date = initialDate
    private var selectedOutfit: ApiService.OutfitDetail? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogScheduleOutfitBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        calendarRepository = CalendarRepository(requireContext())
        apiService = ApiService(requireContext())

        updateDateDisplay()
        setupRecyclerView()
        setupClickListeners()
        loadOutfits()
    }

    private fun setupRecyclerView() {
        outfitAdapter = OutfitSelectionAdapter { outfit ->
            selectedOutfit = outfit
            binding.tvSelectedOutfit.text = "${outfit.name} Selected"
            binding.cardSelectedOutfit.visibility = View.VISIBLE
            binding.btnSchedule.isEnabled = true
        }

        binding.rvOutfits.layoutManager = LinearLayoutManager(
            requireContext(),
            LinearLayoutManager.HORIZONTAL,
            false
        )
        binding.rvOutfits.adapter = outfitAdapter
    }

    private fun setupClickListeners() {
        binding.btnSelectDate.setOnClickListener {
            showDatePicker()
        }

        binding.btnCancel.setOnClickListener {
            dismiss()
        }

        binding.btnSchedule.setOnClickListener {
            scheduleOutfit()
        }
    }

    private fun loadOutfits() {
        lifecycleScope.launch {
            try {
                binding.progressBarOutfits.visibility = View.VISIBLE
                binding.rvOutfits.visibility = View.GONE
                binding.tvNoOutfits.visibility = View.GONE

                val result = apiService.getUserOutfits()

                result.onSuccess { outfits ->
                    binding.progressBarOutfits.visibility = View.GONE

                    if (outfits.isEmpty()) {
                        binding.tvNoOutfits.visibility = View.VISIBLE
                        binding.btnSchedule.isEnabled = false
                    } else {
                        binding.rvOutfits.visibility = View.VISIBLE
                        val outfitItems = outfits.map {
                            OutfitSelectionAdapter.OutfitItem(it, false)
                        }
                        outfitAdapter.submitList(outfitItems)
                    }
                }.onFailure { error ->
                    binding.progressBarOutfits.visibility = View.GONE
                    binding.tvNoOutfits.text = "Failed to load outfits: ${error.message}"
                    binding.tvNoOutfits.visibility = View.VISIBLE
                    binding.btnSchedule.isEnabled = false

                    Toast.makeText(
                        requireContext(),
                        "Failed to load outfits: ${error.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                binding.progressBarOutfits.visibility = View.GONE
                binding.tvNoOutfits.text = "Error loading outfits"
                binding.tvNoOutfits.visibility = View.VISIBLE
                binding.btnSchedule.isEnabled = false

                Toast.makeText(
                    requireContext(),
                    "Error: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        calendar.time = selectedDate

        val datePickerDialog = DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                val newCalendar = Calendar.getInstance()
                newCalendar.set(year, month, dayOfMonth)
                selectedDate = newCalendar.time
                updateDateDisplay()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )

        datePickerDialog.datePicker.minDate = System.currentTimeMillis()
        datePickerDialog.show()
    }

    private fun updateDateDisplay() {
        val dateFormat = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
        binding.tvSelectedDate.text = dateFormat.format(selectedDate)
    }

    private fun scheduleOutfit() {
        val outfit = selectedOutfit
        if (outfit == null) {
            Toast.makeText(requireContext(), "Please select an outfit", Toast.LENGTH_SHORT).show()
            return
        }

        val eventName = binding.etEventName.text.toString().trim().takeIf { it.isNotEmpty() }
        val notes = binding.etNotes.text.toString().trim().takeIf { it.isNotEmpty() }

        binding.btnSchedule.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            val result = calendarRepository.scheduleOutfit(
                outfitId = outfit.outfitId,
                date = selectedDate,
                eventName = eventName,
                notes = notes
            )

            result.onSuccess {
                Toast.makeText(
                    requireContext(),
                    "Outfit scheduled successfully",
                    Toast.LENGTH_SHORT
                ).show()
                onScheduled()
                dismiss()
            }.onFailure { error ->
                binding.btnSchedule.isEnabled = true
                binding.progressBar.visibility = View.GONE

                val message = when {
                    error.message?.contains("Authentication failed") == true ||
                            error.message?.contains("Session expired") == true -> {
                        "Your session has expired. Please login again."
                    }
                    error.message?.contains("already scheduled") == true -> {
                        "This outfit is already scheduled for this date."
                    }
                    error.message?.contains("timed out") == true ||
                            error.message?.contains("cold start") == true -> {
                        "Server is starting up. Please try again in a moment."
                    }
                    error.message?.contains("connect") == true -> {
                        "Cannot connect to server. Please check your internet connection."
                    }
                    else -> "Failed to schedule: ${error.message}"
                }

                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
}