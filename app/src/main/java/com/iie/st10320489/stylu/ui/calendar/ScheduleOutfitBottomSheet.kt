package com.iie.st10320489.stylu.ui.calendar

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.iie.st10320489.stylu.databinding.BottomSheetScheduleOutfitBinding
import com.iie.st10320489.stylu.network.ApiService
import com.iie.st10320489.stylu.repository.CalendarRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class ScheduleOutfitBottomSheet(
    private val onScheduled: () -> Unit,
    initialDate: Date = Date()
) : BottomSheetDialogFragment() {

    private var _binding: BottomSheetScheduleOutfitBinding? = null
    private val binding get() = _binding!!

    private lateinit var calendarRepository: CalendarRepository
    private lateinit var apiService: ApiService
    private lateinit var outfitAdapter: OutfitGridAdapter

    private var selectedDate: Date = initialDate
    private var selectedOutfit: ApiService.OutfitDetail? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetScheduleOutfitBinding.inflate(inflater, container, false)
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
        outfitAdapter = OutfitGridAdapter { outfit ->
            selectedOutfit = outfit
            binding.tvSelectedOutfit.text = "${outfit.name} Selected"
            binding.cardSelectedOutfit.visibility = View.VISIBLE
            binding.btnSchedule.isEnabled = true
        }

        binding.rvOutfits.apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            adapter = outfitAdapter
        }
    }

    private fun setupClickListeners() {
        binding.btnClose.setOnClickListener {
            dismiss()
        }

        binding.btnSelectDate.setOnClickListener {
            showDatePicker()
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
                        outfitAdapter.submitList(outfits)
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

    /**
     * ✅ UPDATED: Schedule outfit (works offline)
     */
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
                    "✅ Outfit scheduled successfully\n(Will sync when online)",
                    Toast.LENGTH_SHORT
                ).show()
                onScheduled()
                dismiss()
            }.onFailure { error ->
                binding.btnSchedule.isEnabled = true
                binding.progressBar.visibility = View.GONE

                Toast.makeText(
                    requireContext(),
                    "Failed to schedule: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}