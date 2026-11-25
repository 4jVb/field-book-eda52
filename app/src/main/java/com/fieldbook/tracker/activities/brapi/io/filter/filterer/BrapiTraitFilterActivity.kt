package com.fieldbook.tracker.activities.brapi.io.filter.filterer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.brapi.io.BrapiCacheModel
import com.fieldbook.tracker.activities.brapi.io.BrapiListPickerActivity
import com.fieldbook.tracker.activities.brapi.io.filter.BrapiCropsFilterActivity
import com.fieldbook.tracker.activities.brapi.io.BrapiFilterCache
import com.fieldbook.tracker.activities.brapi.io.BrapiFilterTypeAdapter
import com.fieldbook.tracker.activities.brapi.io.filter.BrapiSubFilterListActivity
import com.fieldbook.tracker.activities.brapi.io.BrapiTraitImporterActivity
import com.fieldbook.tracker.activities.brapi.io.filter.BrapiTrialsFilterActivity
import com.fieldbook.tracker.activities.brapi.io.mapper.DataTypes
import com.fieldbook.tracker.activities.brapi.io.mapper.toTraitObject
import com.fieldbook.tracker.adapters.CheckboxListAdapter
import com.fieldbook.tracker.brapi.service.BrAPIServiceFactory
import com.fieldbook.tracker.brapi.service.BrAPIServiceV2
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.database.repository.TraitRepository
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.utilities.CompoundTraitIndexer
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.preferences.PreferenceKeys
import com.fieldbook.tracker.traits.formats.Formats
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.brapi.client.v2.model.queryParams.phenotype.VariableQueryParams
import org.brapi.v2.model.core.BrAPIStudy
import org.brapi.v2.model.pheno.BrAPIObservationVariable
import javax.inject.Inject
import androidx.core.content.edit
import com.fieldbook.tracker.brapi.service.BrAPIServiceV1

