package com.fieldbook.tracker.database.dao

import android.content.ContentValues
import com.fieldbook.tracker.database.*
import com.fieldbook.tracker.database.models.CompoundTraitModel

/**
 * EDA52 / Breedbase build — DAO for the compound_trait_index table.
 */
object CompoundTraitDao {

    private const val TABLE = "compound_trait_index"

    /**
     * Inserts or replaces a compound trait mapping.
     */
    fun insert(model: CompoundTraitModel): Long? = withDatabase { db ->
        val cv = ContentValues().apply {
            put("base_trait_name", model.baseTraitName)
            put("toy_type", model.toyType)
            put("toy_value", model.toyValue)
            put("event_type", model.eventType)
            put("event_value", model.eventValue)
            put("observation_variable_db_id", model.observationVariableDbId)
            put("observation_variable_name", model.observationVariableName)
            put("study_id", model.studyId)
        }
        db.insertWithOnConflict(TABLE, null, cv, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
    }

    /**
     * Looks up a compound trait by its full compound name (normalized).
     * Returns null if not found.
     */
    fun findByCompoundName(compoundName: String): CompoundTraitModel? = withDatabase { db ->
        val cursor = db.query(
            TABLE,
            select = arrayOf("id", "base_trait_name", "toy_type", "toy_value",
                "event_type", "event_value", "observation_variable_db_id",
                "observation_variable_name", "study_id"),
            where = "observation_variable_name = ? COLLATE NOCASE",
            whereArgs = arrayOf(compoundName)
        )
        cursor?.toFirst()?.let { CompoundTraitModel.fromRow(it) }
    }

    /**
     * Looks up a compound trait by base name + TOY + event configuration.
     */
    fun find(
        baseTraitName: String,
        toyType: String? = null,
        toyValue: Int? = null,
        eventType: String? = null,
        eventValue: Int? = null
    ): CompoundTraitModel? = withDatabase { db ->
        val sb = StringBuilder("base_trait_name = ? COLLATE NOCASE")
        val args = mutableListOf<String>(baseTraitName)
        if (toyType != null) { sb.append(" AND toy_type = ?"); args.add(toyType) }
        if (toyValue != null) { sb.append(" AND toy_value = ?"); args.add(toyValue.toString()) }
        if (eventType != null) { sb.append(" AND event_type = ?"); args.add(eventType) }
        if (eventValue != null) { sb.append(" AND event_value = ?"); args.add(eventValue.toString()) }

        val cursor = db.query(
            TABLE,
            select = arrayOf("id", "base_trait_name", "toy_type", "toy_value",
                "event_type", "event_value", "observation_variable_db_id",
                "observation_variable_name", "study_id"),
            where = sb.toString(),
            whereArgs = args.toTypedArray()
        )
        cursor?.toFirst()?.let { CompoundTraitModel.fromRow(it) }
    }

    /**
     * Returns all compound traits indexed for the given base trait name.
     */
    fun findAllByBaseName(baseTraitName: String): List<CompoundTraitModel> = withDatabase { db ->
        val cursor = db.query(
            TABLE,
            select = arrayOf("id", "base_trait_name", "toy_type", "toy_value",
                "event_type", "event_value", "observation_variable_db_id",
                "observation_variable_name", "study_id"),
            where = "base_trait_name = ? COLLATE NOCASE",
            whereArgs = arrayOf(baseTraitName)
        )
        cursor?.toTable()?.map { CompoundTraitModel.fromRow(it) } ?: emptyList()
    } ?: emptyList()

    /**
     * Returns every row of the index (it is small: only compound variables imported from lists).
     */
    fun findAll(): List<CompoundTraitModel> = withDatabase { db ->
        val cursor = db.rawQuery(
            "SELECT id, base_trait_name, toy_type, toy_value, event_type, event_value, " +
                "observation_variable_db_id, observation_variable_name, study_id FROM $TABLE", null
        )
        cursor?.toTable()?.map { CompoundTraitModel.fromRow(it) } ?: emptyList()
    } ?: emptyList()

    /**
     * Deletes all entries (used when the DB is reset or when re-importing lists).
     */
    fun deleteAll(): Int = withDatabase { db ->
        db.delete(TABLE, null, null)
    } ?: 0

    /**
     * Counts total entries in the index.
     */
    fun count(): Int = withDatabase { db ->
        val cursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE", null)
        cursor?.use {
            if (it.moveToFirst()) it.getInt(0) else 0
        } ?: 0
    } ?: 0
}
