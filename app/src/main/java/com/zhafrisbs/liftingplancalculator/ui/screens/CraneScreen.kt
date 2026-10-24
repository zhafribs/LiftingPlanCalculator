package com.zhafrisbs.liftingplancalculator.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhafrisbs.liftingplancalculator.core.CONFIG_LABELS
import com.zhafrisbs.liftingplancalculator.core.CONFIGS
import com.zhafrisbs.liftingplancalculator.core.ChartColumn
import com.zhafrisbs.liftingplancalculator.core.ChartRow
import com.zhafrisbs.liftingplancalculator.core.ExcelImport
import com.zhafrisbs.liftingplancalculator.core.Figures
import com.zhafrisbs.liftingplancalculator.core.LiftTotals
import com.zhafrisbs.liftingplancalculator.core.LoadChartData
import com.zhafrisbs.liftingplancalculator.core.UsageState
import com.zhafrisbs.liftingplancalculator.ui.LiftViewModel
import com.zhafrisbs.liftingplancalculator.ui.components.DropdownField
import com.zhafrisbs.liftingplancalculator.ui.components.LabeledTextField
import com.zhafrisbs.liftingplancalculator.ui.components.NumberField
import com.zhafrisbs.liftingplancalculator.ui.components.ResultCard
import com.zhafrisbs.liftingplancalculator.ui.components.ResultRowData
import com.zhafrisbs.liftingplancalculator.ui.components.ResultTone
import com.zhafrisbs.liftingplancalculator.ui.components.SectionCard
import com.zhafrisbs.liftingplancalculator.ui.theme.BorderStrong
import com.zhafrisbs.liftingplancalculator.ui.theme.Danger
import com.zhafrisbs.liftingplancalculator.ui.theme.PanelSurface
import com.zhafrisbs.liftingplancalculator.ui.theme.StateCaution
import com.zhafrisbs.liftingplancalculator.ui.theme.StateOver
import com.zhafrisbs.liftingplancalculator.ui.theme.StateWithin
import com.zhafrisbs.liftingplancalculator.ui.theme.TextMuted
import com.zhafrisbs.liftingplancalculator.ui.theme.TextPrimary
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/**
 * The Crane tab: the crane, its load chart, and the capacity check.
 *
 * The gross weight is **not** entered here — it comes from the Overall Weight tab,
 * which owns the lift.  A lift described in two places is a lift that can be
 * described two different ways, and the one the operator trusts is whichever they
 * read last.
 *
 * **A workbook and a hand-typed line are two charts, and one of them is in
 * charge.**  That is the desktop's own design, and it is why importing a chart
 * *mirrors* the selected point into the manual entry rather than turning the chart
 * into nineteen rows.  The reference shipped that mistake once, under a heading
 * that says *Manual Entry* and above an input that is a single chart point.
 */
