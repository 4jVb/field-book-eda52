package com.fieldbook.tracker.utilities

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java.time.LocalDate

/**
 * EDA52 / Breedbase build — Persistence for the Time of Year / Event dialog configuration.
 *
 * The configuration is scoped to the active (studyId, traitsHash) combination, so it resets
 * when the user switches fields or modifies the trait list.
 */
data class TimeOfYearEventConfig(
    val experimentStartDate: LocalDate? = null,
    val timeOfYearType: String? = null,    // "week", "month", "day", "hour", "minute", or null
    val timeOfYearValue: Int? = null,
    val autoTimeOfYear: Boolean = false,
    val eventType: String? = null,        // "season", "timepoint", or null
    val eventValue: Int? = null,
    val selectedTraitIds: Set<String> = emptySet()  // empty = all traits selected
)

object TimeOfYearEventConfigStore {

    private const val PREFS_NAME = "fieldbook_toy_event_config"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Builds the storage key for a given (studyId, traitsHash) combination.
     */
    private fun key(studyId: Int, traitsHash: String): String = "toy_config_${studyId}_${traitsHash}"

    /**
     * Saves the configuration for the given field+traits combination.
     */
    fun save(context: Context, studyId: Int, traitsHash: String, config: TimeOfYearEventConfig) {
        val k = key(studyId, traitsHash)
        prefs(context).edit {
            putString("${k}_start_date", config.experimentStartDate?.toString())
            putString("${k}_toy_type", config.timeOfYearType)
            config.timeOfYearValue?.let { putInt("${k}_toy_value", it) } ?: remove("${k}_toy_value")
            putBoolean("${k}_auto_toy", config.autoTimeOfYear)
            putString("${k}_event_type", config.eventType)
            config.eventValue?.let { putInt("${k}_event_value", it) } ?: remove("${k}_event_value")
            // Save selected trait IDs as a comma-separated string
            putString("${k}_trait_ids", config.selectedTraitIds.joinToString(","))
        }
    }

    /**
     * Loads the configuration for the given field+traits combination.
     * Returns null if no configuration is saved.
     */
    fun load(context: Context, studyId: Int, traitsHash: String): TimeOfYearEventConfig? {
        val p = prefs(context)
        val k = key(studyId, traitsHash)
        if (!p.contains("${k}_start_date") && !p.contains("${k}_toy_type") && !p.contains("${k}_event_type")) {
            return null
        }
        val startDateStr = p.getString("${k}_start_date", null)
        return TimeOfYearEventConfig(
            experimentStartDate = startDateStr?.let { LocalDate.parse(it) },
            timeOfYearType = p.getString("${k}_toy_type", null),
            timeOfYearValue = if (p.contains("${k}_toy_value")) p.getInt("${k}_toy_value", 0) else null,
            autoTimeOfYear = p.getBoolean("${k}_auto_toy", false),
            eventType = p.getString("${k}_event_type", null),
            eventValue = if (p.contains("${k}_event_value")) p.getInt("${k}_event_value", 0) else null,
            selectedTraitIds = p.getString("${k}_trait_ids", "")?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()
        )
    }

    /**
     * Clears the configuration for a specific field+traits combination.
     */
    fun clear(context: Context, studyId: Int, traitsHash: String) {
        val k = key(studyId, traitsHash)
        prefs(context).edit {
            remove("${k}_start_date")
            remove("${k}_toy_type")
            remove("${k}_toy_value")
            remove("${k}_auto_toy")
            remove("${k}_event_type")
            remove("${k}_event_value")
            remove("${k}_trait_ids")
            remove("${k}_skipped")
        }
    }

    /**
     * Clears all stored configurations (used when the database is deleted).
     */
    fun clearAll(context: Context) {
        prefs(context).edit { clear() }
    }

    /**
     * Checks if a configuration exists for the given field+traits combination.
     */
    fun exists(context: Context, studyId: Int, traitsHash: String): Boolean {
        val k = key(studyId, traitsHash)
        return prefs(context).contains("${k}_start_date") ||
               prefs(context).contains("${k}_toy_type") ||
               prefs(context).contains("${k}_event_type") ||
               prefs(context).contains("${k}_skipped")
    }

    /**
     * Marks the configuration as "skipped" (user pressed "Saltar todo" so the dialog
     * doesn't appear again until the field or traits change).
     */
    fun markSkipped(context: Context, studyId: Int, traitsHash: String) {
        val k = key(studyId, traitsHash)
        prefs(context).edit { putBoolean("${k}_skipped", true) }
    }
}
