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
import com.zhafrisbs.liftingplancalculator.core.ANGLE_BASIS_EXTERNAL
import com.zhafrisbs.liftingplancalculator.core.ANGLE_BASIS_INTERNAL
import com.zhafrisbs.liftingplancalculator.core.Figures
import com.zhafrisbs.liftingplancalculator.core.HITCH_ORDER
import com.zhafrisbs.liftingplancalculator.core.HITCH_SPECS
import com.zhafrisbs.liftingplancalculator.core.LiftTotals
import com.zhafrisbs.liftingplancalculator.core.OneLegInputs
import com.zhafrisbs.liftingplancalculator.core.SlingResult
import com.zhafrisbs.liftingplancalculator.core.solveOneLeg
import com.zhafrisbs.liftingplancalculator.ui.LiftViewModel
import com.zhafrisbs.liftingplancalculator.ui.components.DropdownField
import com.zhafrisbs.liftingplancalculator.ui.components.NumberField
import com.zhafrisbs.liftingplancalculator.ui.components.ResultCard
import com.zhafrisbs.liftingplancalculator.ui.components.ResultRowData
import com.zhafrisbs.liftingplancalculator.ui.components.ResultTone
import com.zhafrisbs.liftingplancalculator.ui.components.SectionCard
import com.zhafrisbs.liftingplancalculator.ui.theme.Danger
import com.zhafrisbs.liftingplancalculator.ui.theme.StateOver
import com.zhafrisbs.liftingplancalculator.ui.theme.TextMuted
import com.zhafrisbs.liftingplancalculator.ui.theme.TextPrimary

/**
 * The Uniform Load tab: one continuous sling, in any of the nineteen arrangements.
 *
 * **The form is built from the arrangement, not from a fixed list of fields.**  The
 * reference keeps each arrangement's shape in a `HitchSpec` — whether it needs a
 * diameter or a rectangle, a choke angle, a pick distance, a secondary sling — and
 * so does this: the sections below appear because the spec asks for them.  A
 * nineteen-branch `when` over the arrangement would be nineteen places for the form
 * and the solver to disagree about what a given arrangement needs.
 *
 * The gross weight is **not** entered here.  It comes from the Overall Weight tab,
 * which owns the lift.  The 75% crane window is not applied either: that sizes the
 * lift against the *crane*, and this tab checks the sling against the load the hook
 * actually carries.
 */
