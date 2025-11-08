package com.iie.st10320489.stylu.data.models.notifications

import com.google.gson.annotations.SerializedName

data class Notification(
    @SerializedName("notifications_id")
    val id: Int? = null,

    @SerializedName("user_id")
    val userId: Int,

    val title: String,
    val message: String,
    val type: String = "general",

    @SerializedName("scheduled_at")
    val scheduledAt: String,   // ISO8601 timestamp

    @SerializedName("sent_at")
    val sentAt: String? = null,

    val status: String = "sent"
)