package com.fieldbook.tracker.utilities.export

import android.content.Context
import android.util.Log
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.traits.CategoricalTraitLayout
import com.fieldbook.tracker.traits.formats.Formats
import com.fieldbook.tracker.traits.formats.presenters.UriPresenter
import com.fieldbook.tracker.traits.formats.presenters.ValuePresenter
import com.fieldbook.tracker.utilities.CategoryJsonUtil
import com.fieldbook.tracker.utilities.DateJsonUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class ValueProcessorFormatAdapter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val spectralFileProcessor: SpectralFileProcessor
) {

    companion object {
        private const val TAG = "ValueProcessorFA"
    }

    fun processValue(value: String, trait: TraitObject, forBrapi: Boolean = false): String? {
        return when {

            trait.format in setOf(Formats.DATE.getDatabaseName()) -> {

                val dateValue = DateJsonUtil.decode(value)

                return (Formats.DATE.getTraitFormatDefinition() as ValuePresenter).represent(context, dateValue, trait)
            }

            // EDA52: POLLINATOR trait removed; this branch was dead code.
            trait.format in CategoricalTraitLayout.POSSIBLE_VALUES -> {
                return CategoryJsonUtil.processValue(
                    buildMap {
                        put("value", value)
                        put("observation_variable_field_book_format", trait.format)
                        put("forBrapi", forBrapi)
                        put("categoryDisplayValue", trait.categoryDisplayValue)
                    })
            }

            // EDA52: spectral trait formats removed; this branch was dead code.
            Formats.isCameraTrait(trait.format) -> {
                if (value.isEmpty()) "" else UriPresenter().represent(context, value, trait)
            }

            else -> value

        }.toString()
    }
}