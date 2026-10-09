package com.zhafrisbs.liftingplancalculator.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zhafrisbs.liftingplancalculator.core.Figures
import com.zhafrisbs.liftingplancalculator.core.LiftTotals
import com.zhafrisbs.liftingplancalculator.core.TANDEM_METHOD_ORDER
import com.zhafrisbs.liftingplancalculator.core.TandemResult
import com.zhafrisbs.liftingplancalculator.core.UsageState
import com.zhafrisbs.liftingplancalculator.core.governingCaseWords
import com.zhafrisbs.liftingplancalculator.core.solveNonuniform
import com.zhafrisbs.liftingplancalculator.core.tandemMethod
import com.zhafrisbs.liftingplancalculator.ui.LiftViewModel
import com.zhafrisbs.liftingplancalculator.ui.components.DropdownField
import com.zhafrisbs.liftingplancalculator.ui.components.NumberField
import com.zhafrisbs.liftingplancalculator.ui.components.ResultCard
import com.zhafrisbs.liftingplancalculator.ui.components.ResultRowData
import com.zhafrisbs.liftingplancalculator.ui.components.ResultTone
import com.zhafrisbs.liftingplancalculator.ui.components.SectionCard
import com.zhafrisbs.liftingplancalculator.ui.components.SlingTagBlock
import com.zhafrisbs.liftingplancalculator.ui.theme.Danger
import com.zhafrisbs.liftingplancalculator.ui.theme.StateCaution
import com.zhafrisbs.liftingplancalculator.ui.theme.StateOver
import com.zhafrisbs.liftingplancalculator.ui.theme.TextMuted
import com.zhafrisbs.liftingplancalculator.ui.theme.TextPrimary

/**
 * The Tandem tab: two independent cranes sharing one load.
 *
 * The two methods take the same five numbers and differ in exactly one place — how
 * crane 2's projected distance is resolved once the cargo tilts.  That follows from
 * where lug 2 is welded on the load, and it is not a detail of presentation: it
 * changes the span, it changes which crane the load runs onto, and it changes what
 * has to be rated.  On the same 60 t lift the two methods size crane 2's sling at
 * **60,000 kg and 33,003 kg** respectively, which is why the choice is offered
 * before the numbers rather than after them.
 *
 * Each crane is booked against **its own** sling tag and **its own** load chart.
 * Neither rating is shared with the other.
 */
