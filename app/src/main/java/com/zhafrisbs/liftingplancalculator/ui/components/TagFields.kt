package com.zhafrisbs.liftingplancalculator.ui.components

import androidx.compose.runtime.Composable
import com.zhafrisbs.liftingplancalculator.core.SINGLE_LEG
import com.zhafrisbs.liftingplancalculator.core.SLING_LEG_OPTIONS

/**
 * One sling tag: its WLL, how many legs it is made of, and — only for a bridle —
 * the angle its rating is declared at.
 *
 * Shared by the Nonuniform Load and Tandem tabs rather than written twice.  The
 * reference's own lesson, in its own words: five tabs each grew their own row
 * builder and the result was rows of three different heights and two different
 * indents, which the operator reported as "the spacing is nice on one tab and not
 * the others".
 *
 * Three rules live here, and each is the reference's:
 *
 *  * **The leg count is a choice, not a calculation.**  The operator says how many
 *    legs the tag is made of; the documents' rule says what that rating is
 *    *declared* on, and the solver derives it.
 *  * **The angle field is hidden for a single leg**, because a single leg hangs
 *    vertical and takes the tag's full rating.  Hiding it is the friendly half of
 *    the rule; [com.zhafrisbs.liftingplancalculator.core.effectiveTagAngle] enforces
 *    the other half, so a leftover 45 degrees cannot quietly derate the capacity by
 *    a third with nothing on screen to explain it.
 *  * **1 comes first** in the list, because it is the ordinary case for these
 *    lifts: each hook path carries its own separately rated sling.
 */
@Composable
fun SlingTagBlock(
    prefix: String,
    tagWll: Double,
    onTagWll: (Double) -> Unit,
    legs: Int,
    onLegs: (Int) -> Unit,
    angle: Double,
    onAngle: (Double) -> Unit,
) {
    NumberField(
        label = "$prefix Tag WLL",
        value = tagWll,
        onValueChange = onTagWll,
        unit = "kg",
        decimals = 1,
        max = 1000000.0,
    )
    DropdownField(
        label = "$prefix Sling Legs",
        options = SLING_LEG_OPTIONS,
        selected = legs,
        optionLabel = { if (it == SINGLE_LEG) "1 \u2014 single leg" else it.toString() },
        onSelected = onLegs,
    )
    if (legs != SINGLE_LEG) {
        NumberField(
            label = "$prefix Tag WLL Angle",
            value = angle,
            onValueChange = onAngle,
            unit = "deg",
            decimals = 1,
            max = 90.0,
        )
    }
}
