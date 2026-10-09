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
import com.zhafrisbs.liftingplancalculator.core.Asym2LegResult
import com.zhafrisbs.liftingplancalculator.core.Figures
import com.zhafrisbs.liftingplancalculator.core.LiftTotals
import com.zhafrisbs.liftingplancalculator.core.solveNonuniform
import com.zhafrisbs.liftingplancalculator.ui.LiftViewModel
import com.zhafrisbs.liftingplancalculator.ui.components.NumberField
import com.zhafrisbs.liftingplancalculator.ui.components.ResultCard
import com.zhafrisbs.liftingplancalculator.ui.components.ResultRowData
import com.zhafrisbs.liftingplancalculator.ui.components.ResultTone
import com.zhafrisbs.liftingplancalculator.ui.components.SectionCard
import com.zhafrisbs.liftingplancalculator.ui.components.SlingTagBlock
import com.zhafrisbs.liftingplancalculator.ui.theme.Danger
import com.zhafrisbs.liftingplancalculator.ui.theme.StateOver
import com.zhafrisbs.liftingplancalculator.ui.theme.TextMuted
import com.zhafrisbs.liftingplancalculator.ui.theme.TextPrimary

/**
 * The Nonuniform Load tab: a 2-leg asymmetric bridle with a clevis shortening grab
 * hook.
 *
 * A *nonuniform* lift is one where the two legs do not share the load equally.
 * The Uniform Load tab assumes each leg carries the same share, and that assumption
 * fails as soon as the centre of gravity sits off the midpoint of the pick points.
 *
 * The geometry is fixed by the operational rule in the reference document: the hook
 * must sit vertically above the centre of gravity, so **both legs share one
 * headroom**.  The load share is a statics result, not a leg count — each leg
 * carries the share set by the moment about the *opposite* pick point, so the leg
 * nearest the COG carries the larger share, and it is the *other* leg that gets
 * shortened.
 */
