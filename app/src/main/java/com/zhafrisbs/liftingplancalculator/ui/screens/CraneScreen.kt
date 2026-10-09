package com.zhafrisbs.liftingplancalculator.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zhafrisbs.liftingplancalculator.core.ChartColumn
import com.zhafrisbs.liftingplancalculator.core.ChartRow
import com.zhafrisbs.liftingplancalculator.core.Crane
import com.zhafrisbs.liftingplancalculator.core.CraneInputs
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
import com.zhafrisbs.liftingplancalculator.ui.theme.Danger
import com.zhafrisbs.liftingplancalculator.ui.theme.StateCaution
import com.zhafrisbs.liftingplancalculator.ui.theme.StateOver
import com.zhafrisbs.liftingplancalculator.ui.theme.StateWithin
import com.zhafrisbs.liftingplancalculator.ui.theme.TextMuted
import com.zhafrisbs.liftingplancalculator.ui.theme.TextPrimary
import java.util.Locale

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
    // The imported workbook's own display name, kept here rather than read back off
    // the chart: the picker's name is not part of the parsed table, and the table's
    // heading has to name the file the operator actually chose.
    var chartName by remember { mutableStateOf("") }

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
                        chartName = name
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

    // Solving on every recomposition is the reference's model too -- every
    // keystroke re-solves -- and it is cheap: this is arithmetic, not I/O.  A
    // failure is captured rather than thrown, because a half-typed number is a
    // normal state of a form, not a fault.
    var solveError by remember(inputs, totals.gross) { mutableStateOf<String?>(null) }
    val result = runCatching { Crane.solve(inputs, totals.gross) }
        .onFailure { solveError = it.message ?: "The crane calculation could not be completed." }
        .onSuccess { solveError = null }
        .getOrNull()

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
                label = "Crane Rated Capacity",
                value = inputs.capacityT,
                onValueChange = { viewModel.updateCraneInputs(inputs.copy(capacityT = it)) },
                unit = "ton",
                decimals = 1,
                max = 10000.0,
            )
        }

        SectionCard(
            title = "Load Chart (Excel)",
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
            title = "Load Chart (Manual Entry)",
            note = "Typing here switches the chart back to this line.",
        ) {
            val row = inputs.rows.firstOrNull() ?: ChartRow()
            ChartEntry(
                row = row,
                onRowChange = { viewModel.editCraneChartRow(it) },
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
        if (inputs.chartSource == "excel" &&
            (inputs.chartBooms.size > 1 || inputs.chartRadii.isNotEmpty())
        ) {
            SectionCard(title = "Working Radius & Boom Length") {
                // The boom comes first because the radii below it are *that*
                // column's: choosing a boom re-asks the radius, and a figure that
                // moved in a field placed above the control you just touched reads
                // as a fault rather than as a consequence.
                if (inputs.chartBooms.size > 1) {
                    DropdownField(
                        label = "Boom Length",
                        options = inputs.chartBooms,
                        selected = inputs.chartBoom ?: inputs.chartBooms.last(),
                        optionLabel = { metres(it) },
                        onSelected = { viewModel.selectCraneBoom(it, totals.gross) },
                    )
                }
                if (inputs.chartRadii.isNotEmpty()) {
                    DropdownField(
                        label = "Working Radius",
                        options = inputs.chartRadii,
                        selected = nearestRadius(inputs.chartRadii, inputs.workingRadius),
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
                        "${chartName.ifEmpty { "The workbook" }} read at boom " +
                            "${Figures.fmt(inputs.chartBoom, 2, "m")} \u2014 the same boom " +
                            "the dropdown above is set to. Change the boom to see " +
                            "another column's capacities."
                    } else {
                        "${chartName.ifEmpty { "The workbook" }} as imported: " +
                            "${rows.size} charted point${
                                if (rows.size == 1) "" else "s"
                            }."
                    },
                ) {
                    ChartTable(headers = headers, rows = rows)
                }
            }
        }

        ResultCard(
            title = "Capacity Check",
            rows = listOf(
                ResultRowData(
                    "Crane",
                    result?.craneName?.ifEmpty { null } ?: Figures.NOTHING,
                ),
                ResultRowData(
                    "Crane rated capacity",
                    Figures.fmt(result?.craneCapacityKg?.takeIf { it > 0 }, 2, "kg"),
                ),
                ResultRowData(
                    "Boom length",
                    Figures.fmt(result?.boomM, 2, "m"),
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
                    Figures.fmt(result?.boomAngleDeg, 2, "\u00b0"),
                ),
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
                    Figures.fmt(result?.radiusM?.takeIf { it > 0 }, 2, "m"),
                ),
                ResultRowData(
                    "Chart capacity at radius",
                    Figures.fmt(result?.chartCapacityKg, 2, "kg"),
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
                    "Overall weight",
                    Figures.fmt(totals.gross.takeIf { it > 0 }, 2, "kg"),
                ),
                ResultRowData(
                    "Capacity usage",
                    Figures.fmt(result?.capacityUsagePercent, 2, "%"),
                    toneFor(result?.capacityBand),
                ),
                ResultRowData(
                    "Chart source",
                    result?.capacitySource?.ifEmpty { null } ?: Figures.NOTHING,
                ),
            ),
        )

        if (solveError != null) {
            Text(
                text = solveError!!,
                color = StateOver,
                style = MaterialTheme.typography.bodySmall,
            )
        } else if (result != null) {
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

/** The manual entry's four columns, in the desktop's order. */
@Composable
private fun ChartEntry(row: ChartRow, onRowChange: (ChartRow) -> Unit) {
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

/** The offered radius closest to the current one, so the dropdown starts on it. */
private fun nearestRadius(radii: List<Double>, current: Double): Double {
    if (radii.isEmpty()) return current
    return radii.minByOrNull { kotlin.math.abs(it - current) } ?: radii.first()
}

private fun metres(value: Double): String = String.format(Locale.US, "%.2f m", value)

private fun toneFor(band: UsageState?): ResultTone = when (band) {
    UsageState.WITHIN -> ResultTone.OK
    UsageState.CAUTION -> ResultTone.CAUTION
    UsageState.OVER -> ResultTone.OVER
    null -> ResultTone.NEUTRAL
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
