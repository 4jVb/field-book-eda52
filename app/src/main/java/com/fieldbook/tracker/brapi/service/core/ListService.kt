package com.fieldbook.tracker.brapi.service.core

import android.util.Log
import org.brapi.client.v2.ApiResponse
import org.brapi.client.v2.model.exceptions.ApiException
import org.brapi.client.v2.model.queryParams.core.ListQueryParams
import org.brapi.client.v2.modules.core.ListsApi
import org.brapi.client.v2.modules.phenotype.ObservationVariablesApi
import org.brapi.client.v2.model.queryParams.phenotype.VariableQueryParams
import org.brapi.v2.model.core.BrAPIListSummary
import org.brapi.v2.model.core.response.BrAPIListDetails
import org.brapi.v2.model.core.response.BrAPIListsListResponse
import org.brapi.v2.model.core.response.BrAPIListsSingleResponse
import org.brapi.v2.model.pheno.BrAPIObservationVariable
import org.brapi.v2.model.pheno.response.BrAPIObservationVariableListResponse

/**
 * EDA52 / Breedbase build — BrAPI Lists service.
 */
class ListService(
    private val listsApi: ListsApi,
    private val traitsApi: ObservationVariablesApi
) {

    /**
     * Downloads one page of lists. The listType is deliberately NOT sent to the server and the
     * enum is not used: Breedbase uses types such as "traits" that are not in the BrAPI enum, so
     * filtering is done on the device by the caller.
     */
    fun getLists(page: Int = 0, pageSize: Int = 100): List<BrAPIListSummary> {
        val params = ListQueryParams().apply {
            this.page(page)
            this.pageSize(pageSize)
        }
        val response: ApiResponse<BrAPIListsListResponse> = listsApi.listsGet(params)
        val body = response.body ?: return emptyList()
        val result = body.result ?: return emptyList()
        return result.data ?: emptyList()
    }

    fun getListDetails(listDbId: String): BrAPIListDetails? {
        val response: ApiResponse<BrAPIListsSingleResponse> = listsApi.listsListDbIdGet(listDbId)
        return response.body?.result
    }

    /** Raw entries of the list. Breedbase returns names, other servers may return DbIds. */
    fun getListItems(listDbId: String): List<String> {
        val details = getListDetails(listDbId) ?: return emptyList()
        return (details.data ?: emptyList()).filter { it.isNotBlank() }
    }

    private fun normalize(value: String?): String =
        value.orEmpty().trim().lowercase(java.util.Locale.ROOT).replace(Regex("\\s+"), " ")

    /**
     * Downloads the variables of the server once (paged) and keeps the ones whose DbId OR name
     * matches an entry of the list. Returns the matched variables and the entries not found.
     */
    fun resolveVariables(
        entries: List<String>,
        onProgress: ((Int, Int) -> Unit)? = null
    ): Pair<List<BrAPIObservationVariable>, List<String>> {
        if (entries.isEmpty()) return Pair(emptyList(), emptyList())
        val wanted = entries.associateBy { normalize(it) }
        val found = LinkedHashMap<String, BrAPIObservationVariable>()
        val matchedKeys = HashSet<String>()
        var page = 0
        var totalPages = 1
        val pageSize = 200
        while (page < totalPages && page < 60) {
            val params = VariableQueryParams().apply {
                this.page(page)
                this.pageSize(pageSize)
            }
            val response: ApiResponse<BrAPIObservationVariableListResponse> =
                traitsApi.variablesGet(params)
            val body = response.body ?: break
            val data = body.result?.data ?: break
            for (v in data) {
                val idKey = normalize(v.observationVariableDbId)
                val nameKey = normalize(v.observationVariableName)
                val key = when {
                    wanted.containsKey(idKey) -> idKey
                    wanted.containsKey(nameKey) -> nameKey
                    else -> null
                }
                if (key != null && v.observationVariableDbId != null) {
                    found[v.observationVariableDbId] = v
                    matchedKeys.add(key)
                }
            }
            totalPages = body.metadata?.pagination?.totalPages ?: 1
            page++
            onProgress?.invoke(page, totalPages)
        }
        val missing = wanted.filterKeys { it !in matchedKeys }.values.toList()
        if (missing.isNotEmpty()) Log.w(TAG, "List entries not found as variables: $missing")
        return Pair(found.values.toList(), missing)
    }

    companion object {
        private const val TAG = "BrApiListService"
    }
}
