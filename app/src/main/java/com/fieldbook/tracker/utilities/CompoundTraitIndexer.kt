package com.fieldbook.tracker.utilities

import android.content.Context
import android.util.Log
import com.fieldbook.tracker.database.dao.CompoundTraitDao
import com.fieldbook.tracker.database.models.CompoundTraitModel
import org.brapi.v2.model.pheno.BrAPIObservationVariable

/**
 * EDA52 / Breedbase build — Indexes compound traits from BrAPI variables into the
 * compound_trait_index table.
 *
 * Called during list import: when a Breedbase list contains compound traits (names
 * with "|"), this indexer parses the name into (base, toy_type, toy_value, event_type,
 * event_value) and stores the mapping for later use during sync.
 *
 * Name formats handled:
 *   base|Week 1|Season 3|COMP:xxxx
 *   base|Season 3|COMP:xxxx
 *   base|Week 1|COMP:xxxx
 *   base|CO:xxxx
 *   base|COV:xxxx
 */
object CompoundTraitIndexer {

    private const val TAG = "CompoundTraitIndexer"

    /**
     * Parses and indexes a list of BrAPIObservationVariable objects.
     * Only variables whose name contains "|" are processed as compound traits.
     */
    fun indexVariables(variables: List<BrAPIObservationVariable>) {
        for (variable in variables) {
            val name = variable.observationVariableName ?: continue
            if (!name.contains("|")) continue  // base trait, skip

            val dbId = variable.observationVariableDbId ?: continue
            indexCompoundTrait(name, dbId)
        }
        Log.i(TAG, "Indexed ${variables.count { it.observationVariableName?.contains("|") == true }} compound traits")
    }

    /**
     * Parses a compound trait name and indexes it.
     */
    fun indexCompoundTrait(fullName: String, observationVariableDbId: String) {
        val parts = fullName.split("|").map { it.trim() }
        if (parts.isEmpty()) return

        val baseName = parts[0]
        var toyType: String? = null
        var toyValue: Int? = null
        var eventType: String? = null
        var eventValue: Int? = null

        // Parse the middle parts (TOY and/or Event)
        for (i in 1 until parts.size) {
            val part = parts[i]
            val tokens = part.split(" ")
            if (tokens.size >= 2) {
                val label = tokens[0].lowercase()
                val value = tokens[1].toIntOrNull()
                if (value != null) {
                    when (label) {
                        "week", "month", "day", "hour", "minute" -> {
                            toyType = label
                            toyValue = value
                        }
                        "season", "timepoint" -> {
                            eventType = label
                            eventValue = value
                        }
                    }
                }
            }
        }

        val model = CompoundTraitModel(
            baseTraitName = baseName,
            toyType = toyType,
            toyValue = toyValue,
            eventType = eventType,
            eventValue = eventValue,
            observationVariableDbId = observationVariableDbId,
            observationVariableName = fullName
        )

        CompoundTraitDao.insert(model)
        Log.d(TAG, "Indexed: $fullName → base=$baseName, toy=$toyType:$toyValue, event=$eventType:$eventValue")
    }
}
