package com.fieldbook.tracker.database.migrators

import android.database.sqlite.SQLiteDatabase

/**
 * EDA52 / Breedbase build — Migration v22: creates the compound_trait_index table.
 *
 * Stores the mapping (base_trait_name, toy_type, toy_value, event_type, event_value) →
 * observation_variable_db_id for traits compuestos in Breedbase. Populated during list
 * import when the list contains compound traits (names with "|").
 */
class CompoundTraitIndexMigratorVersion22 : FieldBookMigrator {

    companion object {
        const val VERSION = 22
        private const val TAG = "CompoundTraitMigrator"

        private const val CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS compound_trait_index (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                base_trait_name TEXT NOT NULL,
                toy_type TEXT,
                toy_value INTEGER,
                event_type TEXT,
                event_value INTEGER,
                observation_variable_db_id TEXT NOT NULL,
                observation_variable_name TEXT NOT NULL,
                study_id INTEGER,
                created_at TEXT DEFAULT (datetime('now'))
            )
        """

        private const val CREATE_INDEX_BASE = """
            CREATE INDEX IF NOT EXISTS idx_compound_trait_base
            ON compound_trait_index(base_trait_name)
        """

        private const val CREATE_INDEX_FULL = """
            CREATE INDEX IF NOT EXISTS idx_compound_trait_full
            ON compound_trait_index(base_trait_name, toy_type, toy_value, event_type, event_value)
        """
    }

    override fun migrate(db: SQLiteDatabase): Result<Any> = runCatching {
        db.beginTransaction()
        try {
            db.execSQL(CREATE_TABLE)
            db.execSQL(CREATE_INDEX_BASE)
            db.execSQL(CREATE_INDEX_FULL)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}
