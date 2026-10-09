package com.zhafrisbs.liftingplancalculator.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zhafrisbs.liftingplancalculator.core.Figures
import com.zhafrisbs.liftingplancalculator.core.LiftTotals
import com.zhafrisbs.liftingplancalculator.core.LoadItem
import com.zhafrisbs.liftingplancalculator.core.LoadType
import com.zhafrisbs.liftingplancalculator.core.TackleItem
import com.zhafrisbs.liftingplancalculator.ui.LiftViewModel
import com.zhafrisbs.liftingplancalculator.ui.components.AddLineButton
import com.zhafrisbs.liftingplancalculator.ui.components.DropdownField
import com.zhafrisbs.liftingplancalculator.ui.components.LabeledTextField
import com.zhafrisbs.liftingplancalculator.ui.components.NumberField
import com.zhafrisbs.liftingplancalculator.ui.components.RemoveLineButton
import com.zhafrisbs.liftingplancalculator.ui.components.ResultCard
import com.zhafrisbs.liftingplancalculator.ui.components.ResultRowData
import com.zhafrisbs.liftingplancalculator.ui.components.ResultTone
import com.zhafrisbs.liftingplancalculator.ui.components.SectionCard
import com.zhafrisbs.liftingplancalculator.ui.theme.Danger
import com.zhafrisbs.liftingplancalculator.ui.theme.SectionBody
import com.zhafrisbs.liftingplancalculator.ui.theme.TextMuted
import com.zhafrisbs.liftingplancalculator.ui.theme.TextPrimary
import kotlin.math.roundToInt

/**
 * The Overall Weight tab: the load items and the tackle.
 *
 * This is the reference's `TotalLoadTab`, and it owns the number every other tab
 * is checked against — on the reference the sling tabs do not collect a load at
 * all, so this tab has to land before any of them can.
 *
 * Three things here are unlike the other tabs, and all three are the reference's
 * design rather than this port's invention:
 *
 *  * **The rows are dynamic.**  A line is measured as a fixed weight, by the
 *    metre or by volume, and which one decides which inputs it shows.  The
 *    measurement is held whether or not it is in use, so switching type back and
 *    forth does not lose what was typed.
 *  * **The rows are repeatable.**  A lift is a list of items, not a fixed set of
 *    fields, so this tab adds and removes lines.
 *  * **The measurement names come from the calculation**, never from a list here
 *    — [LoadType] carries the reference's own wording.
 */