@Composable
fun NonuniformLoadScreen(
    viewModel: LiftViewModel,
    modifier: Modifier = Modifier,
    /** Asks to open this tab's report, or null for a caller that does not want the button. */
    onShowReport: (() -> Unit)? = null,
) {
    val inputs = viewModel.nonuniformInputs
    val totals = LiftTotals.of(viewModel.loadItems, viewModel.tackleItems)

    var solveError by remember(inputs, totals.gross) { mutableStateOf<String?>(null) }
    val result = runCatching { solveNonuniform(inputs, totals.gross) }
        .onFailure { solveError = it.message ?: "The nonuniform calculation could not be completed." }
        .onSuccess { solveError = null }
        .getOrNull() as? Asym2LegResult

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
            title = "2-Leg Asymmetric (Shortening Grab Hook)",
            note = "The COG may be measured from either pick point; the solver " +
                "works out which leg is long.",
        ) {
            NumberField(
                label = "Sling Length",
                value = inputs.slingLength,
                onValueChange = { viewModel.updateNonuniformInputs(inputs.copy(slingLength = it)) },
                unit = "m",
                decimals = 3,
                max = 200.0,
            )
            NumberField(
                label = "Distance Between Pick Points",
                value = inputs.pickDistance,
                onValueChange = { viewModel.updateNonuniformInputs(inputs.copy(pickDistance = it)) },
                unit = "m",
                decimals = 3,
                max = 200.0,
            )
            NumberField(
                label = "COG From Pick Point 1",
                value = inputs.cogFromPick1,
                onValueChange = { viewModel.updateNonuniformInputs(inputs.copy(cogFromPick1 = it)) },
                unit = "m",
                decimals = 3,
                max = 200.0,
            )
            NumberField(
                label = "Height Difference (2 above 1)",
                value = inputs.pickPoint2Height,
                onValueChange = {
                    viewModel.updateNonuniformInputs(inputs.copy(pickPoint2Height = it))
                },
                unit = "m",
                decimals = 3,
                max = 200.0,
            )
            // A signed step, and the sign is not cosmetic: raising the far pick point
            // shortens its drop and so *lengthens* the leg the operator must shorten.
            // The field is not clamped to non-negative for that reason, which is why
            // this note exists rather than a `min = 0.0`.
            Text(
                text = "Positive means pick point 2 sits higher than pick point 1. " +
                    "Enter a negative value if it sits lower.",
                color = TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        SectionCard(title = "Installed Tags") {
            SlingTagBlock(
                prefix = "Sling 1",
                tagWll = inputs.tagWll1,
                onTagWll = { viewModel.updateNonuniformInputs(inputs.copy(tagWll1 = it)) },
                legs = inputs.slingLegs1,
                onLegs = { viewModel.updateNonuniformInputs(inputs.copy(slingLegs1 = it)) },
                angle = inputs.tagWll1Angle,
                onAngle = { viewModel.updateNonuniformInputs(inputs.copy(tagWll1Angle = it)) },
            )
            SlingTagBlock(
                prefix = "Sling 2",
                tagWll = inputs.tagWll2,
                onTagWll = { viewModel.updateNonuniformInputs(inputs.copy(tagWll2 = it)) },
                legs = inputs.slingLegs2,
                onLegs = { viewModel.updateNonuniformInputs(inputs.copy(slingLegs2 = it)) },
                angle = inputs.tagWll2Angle,
                onAngle = { viewModel.updateNonuniformInputs(inputs.copy(tagWll2Angle = it)) },
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
            TagResults(result)
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
                onClick = viewModel::clearNonuniform,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Danger,
                    contentColor = TextPrimary,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Clear")
            }
            Text(
                text = "Clear empties every measurement on this tab. Show " +
                    "report opens this lift and the crane together.",
                color = TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Box(Modifier.fillMaxWidth())
    }
}

@Composable
private fun GeometryResults(result: Asym2LegResult) {
    ResultCard(
        title = "Bridle Geometry",
        rows = buildList {
            add(ResultRowData("Span to leg 1", Figures.fmt(result.span1, 3, "m")))
            add(ResultRowData("Span to leg 2", Figures.fmt(result.span2, 3, "m")))
            add(ResultRowData("Long leg", result.longLeg.toString()))
            add(
                ResultRowData(
                    "Short leg",
                    "${result.shortLeg} \u2014 shortened to fit",
                    ResultTone.CAUTION,
                )
            )
            // Both legs share one headroom, because the hook sits vertically above
            // the COG.
            add(ResultRowData("Headroom", Figures.fmt(result.headroom, 3, "m")))
            add(ResultRowData("Drop from leg 1", Figures.fmt(result.drop1, 3, "m")))
            add(ResultRowData("Drop from leg 2", Figures.fmt(result.drop2, 3, "m")))
            add(ResultRowData("Short leg length", Figures.fmt(result.shortLegLength, 3, "m")))
            // A grab hook can only take length *out* of a leg, so a shortfall is
            // reported as its own positive figure rather than as a negative
            // "shortening" that reads like an instruction to remove a negative amount.
            if (result.needsLengthening) {
                add(
                    ResultRowData(
                        "Lengthening required",
                        Figures.fmt(result.lengthening, 3, "m"),
                        ResultTone.OVER,
                    )
                )
            } else {
                add(
                    ResultRowData(
                        "Shortening",
                        Figures.fmt(result.shorteningMm, 0, "mm"),
                        ResultTone.CAUTION,
                    )
                )
            }
            add(ResultRowData("Length, leg 1", Figures.fmt(result.length1, 3, "m")))
            add(ResultRowData("Length, leg 2", Figures.fmt(result.length2, 3, "m")))
            add(ResultRowData("Angle, leg 1", Figures.fmt(result.angle1, 2, "deg")))
            add(ResultRowData("Angle, leg 2", Figures.fmt(result.angle2, 2, "deg")))
            add(ResultRowData("Angle factor, leg 1", Figures.fmt(result.angleFactor1, 3)))
            add(ResultRowData("Angle factor, leg 2", Figures.fmt(result.angleFactor2, 3)))
            add(ResultRowData("Share, leg 1", Figures.fmt(result.share1, 2, "kg")))
            add(ResultRowData("Share, leg 2", Figures.fmt(result.share2, 2, "kg")))
            add(ResultRowData("Tension, leg 1", Figures.fmt(result.tension1, 2, "kg")))
            add(ResultRowData("Tension, leg 2", Figures.fmt(result.tension2, 2, "kg")))
            add(
                ResultRowData(
                    "Maximum tension",
                    Figures.fmt(result.maxTension, 2, "kg"),
                    ResultTone.CAUTION,
                )
            )
            add(
                ResultRowData(
                    "Required WLL",
                    Figures.fmt(result.requiredWll, 2, "kg"),
                    ResultTone.CAUTION,
                )
            )
            add(ResultRowData("Governing leg", result.governingLeg.toString()))
            add(ResultRowData("Setup SWL", Figures.fmt(result.setupSwl, 2, "kg")))
        },
    )
}

@Composable
private fun TagResults(result: Asym2LegResult) {
    ResultCard(
        title = "Installed Tags",
        rows = buildList {
            add(ResultRowData("Sling 1 tag WLL", Figures.fmt(result.tagWll1, 1, "kg")))
            add(
                ResultRowData(
                    "Sling 1 leg count",
                    "${result.slingLegs1} chosen \u2192 rated on ${result.ratedLegs1}",
                )
            )
            add(ResultRowData("Sling 1 tag angle", Figures.fmt(result.tagWll1Angle, 1, "deg")))
            add(ResultRowData("Sling 1 per-leg capacity", Figures.fmt(result.legCapacity1, 2, "kg")))
            add(
                ResultRowData(
                    "Sling 1 utilisation",
                    Figures.fmt(result.utilisation1, 2, "%"),
                    toneFor(result.equipmentOk),
                )
            )
            add(ResultRowData("Sling 2 tag WLL", Figures.fmt(result.tagWll2, 1, "kg")))
            add(
                ResultRowData(
                    "Sling 2 leg count",
                    "${result.slingLegs2} chosen \u2192 rated on ${result.ratedLegs2}",
                )
            )
            add(ResultRowData("Sling 2 tag angle", Figures.fmt(result.tagWll2Angle, 1, "deg")))
            add(ResultRowData("Sling 2 per-leg capacity", Figures.fmt(result.legCapacity2, 2, "kg")))
            add(
                ResultRowData(
                    "Sling 2 utilisation",
                    Figures.fmt(result.utilisation2, 2, "%"),
                    toneFor(result.equipmentOk),
                )
            )
        },
    )
    Text(
        text = if (result.equipmentOk == null) {
            "Enter both tag WLLs to check the installed equipment."
        } else if (result.equipmentOk) {
            "Both tags meet their required WLL."
        } else {
            "At least one tag is below its required WLL."
        },
        color = if (result.equipmentOk == false) StateOver else TextMuted,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = if (result.equipmentOk == false) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier.padding(top = 6.dp),
    )
}

private fun toneFor(ok: Boolean?): ResultTone = when (ok) {
    null -> ResultTone.NEUTRAL
    true -> ResultTone.OK
    false -> ResultTone.OVER
}
