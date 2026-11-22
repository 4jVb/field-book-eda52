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

    fun getLists(listType: String? = null, page: Int = 0, pageSize: Int = 100): List<BrAPIListSummary> {
        val params = ListQueryParams().apply {
            this.page(page)
            this.pageSize(pageSize)
            if (!listType.isNullOrBlank()) {
                try {
                    val enumValue = org.brapi.v2.model.core.BrAPIListTypes.fromValue(listType)
                    this.listType(enumValue)
                } catch (e: IllegalArgumentException) {
                    Log.w(TAG, "Unknown listType '$listType', ignoring filter", e)
                }
            }
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

    fun getListItems(listDbId: String): List<String> {
        val details = getListDetails(listDbId) ?: return emptyList()
        return details.data ?: emptyList()
    }

    fun getVariablesByIds(variableDbIds: List<String>): List<BrAPIObservationVariable> {
        if (variableDbIds.isEmpty()) return emptyList()
        val idsParam = variableDbIds.joinToString(",")
        val result = mutableListOf<BrAPIObservationVariable>()
        var page = 0
        val pageSize = 200
        var totalPages = 1
        while (page < totalPages) {
            val params = VariableQueryParams().apply {
                this.observationVariableDbId(idsParam)
                this.page(page)
                this.pageSize(pageSize)
            }
            val response: ApiResponse<BrAPIObservationVariableListResponse> =
                traitsApi.variablesGet(params)
            val body = response.body ?: break
            val data = body.result?.data ?: break
            result.addAll(data)
            val pagination = body.metadata?.pagination
            totalPages = pagination?.totalPages ?: 1
            page++
            if (page > 50) break
        }
        return result
    }

    companion object {
        private const val TAG = "BrApiListService"
    }
}