@Composable
fun OverallWeightScreen(viewModel: LiftViewModel, modifier: Modifier = Modifier) {
    val totals = LiftTotals.of(viewModel.loadItems, viewModel.tackleItems)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // No jargon in this line.  It used to read "the weight every sling tab is
        // checked against.  Sling tabs do not ask for it again", which told the
        // operator about the app's own plumbing -- what the *other* tabs do and do
        // not ask for -- instead of what this screen is for.
        Text(
            text = "Add the load you are lifting and the rigging that carries it. " +
                "The overall weight below is what the other tabs check against.",
            color = TextMuted,
            style = MaterialTheme.typography.bodySmall,
        )

        SectionCard(
            title = "Load Items",
            action = {
                AddLineButton(
                    onClick = viewModel::addLoadItem,
                    contentDescription = "Add load item",
                )
            },
            note = if (viewModel.loadItems.isEmpty()) {
                "No load items yet. Tap + to weigh something."
            } else {
                null
            },
        ) {
            viewModel.loadItems.forEach { item ->
                // `key` ties each row's composition -- and so each text box's own
                // remembered text -- to the line it belongs to.  Without it,
                // deleting the first of two lines hands the second line's boxes
                // the first line's remembered state.
                key(item.id) {
                    LoadRow(
                        item = item,
                        onItemChange = viewModel::updateLoadItem,
                        onRemove = { viewModel.removeLoadItem(item.id) },
                    )
                }
            }
        }

        SectionCard(
            title = "Lifting Tackle",
            action = {
                AddLineButton(
                    onClick = viewModel::addTackleItem,
                    contentDescription = "Add lifting tackle line",
                )
            },
            note = if (viewModel.tackleItems.isEmpty()) {
                "No lifting tackle yet. Tap + to add shackles, strops or a block."
            } else {
                null
            },
        ) {
            viewModel.tackleItems.forEach { item ->
                key(item.id) {
                    TackleRow(
                        item = item,
                        onItemChange = viewModel::updateTackleItem,
                        onRemove = { viewModel.removeTackleItem(item.id) },
                    )
                }
            }
        }

        ResultCard(
            title = "Load and tackle",
            rows = listOf(
                ResultRowData("Total load weight", Figures.fmt(totals.load, 2, "kg")),
                ResultRowData("Total lifting tackle weight", Figures.fmt(totals.tackle, 2, "kg")),
                ResultRowData("Overall weight (load + tackle)", Figures.fmt(totals.gross, 2, "kg")),
                ResultRowData(
                    label = "Minimum crane rating (75%)",
                    value = Figures.fmt(totals.requiredChartCapacity, 2, "kg"),
                    tone = if (totals.overLimit) ResultTone.OVER else ResultTone.OK,
                ),
            ),
        )

        SectionCard(title = "Actions") {
            Button(
                onClick = viewModel::clear,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Danger,
                    contentColor = TextPrimary,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Clear")
            }
            Text(
                text = "Removes every load item and every lifting tackle line. " +
                    "Tap + to add lines back.",
                color = TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/**
 * One load item: what it is, how it is measured, and how many.
 *
 * The measurement inputs are rebuilt when the type changes rather than all being
 * shown with the irrelevant ones greyed out.  Five boxes with three of them
 * disabled asks the operator to work out which two matter; showing the two does
 * not.
 */
@Composable
private fun LoadRow(
    item: LoadItem,
    onItemChange: (LoadItem) -> Unit,
    onRemove: () -> Unit,
) {
    RowCard(
        answerLabel = "Line weight",
        answerValue = Figures.fmt(item.weight(), 2, "kg"),
        onRemove = onRemove,
        removeDescription = "Remove load line",
    ) {
        // What the line is measured by, and how many of it.  The dropdown takes
        // the larger share: it is the one control here whose *value* is a phrase
        // ("Weight by Volume") rather than a number, and an even split truncated
        // it to "Weight by V...".
        TwoUp(
            firstWeight = 1.35f,
            first = {
                DropdownField(
                    label = "Measured by",
                    options = LoadType.ORDER,
                    selected = item.loadType,
                    optionLabel = { it.label },
                    onSelected = { onItemChange(item.copy(loadType = it)) },
                )
            },
            second = {
                NumberField(
                    label = "Quantity",
                    value = item.qty.toDouble(),
                    onValueChange = { onItemChange(item.copy(qty = it.roundToInt())) },
                    decimals = 0,
                    // A floor of 1, matching the reference's spin box for a load
                    // line: a line with quantity 0 still carries its weight, where
                    // a floor of 0 would silently zero it.
                    min = 1.0,
                    max = 99999.0,
                )
            },
        )

        // The description is the only genuinely free-text field on the row, so it
        // keeps the full width the grid denies everything else.
        LabeledTextField(
            label = "Description",
            value = item.name,
            onValueChange = { onItemChange(item.copy(name = it)) },
            hint = "e.g. steel plate",
        )

        when (item.loadType) {
            LoadType.FIXED -> NumberField(
                label = "Weight",
                value = item.weightKg,
                onValueChange = { onItemChange(item.copy(weightKg = it)) },
                unit = "kg",
                decimals = 2,
                max = 1.0e9,
            )

            LoadType.PER_METRE -> TwoUp(
                first = {
                    NumberField(
                        label = "Weight per metre",
                        value = item.kgPerMetre,
                        onValueChange = { onItemChange(item.copy(kgPerMetre = it)) },
                        unit = "kg/m",
                        decimals = 2,
                        max = 1.0e6,
                    )
                },
                second = {
                    NumberField(
                        label = "Length",
                        value = item.lengthM,
                        onValueChange = { onItemChange(item.copy(lengthM = it)) },
                        unit = "m",
                        decimals = 2,
                        max = 1.0e5,
                    )
                },
            )

            // Weight by volume is entered as the volume and the material density:
            // volume x density is the weight, and there is no separate
            // weight-per-volume figure to ask for.
            LoadType.PER_VOLUME -> TwoUp(
                first = {
                    NumberField(
                        label = "Volume",
                        value = item.volumeM3,
                        onValueChange = { onItemChange(item.copy(volumeM3 = it)) },
                        unit = "m\u00B3",
                        decimals = 3,
                        max = 1.0e5,
                    )
                },
                second = {
                    NumberField(
                        label = "Density",
                        value = item.densityKgM3,
                        onValueChange = { onItemChange(item.copy(densityKgM3 = it)) },
                        unit = "kg/m\u00B3",
                        decimals = 1,
                        min = 0.001,
                        max = 1.0e8,
                    )
                },
            )
        }

    }
}

/**
 * One line of lifting tackle.
 *
 * No measurement menu: tackle is weighed, so there is nothing to choose, and a
 * dropdown holding one entry would be a control that cannot do anything.
 */
@Composable
private fun TackleRow(
    item: TackleItem,
    onItemChange: (TackleItem) -> Unit,
    onRemove: () -> Unit,
) {
    RowCard(
        answerLabel = "Subtotal",
        answerValue = Figures.fmt(item.subtotal(), 2, "kg"),
        onRemove = onRemove,
        removeDescription = "Remove tackle line",
    ) {
        LabeledTextField(
            label = "Description",
            value = item.description,
            onValueChange = { onItemChange(item.copy(description = it)) },
            hint = "e.g. 4-leg chain sling",
        )

        TwoUp(
            first = {
                // The reference's tackle quantity floor is 0, not 1: a tackle line
                // can legitimately be recorded and then set aside without
                // deleting it.
                NumberField(
                    label = "Quantity",
                    value = item.qty.toDouble(),
                    onValueChange = { onItemChange(item.copy(qty = it.roundToInt())) },
                    decimals = 0,
                    min = 0.0,
                    max = 99999.0,
                )
            },
            second = {
                NumberField(
                    label = "Weight each",
                    value = item.weightKg,
                    onValueChange = { onItemChange(item.copy(weightKg = it)) },
                    unit = "kg",
                    decimals = 2,
                    max = 1.0e6,
                )
            },
        )

    }
}

/**
 * Two fields side by side, each taking an equal share of the row.
 *
 * The row builders put their short fields two to a line.  Stacked one per line, a
 * load item was five full-width boxes tall and a lift with three of them ran off
 * the bottom of the screen; paired, the same item is three lines and a foot, and
 * only the *description* -- the one genuinely free-text field -- still wants the
 * full width.
 *
 * [firstWeight] exists for the odd row that needs an uneven split: a dropdown
 * showing "Weight by Volume" needs more room than the quantity beside it, and an
 * even split truncated the one label that had to stay readable.
 */
@Composable
private fun TwoUp(
    firstWeight: Float = 1f,
    secondWeight: Float = 1f,
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.weight(firstWeight)) { first() }
        Box(Modifier.weight(secondWeight)) { second() }
    }
}

/**
 * A line's card: its fields, then the control that removes it.
 *
 * The remove control sits **below** the fields rather than beside them.  Beside
 * them it took width from every entry box on the row to hold one small button,
 * and on a tall line it ended up floating at the top-right, detached from the row
 * it deletes.  Full-width fields with the cross at the foot of the card keep the
 * row and its delete control in one block, and give the numbers back their width.
 *
 * The line's own answer shares that foot row, so the two things that describe a
 * finished line -- what it weighs and how to remove it -- read together.
 */
@Composable
private fun RowCard(
    answerLabel: String,
    answerValue: String,
    onRemove: () -> Unit,
    removeDescription: String,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = SectionBody,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            content()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The label is what makes the foot of the card read as a result
                // rather than as an orphaned number: "Line weight  0.00 kg" says
                // what the figure is, where a lone "0.00 kg" depended on the
                // field above it for meaning.
                Text(
                    text = answerLabel,
                    color = TextMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = answerValue,
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                RemoveLineButton(onClick = onRemove, contentDescription = removeDescription)
            }
        }
    }
}