@AndroidEntryPoint
class BrapiTraitFilterActivity(
    override val defaultRootFilterKey: String = FILTERER_KEY,
    override val filterName: String = "traits",
    override val titleResId: Int = R.string.brapi_traits_filter_title
) : BrapiSubFilterListActivity<BrAPIStudy>() {

    companion object {
        const val FILTER_NAME = "traits"
        const val FILTERER_KEY = "com.fieldbook.tracker.activities.brapi.io.filters.traits."
        const val REQUEST_LIST_PICKER = 7002
        private const val TAG = "BrapiTraitFilter"
        fun getIntent(context: Context) = Intent(context, BrapiTraitFilterActivity::class.java)
    }

    enum class FilterChoice {
        TRIAL,
        STUDY,
        CROP,
        LIST // EDA52 / Breedbase build: fourth filter option to import by Breedbase list
    }

    @Inject
    lateinit var database: DataHelper

    @Inject
    lateinit var traitRepo: TraitRepository

    private var queryVariablesJob: Job? = null

    private var queried = false

    override fun BrapiCacheModel.filterByPreferences(): BrapiCacheModel {

        val studyDbIds = getIds("${defaultRootFilterKey}${BrapiStudyFilterActivity.FILTER_NAME}")
        val trialDbIds = getIds("${defaultRootFilterKey}${BrapiTrialsFilterActivity.FILTER_NAME}")
        val commonCropNames = getIds("${defaultRootFilterKey}${BrapiCropsFilterActivity.FILTER_NAME}")

        val observationVariableIds = this.studies
            .filter { if (studyDbIds.isNotEmpty()) it.study.studyDbId in studyDbIds else true }
            .filter { if (trialDbIds.isNotEmpty()) it.trialDbId in trialDbIds else true }
            .map { it.study.observationVariableDbIds ?: listOf() }.flatten()

        val filteredVars = this.variables.values
            .filter { if (observationVariableIds.isNotEmpty()) it.observationVariableDbId in observationVariableIds else true }
            .filter { if (commonCropNames.isNotEmpty()) it.commonCropName in commonCropNames else true}
            .associateBy { it.observationVariableDbId }
            .toMutableMap()

        return this.also {
            it.variables = filteredVars
        }
    }

    override fun List<CheckboxListAdapter.Model>.filterBySearchTextPreferences(): List<CheckboxListAdapter.Model> {

        val searchTexts =
            prefs.getStringSet("${filterName}${GeneralKeys.LIST_FILTER_TEXTS}", emptySet())

        return if (searchTexts?.isEmpty() != false) this else filter { model ->
            searchTexts.map { it.lowercase() }.all { tokens ->
                tokens in model.label.lowercase() || tokens in model.subLabel.lowercase() || tokens in model.id.lowercase() || model.searchableTexts.any {
                    it.lowercase().contains(tokens)
                }
            }
        }
    }

    override fun List<CheckboxListAdapter.Model>.filterExists(): List<CheckboxListAdapter.Model> {
        val brapiIds = database.allTraitObjects.map { it.externalDbId }
        return filter { it.id !in brapiIds }
    }

    override fun onSearchTextComplete(searchText: String) {

        prefs.getStringSet("${filterName}${GeneralKeys.LIST_FILTER_TEXTS}", setOf())?.let { texts ->
            prefs.edit {
                putStringSet(
                    "${filterName}${GeneralKeys.LIST_FILTER_TEXTS}",
                    texts.plus(searchText)
                )
            }
        }

        restoreModels()
    }

    override fun setupSearch(models: List<CheckboxListAdapter.Model>) {
        super.setupSearch(models)

        searchBar.editText.setOnEditorActionListener { v, actionId, _ ->

            if (actionId == EditorInfo.IME_ACTION_DONE) {
                onSearchTextComplete(v.text.toString())
                searchBar.editText.text.clear()
                return@setOnEditorActionListener true
            }

            return@setOnEditorActionListener false
        }
    }

    override fun BrapiCacheModel.mapToUiModel() =
        variables.values
            .filter { it.observationVariableDbId != null }.mapNotNull { model ->
                try {
                    CheckboxListAdapter.Model(
                        checked = false,
                        id = model.observationVariableDbId,
                        label = model.synonyms?.firstOrNull() ?: model.observationVariableName
                        ?: model.observationVariableDbId,
                        subLabel = "${model.commonCropName ?: ""} ${model.observationVariableDbId ?: ""}",
                        searchableTexts = model.observationVariableName?.let { listOf(it) }
                            ?: emptyList()
                    ).also {
                        model.scale?.dataType?.name?.let { dataType ->
                            val convertedType = DataTypes.convertBrAPIDataType(dataType)
                            val finalTraitFormat =
                                if (convertedType == "multicat") "categorical" else convertedType

                            Formats.findTrait(finalTraitFormat)?.iconDrawableResourceId?.let { icon ->
                                it.iconResId = icon
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }.toList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        chipGroup.visibility = View.VISIBLE

        setupMainToolbar()

        importTextView.text = getString(R.string.act_brapi_filter_import)

        fetchDescriptionTv.text = getString(R.string.act_brapi_list_filter_loading_variables)

        onBackPressedDispatcher.addCallback(this, standardBackCallback())
    }

    override fun onFinishButtonClicked() {
        //saveFilter()
        BrapiTraitImporterActivity.getIntent(this).also {
           intentLauncher.launch(it.also {
               it.putStringArrayListExtra(BrapiTraitImporterActivity.EXTRA_TRAIT_DB_ID,
                   ArrayList((recyclerView.adapter as CheckboxListAdapter).selected.map { it.id })
               )
           })
        }
    }

    override suspend fun loadData() {
        //super.loadData()

        if (!queried) {

            withContext(Dispatchers.Main) {
                fetchDescriptionTv.text = getString(R.string.act_brapi_list_filter_loading_variables)
                progressBar.visibility = View.VISIBLE
                progressBar.progress = 0
            }

            queryVariablesJob = queryVariables()
            queryVariablesJob?.join()
        }
    }

    private suspend fun queryVariables() = launch(Dispatchers.IO) {

        val pageSize = prefs.getString(PreferenceKeys.BRAPI_PAGE_SIZE, "512")?.toInt() ?: 512

        val variables = arrayListOf<BrAPIObservationVariable>()

        try {

            var count = 0

            queried = true

            if (brapiService is BrAPIServiceV1)
                return@launch

            (brapiService as BrAPIServiceV2).observationVariableService.fetchAll(
                VariableQueryParams().also {
                    it.pageSize(pageSize)
                }
            )
                .catch { e ->
                    onApiException(e)
                    queryVariablesJob?.cancel()
                }
                .collect {

                    var (totalCount, models) = it as Pair<*, *>
                    totalCount = totalCount as Int
                    models = models as List<*>
                    models = models.map { m -> m as BrAPIObservationVariable }

                    count += (models as List<*>).size

                    variables.addAll(models)

                    withContext(Dispatchers.Main) {
                        setProgress(variables.size, totalCount)
                        if (variables.size == totalCount || totalCount < pageSize) {
                            BrapiFilterCache.saveVariables(this@BrapiTraitFilterActivity, variables)
                            restoreModels()
                            cancel()
                        }
                    }
                }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun hasData(): Boolean {
        return BrapiFilterCache.getStoredModels(this).variables.isNotEmpty() or queried
    }

    override fun showNextButton() = (recyclerView.adapter as CheckboxListAdapter).selected.isNotEmpty()

    private fun setupMainToolbar() {

        supportActionBar?.title = getString(R.string.import_brapi_trait_title)

        //enable home button as back button
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

    }

    //add menu to toolbar
    override fun onCreateOptionsMenu(menu: android.view.Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_filter_brapi, menu)
        menu?.findItem(R.id.action_check_all)?.isVisible = false
        menu?.findItem(R.id.action_reset_cache)?.isVisible = true
        menu?.findItem(R.id.action_brapi_filter)?.isVisible = true
        selectionMenuItem = menu?.findItem(R.id.action_clear_selection)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        } else if (item.itemId == R.id.action_brapi_filter) {
            showFilterChoiceDialog()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showFilterChoiceDialog() {

        val tids = BrapiFilterTypeAdapter.toModelList(prefs,
            "${defaultRootFilterKey}${BrapiTrialsFilterActivity.FILTER_NAME}")
            .filter { it.checked }
            .map { it.id }

        val models = BrapiFilterCache.getStoredModels(this).studies
        val trialCount = models.mapNotNull { it.trialDbId }.distinct().size
        val cropCount = models.mapNotNull { it.study.commonCropName }.distinct().size
        val studyCount = models.filter { if (tids.isNotEmpty()) it.trialDbId in tids else true }
            .mapNotNull { it.study.studyDbId }.distinct().size

        saveFilter()

        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_brapi_filter_choices_title)
            .setItems(
                arrayOf(
                    getString(R.string.brapi_filter_type_trial_count, "$trialCount"),
                    getString(R.string.brapi_filter_type_study_count, "$studyCount"),
                    getString(R.string.brapi_filter_type_crop_count, "$cropCount"),
                    getString(R.string.brapi_filter_type_list)
                )
            ) { _, which ->
                if (which == FilterChoice.LIST.ordinal) {
                    startActivityForResult(
                        BrapiListPickerActivity.getIntent(this),
                        REQUEST_LIST_PICKER
                    )
                    return@setItems
                }
                intentLauncher.launch(
                    when (which) {
                        FilterChoice.TRIAL.ordinal -> BrapiTrialsFilterActivity.getIntent(this)
                        FilterChoice.STUDY.ordinal -> BrapiStudyFilterActivity.getIntent(this).also {
                            it.putExtra(BrapiStudyFilterActivity.EXTRA_MODE, "filter")
                        }
                        else -> BrapiCropsFilterActivity.getIntent(this)
                    }.also {
                        it.putExtra(EXTRA_FILTER_ROOT, defaultRootFilterKey)
                    }
                )
            }
            .show()
    }

    // EDA52 / Breedbase build: handle the result of BrapiListPickerActivity.
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_LIST_PICKER && resultCode == RESULT_OK) {
            val listDbId = data?.getStringExtra(BrapiListPickerActivity.EXTRA_LIST_DB_ID) ?: return
            val listName = data.getStringExtra(BrapiListPickerActivity.EXTRA_LIST_NAME) ?: ""
            importTraitsFromList(listDbId, listName)
        }
    }

    private fun importTraitsFromList(listDbId: String, listName: String) {
        val pb = findViewById<android.widget.ProgressBar>(R.id.act_list_filter_pb)
        pb?.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val service = (BrAPIServiceFactory.getBrAPIService(this@BrapiTraitFilterActivity)
                        as? BrAPIServiceV2) ?: run {
                    withContext(Dispatchers.Main) {
                        pb?.visibility = View.GONE
                        Toast.makeText(this@BrapiTraitFilterActivity,
                            R.string.act_brapi_list_import_error, Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }
                // the list calls need the OAuth token, same as every other BrAPI call
                service.authorizeClient()
                val itemIds = service.listService.getListItems(listDbId)
                if (itemIds.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        pb?.visibility = View.GONE
                        Toast.makeText(this@BrapiTraitFilterActivity,
                            R.string.act_brapi_list_import_no_items, Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }
                val (variables, notFound) = service.listService.resolveVariables(itemIds)
                // EDA52: index compound traits (names with "|") for later sync lookup
                CompoundTraitIndexer.indexVariables(variables)
                if (variables.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        pb?.visibility = View.GONE
                        Toast.makeText(this@BrapiTraitFilterActivity,
                            R.string.act_brapi_list_import_no_variables, Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }
                val traitObjects = HashMap<String, TraitObject>()
                variables.forEach { variable ->
                    val trait = variable.toTraitObject(this@BrapiTraitFilterActivity)
                    variable.observationVariableDbId?.let { id -> traitObjects[id] = trait }
                }
                // EDA52: complete categorical labels (trait_details) from Breedbase
                com.fieldbook.tracker.utilities.BreedbaseCategoryEnricher.enrich(
                    this@BrapiTraitFilterActivity, traitObjects.values
                )
                // saveTraitsFromHashmap only inserts traits whose DbId is in the second argument
                traitRepo.saveTraitsFromHashmap(traitObjects, ArrayList(traitObjects.keys))
                prefs.edit { remove(FILTER_NAME) }
                withContext(Dispatchers.Main) {
                    pb?.visibility = View.GONE
                    val extra = if (notFound.isNotEmpty()) " (${notFound.size} no encontrados)" else ""
                    Toast.makeText(this@BrapiTraitFilterActivity,
                        "Importados ${traitObjects.size} traits de \"$listName\"$extra",
                        Toast.LENGTH_LONG).show()
                    setResult(RESULT_OK)
                    finish()
                }
            } catch (e: Exception) {
                Log.e(TAG, "List import failed", e)
                withContext(Dispatchers.Main) {
                    pb?.visibility = View.GONE
                    Toast.makeText(this@BrapiTraitFilterActivity,
                        getString(R.string.act_brapi_list_import_error, e.message ?: ""),
                        Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