@Composable
fun UniformLoadScreen(
    viewModel: LiftViewModel,
    modifier: Modifier = Modifier,
    /**
     * Asks to open this tab's report, or null for a caller that does not want the
     * button.  A sling tab's report carries the crane *by default* -- the crane is
     * booked whichever arrangement is used -- so the caller asks the "Include:
     * Crane" question before opening it.
     */
    onShowReport: (() -> Unit)? = null,
) {
    val inputs = viewModel.slingInputs
    val totals = LiftTotals.of(viewModel.loadItems, viewModel.tackleItems)
    val spec = HITCH_SPECS.getValue(inputs.hitch)

    // Solving on every recomposition is the reference's model too, and it is
    // arithmetic rather than I/O.  A failure is captured rather than thrown: a
    // half-typed form is a normal state, and every one of those failures is a
    // sentence written for the operator.
    var solveError by remember(inputs, totals.gross) { mutableStateOf<String?>(null) }
    val result = runCatching { solveOneLeg(inputs, totals.gross) }
        .onFailure { solveError = it.message ?: "The sling calculation could not be completed." }
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

        SectionCard(title = "Sling") {
            DropdownField(
                label = "Arrangement",
                options = HITCH_ORDER,
                selected = inputs.hitch,
                optionLabel = { HITCH_SPECS.getValue(it).label },
                onSelected = { viewModel.selectSlingHitch(it) },
            )
            NumberField(
                label = "Sling Total Length",
                value = inputs.slingLength,
                onValueChange = { viewModel.updateSlingInputs(inputs.copy(slingLength = it)) },
                unit = "m",
                decimals = 3,
                max = 200.0,
            )
        }

        // The load's shape.  A round load is described by its diameter, a
        // rectangular one by its height and its length, and an arrangement that
        // takes no particular shape takes neither.
        if (spec.roundLoad || spec.rectangularLoad) {
            SectionCard(title = "Load") {
                if (spec.roundLoad) {
                    NumberField(
                        label = "Load Diameter",
                        value = inputs.diameter,
                        onValueChange = {
                            viewModel.updateSlingInputs(inputs.copy(diameter = it))
                        },
                        unit = "m",
                        decimals = 3,
                        max = 50.0,
                    )
                }
                if (spec.rectangularLoad) {
                    NumberField(
                        label = "Load Side / Height",
                        value = inputs.side,
                        onValueChange = { viewModel.updateSlingInputs(inputs.copy(side = it)) },
                        unit = "m",
                        decimals = 3,
                        max = 50.0,
                    )
                    NumberField(
                        label = "Load Bottom / Length",
                        value = inputs.bottom,
                        onValueChange = { viewModel.updateSlingInputs(inputs.copy(bottom = it)) },
                        unit = "m",
                        decimals = 3,
                        max = 50.0,
                    )
                }
            }
        }

        if (spec.needsAngle) {
            SectionCard(
                title = "Choke",
                // The reference's own wording for what this arrangement's angle
                // measures: a round choke's contact angle and a rectangular
                // choke's flare are not the same angle.
                note = spec.angleDescription,
            ) {
                NumberField(
                    label = "Choke Angle",
                    value = inputs.chokeAngle,
                    onValueChange = { viewModel.updateSlingInputs(inputs.copy(chokeAngle = it)) },
                    unit = "deg",
                    decimals = 1,
                    max = 179.0,
                )
                DropdownField(
                    label = "Angle Basis",
                    options = listOf(ANGLE_BASIS_INTERNAL, ANGLE_BASIS_EXTERNAL),
                    selected = inputs.angleBasis,
                    optionLabel = {
                        if (it == ANGLE_BASIS_INTERNAL) {
                            "Internal \u2014 measured at the choke"
                        } else {
                            "External \u2014 hook to load"
                        }
                    },
                    onSelected = { viewModel.updateSlingInputs(inputs.copy(angleBasis = it)) },
                )
            }
        }

        // A 4-leg bridle is spread over a rectangle, so it needs two dimensions
        // where every other pick-point arrangement needs one distance.
        if (spec.needsPickDistance || spec.shape == "bridle4") {
            SectionCard(title = "Pick Points") {
                if (spec.needsPickDistance) {
                    NumberField(
                        label = "Pick-Point Distance",
                        value = inputs.pickDistance,
                        onValueChange = {
                            viewModel.updateSlingInputs(inputs.copy(pickDistance = it))
                        },
                        unit = "m",
                        decimals = 3,
                        max = 100.0,
                    )
                }
                if (spec.shape == "bridle4") {
                    NumberField(
                        label = "Pick-Point Length",
                        value = inputs.pickLength,
                        onValueChange = {
                            viewModel.updateSlingInputs(inputs.copy(pickLength = it))
                        },
                        unit = "m",
                        decimals = 3,
                        max = 100.0,
                    )
                    NumberField(
                        label = "Pick-Point Width",
                        value = inputs.pickWidth,
                        onValueChange = {
                            viewModel.updateSlingInputs(inputs.copy(pickWidth = it))
                        },
                        unit = "m",
                        decimals = 3,
                        max = 100.0,
                    )
                }
            }
        }

        if (spec.nested != 0) {
            SectionCard(
                title = "Secondary Slings",
                note = "Each leg of the master sling carries its own ${spec.nested}-leg sling.",
            ) {
                NumberField(
                    label = "Secondary Sling Length",
                    value = inputs.secondaryLength,
                    onValueChange = {
                        viewModel.updateSlingInputs(inputs.copy(secondaryLength = it))
                    },
                    unit = "m",
                    decimals = 3,
                    max = 100.0,
                )
                NumberField(
                    label = "Secondary Pick Spacing",
                    value = inputs.secondarySpread,
                    onValueChange = {
                        viewModel.updateSlingInputs(inputs.copy(secondarySpread = it))
                    },
                    unit = "m",
                    decimals = 3,
                    max = 100.0,
                )
                NumberField(
                    label = "Secondary Tag WLL",
                    value = inputs.secondaryTagWll,
                    onValueChange = {
                        viewModel.updateSlingInputs(inputs.copy(secondaryTagWll = it))
                    },
                    unit = "kg",
                    decimals = 1,
                    max = 100000.0,
                )
                NumberField(
                    label = "Secondary Tag WLL Angle",
                    value = inputs.secondaryTagWllAngle,
                    onValueChange = {
                        viewModel.updateSlingInputs(inputs.copy(secondaryTagWllAngle = it))
                    },
                    unit = "deg",
                    decimals = 1,
                    max = 90.0,
                )
            }
        }

        SectionCard(
            title = "Installed Tag",
            note = if (spec.needsTagAngle) {
                "The tag's rating is derated by cos(Tag WLL Angle) across the legs."
            } else {
                "A single leg hangs vertical, so it takes the tag's full rating and " +
                    "there is no angle to enter."
            },
        ) {
            NumberField(
                label = "Tag WLL",
                value = inputs.tagWll,
                onValueChange = { viewModel.updateSlingInputs(inputs.copy(tagWll = it)) },
                unit = "kg",
                decimals = 1,
                max = 1000000.0,
            )
            // Hidden, not disabled, for a one-leg arrangement: a field that can be
            // typed into but is ignored is worse than no field.  The engine zeroes
            // it as well, so this is the friendly half of one rule.
            if (spec.needsTagAngle) {
                NumberField(
                    label = "Tag WLL Angle",
                    value = inputs.tagWllAngle,
                    onValueChange = {
                        viewModel.updateSlingInputs(inputs.copy(tagWllAngle = it))
                    },
                    unit = "deg",
                    decimals = 1,
                    max = 90.0,
                )
            }
        }

        if (solveError != null) {
            Text(
                text = solveError!!,
                color = StateOver,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
            )
        } else if (result != null) {
            SlingResults(result)
            TagResults(result)
            if (result.nested != 0) SecondaryResults(result)
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
                onClick = viewModel::clearSling,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Danger,
                    contentColor = TextPrimary,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Clear")
            }
            Text(
                text = "Clear empties every value on this tab and returns the " +
                    "arrangement to Direct hook. Show report opens this arrangement " +
                    "and the crane together.",
                color = TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Box(Modifier.fillMaxWidth())
    }
}

