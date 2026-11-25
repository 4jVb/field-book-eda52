package com.fieldbook.tracker.utilities

import android.content.Context
import android.util.Log
import com.fieldbook.tracker.brapi.service.BrAPIService
import com.fieldbook.tracker.objects.TraitObject
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

/**
 * EDA52 / Breedbase build — Breedbase's BrAPI does not return trait_details (the readable
 * labels, e.g. Bajo/Medio/Alto), only trait_categories (1/2/3). Both are available from
 * Breedbase's own endpoint /cvterm/prop/get?cvterm_id=<variable DbId>, so categorical traits
 * are completed from there when they are imported:
 *   label = trait_details (what the evaluator sees), value = trait_categories (what is uploaded).
 * Servers that do not have that endpoint are left untouched.
 */
object BreedbaseCategoryEnricher {

    private const val TAG = "BreedbaseCategories"

    private val cache = ConcurrentHashMap<String, Map<String, String>>()

    /** Blocking; runs the network work on its own thread so it is safe to call from any thread. */
    fun enrich(context: Context, traits: Collection<TraitObject>) {
        val appContext = context.applicationContext
        var failure: Throwable? = null
        val worker = thread(start = true, name = "breedbase-categories") {
            try {
                doEnrich(appContext, traits)
            } catch (t: Throwable) {
                failure = t
            }
        }
        worker.join()
        failure?.let { Log.w(TAG, "Could not complete categories from Breedbase", it) }
    }

    private fun serverRoot(context: Context): String? = try {
        val url = URL(BrAPIService.getBrapiUrl(context))
        val port = if (url.port == -1) "" else ":${url.port}"
        "${url.protocol}://${url.host}$port"
    } catch (e: Exception) {
        null
    }

    private fun doEnrich(context: Context, traits: Collection<TraitObject>) {
        val root = serverRoot(context) ?: return
        var failures = 0
        for (trait in traits) {
            if (failures >= 2) break // the server does not offer this endpoint: stop trying
            if (trait.format != "categorical") continue
            val id = trait.externalDbId
            if (id.isNullOrEmpty()) continue

            val props = cache[id] ?: fetchProps(root, id)?.also { cache[id] = it }
            if (props == null) {
                failures++
                continue
            }

            val json = BrapiTraitCategoriesParser.parse(props["trait_categories"], props["trait_details"])
                ?: continue
            val list = CategoryJsonUtil.decode(json)
            if (list.isEmpty()) continue

            trait.categories = CategoryJsonUtil.buildCategoryList(list)
            trait.details = trait.details.substringBefore("\nCategories:") +
                "\nCategories: " + CategoryJsonUtil.buildCategoryDescriptionString(list)
            Log.i(TAG, "${trait.name}: ${trait.categories}")
        }
    }

    private fun fetchProps(root: String, id: String): Map<String, String>? {
        var connection: HttpURLConnection? = null
        try {
            val url = URL("$root/cvterm/prop/get?cvterm_id=${URLEncoder.encode(id, "UTF-8")}")
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/json")
            if (connection.responseCode != 200) {
                Log.w(TAG, "cvterm/prop/get answered ${connection.responseCode} for $id")
                return null
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val array = JSONArray(body)
            val map = HashMap<String, String>()
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                map[o.optString("type_name")] = o.optString("value")
            }
            return map
        } catch (e: Exception) {
            Log.w(TAG, "cvterm/prop/get failed for $id", e)
            return null
        } finally {
            connection?.disconnect()
        }
    }
}
