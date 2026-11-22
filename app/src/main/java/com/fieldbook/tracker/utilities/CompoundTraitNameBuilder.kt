package com.fieldbook.tracker.utilities

import com.fieldbook.tracker.utilities.TimeOfYearEventConfig

/**
 * EDA52 / Breedbase build — Builder for compound trait names in Breedbase format.
 *
 * Format: base|{TOY_TYPE} {N}|{EVENT_TYPE} {N}|COMP:xxxx
 * Variants:
 *   base|{EVENT_TYPE} {N}|COMP:xxxx  (no TOY)
 *   base|{TOY_TYPE} {N}|COMP:xxxx     (no Event)
 *   base|CO:xxxx                      (base, no TOY no Event)
 *   base|COV:xxxx                     (base variant, no TOY no Event)
 *
 * The COMP:xxxx suffix is added by the caller after looking up the variableDbId;
 * this builder produces the "base|TOY|EVENT" part that is used for matching against
 * the compound_trait_index.
 */
object CompoundTraitNameBuilder {

    /**
     * Builds the compound trait name prefix (without COMP:xxxx suffix) from a base
     * trait name and a TimeOfYearEventConfig.
     *
     * @param baseTraitName the base trait name (e.g. "Plant Height")
     * @param config the Time of Year / Event configuration
     * @return the compound name prefix (e.g. "Plant Height|Week 1|Season 3")
     */
    fun build(baseTraitName: String, config: TimeOfYearEventConfig): String {
        val parts = mutableListOf(baseTraitName)

        // Add Time of Year if configured
        if (config.timeOfYearType != null && config.timeOfYearType != "none" && config.timeOfYearValue != null) {
            val toyLabel = toyLabel(config.timeOfYearType)
            parts.add("$toyLabel ${config.timeOfYearValue}")
        }

        // Add Event if configured
        if (config.eventType != null && config.eventType != "none" && config.eventValue != null) {
            val eventLabel = eventLabel(config.eventType)
            parts.add("$eventLabel ${config.eventValue}")
        }

        return parts.joinToString("|")
    }

    /**
     * Returns the capitalized label for a Time of Year type.
     * "week" -> "Week", "month" -> "Month", "day" -> "Day", "hour" -> "Hour", "minute" -> "Minute"
     */
    private fun toyLabel(type: String): String = when (type.lowercase()) {
        "week" -> "Week"
        "month" -> "Month"
        "day" -> "Day"
        "hour" -> "Hour"
        "minute" -> "Minute"
        else -> type.replaceFirstChar { it.uppercase() }
    }

    /**
     * Returns the capitalized label for an Event type.
     * "season" -> "Season", "timepoint" -> "Timepoint"
     */
    private fun eventLabel(type: String): String = when (type.lowercase()) {
        "season" -> "Season"
        "timepoint" -> "Timepoint"
        else -> type.replaceFirstChar { it.uppercase() }
    }

    /**
     * Normalizes a trait name for matching against the compound_trait_index.
     * - Trims spaces
     * - Case-insensitive (caller should lowercase both sides)
     * - Replaces "..N" with " - N" (Breedbase convention)
     * - Replaces "." with " "
     * - Collapses multiple spaces into one
     */
    fun normalize(name: String): String {
        var n = name.trim()
        // Replace "..N" with " - N"
        n = Regex("\\.{2,}(\\d+)").replace(n) { " - ${it.groupValues[1]}" }
        // Replace dots with spaces
        n = n.replace(".", " ")
        // Collapse multiple spaces
        n = Regex("\\s+").replace(n, " ")
        return n.trim()
    }
}
