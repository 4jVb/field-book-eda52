package com.fieldbook.tracker.utilities

import android.content.Context
import com.fieldbook.tracker.R
import com.fieldbook.tracker.database.dao.CompoundTraitDao
import com.fieldbook.tracker.objects.TraitObject

/**
 * EDA52 / Breedbase build — decides, before a BrAPI upload, which Breedbase variable each base
 * trait must be sent as when a Time of Year / Event configuration is active.
 *
 * Strict rule: every selected base trait needs exactly one compound variable in the
 * compound_trait_index that matches (base name, time-of-year, event). If one is missing, or
 * ambiguous, nothing is uploaded.
 */
object CompoundUploadPlanner {

    data class Override(val dbId: String, val name: String)

    data class Plan(
        /** base variable DbId (external id of the local trait) -> compound variable */
        val overrides: Map<String, Override>,
        val missing: List<String>,
        val ambiguous: List<String>,
        val indexEmpty: Boolean
    ) {
        val ok: Boolean get() = missing.isEmpty() && ambiguous.isEmpty() && !indexEmpty

        fun errorMessage(context: Context): String = when {
            indexEmpty -> context.getString(R.string.brapi_compound_index_empty)
            missing.isNotEmpty() -> context.getString(
                R.string.brapi_compound_missing, missing.joinToString("\n") { "• $it" })
            else -> context.getString(
                R.string.brapi_compound_ambiguous, ambiguous.joinToString("\n") { "• $it" })
        }
    }

    private val toyTypes = setOf("week", "month", "day", "hour", "minute")
    private val eventTypes = setOf("season", "timepoint")

    private fun isNone(type: String?) = type.isNullOrBlank() || type.equals("none", ignoreCase = true)

    /** True when the config asks for a time of year and/or an event. */
    fun isActive(config: TimeOfYearEventConfig?): Boolean {
        if (config == null) return false
        val toy = !isNone(config.timeOfYearType) && config.timeOfYearValue != null
        val event = !isNone(config.eventType) && config.eventValue != null
        return toy || event
    }

    private fun baseKey(name: String?): String =
        CompoundTraitNameBuilder.normalize((name ?: "").split("|")[0]).lowercase()

    /** A trait that is already a compound variable (e.g. "Height|Week 1|COMP:0001") is not changed. */
    private fun alreadyCompound(name: String?): Boolean =
        (name ?: "").split("|").drop(1)
            .map { it.trim().split(" ")[0].lowercase() }
            .any { it in toyTypes || it in eventTypes }

    fun build(traits: List<TraitObject>, config: TimeOfYearEventConfig): Plan {
        val index = CompoundTraitDao.findAll()
        val selected = if (config.selectedTraitIds.isEmpty()) traits
        else traits.filter { it.id in config.selectedTraitIds }

        val wantToy = if (isNone(config.timeOfYearType) || config.timeOfYearValue == null) null
        else config.timeOfYearType!!.lowercase()
        val wantToyValue = if (wantToy == null) null else config.timeOfYearValue
        val wantEvent = if (isNone(config.eventType) || config.eventValue == null) null
        else config.eventType!!.lowercase()
        val wantEventValue = if (wantEvent == null) null else config.eventValue

        val overrides = LinkedHashMap<String, Override>()
        val missing = ArrayList<String>()
        val ambiguous = ArrayList<String>()

        for (trait in selected) {
            if (alreadyCompound(trait.name)) continue
            val externalId = trait.externalDbId
            // not imported from BrAPI: it can never be uploaded, so there is nothing to translate
            if (externalId.isNullOrEmpty()) continue

            val key = baseKey(trait.name)
            val matches = index.filter {
                baseKey(it.baseTraitName) == key &&
                    it.toyType?.lowercase() == wantToy && it.toyValue == wantToyValue &&
                    it.eventType?.lowercase() == wantEvent && it.eventValue == wantEventValue
            }.distinctBy { it.observationVariableDbId }

            val expected = CompoundTraitNameBuilder.build(
                (trait.name ?: "").split("|")[0].trim(), config
            ).replace("|", " | ")

            when {
                matches.isEmpty() -> missing.add(expected)
                matches.size > 1 -> ambiguous.add(expected)
                else -> overrides[externalId] =
                    Override(matches[0].observationVariableDbId, matches[0].observationVariableName)
            }
        }
        return Plan(overrides, missing, ambiguous, index.isEmpty())
    }
}