@Composable
fun TandemScreen(
    viewModel: LiftViewModel,
    modifier: Modifier = Modifier,
    /** Asks to open this tab's report, or null for a caller that does not want the button. */
    onShowReport: (() -> Unit)? = null,
) {
    val inputs = viewModel.tandemInputs
    val totals = LiftTotals.of(viewModel.loadItems, viewModel.tackleItems)
    val method = tandemMethod(inputs.scenario)

    var solveError by remember(inputs, totals.gross) { mutableStateOf<String?>(null) }
    val result = runCatching { solveNonuniform(inputs, totals.gross) }
        .onFailure { solveError = it.message ?: "The tandem calculation could not be completed." }
        .onSuccess { solveError = null }
        .getOrNull() as? TandemResult

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

        SectionCard(
            title = "Method",
            note = method.useFor,
        ) {
            DropdownField(
                label = "Tandem Method",
                options = TANDEM_METHOD_ORDER,
                selected = inputs.scenario,
                optionLabel = { tandemMethod(it).label },
                onSelected = { viewModel.selectTandemMethod(it) },
            )
            // The notes travel with the choice rather than sitting in a help page:
            // which case governs, and whether the load can be stood upright at all,
            // both follow from where lug 2 is welded.
            Text(
                text = "${method.lug2}\n\n${method.spanBehaviour}\n\n${method.maxRotation}",
                color = TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "Crane 2 projection: ${method.rule}",
                color = TextPrimary,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
            )
        }

        SectionCard(
            title = "Lift",
            note = "The span between the hooks is the sum of the two COG distances, " +
                "so it is not entered separately.",
        ) {
            NumberField(
                label = "Distance, Crane 1 Hook to COG",
                value = inputs.distA,
                onValueChange = { viewModel.updateTandemInputs(inputs.copy(distA = it)) },
                unit = "m",
                decimals = 3,
                max = 200.0,
            )
            NumberField(
                label = "Distance, Crane 2 Hook to COG",
                value = inputs.distB,
                onValueChange = { viewModel.updateTandemInputs(inputs.copy(distB = it)) },
                unit = "m",
                decimals = 3,
                max = 200.0,
            )
            NumberField(
                label = "COG Depth Below the Baseline",
                value = inputs.cogHeight,
                onValueChange = { viewModel.updateTandemInputs(inputs.copy(cogHeight = it)) },
                unit = "m",
                decimals = 3,
                max = 200.0,
            )
            NumberField(
                label = "Cargo Tilt Angle",
                value = inputs.tiltAngle,
                onValueChange = { viewModel.updateTandemInputs(inputs.copy(tiltAngle = it)) },
                unit = "deg",
                decimals = 2,
                max = 90.0,
            )
        }

        SectionCard(title = "Crane 1") {
            SlingTagBlock(
                prefix = "Crane 1",
                tagWll = inputs.tagWllA,
                onTagWll = { viewModel.updateTandemInputs(inputs.copy(tagWllA = it)) },
                legs = inputs.slingLegsA,
                onLegs = { viewModel.updateTandemInputs(inputs.copy(slingLegsA = it)) },
                angle = inputs.tagWllAAngle,
                onAngle = { viewModel.updateTandemInputs(inputs.copy(tagWllAAngle = it)) },
            )
            NumberField(
                label = "Crane 1 Rated Capacity",
                value = inputs.craneRatedA,
                onValueChange = { viewModel.updateTandemInputs(inputs.copy(craneRatedA = it)) },
                unit = "kg",
                decimals = 1,
                max = 1000000.0,
            )
        }

        SectionCard(title = "Crane 2") {
            SlingTagBlock(
                prefix = "Crane 2",
                tagWll = inputs.tagWllB,
                onTagWll = { viewModel.updateTandemInputs(inputs.copy(tagWllB = it)) },
                legs = inputs.slingLegsB,
                onLegs = { viewModel.updateTandemInputs(inputs.copy(slingLegsB = it)) },
                angle = inputs.tagWllBAngle,
                onAngle = { viewModel.updateTandemInputs(inputs.copy(tagWllBAngle = it)) },
            )
            NumberField(
                label = "Crane 2 Rated Capacity",
                value = inputs.craneRatedB,
                onValueChange = { viewModel.updateTandemInputs(inputs.copy(craneRatedB = it)) },
                unit = "kg",
                decimals = 1,
                max = 1000000.0,
            )
        }

        if (solveError != null) {
            Text(
                text = solveError!!,
                color = StateOver,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
            )
        } else if (result != null) {
            GeometryResults(result)
            CaseCard(
                title = "Case A \u2014 Level Baseline",
                shareA = result.shareALevel, shareB = result.shareBLevel,
                tensionA = result.tensionALevel, tensionB = result.tensionBLevel,
                requiredA = result.requiredALevel, requiredB = result.requiredBLevel,
            )
            CaseCard(
                title = "Case B \u2014 Declared Tilt",
                shareA = result.shareATilt, shareB = result.shareBTilt,
                tensionA = result.tensionATilt, tensionB = result.tensionBTilt,
                requiredA = result.requiredATilt, requiredB = result.requiredBTilt,
            )
            // Only the aligned pivot method has a Case C at all.  A card reading
            // "0.00 kg / 60,000 kg" for a rotation that cannot happen would be worse
            // than no card, so the whole section is absent rather than zeroed.
            if (result.fullRotation) {
                CaseCard(
                    title = "Case C \u2014 Full 90\u00b0 Vertical",
                    shareA = result.shareAVertical, shareB = result.shareBVertical,
                    tensionA = result.tensionAVertical, tensionB = result.tensionBVertical,
                    requiredA = result.requiredAVertical, requiredB = result.requiredBVertical,
                )
            }
            GoverningResults(result)
            CraneResults(result)
        }

        SectionCard(title = "Actions") {
            if (onShowReport != null) {
                Button(
                    onClick = onShowReport,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Show report")
                }
            }
            Button(
                onClick = viewModel::clearTandem,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Danger,
                    contentColor = TextPrimary,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Clear")
            }
            Text(
                text = "Clear empties every measurement on this tab and keeps " +
                    "the method. Show report opens this lift and the crane together.",
                color = TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Box(Modifier.fillMaxWidth())
    }
}

@Composable
private fun GeometryResults(result: TandemResult) {
    ResultCard(
        title = "Tilted Geometry",
        rows = listOf(
            ResultRowData("Level span between hooks", Figures.fmt(result.span, 3, "m")),
            ResultRowData("COG depth below baseline", Figures.fmt(result.cogHeight, 3, "m")),
            ResultRowData("Cargo tilt", Figures.fmt(result.tiltAngle, 2, "deg")),
            // The angle beyond which the lift stops being a lift.  With lug 2 on the
            // top surface the span closes long before vertical.
            ResultRowData(
                "Tilt limit for this method",
                if (result.fullRotation) {
                    "Full 90\u00b0"
                } else {
                    Figures.fmt(result.maxTiltAngle, 1, "deg")
                },
                if (result.fullRotation) ResultTone.NEUTRAL else ResultTone.CAUTION,
            ),
            ResultRowData("COG shift under lug 1", Figures.fmt(result.cogShift, 3, "m")),
            ResultRowData("Lug 2 inward travel", Figures.fmt(result.lug2Travel, 3, "m")),
            ResultRowData("Crane 1 projected distance", Figures.fmt(result.distANew, 3, "m")),
            ResultRowData("Crane 2 projected distance", Figures.fmt(result.distBNew, 3, "m")),
            ResultRowData("Span after the tilt", Figures.fmt(result.spanNew, 3, "m")),
            ResultRowData("Hook height difference", Figures.fmt(result.heightDifference, 3, "m")),
        ),
    )
}

/**
 * One named case, side by side for the two cranes.
 *
 * A case the method cannot reach arrives with all six figures null, and the card is
 * skipped entirely — see the call site.
 */
@Composable
private fun CaseCard(
    title: String,
    shareA: Double?,
    shareB: Double?,
    tensionA: Double?,
    tensionB: Double?,
    requiredA: Double?,
    requiredB: Double?,
) {
    ResultCard(
        title = title,
        rows = listOf(
            ResultRowData("Crane 1 share", Figures.fmt(shareA, 2, "kg")),
            ResultRowData("Crane 1 tension", Figures.fmt(tensionA, 2, "kg")),
            ResultRowData("Crane 1 required WLL", Figures.fmt(requiredA, 2, "kg")),
            ResultRowData("Crane 2 share", Figures.fmt(shareB, 2, "kg")),
            ResultRowData("Crane 2 tension", Figures.fmt(tensionB, 2, "kg")),
            ResultRowData("Crane 2 required WLL", Figures.fmt(requiredB, 2, "kg")),
        ),
    )
}

@Composable
private fun GoverningResults(result: TandemResult) {
    ResultCard(
        title = "Governing Case, Per Crane",
        rows = listOf(
            ResultRowData("Crane 1 required WLL", Figures.fmt(result.requiredWllA, 2, "kg")),
            // Which case sized the crane is a fact about the solve, so the words come
            // from the model rather than being written again here.
            ResultRowData("Crane 1 sized by", governingCaseWords(result.governingCaseA)),
            ResultRowData("Crane 2 required WLL", Figures.fmt(result.requiredWllB, 2, "kg")),
            ResultRowData("Crane 2 sized by", governingCaseWords(result.governingCaseB)),
            ResultRowData(
                "Maximum tension",
                Figures.fmt(result.maxTension, 2, "kg"),
                ResultTone.CAUTION,
            ),
            ResultRowData(
                "Required WLL",
                Figures.fmt(result.requiredWll, 2, "kg"),
                ResultTone.CAUTION,
            ),
            ResultRowData("Setup SWL", Figures.fmt(result.setupSwl, 2, "kg")),
        ),
    )
}

@Composable
private fun CraneResults(result: TandemResult) {
    for (index in 1..2) {
        val first = index == 1
        ResultCard(
            title = "Crane $index",
            rows = listOf(
                ResultRowData("Tag WLL", Figures.fmt(if (first) result.tagWllA else result.tagWllB, 1, "kg")),
                ResultRowData(
                    "Sling legs",
                    "${
                        if (first) result.slingLegsA else result.slingLegsB
                    } chosen \u2192 rated on ${
                        if (first) result.ratedLegsA else result.ratedLegsB
                    }",
                ),
                ResultRowData(
                    "Tag angle",
                    Figures.fmt(if (first) result.tagWllAAngle else result.tagWllBAngle, 1, "deg"),
                ),
                ResultRowData(
                    "Per-leg capacity",
                    Figures.fmt(if (first) result.legCapacityA else result.legCapacityB, 2, "kg"),
                ),
                ResultRowData(
                    "Tag utilisation",
                    Figures.fmt(if (first) result.utilisationA else result.utilisationB, 2, "%"),
                    toneFor(if (first) result.equipmentOk else result.equipmentOk),
                ),
                ResultRowData(
                    "Crane rated capacity",
                    Figures.fmt(if (first) result.craneRatedA else result.craneRatedB, 1, "kg"),
                ),
                ResultRowData(
                    "Crane usage",
                    Figures.fmt(if (first) result.craneUsageA else result.craneUsageB, 2, "%"),
                    usageTone(if (first) result.craneUsageStateA else result.craneUsageStateB),
                ),
                ResultRowData(
                    "Crane usage band",
                    usageWords(if (first) result.craneUsageStateA else result.craneUsageStateB),
                    usageTone(if (first) result.craneUsageStateA else result.craneUsageStateB),
                ),
            ),
        )
    }

    val risk = result.craneRiskFlag
    Text(
        text = when (risk) {
            null -> "Enter both crane rated capacities to check each crane against " +
                "the 75% window."
            // A crane booked past the window raises a flag beside the figure rather
            // than flipping the verdict: it is a rigging judgement about the crane,
            // not about the slings, and the operator has to be the one to make it.
            true -> "CHECK \u2014 at least one crane is booked outside the 75% window. " +
                "That is a judgement about the crane, not the slings."
            false -> "Both cranes are inside the 75% window."
        },
        color = if (risk == true) StateCaution else TextMuted,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = if (risk == true) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier.padding(top = 6.dp),
    )
}

private fun usageWords(state: UsageState?): String = when (state) {
    UsageState.WITHIN -> "within 75%"
    UsageState.CAUTION -> "between 75% and the chart"
    UsageState.OVER -> "over the chart"
    null -> Figures.NOTHING
}

private fun usageTone(state: UsageState?): ResultTone = when (state) {
    UsageState.WITHIN -> ResultTone.OK
    UsageState.CAUTION -> ResultTone.CAUTION
    UsageState.OVER -> ResultTone.OVER
    null -> ResultTone.NEUTRAL
}

private fun toneFor(ok: Boolean?): ResultTone = when (ok) {
    null -> ResultTone.NEUTRAL
    true -> ResultTone.OK
    false -> ResultTone.OVER
}
