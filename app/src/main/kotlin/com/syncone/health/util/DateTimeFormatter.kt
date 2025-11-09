package com.syncone.health.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Formats timestamps for display.
 */
object DateTimeFormatter {

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("MMM dd", Locale.getDefault())
    private val fullFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())

    /**
     * Format timestamp as relative time (e.g., "2h ago", "just now").
     */
    fun formatRelative(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp

        return when {
            diff < TimeUnit.MINUTES.toMillis(1) -> "just now"
            diff < TimeUnit.HOURS.toMillis(1) -> {
                val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
                "${minutes}m ago"
            }
            diff < TimeUnit.DAYS.toMillis(1) -> {
                val hours = TimeUnit.MILLISECONDS.toHours(diff)
                "${hours}h ago"
            }
            diff < TimeUnit.DAYS.toMillis(7) -> {
                val days = TimeUnit.MILLISECONDS.toDays(diff)
                "${days}d ago"
            }
            else -> dateFormat.format(Date(timestamp))
        }
    }

    /**
     * Format timestamp as time (HH:mm).
     */
    fun formatTime(timestamp: Long): String {
        return timeFormat.format(Date(timestamp))
    }

    /**
     * Format timestamp as date (MMM dd).
     */
    fun formatDate(timestamp: Long): String {
        return dateFormat.format(Date(timestamp))
    }

    /**
     * Format timestamp as full date and time (MMM dd, yyyy HH:mm).
     */
    fun formatFull(timestamp: Long): String {
        return fullFormat.format(Date(timestamp))
    }

    /**
     * Check if timestamp is today.
     */
    fun isToday(timestamp: Long): Boolean {
        val today = Date()
        val date = Date(timestamp)
        return dateFormat.format(today) == dateFormat.format(date)
    }
}
