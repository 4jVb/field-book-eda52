package com.fieldbook.tracker.database.models

import com.fieldbook.tracker.database.Row

/**
 * EDA52 / Breedbase build — Model for the compound_trait_index table.
 * Maps a (base_trait_name, toy_type, toy_value, event_type, event_value) combination
 * to the Breedbase observation_variable_db_id of the corresponding compound trait.
 */
data class CompoundTraitModel(
    val id: Int = 0,
    val baseTraitName: String,
    val toyType: String? = null,
    val toyValue: Int? = null,
    val eventType: String? = null,
    val eventValue: Int? = null,
    val observationVariableDbId: String,
    val observationVariableName: String,
    val studyId: Int? = null
) {
    companion object {
        fun fromRow(row: Row): CompoundTraitModel = CompoundTraitModel(
            id = (row["id"] as? Number)?.toInt() ?: 0,
            baseTraitName = row["base_trait_name"] as? String ?: "",
            toyType = row["toy_type"] as? String,
            toyValue = (row["toy_value"] as? Number)?.toInt(),
            eventType = row["event_type"] as? String,
            eventValue = (row["event_value"] as? Number)?.toInt(),
            observationVariableDbId = row["observation_variable_db_id"] as? String ?: "",
            observationVariableName = row["observation_variable_name"] as? String ?: "",
            studyId = (row["study_id"] as? Number)?.toInt()
        )
    }
}
