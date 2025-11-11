package com.iie.st10320489.stylu.data.models.calendar

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.Date

@Parcelize
data class CalendarEvent(
    val eventId: Int = 0,
    val userId: String,
    val outfitId: Int?,
    val eventDate: Date,
    val eventName: String?,
    val eventType: String? = null, // "work", "casual", "formal", "sport"
    val notes: String? = null,
    val createdAt: Date = Date()
) : Parcelable