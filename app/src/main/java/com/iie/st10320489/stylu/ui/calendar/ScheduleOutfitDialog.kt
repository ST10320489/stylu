package com.iie.st10320489.stylu.ui.calendar

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.iie.st10320489.stylu.databinding.DialogScheduleOutfitBinding
import com.iie.st10320489.stylu.repository.CalendarRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class ScheduleOutfitDialog(
    private val outfitId: Int,
    private val outfitName: String,
    private val onScheduled: () -> Unit,
    initialDate: Date = Date()
) : DialogFragment() {

    private var _binding: DialogScheduleOutfitBinding? = null
    private val binding get() = _binding!!
    private lateinit var calendarRepository: CalendarRepository
    private var selectedDate: Date = initialDate

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

        binding.tvOutfitName.text = outfitName
        updateDateDisplay()
        setupClickListeners()
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

        // Don't allow past dates
        datePickerDialog.datePicker.minDate = System.currentTimeMillis()
        datePickerDialog.show()
    }

    private fun updateDateDisplay() {
        val dateFormat = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
        binding.tvSelectedDate.text = dateFormat.format(selectedDate)
    }

    private fun scheduleOutfit() {
        val eventName = binding.etEventName.text.toString().trim().takeIf { it.isNotEmpty() }
        val notes = binding.etNotes.text.toString().trim().takeIf { it.isNotEmpty() }

        binding.btnSchedule.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            val result = calendarRepository.scheduleOutfit(
                outfitId = outfitId,
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