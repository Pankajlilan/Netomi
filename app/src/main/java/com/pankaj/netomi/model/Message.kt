package com.pankaj.netomi.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Message(
    val id: String = "",
    val text: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isFromBot: Boolean = false,
    val userId: String = ""
) : Parcelable