@Composable
private fun SlingResults(result: SlingResult) {
    ResultCard(
        title = "Sling Result",
        rows = buildList {
            add(ResultRowData("Arrangement", result.hitchLabel))
            add(ResultRowData("Effective legs", result.effectiveLegs.toString()))
            add(ResultRowData("Sling angle factor", Figures.fmt(result.angleFactor, 3)))
            if (result.angle > 0) {
                add(ResultRowData("Sling angle", Figures.fmt(result.angle, 2, "deg")))
            }
            // A 2-leg basket carries a longitudinal and a transverse angle, and the
            // documents publish both before multiplying them.
            if (result.angleLong != null && result.angleTrans != null) {
                add(ResultRowData("Longitudinal angle", Figures.fmt(result.angleLong, 2, "deg")))
                add(ResultRowData("Transverse angle", Figures.fmt(result.angleTrans, 2, "deg")))
                add(ResultRowData("Combined factor", Figures.fmt(result.angleFactor, 3)))
            }
            add(ResultRowData("Sling length", Figures.fmt(result.slingLength, 3, "m")))
            if (result.wrapLength > 0) {
                add(ResultRowData("Wrap length", Figures.fmt(result.wrapLength, 3, "m")))
            }
            add(ResultRowData("Free leg", Figures.fmt(result.freeLegLength, 3, "m")))
            add(ResultRowData("Headroom above load", Figures.fmt(result.hookToLoad, 3, "m")))
            if (result.externalAngle != null) {
                add(ResultRowData("Choke angle (external)", Figures.fmt(result.externalAngle, 1, "deg")))
                add(ResultRowData("Choke angle (internal)", Figures.fmt(result.internalAngle, 1, "deg")))
                add(ResultRowData("Choke reduction", Figures.fmt(result.chokeReductionFactor, 2)))
            }
            add(ResultRowData("Reeve factor", Figures.fmt(result.combinedReeveFactor, 2)))
            add(
                ResultRowData(
                    "Rated on",
                    "${result.ratingLegs} leg" + if (result.ratingLegs == 1) "" else "s",
                )
            )
            add(ResultRowData("Tension per leg", Figures.fmt(result.tensionEach, 2, "kg")))
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
        },
    )
    if (result.shapeNote.isNotEmpty()) {
        Text(
            text = result.shapeNote,
            color = TextMuted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun TagResults(result: SlingResult) {
    ResultCard(
        title = "Installed Tag",
        rows = buildList {
            add(ResultRowData("Tag WLL", Figures.fmt(result.tagWll, 1, "kg")))
            if (result.tagWllAngle > 0) {
                add(ResultRowData("Tag WLL angle", Figures.fmt(result.tagWllAngle, 1, "deg")))
                add(ResultRowData("Tag angle factor", Figures.fmt(result.tagAngleFactor, 3)))
            }
            add(
                ResultRowData(
                    "Per-leg capacity",
                    Figures.fmt(result.legCapacity, 2, "kg"),
                )
            )
            add(
                ResultRowData(
                    "Utilisation",
                    Figures.fmt(result.utilisation, 2, "%"),
                    toneFor(result.equipmentOk),
                )
            )
            add(
                ResultRowData(
                    "Status",
                    result.equipmentStatus,
                    toneFor(result.equipmentOk),
                )
            )
        },
    )
}

@Composable
private fun SecondaryResults(result: SlingResult) {
    val groups = result.secondaryGroups.orEmpty()
    ResultCard(
        title = "Secondary Slings",
        rows = buildList {
            add(ResultRowData("Physical legs", (result.secondaryLegs ?: 0).toString()))
            add(ResultRowData("Rated on", "${result.secondaryRatingLegs ?: 0} legs"))
            add(ResultRowData("Secondary angle", Figures.fmt(result.secondaryAngle, 2, "deg")))
            add(ResultRowData("Secondary factor", Figures.fmt(result.secondaryAf, 3)))
            add(ResultRowData("Combined factor", Figures.fmt(result.secondaryCombinedAf, 3)))
            add(ResultRowData("Secondary height", Figures.fmt(result.secondaryHeight, 3, "m")))
            add(ResultRowData("Total combined length", Figures.fmt(result.totalCombinedLength, 2, "m")))
            if (result.pileLevelDifference != null) {
                add(
                    ResultRowData(
                        "Pile level difference",
                        Figures.fmt(result.pileLevelDifference, 3, "m"),
                        ResultTone.CAUTION,
                    )
                )
            }
            add(
                ResultRowData(
                    "Highest secondary tension",
                    Figures.fmt(result.secondaryTension, 2, "kg"),
                    ResultTone.CAUTION,
                )
            )
            add(
                ResultRowData(
                    "Secondary required WLL",
                    Figures.fmt(result.secondaryRequiredWll, 2, "kg"),
                    ResultTone.CAUTION,
                )
            )
            if (result.secondaryLegCapacity != null) {
                add(
                    ResultRowData(
                        "Secondary per-leg capacity",
                        Figures.fmt(result.secondaryLegCapacity, 2, "kg"),
                    )
                )
                add(
                    ResultRowData(
                        "Secondary utilisation",
                        Figures.fmt(result.secondaryUtilisation, 2, "%"),
                        toneFor(result.secondaryEquipmentOk),
                    )
                )
                add(
                    ResultRowData(
                        "Secondary status",
                        result.secondaryEquipmentStatus,
                        toneFor(result.secondaryEquipmentOk),
                    )
                )
            }
        },
    )
    if (groups.size > 1) {
        // The reference documents report every leg group, outermost first, and mark
        // the one that governs.  The outermost group has the shortest drop, so the
        // piles under it are the highest and it carries the highest tension.
        Text(
            text = "Leg groups, outermost first:",
            color = TextMuted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
        )
        ResultCard(
            title = "Leg Groups",
            rows = groups.map { group ->
                ResultRowData(
                    label = "${Figures.fmt(group.offset, 2, "m")} offset" +
                        if (group.governing) " (governing)" else "",
                    value = Figures.fmt(group.tension, 2, "kg"),
                    tone = if (group.governing) ResultTone.CAUTION else ResultTone.NEUTRAL,
                )
            },
        )
    }
}

private fun toneFor(ok: Boolean?): ResultTone = when (ok) {
    null -> ResultTone.NEUTRAL
    true -> ResultTone.OK
    false -> ResultTone.OVER
}
