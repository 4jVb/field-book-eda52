package com.fieldbook.tracker.dialogs

import android.app.DatePickerDialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.R
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.utilities.TimeOfYearCalculator
import com.fieldbook.tracker.utilities.TimeOfYearEventConfig
import java.time.LocalDate
import java.util.Calendar

/**
 * EDA52 / Breedbase build — Dialog for configuring Time of Year and Event.
 *
 * Shows:
 *  - Experiment start date (optional, with DatePicker)
 *  - Time of Year type (week/month/day/hour/minute/none) + value (ranges depend on type)
 *  - Auto-calculate checkbox (only for week/month) — computes value from start date
 *  - Event type (season/timepoint/none) + value (season 1-6, timepoint 1-20)
 *  - Trait selection list (all selected by default; user can uncheck specific traits)
 *
 * Rules:
 *  - Only one Time of Year type can be selected (no redundancy)
 *  - Only one Event type can be selected
 *  - "Saltar todo" saves an empty config and prevents the dialog from showing again
 *  - Configuration persists per (studyId, traitsHash) combination
 */
class TimeOfYearEventDialog(
    private val context: Context,
    private val traits: List<TraitObject>,
    private val onConfigSaved: (TimeOfYearEventConfig) -> Unit,
    private val onSkip: () -> Unit
) {
    private var experimentStartDate: LocalDate? = null
    private var selectedTraitIds: MutableSet<String> = traits.map { it.id }.toMutableSet()

    // Spinner adapters
    private val toyTypes = listOf("none", "week", "month", "day", "hour", "minute")
    private val toyTypeLabels = mapOf(
        "none" to context.getString(R.string.toy_dialog_toy_none),
        "week" to context.getString(R.string.toy_dialog_toy_week),
        "month" to context.getString(R.string.toy_dialog_toy_month),
        "day" to context.getString(R.string.toy_dialog_toy_day),
        "hour" to context.getString(R.string.toy_dialog_toy_hour),
        "minute" to context.getString(R.string.toy_dialog_toy_minute)
    )
    private val eventTypes = listOf("none", "season", "timepoint")
    private val eventTypeLabels = mapOf(
        "none" to context.getString(R.string.toy_dialog_toy_none),
        "season" to context.getString(R.string.toy_dialog_event_season),
        "timepoint" to context.getString(R.string.toy_dialog_event_timepoint)
    )

    fun show(currentConfig: TimeOfYearEventConfig? = null) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_time_of_year_event, null)

        val startDateBtn = view.findViewById<Button>(R.id.toy_dialog_start_date_btn)
        val clearDateBtn = view.findViewById<Button>(R.id.toy_dialog_start_date_clear_btn)
        val toyTypeSpinner = view.findViewById<android.widget.Spinner>(R.id.toy_dialog_toy_type_spinner)
        val toyValueSpinner = view.findViewById<android.widget.Spinner>(R.id.toy_dialog_toy_value_spinner)
        val autoToyCb = view.findViewById<CheckBox>(R.id.toy_dialog_auto_toy_cb)
        val eventTypeSpinner = view.findViewById<android.widget.Spinner>(R.id.toy_dialog_event_type_spinner)
        val eventValueSpinner = view.findViewById<android.widget.Spinner>(R.id.toy_dialog_event_value_spinner)
        val traitsRv = view.findViewById<RecyclerView>(R.id.toy_dialog_traits_rv)
        val traitsSummary = view.findViewById<TextView>(R.id.toy_dialog_traits_summary)

        // Restore current config
        experimentStartDate = currentConfig?.experimentStartDate
        if (experimentStartDate != null) {
            startDateBtn.text = experimentStartDate.toString()
        }

        // Setup TOY type spinner
        toyTypeSpinner.adapter = ArrayAdapter(
            context, android.R.layout.simple_spinner_dropdown_item,
            toyTypes.map { toyTypeLabels[it] ?: it }
        )
        val currentToyTypeIndex = toyTypes.indexOf(currentConfig?.timeOfYearType ?: "none").coerceAtLeast(0)
        toyTypeSpinner.setSelection(currentToyTypeIndex)

        // Setup TOY value spinner (depends on type)
        fun updateToyValueSpinner(type: String, currentValue: Int? = null) {
            val values = when (type) {
                "week" -> (1..54).toList()
                "month" -> (1..16).toList()
                "day" -> (1..366).toList()
                "hour" -> (1..24).toList()
                "minute" -> listOf(15, 30, 45, 60)
                else -> emptyList()
            }
            toyValueSpinner.adapter = ArrayAdapter(
                context, android.R.layout.simple_spinner_dropdown_item,
                values.map { it.toString() }
            )
            if (currentValue != null && values.contains(currentValue)) {
                toyValueSpinner.setSelection(values.indexOf(currentValue))
            } else if (values.isNotEmpty()) {
                toyValueSpinner.setSelection(0)
            }
            // Disable value spinner if auto-calculate is on
            toyValueSpinner.isEnabled = !autoToyCb.isChecked && type != "none"
        }
        updateToyValueSpinner(toyTypes[currentToyTypeIndex], currentConfig?.timeOfYearValue)

        toyTypeSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, v: View?, position: Int, id: Long) {
                val type = toyTypes[position]
                updateToyValueSpinner(type)
                // Auto-calculate only available for week and month
                autoToyCb.isEnabled = (type == "week" || type == "month")
                if (type != "week" && type != "month") {
                    autoToyCb.isChecked = false
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        // Auto-calculate checkbox
        autoToyCb.isChecked = currentConfig?.autoTimeOfYear ?: false
        autoToyCb.setOnCheckedChangeListener { _, isChecked ->
            toyValueSpinner.isEnabled = !isChecked && toyTypes[toyTypeSpinner.selectedItemPosition] != "none"
            if (isChecked && experimentStartDate != null) {
                val type = toyTypes[toyTypeSpinner.selectedItemPosition]
                val today = LocalDate.now()
                val computed = when (type) {
                    "week" -> TimeOfYearCalculator.calculateRelativeWeek(experimentStartDate!!, today)
                    "month" -> TimeOfYearCalculator.calculateRelativeMonth(experimentStartDate!!, today)
                    else -> null
                }
                computed?.let { cv ->
                    val values = (toyValueSpinner.adapter as? ArrayAdapter<String>)?.let { adapter ->
                        (0 until adapter.count).map { adapter.getItem(it) ?: "" }
                    } ?: emptyList()
                    val cvStr = cv.toString()
                    val idx = values.indexOf(cvStr)
                    if (idx >= 0) toyValueSpinner.setSelection(idx)
                }
            }
        }

        // Start date picker
        startDateBtn.setOnClickListener {
            val cal = Calendar.getInstance()
            experimentStartDate?.let {
                cal.set(it.year, it.monthValue - 1, it.dayOfMonth)
            }
            DatePickerDialog(context, { _, year, month, day ->
                experimentStartDate = LocalDate.of(year, month + 1, day)
                startDateBtn.text = experimentStartDate.toString()
                // Trigger auto-calculate if enabled
                if (autoToyCb.isChecked) {
                    val type = toyTypes[toyTypeSpinner.selectedItemPosition]
                    val today = LocalDate.now()
                    val computed = when (type) {
                        "week" -> TimeOfYearCalculator.calculateRelativeWeek(experimentStartDate!!, today)
                        "month" -> TimeOfYearCalculator.calculateRelativeMonth(experimentStartDate!!, today)
                        else -> null
                    }
                    computed?.let { cv ->
                        val values = (toyValueSpinner.adapter as? ArrayAdapter<String>)?.let { adapter ->
                            (0 until adapter.count).map { adapter.getItem(it) ?: "" }
                        } ?: emptyList()
                        val idx = values.indexOf(cv.toString())
                        if (idx >= 0) toyValueSpinner.setSelection(idx)
                    }
                }
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }
        clearDateBtn.setOnClickListener {
            experimentStartDate = null
            startDateBtn.text = context.getString(R.string.toy_dialog_select_date)
        }

        // Event type spinner
        eventTypeSpinner.adapter = ArrayAdapter(
            context, android.R.layout.simple_spinner_dropdown_item,
            eventTypes.map { eventTypeLabels[it] ?: it }
        )
        val currentEventTypeIndex = eventTypes.indexOf(currentConfig?.eventType ?: "none").coerceAtLeast(0)
        eventTypeSpinner.setSelection(currentEventTypeIndex)

        // Event value spinner
        fun updateEventValueSpinner(type: String, currentValue: Int? = null) {
            val values = when (type) {
                "season" -> (1..6).toList()
                "timepoint" -> (1..20).toList()
                else -> emptyList()
            }
            eventValueSpinner.adapter = ArrayAdapter(
                context, android.R.layout.simple_spinner_dropdown_item,
                values.map { it.toString() }
            )
            if (currentValue != null && values.contains(currentValue)) {
                eventValueSpinner.setSelection(values.indexOf(currentValue))
            } else if (values.isNotEmpty()) {
                eventValueSpinner.setSelection(0)
            }
        }
        updateEventValueSpinner(eventTypes[currentEventTypeIndex], currentConfig?.eventValue)
        eventTypeSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, v: View?, position: Int, id: Long) {
                updateEventValueSpinner(eventTypes[position])
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        // Traits selection RecyclerView
        val traitAdapter = TraitCheckboxAdapter { traitId, isChecked ->
            if (isChecked) selectedTraitIds.add(traitId)
            else selectedTraitIds.remove(traitId)
            updateTraitsSummary(traitsSummary)
        }
        traitsRv.adapter = traitAdapter
        traitsRv.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
        traitAdapter.submitList(traits.map { TraitCheckboxItem(it, selectedTraitIds.contains(it.id)) })

        updateTraitsSummary(traitsSummary)

        // Build dialog
        AlertDialog.Builder(context)
            .setTitle(R.string.toy_dialog_title)
            .setView(view)
            .setPositiveButton(R.string.toy_dialog_save) { _, _ ->
                val toyType = toyTypes[toyTypeSpinner.selectedItemPosition]
                val toyValue = if (toyType != "none" && !autoToyCb.isChecked) {
                    toyValueSpinner.selectedItem?.toString()?.toIntOrNull()
                } else if (toyType != "none" && autoToyCb.isChecked) {
                    // Use the computed value that's shown in the spinner
                    toyValueSpinner.selectedItem?.toString()?.toIntOrNull()
                } else null

                val eventType = eventTypes[eventTypeSpinner.selectedItemPosition]
                val eventValue = if (eventType != "none") {
                    eventValueSpinner.selectedItem?.toString()?.toIntOrNull()
                } else null

                val config = TimeOfYearEventConfig(
                    experimentStartDate = experimentStartDate,
                    timeOfYearType = if (toyType == "none") null else toyType,
                    timeOfYearValue = toyValue,
                    autoTimeOfYear = autoToyCb.isChecked,
                    eventType = if (eventType == "none") null else eventType,
                    eventValue = eventValue,
                    selectedTraitIds = selectedTraitIds
                )
                onConfigSaved(config)
            }
            .setNegativeButton(R.string.toy_dialog_skip) { _, _ ->
                onSkip()
            }
            .setCancelable(false)
            .show()
    }

    private fun updateTraitsSummary(textView: TextView) {
        val total = traits.size
        val selected = selectedTraitIds.size
        textView.text = if (selected == total) {
            context.getString(R.string.toy_dialog_traits_all_selected)
        } else {
            "$selected / $total traits seleccionados"
        }
    }

    // Inner adapter for trait checkboxes
    private data class TraitCheckboxItem(val trait: TraitObject, val checked: Boolean)

    private class TraitCheckboxAdapter(
        private val onChecked: (String, Boolean) -> Unit
    ) : ListAdapter<TraitCheckboxItem, TraitCheckboxAdapter.ViewHolder>(DIFF) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val cb = android.widget.CheckBox(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setPadding(16, 8, 16, 8)
            }
            return ViewHolder(cb)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = getItem(position)
            val cb = holder.itemView as android.widget.CheckBox
            cb.text = item.trait.name
            cb.setOnCheckedChangeListener(null)  // prevent loop
            cb.isChecked = item.checked
            cb.setOnCheckedChangeListener { _, isChecked ->
                onChecked(item.trait.id, isChecked)
            }
        }

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

        companion object {
            val DIFF = object : DiffUtil.ItemCallback<TraitCheckboxItem>() {
                override fun areItemsTheSame(a: TraitCheckboxItem, b: TraitCheckboxItem) =
                    a.trait.id == b.trait.id
                override fun areContentsTheSame(a: TraitCheckboxItem, b: TraitCheckboxItem) = a == b
            }
        }
    }
}
