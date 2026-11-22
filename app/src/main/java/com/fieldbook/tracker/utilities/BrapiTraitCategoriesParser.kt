package com.fieldbook.tracker.utilities

import com.google.gson.Gson
import com.google.gson.JsonObject
import org.brapi.v2.model.pheno.BrAPIScaleValidValuesCategories

/**
 * EDA52 / Breedbase build — Fallback parser for trait categories.
 *
 * Breedbase represents categorical variables in two different ways:
 *  1. Standard BrAPI: scale.validValues.categories list of {label, value}.
 *  2. Breedbase-specific: additionalInfo.trait_categories (e.g. "3/2/1") + additionalInfo.trait_details
 *     (e.g. "Alto/Medio/Bajo"). Some variables (e.g. "Género") return "NA" for trait_categories and
 *     only populate trait_details.
 *
 * Output: Field Book internal JSON representation:
 * `[{"label":"Alto","value":"3"},{"label":"Medio","value":"2"},{"label":"Bajo","value":"1"}]`
 */
object BrapiTraitCategoriesParser {

    fun parseFromAdditionalInfo(additionalInfo: JsonObject?): String? {
        if (additionalInfo == null) return null
        val traitCategories = additionalInfo.get("trait_categories")?.asString
        val traitDetails = additionalInfo.get("trait_details")?.asString
        return parse(traitCategories, traitDetails)
    }

    fun parse(traitCategories: String?, traitDetails: String?): String? {
        val cats = sanitize(traitCategories)
        val details = sanitize(traitDetails)

        if (cats.isNotEmpty() && details.isNotEmpty()) {
            val valueTokens = cats.split("/").map { it.trim() }.filter { it.isNotEmpty() }
            val labelTokens = details.split("/").map { it.trim() }.filter { it.isNotEmpty() }
            if (valueTokens.size == labelTokens.size && valueTokens.isNotEmpty()) {
                return buildJson(valueTokens, labelTokens)
            }
        }

        if (cats.isNotEmpty()) {
            val tokens = cats.split("/").map { it.trim() }.filter { it.isNotEmpty() }
            if (tokens.isNotEmpty()) return buildJson(tokens, tokens)
        }

        if (details.isNotEmpty()) {
            val tokens = details.split("/").map { it.trim() }.filter { it.isNotEmpty() }
            if (tokens.isNotEmpty()) return buildJson(tokens, tokens)
        }

        return null
    }

    private fun sanitize(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val trimmed = raw.trim()
        return if (trimmed.equals("NA", ignoreCase = true) ||
            trimmed.equals("N/A", ignoreCase = true) ||
            trimmed.equals("null", ignoreCase = true)
        ) ""
        else trimmed
    }

    private fun buildJson(values: List<String>, labels: List<String>): String {
        val list = values.mapIndexed { i, value ->
            BrAPIScaleValidValuesCategories().apply {
                this.value = value
                this.label = labels[i]
            }
        }
        return Gson().toJson(list)
    }
}
