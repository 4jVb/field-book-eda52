package com.fieldbook.tracker.traits.formats

import android.content.Context

enum class Formats {
    BOOLEAN, BASE_PHOTO, CAMERA, CATEGORICAL, COUNTER, DATE,
    HARDWARE, LABEL_PRINT, LOCATION, GNSS, NUMERIC, PERCENT,
    TEXT, CUSTOM, DISEASE_RATING;

    companion object {
        fun isCameraTrait(format: String) = format in getCameraFormats().map { it.getDatabaseName() }
        fun isExternalCameraTrait(format: String) = false
        fun getSpectralFormats() = emptySet<Formats>()
        fun getExperimentalFormats() = emptySet<Formats>()
        fun getHardwareFormats() = entries.filter { it in setOf(LABEL_PRINT) }
        fun getCustomFormats() = entries.filter { it in setOf(DISEASE_RATING) }
        fun getCameraFormats() = entries.filter { it in setOf(CAMERA) }
        fun getMainFormats() = entries - getCameraFormats().toSet() - getHardwareFormats().toSet() - getCustomFormats().toSet()
        fun getBaseFormats() = setOf(BASE_PHOTO, HARDWARE, CUSTOM)
        fun findTrait(format: String) = entries.find { it.getDatabaseName() == format }?.getTraitFormatDefinition()
        fun isGeoFormat(format: String) = format in setOf(LOCATION.getDatabaseName(), GNSS.getDatabaseName())
    }

    fun getTraitFormatDefinition() = when (this) {
        BOOLEAN -> BooleanFormat()
        BASE_PHOTO -> BasePhotoFormat()
        CAMERA -> PhotoFormat()
        CATEGORICAL -> CategoricalFormat()
        COUNTER -> CounterFormat()
        DATE -> DateFormat()
        LOCATION -> LocationFormat()
        GNSS -> GnssFormat()
        NUMERIC -> NumericFormat()
        PERCENT -> PercentFormat()
        DISEASE_RATING -> DiseaseRatingFormat()
        LABEL_PRINT -> ZebraLabelPrintFormat()
        CUSTOM -> CustomFormat()
        HARDWARE -> HardwareFormat()
        else -> TextFormat()
    }

    fun getReadableName(ctx: Context): String {
        val d = getTraitFormatDefinition()
        return d.stringNameAux?.let { it(ctx) } ?: ctx.getString(d.nameStringResourceId)
    }

    fun getName(ctx: Context): String = ctx.getString(getTraitFormatDefinition().nameStringResourceId)

    fun getDatabaseName() = getTraitFormatDefinition().databaseName

    fun getIcon() = getTraitFormatDefinition().iconDrawableResourceId
}
