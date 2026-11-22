package com.fieldbook.tracker.activities.brapi.io

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.ThemedActivity
import com.fieldbook.tracker.brapi.service.BrAPIServiceFactory
import com.fieldbook.tracker.brapi.service.BrAPIServiceV2
import com.fieldbook.tracker.utilities.InsetHandler
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.brapi.v2.model.core.BrAPIListSummary

class BrapiListPickerActivity : ThemedActivity(), CoroutineScope by MainScope() {

    companion object {
        const val TAG = "BrapiListPicker"
        const val EXTRA_LIST_DB_ID = "listDbId"
        const val EXTRA_LIST_NAME = "listName"
        const val EXTRA_LIST_SIZE = "listSize"

        fun getIntent(activity: Activity): Intent =
            Intent(activity, BrapiListPickerActivity::class.java)
    }

    private lateinit var toolbar: Toolbar
    private lateinit var searchEt: TextInputEditText
    private lateinit var typeChipGroup: ChipGroup
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var loadMoreBtn: MaterialButton
    private lateinit var importBtn: MaterialButton

    private val adapter = ListsAdapter { selected ->
        currentSelection = selected
        importBtn.isEnabled = selected != null
    }

    private var currentSelection: BrAPIListSummary? = null
    private val allLists = mutableListOf<BrAPIListSummary>()
    private var currentPage = 0
    private val pageSize = 100
    private var currentTypeFilter: String? = "traits"

    private val brapiService by lazy {
        (BrAPIServiceFactory.getBrAPIService(this) as? BrAPIServiceV2).also { service ->
            if (service == null) {
                Log.e(TAG, "BrAPI v1 server is not supported for lists")
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_brapi_list_picker)

        toolbar = findViewById(R.id.act_brapi_list_picker_tb)
        searchEt = findViewById(R.id.act_brapi_list_picker_search_et)
        typeChipGroup = findViewById(R.id.act_brapi_list_picker_type_cg)
        recyclerView = findViewById(R.id.act_brapi_list_picker_rv)
        progressBar = findViewById(R.id.act_brapi_list_picker_pb)
        loadMoreBtn = findViewById(R.id.act_brapi_list_picker_load_more_btn)
        importBtn = findViewById(R.id.act_brapi_list_picker_import_btn)

        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            title = getString(R.string.act_brapi_list_picker_title)
            setDisplayHomeAsUpEnabled(true)
        }
        InsetHandler.setupStandardInsets(findViewById(android.R.id.content), toolbar)
        onBackPressedDispatcher.addCallback(this, standardBackCallback())
        recyclerView.adapter = adapter

        searchEt.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { applyFilter() }
        })

        typeChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            currentTypeFilter = when {
                checkedIds.isEmpty() -> null
                checkedIds.contains(R.id.act_brapi_list_picker_chip_traits) -> "traits"
                checkedIds.contains(R.id.act_brapi_list_picker_chip_germplasm) -> "germplasm"
                else -> null
            }
            restartFetch()
        }

        loadMoreBtn.setOnClickListener { currentPage++; fetchLists() }
        importBtn.setOnClickListener {
            currentSelection?.let { selected ->
                val data = Intent().apply {
                    putExtra(EXTRA_LIST_DB_ID, selected.listDbId)
                    putExtra(EXTRA_LIST_NAME, selected.listName)
                    putExtra(EXTRA_LIST_SIZE, selected.listSize)
                }
                setResult(RESULT_OK, data)
                finish()
            }
        }
        restartFetch()
    }

    private fun restartFetch() {
        allLists.clear()
        adapter.submitList(emptyList())
        currentPage = 0
        currentSelection = null
        importBtn.isEnabled = false
        fetchLists()
    }

    private fun fetchLists() {
        progressBar.visibility = View.VISIBLE
        loadMoreBtn.visibility = View.GONE
        launch(Dispatchers.IO) {
            try {
                val pageResult = brapiService?.listService?.getLists(
                    listType = currentTypeFilter, page = currentPage, pageSize = pageSize
                ) ?: emptyList()
                withContext(Dispatchers.Main) {
                    allLists.addAll(pageResult)
                    applyFilter()
                    if (pageResult.size >= pageSize) loadMoreBtn.visibility = View.VISIBLE
                    else loadMoreBtn.visibility = View.GONE
                    progressBar.visibility = View.GONE
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch lists page $currentPage", e)
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    loadMoreBtn.visibility = View.GONE
                }
            }
        }
    }

    private fun applyFilter() {
        val query = searchEt.text?.toString()?.trim().orEmpty()
        val filtered = if (query.isEmpty()) allLists.toList()
        else allLists.filter {
            it.listName?.contains(query, ignoreCase = true) == true ||
            it.listDescription?.contains(query, ignoreCase = true) == true
        }
        adapter.submitList(filtered)
    }

    override fun onDestroy() { super.onDestroy(); cancel() }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            setResult(RESULT_CANCELED)
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private class ListsAdapter(
        private val onSelect: (BrAPIListSummary?) -> Unit
    ) : ListAdapter<BrAPIListSummary, ListViewHolder>(DIFF) {
        private var selectedPos = RecyclerView.NO_POSITION
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ListViewHolder {
            val tv = TextView(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                setPadding(48, 32, 48, 32); textSize = 16f
            }
            return ListViewHolder(tv)
        }
        override fun onBindViewHolder(holder: ListViewHolder, position: Int) {
            val item = getItem(position)
            val tv = holder.itemView as TextView
            val name = item.listName ?: "(sin nombre)"
            val type = item.listType?.toString()?.lowercase() ?: ""
            val size = item.listSize ?: 0
            val desc = item.listDescription?.takeIf { it.isNotBlank() } ?: ""
            tv.text = buildString {
                append(name)
                if (type.isNotEmpty()) append("  [$type]")
                append("\n"); append("$size items")
                if (desc.isNotEmpty()) { append("\n"); append(desc) }
            }
            val isSelected = position == selectedPos
            tv.setBackgroundColor(if (isSelected) 0x33000000 else 0x00000000)
            tv.setOnClickListener {
                val oldPos = selectedPos
                selectedPos = holder.bindingAdapterPosition
                if (oldPos != RecyclerView.NO_POSITION) notifyItemChanged(oldPos)
                notifyItemChanged(selectedPos)
                onSelect(if (selectedPos != RecyclerView.NO_POSITION) getItem(selectedPos) else null)
            }
        }
        companion object {
            val DIFF = object : DiffUtil.ItemCallback<BrAPIListSummary>() {
                override fun areItemsTheSame(a: BrAPIListSummary, b: BrAPIListSummary) = a.listDbId == b.listDbId
                override fun areContentsTheSame(a: BrAPIListSummary, b: BrAPIListSummary) = a == b
            }
        }
    }
    private class ListViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
}
