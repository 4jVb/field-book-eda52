package com.fieldbook.tracker.utilities

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import org.brapi.v2.model.pheno.BrAPIObservationVariable
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

    /**
     * Builds the categories of a Breedbase variable.
     *  - categories + details with the same count -> label = detail (what the evaluator sees),
     *    value = category (what Breedbase accepts on upload).
     *  - only details (no categories)            -> label = value = detail.
     *  - only categories                         -> as sent by the server.
     * Returns null when the variable has no category information at all.
     */
    fun fromVariable(variable: BrAPIObservationVariable): List<BrAPIScaleValidValuesCategories>? {
        val levels = listOf(
            variable.additionalInfo,
            additionalInfoOf(variable.trait),
            additionalInfoOf(variable.scale),
            additionalInfoOf(variable.method)
        )
        val details = sanitize(findText(levels, listOf("trait_details", "traitdetails", "details")))
        val catsText = sanitize(findText(levels, listOf("trait_categories", "traitcategories")))
        val detailTokens = details.split("/").map { it.trim() }.filter { it.isNotEmpty() }

        val scaleCats = variable.scale?.validValues?.categories.orEmpty()
        if (scaleCats.isNotEmpty()) {
            if (detailTokens.size == scaleCats.size) {
                return scaleCats.mapIndexed { i, c ->
                    BrAPIScaleValidValuesCategories().apply {
                        this.value = c.value ?: c.label
                        this.label = detailTokens[i]
                    }
                }
            }
            return scaleCats
        }

        val tokensCats = catsText.split("/").map { it.trim() }.filter { it.isNotEmpty() }
        val result = when {
            tokensCats.isNotEmpty() && tokensCats.size == detailTokens.size ->
                tokensCats.mapIndexed { i, v -> cat(v, detailTokens[i]) }
            tokensCats.isNotEmpty() -> tokensCats.map { cat(it, it) }
            detailTokens.isNotEmpty() -> detailTokens.map { cat(it, it) }
            else -> null
        }
        return result
    }

    private fun cat(value: String, label: String) = BrAPIScaleValidValuesCategories().apply {
        this.value = value
        this.label = label
    }

    /** Reads additionalInfo of BrAPI sub-objects without depending on each class declaring it. */
    fun additionalInfoOf(obj: Any?): JsonObject? = try {
        if (obj == null) null else obj.javaClass.getMethod("getAdditionalInfo").invoke(obj) as? JsonObject
    } catch (e: Exception) {
        null
    }

    /** First value found under any of [keys] (case-insensitive), searching nested objects too. */
    private fun findText(objects: List<JsonObject?>, keys: List<String>): String? {
        for (o in objects) {
            val found = findIn(o, keys)
            if (found != null) return found
        }
        return null
    }

    private fun findIn(element: JsonElement?, keys: List<String>): String? {
        if (element == null || element.isJsonNull) return null
        if (element.isJsonObject) {
            for ((k, v) in element.asJsonObject.entrySet()) {
                if (keys.any { it.equals(k, ignoreCase = true) }) {
                    textOf(v)?.let { return it }
                }
            }
            for ((_, v) in element.asJsonObject.entrySet()) {
                findIn(v, keys)?.let { return it }
            }
        }
        return null
    }

    private fun textOf(v: JsonElement): String? = when {
        v.isJsonNull -> null
        v.isJsonPrimitive -> v.asString
        v is JsonArray -> v.mapNotNull { if (it.isJsonPrimitive) it.asString else null }.joinToString("/")
        else -> null
    }

    /** Compact dump of every additionalInfo, used only for diagnostics in the log. */
    fun debugInfo(variable: BrAPIObservationVariable): String =
        "variable=${variable.additionalInfo} trait=${additionalInfoOf(variable.trait)} " +
            "scale=${additionalInfoOf(variable.scale)} method=${additionalInfoOf(variable.method)}"

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
