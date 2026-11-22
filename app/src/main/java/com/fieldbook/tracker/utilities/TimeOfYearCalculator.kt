package com.fieldbook.tracker.utilities

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields

/**
 * EDA52 / Breedbase build — Calculator for relative Time of Year values.
 *
 * Implements the user's rules:
 *  - Week relative: the week of the experiment start date is week 1; the current week
 *    is the difference in weeks + 1. Uses ISO week numbering.
 *  - Month relative: exactly 4 relative weeks = 1 relative month (28 days = 1 month,
 *    regardless of calendar month length).
 *  - Day of year: 1-365/366.
 *  - Hour: 1-24 (24h format).
 *  - Minute: from {15, 30, 45, 60} set.
 */
object TimeOfYearCalculator {

    /**
     * Computes the relative week number between start and current dates.
     * Week 1 = the week containing startDate.
     * Returns 1 if currentDate is before or equal to startDate's week.
     */
    fun calculateRelativeWeek(startDate: LocalDate, currentDate: LocalDate): Int {
        val weekFields = WeekFields.ISO
        val startWeek = startDate.get(weekFields.weekOfWeekBasedYear())
        val startYear = startDate.get(weekFields.weekBasedYear())
        val currentWeek = currentDate.get(weekFields.weekOfWeekBasedYear())
        val currentYear = currentDate.get(weekFields.weekBasedYear())

        // Calculate total weeks between start and current, accounting for year boundaries
        val weeksBetween = ChronoUnit.WEEKS.between(startDate, currentDate)
        return if (weeksBetween <= 0) 1 else (weeksBetween.toInt() + 1)
    }

    /**
     * Computes the relative month number between start and current dates.
     * 1 relative month = exactly 4 relative weeks (28 days).
     * Returns 1 if currentDate is before startDate.
     */
    fun calculateRelativeMonth(startDate: LocalDate, currentDate: LocalDate): Int {
        val daysBetween = ChronoUnit.DAYS.between(startDate, currentDate)
        if (daysBetween < 0) return 1
        val relativeWeeks = (daysBetween / 7).toInt() + 1
        return ((relativeWeeks - 1) / 4) + 1
    }

    /**
     * Returns the current day of year (1-365 or 1-366 for leap years).
     */
    fun getCurrentDayOfYear(date: LocalDate = LocalDate.now()): Int = date.dayOfYear

    /**
     * Returns the current hour in 24h format (1-24, where 24 = midnight).
     */
    fun getCurrentHour(): Int {
        val hour = java.time.LocalTime.now().hour
        return if (hour == 0) 24 else hour
    }

    /**
     * Validates a Time of Year value against its type.
     * @param type "week", "month", "day", "hour", or "minute"
     * @param value the numeric value
     * @return true if valid, false otherwise
     */
    fun isValidToyValue(type: String, value: Int): Boolean = when (type.lowercase()) {
        "week" -> value in 1..54
        "month" -> value in 1..16
        "day" -> value in 1..366
        "hour" -> value in 1..24
        "minute" -> value in setOf(15, 30, 45, 60)
        else -> false
    }

    /**
     * Validates an Event value against its type.
     * @param type "season" or "timepoint"
     * @param value the numeric value
     * @return true if valid, false otherwise
     */
    fun isValidEventValue(type: String, value: Int): Boolean = when (type.lowercase()) {
        "season" -> value in 1..6
        "timepoint" -> value in 1..20
        else -> false
    }
}
