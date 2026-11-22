package com.fieldbook.tracker.utilities

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.fieldbook.tracker.R
import com.fieldbook.tracker.database.dao.CompoundTraitDao
import com.fieldbook.tracker.dialogs.TimeOfYearEventDialog
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.preferences.GeneralKeys
import java.security.MessageDigest

/**
 * EDA52 / Breedbase build — Helper for the Time of Year / Event dialog lifecycle.
 *
 * Responsibilities:
 *  - Computes the traits hash for the active field (used as part of the config storage key)
 *  - Checks if the dialog should be shown (config not yet saved for this field+traits)
 *  - Shows the dialog and persists the config
 *  - Provides the active config to BrapiSyncUtil for compound trait lookup
 *  - Triggers config reset when the field or traits change
 */
object TimeOfYearEventHelper {

    private const val TAG = "ToyEventHelper"

    /**
     * Computes a hash of the trait names for the given study, to detect when the
     * trait list changes and the TOY config should be reset.
     */
    fun computeTraitsHash(traits: List<TraitObject>): String {
        val sortedNames = traits.mapNotNull { it.name }.sorted().joinToString(",")
        val md = MessageDigest.getInstance("MD5")
        val bytes = md.digest(sortedNames.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Checks if the TOY dialog should be shown for the given field+traits combination.
     * Returns true if no config is saved (dialog should appear).
     */
    fun shouldShowDialog(
        context: Context,
        studyId: Int,
        traits: List<TraitObject>
    ): Boolean {
        val hash = computeTraitsHash(traits)
        return !TimeOfYearEventConfigStore.exists(context, studyId, hash)
    }

    /**
     * Shows the Time of Year / Event dialog if needed (no config saved yet).
     * Does nothing if a config already exists for the current field+traits.
     *
     * @return true if the dialog was shown, false if it was skipped (config exists).
     */
    fun showDialogIfNeeded(
        context: Context,
        studyId: Int,
        traits: List<TraitObject>
    ): Boolean {
        if (!shouldShowDialog(context, studyId, traits)) return false

        val hash = computeTraitsHash(traits)
        val currentConfig = TimeOfYearEventConfigStore.load(context, studyId, hash)

        val dialog = TimeOfYearEventDialog(
            context = context,
            traits = traits,
            onConfigSaved = { config ->
                TimeOfYearEventConfigStore.save(context, studyId, hash, config)
                Log.i(TAG, "TOY config saved for study $studyId: toy=${config.timeOfYearType}:${config.timeOfYearValue}, event=${config.eventType}:${config.eventValue}")
            },
            onSkip = {
                TimeOfYearEventConfigStore.markSkipped(context, studyId, hash)
                Log.i(TAG, "TOY dialog skipped for study $studyId")
            }
        )
        dialog.show(currentConfig)
        return true
    }

    /**
     * Resets (clears) the TOY config for the given studyId. Called when the field changes
     * or when traits are modified.
     */
    fun resetConfig(context: Context, studyId: Int, traits: List<TraitObject>) {
        val hash = computeTraitsHash(traits)
        TimeOfYearEventConfigStore.clear(context, studyId, hash)
    }

    /**
     * Returns the active TOY config for the given field+traits, or null if none.
     */
    fun getActiveConfig(
        context: Context,
        studyId: Int,
        traits: List<TraitObject>
    ): TimeOfYearEventConfig? {
        val hash = computeTraitsHash(traits)
        return TimeOfYearEventConfigStore.load(context, studyId, hash)
    }
}
