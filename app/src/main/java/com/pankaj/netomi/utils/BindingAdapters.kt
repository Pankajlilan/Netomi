package com.pankaj.netomi.utils

import android.widget.TextView
import androidx.databinding.BindingAdapter
import java.text.SimpleDateFormat
import java.util.*

@BindingAdapter("formattedTimestamp")
fun TextView.setFormattedTimestamp(timestamp: Long) {
    val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    text = formatter.format(Date(timestamp))
}