@Composable
fun CraneScreen(
    viewModel: LiftViewModel,
    modifier: Modifier = Modifier,
    /**
     * Opens this tab's report, or null for a caller that does not want the button.
     *
     * Drawn under a null check rather than defaulted to a no-op, because a caller
     * that passes nothing should get the form *without* the control rather than a
     * button that silently does nothing.
     *
     * The crane's report is the crane alone, with no "Include" question: this tab's
     * one section *is* the crane, and a checkbox that adds a section the report
     * already carries is a control that cannot do anything.
     */
    onShowReport: (() -> Unit)? = null,
) {
    val inputs = viewModel.craneInputs
    val totals = LiftTotals.of(viewModel.loadItems, viewModel.tackleItems)
    val context = LocalContext.current
    var importError by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val bytes = readBytes(context, uri)
            when {
                bytes == null ->
                    importError = "That file could not be opened."

                else -> runCatching { ExcelImport.readChart(bytes) }
                    .onSuccess { chart ->
                        importError = null
                        val name = displayName(context, uri) ?: "workbook.xlsx"
                        viewModel.importCraneChart(
                            chart = chart,
                            name = name,
                            gross = totals.gross,
                        )
                    }
                    .onFailure { failure ->
                        importError = failure.message ?: "That workbook could not be read."
                    }
            }
        }
    }

    // **The answer is the ViewModel's, solved there rather than here.**
    //
    // It used to be solved in this composable on every recomposition, and that *was*
    // the reference's model -- every keystroke re-solves, and it is cheap arithmetic
    // rather than I/O.  It moved because the two tabs are one lift: the Graph tab's
    // angle feeds straight back into this check, so a solve here and a solve there
    // would be two answers to one question, agreeing only until one of them was
    // refreshed.  The ViewModel re-solves on every write to the crane, the load or the
    // tackle, so nothing on this screen can read an answer older than its inputs.
    //
    // **A failed solve is shown, and shown the desktop's way.**  When the engine refuses
    // the inputs -- a chart it cannot read, a negative lift weight -- this screen puts a
    // "Could not check" badge carrying the engine's own message where the check's rows
    // go, which is what the reference's `render` does with `section.error`.  The card of
    // em dashes it used to leave behind was indistinguishable from a chart that holds
    // nothing, and the one thing a failure must not be is silent.  The message is the
    // ViewModel's (`craneError`) for the same reason the result is: a message composed
    // here would be a second opinion about a failure the engine already described.
    val result = viewModel.craneResult
    val craneError = viewModel.craneError

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = "Checked against the lift on the Overall Weight tab: " +
                "${Figures.fmt(totals.gross, 2, "kg")} at the hook.",
            color = TextMuted,
            style = MaterialTheme.typography.bodySmall,
        )

        SectionCard(title = "Crane") {
            LabeledTextField(
                label = "Crane",
                value = inputs.name,
                onValueChange = { viewModel.updateCraneInputs(inputs.copy(name = it)) },
                hint = "e.g. Kobelco CKE90G-2",
            )
            NumberField(
                label = "Crane rated capacity",
                value = inputs.capacityT,
                onValueChange = { viewModel.updateCraneInputs(inputs.copy(capacityT = it)) },
                unit = "ton",
                decimals = 1,
                max = 10000.0,
            )
        }

        SectionCard(
            title = "Load chart \u2014 Excel workbook",
            note = if (inputs.chartLabel.isNotEmpty()) inputs.chartLabel else null,
        ) {
            Button(
                onClick = { picker.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Open Excel Load Chart\u2026")
            }

            if (importError != null) {
                Text(
                    text = importError!!,
                    color = StateOver,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        // The manual entry: ONE line, with the four columns of the desktop's own
        // ManualChartRow.  There is no `+` and no delete, because a load chart
        // entry is one charted point rather than a table.
        SectionCard(
            title = "Load chart \u2014 manual entry",
            note = "Typing here switches the chart back to this line.",
        ) {
            val row = inputs.rows.firstOrNull() ?: ChartRow()
            // With a jib, the boom column is the **assembly** the capacity below is read
            // off -- the workbook's boom plus the jib the configuration selected -- and it
            // is shown rather than typed, because it is not a figure anybody enters.  See
            // `boomAssembly` for what it reads and why.
            ChartEntry(
                row = row,
                onRowChange = { viewModel.editCraneChartRow(it) },
                assembly = boomAssembly(row, inputs.jibActive, inputs.jibLength),
            )
        }

        // The workbook's own selection, directly under the entry it drives, and
        // only when there *is* a workbook: a chart typed by hand has no boom columns
        // to choose between and no charted radii to pick from, so these two controls
        // would be two controls that cannot do anything.  Typing in the entry then
        // sets the working radius, which is what that section's note says.
        //
        // Both lists are the workbook's own data.  A radius the chart does not chart
        // at the selected boom is not a radius the operator can pick, which is the
        // point of offering them as dropdowns rather than free entry.
        //
        // **Changing either one writes through to the entry.**  The entry is this
        // selection written down, so a pick that did not reach it would leave the two
        // load-chart views disagreeing about the same lift.
        //
        // **With a jib fitted, only the radius picker goes.**
        //
        // The radius is not the operator's to choose while a jib is in charge -- it
        // follows from the boom angle the jib sheet is read at -- so that picker has
        // nothing left to drive.
        //
        // The boom picker stays, and that is the desktop's own rule: its `sync` hides
        // `radiusWrap` on `jibActive` and hides `boomWrap` only when the chart has one
        // boom or fewer, so a boom is still selectable with a jib fitted.  Choosing one
        // does reset the configuration to the main boom -- `ExcelImport.applied` opens
        // on the main boom alone, because the jib tables have to be looked up again at
        // the new boom -- and the operator sees that as the jib card above resetting.
        // Hiding the picker instead, as this screen used to, made its possible outcomes
        // fewer than the desktop's, and the two are meant to agree.
        //
        // The card itself goes when neither picker has anything to draw: a hand-typed
        // chart, or a one-boom chart with a jib fitted.
        if (inputs.chartSource == "excel" &&
            (inputs.chartBooms.size > 1 ||
                (!inputs.jibActive && inputs.chartRadii.isNotEmpty()))
        ) {
            SectionCard(title = "Working Radius & Boom Length") {
                // The boom comes first because the radii below it are *that*
                // column's: choosing a boom re-asks the radius, and a figure that
                // moved in a field placed above the control you just touched reads
                // as a fault rather than as a consequence.
                if (inputs.chartBooms.size > 1) {
                    DropdownField(
                        label = "Boom length",
                        options = inputs.chartBooms,
                        selected = inputs.chartBoom ?: inputs.chartBooms.last(),
                        optionLabel = { metres(it) },
                        onSelected = { viewModel.selectCraneBoom(it, totals.gross) },
                    )
                }
                if (!inputs.jibActive && inputs.chartRadii.isNotEmpty()) {
                    DropdownField(
                        label = "Working radius",
                        options = inputs.chartRadii,
                        selected = nearest(inputs.chartRadii, inputs.workingRadius),
                        optionLabel = { metres(it) },
                        onSelected = { viewModel.selectCraneRadius(it) },
                    )
                }
            }
        }

        // **The workbook, listed.**  The reference's desktop shows the whole imported
        // chart in a table, and the phone did not -- a *named* narrowing, recorded in
        // the reference's own notes, which meant nothing on the phone showed the
        // points the figures were being read from.
        //
        // The desktop's table is Radius / Load / Boom length, which is the column
        // format's shape.  A matrix workbook has no such rows: its cells are a
        // capacity per (radius, boom), so the table here follows **the boom the app
        // is actually using** and the boom selector above re-renders it.  Listing all
        // 133 cells of the operator's own workbook would be a table nobody reads on a
        // phone, and would bury the seven points that matter.
        val chart = viewModel.craneChart
        if (inputs.chartSource == "excel" && chart != null) {
            val (headers, rows) = chartTable(chart, inputs.chartBoom)
            if (rows.isNotEmpty()) {
                SectionCard(
                    title = "Load Chart (Workbook Table)",
                    note = if (chart.matrix) {
                        "${inputs.chartName.ifEmpty { "The workbook" }} read at boom " +
                            "${Figures.fmt(inputs.chartBoom, 2, "m")} \u2014 the same boom " +
                            "the dropdown above is set to. Change the boom to see " +
                            "another column's capacities."
                    } else {
                        "${inputs.chartName.ifEmpty { "The workbook" }} as imported: " +
                            "${rows.size} charted point${
                                if (rows.size == 1) "" else "s"
                            }."
                    },
                ) {
                    ChartTable(headers = headers, rows = rows)
                }
            }
        }

        // **Where the crane stands on the workbook's own axis.**
        //
        // A new-format workbook prints the crane's position, and every figure the Graph
        // tab draws is measured from it: the axis line, the reach, and the angle the
        // diagram settles at.  The phone showed this nowhere, so the one figure every
        // other figure on the graph hangs off was the one figure the operator could not
        // check.  It is shown for the workbook that carries it and for no other -- a
        // hand-typed chart has no axis to be located on.
        //
        // The words are the desktop's, verbatim, and so is the format: the raw figures
        // as the workbook prints them, not the `-1.70` the rest of this screen pads to.
        inputs.chartPosition?.let { position ->
            Text(
                text = "Crane location on axis (X, Y): " +
                    "(${plainNumber(position.first)}, ${plainNumber(position.second)})" +
                    " \u2014 from the workbook",
                color = TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        // **The workbook's jib sheet, and the configuration that reads it.**
        //
        // Shown only for a workbook that carries jib tables: a chart with no jib sheet
        // has no jib to configure, and three radios over nothing would be three controls
        // that cannot do anything.
        //
        // The configuration *is* which table the capacity is read off.  The plain jib is
        // the workbook's shortest table and the extended jib its longest, so what is left
        // to choose is the table's own two figures: the boom angle the check is read at,
        // and which of the offsets that jib allows it is fitted at.  Both are listed from
        // the workbook's own rows rather than typed -- the way the working radius is
        // listed from the main chart's -- and both go through the ViewModel, which is
        // where the Graph tab's own configuration radios meet them.
        if (inputs.chartJibs.isNotEmpty()) {
            SectionCard(
                title = "Jib configuration",
                note = "From the workbook's jib sheet",
            ) {
                CONFIGS.forEach { config ->
                    JibConfigChoice(
                        label = CONFIG_LABELS[config] ?: config,
                        selected = inputs.jibConfig == config,
                    ) {
                        viewModel.chooseJib(config = config)
                    }
                }

                // The two lists below belong to a jib, not to the main boom: with the
                // main boom selected there is no jib chart to read an angle from and no
                // jib fitted to have an offset, so neither list would have anything in
                // it.  A dropdown over an empty list is a control that cannot do
                // anything, so it is not drawn.
                if (inputs.jibActive) {
                    Text(
                        text = "Jib ${Figures.fmt(inputs.jibLength ?: 0.0, 2, "m")}" +
                            " \u2014 set by the configuration",
                        color = TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )

                    // The angle, listed from the selected jib chart's own rows: it is the
                    // jib chart's *input*, the way the working radius is the main boom's.
                    // The one shown selected is the angle the check is already being read
                    // at, which the Graph tab may have moved off a charted row -- so the
                    // nearest charted angle is the one that matches it.  With nothing
                    // published yet, the first row is the one an empty select would show.
                    val angles = inputs.chartJibPoints
                        .map { it.first }
                        .sorted()
                    if (angles.isNotEmpty()) {
                        DropdownField(
                            label = "Boom angle",
                            options = angles,
                            selected = (inputs.boomAngleDeg ?: result?.boomAngleDeg)
                                ?.let { nearest(angles, it) }
                                ?: angles.first(),
                            optionLabel = { angleLabel(it) },
                            onSelected = { viewModel.setBoomAngle(it) },
                        )
                    }

                    // The offsets, filtered to the length the configuration picked: the
                    // workbook charts every offset it offers, and only some of them
                    // belong to the jib now fitted to this boom.
                    val offsets = inputs.chartJibs
                        .filter { abs(it.length - (inputs.jibLength ?: 0.0)) < JIB_SAME }
                        .map { it.offset }
                        .sorted()
                    if (offsets.isNotEmpty()) {
                        DropdownField(
                            label = "Jib offset angle",
                            options = offsets,
                            selected = inputs.jibOffset?.let { nearest(offsets, it) }
                                ?: offsets.first(),
                            optionLabel = { Figures.fmt(it, 0, "\u00b0") },
                            onSelected = { viewModel.chooseJib(offset = it) },
                        )
                    }
                }
            }
        }

        if (result == null) {
            CapacityCheckFailure(craneError)
        } else {
            ResultCard(
                title = "Capacity check",
                rows = listOfNotNull(
                    ResultRowData(
                        "Crane",
                        result.craneName.ifEmpty { null } ?: Figures.NOTHING,
                    ),
                    ResultRowData(
                        "Crane rated capacity",
                        Figures.fmt(result.craneCapacityKg.takeIf { it > 0 }, 2, "kg"),
                    ),
                    ResultRowData(
                        "Boom length",
                        Figures.fmt(result.boomM, 2, "m"),
                    ),
                    // The manual entry's own angle, carried straight through to here.
                    //
                    // It was already **computed** -- `Crane.solve` reads it off the row
                    // the moment a radius and a capacity are typed -- but the card did
                    // not show it, so the one field the operator had just filled in was
                    // the one they could not see the effect of.  A blank here is "not
                    // entered" rather than "0 degrees": a load chart does not chart an
                    // angle, and a workbook import therefore leaves this empty.
                    ResultRowData(
                        "Boom angle",
                        Figures.fmt(result.boomAngleDeg, 2, "\u00b0"),
                    ),
                    // **The jib, as the two figures that pick it.**
                    //
                    // A fitted jib is visible nowhere else on this card: the capacity below
                    // it comes from a different table, read against an angle rather than a
                    // radius, and whoever checks that figure has to know which jib it was
                    // checked with.  Shown only while one is fitted -- a "Jib" row on a
                    // main-boom check would name a jib that is not there.  The offset is the
                    // desktop's own whole-degree form, so 25 degrees reads `25` and not
                    // `25.0`.
                    if (inputs.jibActive) {
                        ResultRowData(
                            "Jib",
                            "${Figures.fmt(inputs.jibLength ?: 0.0, 2, "m")} at " +
                                "${Figures.fmt(inputs.jibOffset ?: 0.0, 0, "\u00b0")} offset",
                        )
                    } else {
                        null
                    },
                    // The radius the lookup actually happened at.
                    //
                    // It was **used** all along -- every capacity in this card is read
                    // at it -- but it had no row of its own, so the manual entry's radius
                    // was the one column of the four with nowhere to arrive.  The other
                    // three each have a row here, which is what made the card look
                    // complete while this one was missing.
                    //
                    // It is `radiusM` rather than `workingRadius`, and they differ: with
                    // no working radius typed the solve falls back to the entry's own
                    // charted radius, and this is the figure the capacity beside it was
                    // looked up at -- the one worth showing.
                    ResultRowData(
                        "Working radius",
                        Figures.fmt(result.radiusM.takeIf { it > 0 }, 2, "m"),
                    ),
                    // **The capacity's own name follows the table it came from.**  With a jib
                    // the figure is the jib chart's, looked up against a boom angle; calling
                    // that "capacity at radius" would name the main boom's lookup for a
                    // number the main boom never supplied.
                    ResultRowData(
                        if (inputs.jibActive) {
                            "Jib capacity at boom angle"
                        } else {
                            "Chart capacity at radius"
                        },
                        Figures.fmt(result.chartCapacityKg, 2, "kg"),
                    ),
                    // The figure the capacity above is measured *against*.
                    //
                    // It sat on this tab already, in the line at the very top, but that
                    // line is about where the number came from.  Here it is about the
                    // comparison: a capacity and a usage percentage with no stated load
                    // between them leave a reviewer to find the weight somewhere else and
                    // hold it in their head.  It is the load plus the tackle -- the gross
                    // at the hook, which is what the chart is read against -- and not the
                    // sling load, which is a different figure and lives on the Uniform
                    // tab.
                    ResultRowData(
                        "Gross load at hook",
                        Figures.fmt(totals.gross.takeIf { it > 0 }, 2, "kg"),
                    ),
                    ResultRowData(
                        "Capacity usage",
                        Figures.fmt(result.capacityUsagePercent, 2, "%"),
                        toneFor(result.capacityBand),
                    ),
                    ResultRowData(
                        "Chart source",
                        result.capacitySource.ifEmpty { null } ?: Figures.NOTHING,
                    ),
                ),
            )

            Text(
                text = result.capacityStatus,
                color = when (result.capacityBand) {
                    UsageState.WITHIN -> StateWithin
                    UsageState.CAUTION -> StateCaution
                    UsageState.OVER -> StateOver
                    null -> TextMuted
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        // **The capacity-against-radius picture.**
        //
        // The desktop draws it under the same card's rows, so it is drawn here as the
        // same panel those rows sit on.  The `ResultCard` above cannot hold it: a card of
        // label-and-value rows is all that widget is, and one that grew a picture slot
        // would be a widget with a hole in it.
        //
        // **Suppressed entirely while a jib is active**, and that is not a layout
        // preference.  The plotted curve is the *main boom* chart's -- capacity against
        // radius -- where a jib's capacity comes from the jib table read against a boom
        // angle.  The main boom's marker would then sit at a radius the jib check never
        // used, ringed and labelled with a capacity that came from somewhere else, which
        // is a picture that agrees with nothing on the card beside it.
        if (result != null && !inputs.jibActive) {
            val points = capacityPlot(inputs.chartPoints, inputs.chartPairs())
            if (points.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = PanelSurface,
                    border = BorderStroke(1.dp, BorderStrong),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    CapacityPlot(
                        points = points,
                        radiusM = result.radiusM,
                        capacityKg = result.chartCapacityKg,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }

        SectionCard(title = "Actions") {
            // Above Clear, because a long form buries a button at the bottom of the
            // form and this is the one an operator reaches for at the end.
            if (onShowReport != null) {
                Button(
                    onClick = onShowReport,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Show report")
                }
            }
            Button(
                onClick = viewModel::clearCrane,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Danger,
                    contentColor = TextPrimary,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Clear")
            }
            Text(
                text = "Clear puts the crane, the working radius and the load chart " +
                    "back to blank. The load itself is on the Overall Weight tab. " +
                    "Show report opens the crane on its own.",
                color = TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Box(Modifier.fillMaxWidth())
    }
}

/**
 * The manual entry's four columns, in the desktop's order.
 *
 * [assembly] is the boom column's content while a jib is fitted -- the workbook's boom
 * plus the jib, `31+7.2` -- and null when the column is an ordinary length the operator
 * enters.  It arrives as a string rather than as the two figures because the assembly is
 * text (`31+7.2` is not a number), and it is passed in rather than worked out here
 * because the assembly is a fact about the *configuration*: a [ChartEntry] that read the
 * jib for itself would be a second place the jib is read from.
 */
@Composable
private fun ChartEntry(row: ChartRow, onRowChange: (ChartRow) -> Unit, assembly: String?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.weight(1f)) {
            NumberField(
                label = ChartColumn.RADIUS.caption,
                value = row.radius,
                onValueChange = { onRowChange(row.copy(radius = it)) },
                unit = ChartColumn.RADIUS.unit,
                decimals = ChartColumn.RADIUS.decimals,
                max = ChartColumn.RADIUS.max,
            )
        }
        Box(Modifier.weight(1f)) {
            NumberField(
                label = ChartColumn.CAPACITY.caption,
                value = row.capacity,
                onValueChange = { onRowChange(row.copy(capacity = it)) },
                unit = ChartColumn.CAPACITY.unit,
                decimals = ChartColumn.CAPACITY.decimals,
                max = ChartColumn.CAPACITY.max,
            )
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.weight(1f)) {
            NumberField(
                label = ChartColumn.ANGLE.caption,
                value = row.angle,
                onValueChange = { onRowChange(row.copy(angle = it)) },
                unit = ChartColumn.ANGLE.unit,
                decimals = ChartColumn.ANGLE.decimals,
                max = ChartColumn.ANGLE.max,
            )
        }
        Box(Modifier.weight(1f)) {
            if (assembly != null) {
                AssemblyField(label = ChartColumn.BOOM.caption, value = assembly)
            } else {
                NumberField(
                    label = ChartColumn.BOOM.caption,
                    value = row.boom,
                    onValueChange = { onRowChange(row.copy(boom = it)) },
                    unit = ChartColumn.BOOM.unit,
                    decimals = ChartColumn.BOOM.decimals,
                    max = ChartColumn.BOOM.max,
                )
            }
        }
    }
}

/**
 * The workbook's own points, as a header row and one row per charted point.
 *
 * A **pure function rather than rows built inside the composable**, so the shape can
 * be asserted without a device.  Which column a cell lands in is exactly the kind of
 * thing that renders plausibly while being wrong -- a table showing radius under a
 * heading that says Boom looks entirely ordinary.
 *
 * The matrix case reads the same **boom column the solve reads**, so the table and
 * the figures above it cannot disagree about which column is in charge.  A matrix
 * workbook lists one column at a time rather than all of them: the operator's own
 * has 133 cells, and burying the seven that matter in a table nobody scrolls is not
 * the same as showing the chart.
 */
internal fun chartTable(
    chart: LoadChartData,
    boom: Double?,
): Pair<List<String>, List<List<String>>> {
    if (chart.matrix) {
        val index = chart.boomIndex(boom)
        val rows = chart.rows.mapNotNull { row ->
            val radius = row.radius ?: return@mapNotNull null
            val capacity = row.capacities.getOrNull(index) ?: return@mapNotNull null
            listOf(Figures.fmt(radius, 2, "m"), Figures.fmt(capacity, 2, "kg"))
        }
        return listOf("Radius", "Capacity") to rows
    }
    val rows = chart.rows.mapNotNull { row ->
        val radius = row.radius ?: return@mapNotNull null
        val load = row.load ?: return@mapNotNull null
        listOf(
            Figures.fmt(radius, 2, "m"),
            Figures.fmt(load, 2, "kg"),
            Figures.fmt(row.boom, 2, "m"),
        )
    }
    return listOf("Radius", "Load", "Boom") to rows
}

/**
 * The table itself: a header row and one row per point, in evenly divided columns.
 *
 * One `Column` with its own tight spacing rather than loose `Row`s, because
 * [SectionCard]'s content is spaced for cards -- nineteen table rows at a card's gap
 * is a table that reads as nineteen cards.
 */
@Composable
private fun ChartTable(headers: List<String>, rows: List<List<String>>) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            headers.forEach { header ->
                Text(
                    text = header,
                    color = TextMuted,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        rows.forEach { cells ->
            Row(modifier = Modifier.fillMaxWidth()) {
                cells.forEach { cell ->
                    Text(
                        text = cell,
                        color = TextPrimary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * The offered figure closest to the current one, so a dropdown starts on it.
 *
 * The desktop's own `nearest`, and it serves all three dropdowns this screen draws: the
 * main chart's radii, and the jib chart's angles and offsets.  The radius picker passes
 * the operator's working radius before an import has offered anything, so an empty list
 * returns the wanted figure unchanged rather than throwing.
 */
private fun nearest(offered: List<Double>, wanted: Double): Double {
    if (offered.isEmpty()) return wanted
    return offered.minByOrNull { abs(it - wanted) } ?: offered.first()
}

private fun metres(value: Double): String = String.format(Locale.US, "%.2f m", value)

private fun toneFor(band: UsageState?): ResultTone = when (band) {
    UsageState.WITHIN -> ResultTone.OK
    UsageState.CAUTION -> ResultTone.CAUTION
    UsageState.OVER -> ResultTone.OVER
    null -> ResultTone.NEUTRAL
}

/**
 * The capacity check when the solve failed, as the desktop draws it.
 *
 * The reference's `render` replaces the whole card body with one badge --
 * `statusBadge("over", "Could not check", section.error)` -- and clears the meter, the
 * rows and the plot beside it.  That is why this is not a [ResultCard] with a failure
 * row in it: the badge is not a label-and-value pair, and writing the message into a
 * value column would put the state in the muted column and the explanation in bold,
 * which is the reverse of what the failure is saying.
 *
 * The frame around the badge repeats [ResultCard]'s because a card whose body is a
 * single badge has no rows to hand that widget.  The wording is the desktop's: the state
 * is its `"Could not check"`, and [message] is the engine's own, shown only when there
 * is one -- the reference draws its `sub` line only for a truthy `section.error`, so an
 * empty message shows the state alone rather than an empty line under it.
 */
@Composable
private fun CapacityCheckFailure(message: String?) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Capacity Check",
            color = TextPrimary,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = PanelSurface,
            border = BorderStroke(1.dp, BorderStrong),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Could not check",
                    color = StateOver,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (!message.isNullOrEmpty()) {
                    Text(
                        text = message,
                        color = TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

/**
 * The workbook's display name.
 *
 * The document picker hands back a `content://` URI, and the commonest provider's
 * last path segment is a MediaStore **row id** — `msf%3A353921` — not a filename.
 * Reading the name off the URI would therefore label a perfectly good workbook
 * "msf%3A353921", so the provider is asked for it.
 */
private fun displayName(context: Context, uri: Uri): String? = runCatching {
    val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
    context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) cursor.getString(index) else null
        } else {
            null
        }
    }
}.getOrNull()

private fun readBytes(context: Context, uri: Uri): ByteArray? = runCatching {
    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
}.getOrNull()

// ---------------------------------------------------------------------------
// The assembly, the jib configuration and the capacity plot
// ---------------------------------------------------------------------------

/**
 * Two figures this close are the same figure.
 *
 * The loader's own `1e-9`, and here it asks which of the workbook's jib tables belongs
 * to the length the configuration selected: a length read out of a cell is not reliably
 * the same double as the one written in the sheet beside it.
 */
private const val JIB_SAME = 1e-9

/**
 * The boom column once a jib is fitted: the assembly, shown rather than typed.
 *
 * With a jib the boom length is not the operator's to enter.  It is the workbook's own
 * boom plus the jib the configuration selected, and the desktop prints the two joined by
 * a `+` -- `31+7.2` -- because that is the crane the capacity in the check below was read
 * off.  A number typed into this column would describe a *different* crane, in the one
 * field whoever checks the plan would trust.
 *
 * `readOnly` rather than a plain box: the assembly is text, so it cannot be a
 * `NumberField`, and it has to look like the field it stands in for -- same box, same
 * label, same place in the row.
 */
@Composable
private fun AssemblyField(label: String, value: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = {},
        readOnly = true,
        singleLine = true,
        label = { Text(label) },
        // The entry's own colours, repeated because the widgets' shared ones are private
        // to their file.  Focused and unfocused are deliberately the same: this box is
        // not a control, so a focus ring on it would offer something that is not there.
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = BorderStrong,
            unfocusedBorderColor = BorderStrong,
            focusedLabelColor = TextMuted,
            unfocusedLabelColor = TextMuted,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * One radio of the jib configuration, with the desktop's own caption beside it.
 *
 * A `Row` of its own because the radio and its label have to sit on one line and there
 * are three of them: three hand-written rows would be three places for the spacing to
 * drift apart, which is the fault the shared widgets exist to prevent.
 */
@Composable
private fun JibConfigChoice(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(
            text = label,
            color = TextPrimary,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

// -- the plot's own colours and its scale ------------------------------------
//
// Verbatim from the SVG the desktop builds in `crane.js`'s `plot`, and kept here rather
// than in `theme/Color.kt` because none of them is used anywhere else: they are the
// chart's colours, not the app's palette.

/** The grid lines, and the axis labels beside them. */
private val PlotGrid = Color(0xFF243040)
private val PlotLabel = Color(0xFF64748D)

/** The charted points and the line joining them. */
private val PlotLine = Color(0xFF5CB3FF)

/** The working radius: the dashed line down to the axis and the ring on the point. */
private val PlotMarker = Color(0xFFE2A558)

/**
 * The desktop's viewBox, and its four margins.
 *
 * The plot is drawn in the SVG's own 480 x 220 units and scaled to whatever width the
 * card gives it, which is what the desktop's `width="100%"` does: one uniform scaling,
 * so the chart keeps its proportions on any phone rather than being stretched sideways.
 */
private const val PLOT_WIDTH = 480f
private const val PLOT_HEIGHT = 220f
private const val PLOT_MARGIN_LEFT = 46f
private const val PLOT_MARGIN_RIGHT = 16f
private const val PLOT_MARGIN_TOP = 14f
private const val PLOT_MARGIN_BOTTOM = 30f

/**
 * The type, held at a size a phone can read.
 *
 * The desktop sets these in viewBox units -- 10 of them, which would scale down to
 * roughly 6.5 sp on a phone's card and be a tick nobody could read.  The desktop's own
 * figures are kept as `sp` instead of shrinking with the geometry; only the geometry
 * scales.  The marker's label is half a point larger there, and is here too.
 */
private val PlotLabelSize = 10.sp
private val PlotMarkerLabelSize = 10.5.sp

/**
 * The capacity-against-radius picture, ported from `plot` in the desktop's `crane.js`.
 *
 * **Presentation only.**  Every figure on it -- the charted points, the marker's radius
 * and the capacity read there -- was solved before this was called; nothing here re-solves
 * or re-reads a chart.  The desktop says the same of its own version, and it is what keeps
 * the picture and the rows above it from being two answers to one question.
 *
 * [radiusM] and [capacityKg] are the *solved* working radius and the capacity found at
 * it, so the marker sits on the curve's own point for the lift rather than on whatever
 * the entry says.  It is left out when there is no capacity to ring, because a ring at
 * zero is a claim the chart never made.
 */
@Composable
private fun CapacityPlot(
    points: List<Pair<Double, Double>>,
    radiusM: Double,
    capacityKg: Double?,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val markerCapacity = capacityKg?.takeIf { it != 0.0 }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(PLOT_WIDTH / PLOT_HEIGHT),
    ) {
        val scale = size.width / PLOT_WIDTH
        val left = PLOT_MARGIN_LEFT * scale
        val right = PLOT_MARGIN_RIGHT * scale
        val top = PLOT_MARGIN_TOP * scale
        val bottom = PLOT_MARGIN_BOTTOM * scale
        val plotRight = size.width - right
        val plotBottom = size.height - bottom

        // The scales run to the next round maximum *including* the marker, so the working
        // radius can never sit on or past the right edge of its own chart.
        val marker = if (radiusM > 0 && markerCapacity != null) {
            radiusM to markerCapacity
        } else {
            null
        }
        // `maxOfOrNull` rather than `maxOf`: an empty [points] list would throw, and a
        // chart with nothing on it is worth drawing as an empty grid rather than crashing
        // a tab.  `niceMax` turns the resulting zero into a scale of one.
        val xMax = niceMax(maxOf(points.maxOfOrNull { it.first } ?: 0.0, marker?.first ?: 0.0))
        val yMax = niceMax(maxOf(points.maxOfOrNull { it.second } ?: 0.0, marker?.second ?: 0.0))

        fun x(radius: Double): Float = left + (radius / xMax).toFloat() * (plotRight - left)
        fun y(capacity: Double): Float =
            plotBottom - (capacity / yMax).toFloat() * (plotBottom - top)

        // The grid: four divisions each way, labelled with the figure at the line.  The
        // capacity labels are in **tonnes**, which is the unit the chart is printed in and
        // the unit the axis title names -- a grid labelled in kilograms would be five
        // digits at every line.
        for (i in 1..4) {
            val gx = left + (i / 4f) * (plotRight - left)
            val gy = plotBottom - (i / 4f) * (plotBottom - top)
            drawLine(PlotGrid, Offset(gx, top), Offset(gx, plotBottom), strokeWidth = scale)
            drawLine(PlotGrid, Offset(left, gy), Offset(plotRight, gy), strokeWidth = scale)
            drawPlotLabel(
                measurer = measurer,
                text = whole(i / 4.0 * xMax),
                x = gx,
                baselineY = plotBottom + 14f * scale,
                anchor = 0.5f,
                colour = PlotLabel,
                size = PlotLabelSize,
            )
            drawPlotLabel(
                measurer = measurer,
                text = whole(i / 4.0 * yMax / 1000.0),
                x = left - 8f * scale,
                baselineY = gy + 3f * scale,
                anchor = 1f,
                colour = PlotLabel,
                size = PlotLabelSize,
            )
        }

        // The charted points, joined in radius order: the capacity curve.
        val curve = Path()
        points.forEachIndexed { index, point ->
            val px = x(point.first)
            val py = y(point.second)
            if (index == 0) curve.moveTo(px, py) else curve.lineTo(px, py)
        }
        drawPath(curve, PlotLine, style = Stroke(width = 2f * scale))
        points.forEach { point ->
            drawCircle(
                PlotLine,
                radius = 2.6f * scale,
                center = Offset(x(point.first), y(point.second)),
            )
        }

        marker?.let { working ->
            val mx = x(working.first)
            val my = y(working.second)
            drawLine(
                PlotMarker,
                Offset(mx, top),
                Offset(mx, plotBottom),
                strokeWidth = scale,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f * scale, 3f * scale)),
            )
            drawCircle(
                PlotMarker,
                radius = 5f * scale,
                center = Offset(mx, my),
                style = Stroke(width = 2f * scale),
            )
            drawPlotLabel(
                measurer = measurer,
                text = "${Figures.fmt(working.second, 0, "kg")} at " +
                    Figures.fmt(working.first, 2, "m"),
                x = mx + 8f * scale,
                baselineY = my - 8f * scale,
                anchor = 0f,
                colour = PlotMarker,
                size = PlotMarkerLabelSize,
            )
        }

        drawPlotLabel(
            measurer = measurer,
            text = "radius (m)",
            x = plotRight,
            baselineY = size.height - 6f * scale,
            anchor = 1f,
            colour = PlotLabel,
            size = PlotLabelSize,
        )
        drawPlotLabel(
            measurer = measurer,
            text = "rated load (t)",
            x = 6f * scale,
            baselineY = top,
            anchor = 0f,
            colour = PlotLabel,
            size = PlotLabelSize,
        )
    }
}

/**
 * A label positioned by its baseline and its `text-anchor`, exactly as the SVG does.
 *
 * [anchor] is the desktop's own `text-anchor`: 0 is `start`, 0.5 `middle`, 1 `end`.  The
 * desktop positions text by its baseline and Compose by its box, so the baseline is
 * recovered from the layout rather than guessed at -- a tick label a few pixels out is a
 * label that reads as belonging to the line next to the one it measures.
 */
private fun DrawScope.drawPlotLabel(
    measurer: TextMeasurer,
    text: String,
    x: Float,
    baselineY: Float,
    anchor: Float,
    colour: Color,
    size: TextUnit,
) {
    val layout = measurer.measure(text, TextStyle(color = colour, fontSize = size))
    drawText(
        layout,
        topLeft = Offset(
            x - anchor * layout.size.width.toFloat(),
            baselineY - layout.getLineBaseline(0),
        ),
    )
}

/**
 * The points the capacity plot draws, in radius order, or empty for nothing to draw.
 *
 * **A pure function rather than points built inside the composable**, for the reason
 * `chartTable` is one: which points are plotted, and which of them are the same point
 * twice, is exactly the kind of thing that draws plausibly while being wrong.
 *
 * The desktop's own two sources, in its own order.  The workbook's chart at the selected
 * boom comes first, because it is the chart the capacities were read from, and the manual
 * entry's point is added only where the chart does not already chart that radius -- a
 * radius charted twice would be a dot drawn twice, and two capacities at one radius would
 * make the curve double back on itself.
 *
 * Empty for fewer than two points: a picture of one point is a dot on an empty grid, and
 * the desktop draws nothing at all in that case.  It is also what a hand-typed chart looks
 * like, which is the answer rather than a gap -- one charted point is not a curve.
 */
internal fun capacityPlot(
    chartPoints: List<Pair<Double, Double>>,
    entryPoints: List<Pair<Double, Double>>,
): List<Pair<Double, Double>> {
    val seen = mutableSetOf<Double>()
    val points = mutableListOf<Pair<Double, Double>>()
    for (point in chartPoints + entryPoints) {
        if (point.first > 0 && point.second > 0 && seen.add(point.first)) points.add(point)
    }
    points.sortBy { it.first }
    return if (points.size < 2) emptyList() else points
}

/**
 * The next axis maximum above [value]: 2, 5 or 10 times a power of ten.
 *
 * The desktop's `niceMax`, and it is what keeps the four grid divisions on whole figures
 * -- a grid labelled 0.37, 0.75, 1.12 is a grid no capacity can be read off.  A
 * non-positive maximum returns 1 rather than 0, so the scale can never divide by zero.
 */
internal fun niceMax(value: Double): Double {
    if (value <= 0.0) return 1.0
    val power = 10.0.pow(floor(log10(value)))
    val scaled = value / power
    val step = if (scaled <= 2.0) 2.0 else if (scaled <= 5.0) 5.0 else 10.0
    return step * power
}

/** A grid label's figure, as the desktop's own `toFixed(0)` prints it. */
private fun whole(value: Double): String = String.format(Locale.US, "%.0f", value)

/**
 * A jib chart's angle: whole degrees where it is whole, one decimal where it is not.
 *
 * The desktop labels an integer angle `25` and a fractional one `25.5`, because a chart's
 * own angles are round figures and an angle the graph has published is not.
 */
private fun angleLabel(value: Double): String =
    Figures.fmt(value, if (value == floor(value)) 0 else 1, "\u00b0")

/**
 * A figure as the desktop's own template string prints it.
 *
 * `crane.js` writes the assembly and the crane's position with `${value}`, which is
 * JavaScript's `String(number)`: the shortest form that round-trips, so 31 reads `31` and
 * 7.2 reads `7.2` -- not the `31.00` this screen pads every other figure to.  A boom
 * column reading `31.00+7.20` is an assembly the desktop does not have, and the position
 * line is read back against the workbook's own axis, where `(-1.70, 2.80)` and
 * `(-1.7, 2.8)` are one point written two ways.
 *
 * Java's `Double.toString` prints a whole number as `31.0` where JavaScript prints `31`,
 * so that one suffix is what gets removed.
 */
private fun plainNumber(value: Double): String = value.toString().removeSuffix(".0")

/**
 * The boom column's content while a jib is fitted, or null for an ordinary length.
 *
 * The workbook's own boom and the jib the configuration selected, **joined** as the
 * desktop joins them -- `31+7.2` rather than 38.2.  The two are not added because an
 * assembly is not a longer boom: the jib hangs off the boom's tip at an offset, it does
 * not extend it, and a single number in that column would describe a crane with no jib
 * on it while the capacity beside it came from one.
 *
 * Null in the two cases the column is an ordinary length: with the main boom selected
 * there is no jib to show, and a jib length of zero is no jib fitted at all.
 *
 * A pure function rather than an expression in the screen body, so "is this an assembly,
 * and what does it read" has one answer rather than one per caller.
 */
private fun boomAssembly(row: ChartRow, jibActive: Boolean, jibLength: Double?): String? {
    if (!jibActive) return null
    val jib = jibLength?.takeIf { it != 0.0 } ?: return null
    return "${plainNumber(row.boom)}+${plainNumber(jib)}"
}
