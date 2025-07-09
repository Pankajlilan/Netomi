package com.pankaj.netomi.common

import android.widget.TextView
import androidx.databinding.BindingAdapter
import java.text.SimpleDateFormat
import java.util.*

object BindingAdapters {
    
    @BindingAdapter("timeFormat")
    @JvmStatic
    fun setTimeFormat(textView: TextView, timestamp: Long) {
        val formatter = SimpleDateFormat("hh:mm a", Locale.getDefault())
        textView.text = formatter.format(Date(timestamp))
    }
    
    @BindingAdapter("visibilityByCount")
    @JvmStatic
    fun setVisibilityByCount(textView: TextView, count: Int) {
        textView.visibility = if (count > 0) android.view.View.VISIBLE else android.view.View.GONE
    }
}
