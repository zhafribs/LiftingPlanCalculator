package com.zhafrisbs.liftingplancalculator.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * The nonuniform-lift report blocks, ported from `report_1leg.py`.
 *
 * Kept apart from the uniform sections because a nonuniform lift reports **every
 * figure per leg**: the uniform tab's single `tensionEach` and `requiredWll` have no
 * counterpart here, and folding the two into one section would hide exactly the
 * per-leg split the report exists to show.
 *
 * [title] names the block, because the two scenarios now sit on separate tabs and a
 * tandem plan headed "Nonuniform Load" would misname the lift.
 */

/**
 * One nonuniform block, or the reason there is none.
 *
 * Every early return is a distinct sentence rather than a shared "nothing here":
 * *no state* means the tab was never opened, *an error* means it was opened and
 * could not be solved, and *a zero load* means a worked example is sitting there
 * with no real lift behind it.  A single "not set up" for all three would tell the
 * reader to go and fill in a form that is already full.
 */
internal fun nonuniformSections(
    result: NonuniformResult?,
    firstNumber: Int,
    title: String = "Nonuniform Load",
    error: String? = null,
): List<String> {
    val heading = heading(firstNumber, title)
    if (error != null) {
        return listOf(heading, p("$error Complete the $title tab to include it in this plan."))
    }
    if (result == null) {
        return listOf(
            heading,
            p(
                "No ${title.lowercase()} has been set up. Complete the $title tab " +
                    "to include it in this plan."
            ),
        )
    }
    // A solved scenario is not by itself evidence that a lift was set up: the
    // forms seed themselves with the documents' worked examples.  The hook load
    // arriving from the Overall Weight tab is what marks it as real, and without
    // that check a default report would carry an unconfigured section full of
    // zeroes beside a real uniform section.
    if (result.loadWeight <= 0.0) {
        return listOf(
            heading,
            p(
                "No ${title.lowercase()} has been set up. Enter a load weight on " +
                    "the Overall Weight tab and complete the $title tab to include " +
                    "it in this plan."
            ),
        )
    }
    return when (result) {
        is Asym2LegResult -> asymSections(result, firstNumber, title)
        is TandemResult -> tandemSections(result, firstNumber, title)
    }
}

/**
 * The two-leg asymmetric bridle with a shortened leg.
 *
 * Every step the reference document publishes is reproduced with its own heading,
 * because the report is the evidence a reviewer reads: a bare answer cannot be
 * checked, and a heading is what names which calculation an equation is.  The four
 * sections and their order are the document's own.
 */
internal fun asymSections(
    result: Asym2LegResult,
    firstNumber: Int = 1,
    title: String = "Nonuniform Load",
): List<String> {
    val out = mutableListOf(heading(firstNumber, title))
    val step = result.pickPoint2Height
    out += subheading("Lift Input Parameters")
    out += table(
        listOf(
            "Load weight" to fmt(result.loadWeight, 2, "kg"),
            "Lift configuration" to labelOrNotEntered(result.scenarioLabel),
            "Standard sling length available (both legs)" to
                fmt(result.slingLength, 3, "m"),
            "Distance between pick points (total span)" to
                fmt(result.pickDistance, 3, "m"),
            "COG horizontal position from pick point 1" to
                fmt(result.cogFromPick1, 3, "m"),
            "Vertical height difference between pick points" to
                fmt(step, 3, "m") + when {
                    step > 0 -> " (pick point 2 higher)"
                    step < 0 -> " (pick point 2 lower)"
                    else -> " (level)"
                },
            "Base reeve factor (RF)" to fmt(result.baseReeveFactor, 2),
        )
    )

    val longLeg = result.longLeg
    val shortLeg = result.shortLeg
    val span1 = result.span1
    val span2 = result.span2
    val totalSpan = result.pickDistance
    val standard = result.slingLength
    val headroom = result.headroom
    val shortLength = result.shortLegLength
    val drop1 = result.drop1
    val drop2 = result.drop2
    val longLegName = "Sling $longLeg"
    val shortLegName = "Sling $shortLeg"
    val longSpan = max(span1, span2)

    out += heading(
        firstNumber + 1,
        "Geometric & Headroom Calculations (Shortening Method with Elevation Delta)",
    )
    val stepProse = when {
        step > 0 ->
            "The pick points are not level: pick point 2 is ${fmt(step, 3, "m")} " +
                "higher than pick point 1. That step is not cosmetic. The long leg " +
                "sets the hook's height above its own pick point, and the drop to " +
                "the far pick point follows from the step, so raising it shortens " +
                "that drop and lengthens the leg the operator has to shorten. The " +
                "document states the rule as Y2 = Y1 - Height Difference; a " +
                "positive entry means pick point 2 is higher and a negative one " +
                "means it is lower."
        step < 0 ->
            "Pick point 2 sits ${fmt(-step, 3, "m")} below pick point 1, so the " +
                "drop from the hook down to it is the longer of the two and the " +
                "leg spanning to it needs less shortening. The document's rule is " +
                "the same one, Y2 = Y1 - Height Difference, entered with a " +
                "negative value to say 'lower' rather than 'level'."
        else ->
            "Both pick points are level, so the two legs share one headroom and " +
                "the shorter span is set purely by the off-centre COG. Enter a " +
                "non-zero height difference to handle a step between the pick " +
                "points; the rule is Y2 = Y1 - Height Difference."
    }
    out += p(
        "Operational rule: to make an asymmetrical lift without tilting the load, " +
            "the crane hook must sit vertically above the centre of gravity. " +
            "Because the COG is off-centre, one side of the horizontal span is " +
            "shorter than the other. Both legs start at the same standard length, " +
            "and the longer span keeps that full length and so fixes the hook's " +
            "height. $stepProse"
    )
    out += subheading("Declared Horizontal Spans:")
    out += labelValue("Left span (X1)", fmt(span1, 3, "m"))
    out += equation(
        "\\text{X}_2 = \\text{Total Span} - \\text{X}_1 = " +
            "${texNum(totalSpan, 3)} - ${texNum(span1, 3)} = " +
            "${texNum(span2, 3)}\\ \\text{m}"
    )
    out += subheading(
        "Required Length for the Long Leg ($longLegName, Stays at Full Length):"
    )
    out += equation(
        "\\text{Length}_{$longLeg} = \\text{Standard Length} = " +
            "${texNum(standard, 3)}\\ \\text{m}"
    )
    // Whichever pick point the long leg reaches, that leg's drop comes from the
    // Pythagoras; the other pick point's drop follows from the step.  Writing the
    // pair the other way round would print an equation that does not hold for a
    // lift whose long leg is sling 2 -- and it would still look entirely ordinary,
    // because every number on it would be a real number.
    val longIs1 = longLeg == 1
    val spanLong = if (longIs1) span1 else span2
    val spanFar = if (longIs1) span2 else span1
    val dropLong = headroom
    val dropFar = if (longIs1) drop2 else drop1
    // The step is printed as a magnitude, with the direction carried by the
    // operator, so no line ever reads "6.062 - -0.500".  The sign follows from the
    // two drops rather than from the step's own sign, which is what makes it right
    // in all four combinations of which leg is long and which pick point is up.
    val stepSign = if (dropFar < dropLong) "-" else "+"
    val stepMag = texNum(abs(step), 3)
    out += subheading(
        "Required Vertical Height from the Hook to Pick Point $longLeg Level (Y$longLeg):"
    )
    out += equation(
        "\\text{Y}_{$longLeg} = \\sqrt{\\text{Length}^2 - \\text{X}_{$longLeg}^2} = " +
            "\\sqrt{${texNum(standard, 3)}^2 - ${texNum(spanLong, 3)}^2} = " +
            "${texNum(dropLong, 3)}\\ \\text{m}"
    )
    out += subheading(
        "Required Vertical Height from the Hook to Pick Point $shortLeg Level (Y$shortLeg):"
    )
    out += equation(
        "\\text{Y}_{$shortLeg} = \\text{Y}_{$longLeg} $stepSign " +
            "\\text{Height Difference} = ${texNum(dropLong, 3)} $stepSign " +
            "$stepMag = ${texNum(dropFar, 3)}\\ \\text{m}"
    )
    out += subheading(
        "Required Length for the Short Leg ($shortLegName, Adjusted Leg):"
    )
    out += equation(
        "\\text{Length}_{$shortLeg} = \\sqrt{\\text{Y}_{$shortLeg}^2 + " +
            "\\text{X}_{$shortLeg}^2} = \\sqrt{${texNum(dropFar, 3)}^2 + " +
            "${texNum(spanFar, 3)}^2} = ${texNum(shortLength, 3)}\\ \\text{m}"
    )
    if (result.needsLengthening) {
        out += subheading("Sling Lengthening Required:")
        out += p(
            "The far pick point is low enough that the leg reaching it has to be " +
                "longer than the standard sling. A clevis shortening grab hook can " +
                "only take length out of a leg, so this setup cannot be made with " +
                "the equipment on hand: use a longer sling, or reduce the step " +
                "between the pick points."
        )
        out += equation(
            "\\text{Lengthening} = \\text{Required Length} - \\text{Standard Length} = " +
                "${texNum(shortLength, 3)} - ${texNum(standard, 3)} = " +
                "${texNum(result.lengthening, 3)}\\ \\text{m}"
        )
    } else {
        out += subheading("Sling Shortening Adjustment Needed:")
        out += equation(
            "\\text{Shortening} = \\text{Standard Length} - \\text{Required Length} = " +
                "${texNum(standard, 3)} - ${texNum(shortLength, 3)} = " +
                "${texNum(result.shortening, 3)}\\ \\text{m}"
        )
    }
    out += subheading("Sling Leg Angle from Vertical:")
    out += equation(
        "\\text{Angle}_{$longLeg} = \\arcsin\\frac{\\text{X}_{$longLeg}}" +
            "{\\text{Length}_{$longLeg}} = " +
            "\\arcsin\\frac{${texNum(max(span1, span2), 3)}}" +
            "{${texNum(standard, 3)}} = " +
            fmt(if (longLeg == 1) result.angle1 else result.angle2, 2, "\u00b0")
    )
    out += equation(
        "\\text{Angle}_{$shortLeg} = \\arcsin\\frac{\\text{X}_{$shortLeg}}" +
            "{\\text{Length}_{$shortLeg}} = " +
            "\\arcsin\\frac{${texNum(min(span1, span2), 3)}}" +
            "{${texNum(shortLength, 3)}} = " +
            fmt(if (shortLeg == 1) result.angle1 else result.angle2, 2, "\u00b0")
    )
    out += table(
        listOf(
            "$longLegName angle from vertical (${
                String.format(java.util.Locale.US, "%,.3f", longSpan)
            } m span)" to fmt(if (longLeg == 1) result.angle1 else result.angle2, 2, "\u00b0"),
            "$shortLegName angle from vertical" to
                fmt(if (shortLeg == 1) result.angle1 else result.angle2, 2, "\u00b0"),
        )
    )

    out += heading(firstNumber + 2, "Rigging Factors & Tension Calculations")
    out += p(
        "The load share is a statics result rather than a leg count: each leg " +
            "carries the share set by the moment about the opposite pick point, so " +
            "the leg on the short side of the COG carries the larger share. The " +
            "angle factor then divides that share, because a leg standing further " +
            "from vertical carries more tension for the same share."
    )
    for (leg in 1..2) {
        out += subheading("Sling $leg Angle Factor (AF$leg):")
        out += equation(
            "\\text{AF}_{$leg} = \\cos(${
                fmt(if (leg == 1) result.angle1 else result.angle2, 2, "\u00b0")
            }) = ${fmt(if (leg == 1) result.angleFactor1 else result.angleFactor2, 3)}"
        )
    }
    out += subheading("Base Reeve Factor (RF):")
    out += equation("\\text{RF} = ${texNum(result.baseReeveFactor, 2)}")
    out += subheading("Load Weight Distribution to Pick Point 1:")
    out += equation(
        "\\text{Weight}_1 = \\text{Load} \\times \\frac{\\text{X}_2}" +
            "{\\text{Total Span}} = ${texNum(result.loadWeight, 1)} \\times " +
            "\\frac{${texNum(span2, 3)}}{${texNum(totalSpan, 3)}} = " +
            "${texNum(result.share1, 1)}\\ \\text{kg}"
    )
    out += subheading("Load Weight Distribution to Pick Point 2:")
    out += equation(
        "\\text{Weight}_2 = \\text{Load} \\times \\frac{\\text{X}_1}" +
            "{\\text{Total Span}} = ${texNum(result.loadWeight, 1)} \\times " +
            "\\frac{${texNum(span1, 3)}}{${texNum(totalSpan, 3)}} = " +
            "${texNum(result.share2, 1)}\\ \\text{kg}"
    )
    out += subheading(
        "Tension in Sling 1 (${if (longLeg == 1) "Longer" else "Shorter"} Leg):"
    )
    out += equation(
        "\\text{Tension}_1 = \\frac{\\text{Weight}_1}{\\text{AF}_1} = " +
            "\\frac{${texNum(result.share1, 1)}}" +
            "{${texNum(result.angleFactor1, 3)}} = " +
            "${texNum(result.tension1, 1)}\\ \\text{kg}"
    )
    out += subheading(
        "Tension in Sling 2 (${if (longLeg == 2) "Longer" else "Shorter"} Leg):"
    )
    out += equation(
        "\\text{Tension}_2 = \\frac{\\text{Weight}_2}{\\text{AF}_2} = " +
            "\\frac{${texNum(result.share2, 1)}}" +
            "{${texNum(result.angleFactor2, 3)}} = " +
            "${texNum(result.tension2, 1)}\\ \\text{kg}"
    )
    out += table(
        listOf(
            "Weight share, sling 1" to fmt(result.share1, 1, "kg"),
            "Weight share, sling 2" to fmt(result.share2, 1, "kg"),
            "Sling line tension, sling 1" to fmt(result.tension1, 1, "kg"),
            "Sling line tension, sling 2" to fmt(result.tension2, 1, "kg"),
        )
    )

    out += heading(firstNumber + 3, "Final Capacity & Equipment Selection")
    val reeve = result.baseReeveFactor
    for (leg in 1..2) {
        out += subheading("Minimum Required Sling WLL for Sling $leg (Tag Rating):")
        out += equation(
            "\\text{Min WLL}_{$leg} = \\frac{\\text{Weight}_{$leg}}" +
                "{\\text{RF} \\times \\text{AF}_{$leg}} = " +
                "\\frac{${texNum(if (leg == 1) result.share1 else result.share2, 1)}}" +
                "{${texNum(reeve, 2)} \\times " +
                "${texNum(if (leg == 1) result.angleFactor1 else result.angleFactor2, 3)}} = " +
                "${texNum(if (leg == 1) result.requiredWll1 else result.requiredWll2, 1)}" +
                "\\ \\text{kg}"
        )
    }
    out += subheading("Governing Leg:")
    out += p(
        "The governing leg is the one demanding the higher rated sling. It is the " +
            "leg on the short side of the COG, because it carries the larger share " +
            "and stands closer to vertical."
    )
    out += table(
        listOf(
            "Minimum required sling WLL, sling 1" to fmt(result.requiredWll1, 1, "kg"),
            "Minimum required sling WLL, sling 2" to fmt(result.requiredWll2, 1, "kg"),
            "Governing leg" to
                "Sling ${result.governingLeg} \u2014 ${fmt(result.requiredWll, 1, "kg")}",
        )
    )
    out += subheading("Installed Tag Rating Check:")
    var checked = false
    for (leg in 1..2) {
        val block = if (leg == 1) {
            installedTagBlock(
                tagWll = result.tagWll1,
                ratedLegs = result.ratedLegs1,
                tagAngleFactor = result.tagAngleFactor1,
                legCapacity = result.legCapacity1,
                requiredWll = result.requiredWll1,
                utilisation = result.utilisation1,
                label = "$leg",
            )
        } else {
            installedTagBlock(
                tagWll = result.tagWll2,
                ratedLegs = result.ratedLegs2,
                tagAngleFactor = result.tagAngleFactor2,
                legCapacity = result.legCapacity2,
                requiredWll = result.requiredWll2,
                utilisation = result.utilisation2,
                label = "$leg",
            )
        }
        if (block.isEmpty()) continue
        checked = true
        out += block
    }
    if (checked) {
        val note = nonuniformRatingLegsNote(
            listOf(
                Triple("Sling 1", result.slingLegs1, result.ratedLegs1),
                Triple("Sling 2", result.slingLegs2, result.ratedLegs2),
            )
        )
        if (note.isNotEmpty()) out += note
        out += table(
            listOf(
                "Sling 1 leg capacity" to
                    (result.legCapacity1?.let { fmt(it, 1, "kg") } ?: "Not entered"),
                "Sling 1 utilisation" to
                    (result.utilisation1?.let { fmt(it, 2, "%") } ?: "\u2014"),
                "Sling 2 leg capacity" to
                    (result.legCapacity2?.let { fmt(it, 1, "kg") } ?: "Not entered"),
                "Sling 2 utilisation" to
                    (result.utilisation2?.let { fmt(it, 2, "%") } ?: "\u2014"),
            )
        )
    } else {
        // Not the same statement as a pass.  Saying nothing would let a reviewer
        // read the section as complete when the check was never done.
        out += p(
            "Not calculated \u2014 enter the installed tag WLL for each leg on the " +
                "Nonuniform Load tab to complete the equipment check."
        )
    }
    out += subheading("Configured Setup SWL:")
    out += equation(
        "\\text{Setup SWL} = \\text{Load Weight} = " +
            "${texNum(result.setupSwl, 0)}\\ \\text{kg}"
    )
    return out
}

/**
 * How a governing case reads in the report's prose.
 *
 * Deliberately **not** the same words the Tandem tab uses.  The tab names the
 * case that sized a crane ("level baseline"); the report names the *angle* the
 * case was solved at ("0° level baseline"), because a sentence about angles
 * that says only "the level case" leaves the reader matching it against a figure
 * rather than against a name.  The reference keeps the two tables apart for that
 * reason, so this is a second table on purpose rather than a duplicate.
 */
private val REPORT_CASE_WORDS = mapOf(
    "level" to "0° level baseline",
    "tilt" to "declared tilt",
    "vertical" to "90° vertical rotation",
)

private fun caseWords(case: String?): String =
    REPORT_CASE_WORDS[case] ?: case.orEmpty()

/** The crane carrying the larger of the two governing WLL figures. */
private fun governingCrane(result: TandemResult): String =
    if (result.requiredWllA >= result.requiredWllB) "1" else "2"

/**
 * The two-crane tandem lift at the level and tilted cases.
 *
 * The two cases are reported as two labelled passes rather than collapsed to one,
 * because the governing case differs per crane: whichever crane is further from the
 * COG is governed by the level baseline, and the other is governed by the tilt.
 * Reporting a single pair of tensions would hide which case actually sizes each
 * crane, which is the whole reason the document solves the geometry in two steps.
 */
internal fun tandemSections(
    result: TandemResult,
    firstNumber: Int = 1,
    title: String = "Tandem Lift",
): List<String> {
    val tilt = result.tiltAngle
    val tilt0 = String.format(java.util.Locale.US, "%,.0f", tilt)
    val distA = result.distA
    val distB = result.distB
    val span = result.span
    val load = result.loadWeight
    val af = result.angleFactor
    val reeve = result.baseReeveFactor
    val angle = result.angle
    val hasVertical = result.tensionBVertical != null

    val out = mutableListOf(heading(firstNumber, title))
    out += subheading("Lift Input Parameters")
    out += table(
        listOf(
            "Total load weight" to fmt(result.loadWeight, 2, "kg"),
            "Lift configuration" to labelOrNotEntered(result.methodFullLabel),
            "Lug 2 position" to labelOrNotEntered(result.methodLug2),
            "Crane 2 projected distance, solved as" to
                labelOrNotEntered(result.methodRule),
            "Sling arrangement" to
                "Straight vertical direct hitch — both sling legs vertical, so " +
                "the angle factor is 1.000",
            "Initial horizontal distance, crane 1 hook to COG (D-1)" to
                fmt(result.distA, 3, "m"),
            "Initial horizontal distance, crane 2 hook to COG (D-2)" to
                fmt(result.distB, 3, "m"),
            "Total span between crane hooks (L)" to fmt(result.span, 3, "m"),
            "COG vertical depth below the lifting point baseline" to
                fmt(result.cogHeight, 3, "m"),
            "Cargo tilt angle from horizontal" to
                String.format(java.util.Locale.US, "%,.2f°", tilt),
            "Base reeve factor (RF)" to fmt(result.baseReeveFactor, 2),
        )
    )
    out += p(
        "The total span between the hooks is the sum of the two COG distances, " +
            "because the centre of gravity lies between the hooks on the level " +
            "baseline. The COG depth is the vertical distance from that baseline " +
            "down to the physical centre of gravity inside the cargo, and it is " +
            "what lets the tilt shift the COG sideways instead of only rotating " +
            "the load about it."
    )
    out += p(
        "The configuration decides how crane 2's distance is resolved once the " +
            "load leans: ${labelOrNotEntered(result.methodRule)} " +
            "${labelOrNotEntered(result.methodSpanBehaviour)} Maximum rotation: " +
            "${labelOrNotEntered(result.methodMaxRotation)} Use for: " +
            "${labelOrNotEntered(result.methodUseFor)}"
    )

    out += heading(
        firstNumber + 1,
        "Geometric & Headroom Calculations (Dynamic Tilt Method)",
    )
    out += p(
        "Operational rule: the geometry is solved in order. The baseline 0° " +
            "level lift fixes where the load divides between the two hooks, and " +
            "the tilt is then applied, which shifts that division because the " +
            "centre of gravity sits below the baseline joining the lifting points " +
            "rather than in it."
    )
    out += subheading("Declared Sling Leg Angles:")
    out += equation(
        "\\text{Angle}_1 = \\text{Angle}_2 = ${texNum(angle, 2)}^\\circ\\ " +
            "\\text{from vertical}"
    )
    out += subheading("New Horizontal Distance from Crane 1 to COG at $tilt0° Tilt:")
    out += equation(
        "\\text{ND}_1 = \\text{h} \\times \\sin\\theta + " +
            "\\text{D}_1 \\times \\cos\\theta = " +
            "${texNum(result.cogHeight, 3)} \\times \\sin(${texNum(tilt, 2)}^\\circ) + " +
            "${texNum(distA, 3)} \\times \\cos(${texNum(tilt, 2)}^\\circ) = " +
            "${texNum(result.distANew, 3)}\\ \\text{m}"
    )
    out += subheading("New Horizontal Distance from Crane 2 to COG at $tilt0° Tilt:")
    // The two configurations part company here and nowhere else in the geometry,
    // so the equation printed has to be the one that was solved.  Printing the
    // other method's formula would put a wrong working in front of a reader
    // checking the numbers, and would look right while being wrong.
    if (result.fullRotation) {
        out += p(
            "Lug 2 is inline with the centre of gravity, so it does not travel " +
                "inward as the load leans; only the projection shortens it."
        )
        out += equation(
            "\\text{ND}_2 = \\text{D}_2 \\times \\cos\\theta = " +
                "${texNum(distB, 3)} \\times \\cos(${texNum(tilt, 2)}^\\circ) = " +
                "${texNum(result.distBNew, 3)}\\ \\text{m}"
        )
    } else {
        out += p(
            "Lug 2 sits on the top surface, above the centre of gravity, so it " +
                "travels inward by its own height above the COG before it is " +
                "projected. The document's heading writes this as " +
                "D-2 − h×tan(θ)×cos(θ); its own working, and every figure " +
                "printed from it, take the projection over the whole of " +
                "D-2 − h×tan(θ), which is what is solved here."
        )
        out += equation(
            "\\text{ND}_2 = \\left(\\text{D}_2 - \\text{h} \\times " +
                "\\tan\\theta\\right) \\times \\cos\\theta = " +
                "\\left(${texNum(distB, 3)} - ${texNum(result.cogHeight, 3)} \\times " +
                "\\tan(${texNum(tilt, 2)}^\\circ)\\right) \\times " +
                "\\cos(${texNum(tilt, 2)}^\\circ) = " +
                "${texNum(result.distBNew, 3)}\\ \\text{m}"
        )
        out += p(
            "Lug 2's inward travel is ${fmt(result.lug2Travel, 3, "m")}, taken off " +
                "D-2 before the projection. The projected distance reaches nothing " +
                "at ${fmt(result.maxTiltAngle, 2, "°")}, which is where tan(θ) = " +
                "D-2 / h: beyond that angle the load has tipped about crane 2 " +
                "rather than hanging from both hooks, so it is a tip-over and not " +
                "a rotation to plan against."
        )
    }
    out += subheading("New Total Effective Span Between Hooks (L-new):")
    out += equation(
        "\\text{L}_{new} = \\text{ND}_1 + \\text{ND}_2 = " +
            "${texNum(result.distANew, 3)} + ${texNum(result.distBNew, 3)} = " +
            "${texNum(result.spanNew, 3)}\\ \\text{m}"
    )
    if (!result.fullRotation) {
        out += p(
            "The span closes from ${fmt(span, 3, "m")} at the level baseline to " +
                "${fmt(result.spanNew, 3, "m")} at $tilt0°, because lug 2 has " +
                "travelled inward while the COG shifted " +
                "${fmt(result.cogShift, 3, "m")} sideways. Shortening crane 2's " +
                "lever arm is what carries the load onto it."
        )
    }
    out += subheading("Required Hook Height Differential to Hold the Declared Tilt:")
    out += equation(
        "\\text{Height Difference} = \\text{Total Span} \\times \\sin\\theta = " +
            "${texNum(span, 3)} \\times \\sin(${texNum(tilt, 2)}^\\circ) = " +
            "${texNum(result.heightDifference, 3)}\\ \\text{m}"
    )

    out += heading(firstNumber + 2, "Rigging Factors & Tension Calculations")
    out += p(
        "Each hook takes the share set by the moment about the other hook, so the " +
            "crane nearer the COG carries the larger share. The angle factor then " +
            "divides that share to give the sling line tension."
    )
    out += subheading("Sling Angle Factor (AF-1 and AF-2):")
    out += equation(
        "\\text{AF}_1 = \\text{AF}_2 = \\cos(${texNum(angle, 2)}^\\circ) = " +
            "${fmt(result.angleFactor, 3)}"
    )
    out += subheading("Base Reeve Factor (RF):")
    out += equation("\\text{RF} = ${texNum(reeve, 2)}")
    out += subheading("Case A — Weight Distribution on Crane 1 (0° Level):")
    out += equation(
        "\\text{Weight}_1 = \\text{Load} \\times \\frac{\\text{D}_2}" +
            "{\\text{L}} = ${texNum(load, 1)} \\times " +
            "\\frac{${texNum(distB, 3)}}{${texNum(span, 3)}} = " +
            "${texNum(result.shareALevel, 1)}\\ \\text{kg}"
    )
    out += subheading("Case A — Weight Distribution on Crane 2 (0° Level):")
    out += equation(
        "\\text{Weight}_2 = \\text{Load} \\times \\frac{\\text{D}_1}" +
            "{\\text{L}} = ${texNum(load, 1)} \\times " +
            "\\frac{${texNum(distA, 3)}}{${texNum(span, 3)}} = " +
            "${texNum(result.shareBLevel, 1)}\\ \\text{kg}"
    )
    out += subheading("Case A — Sling Line Tension at 0° Level Lift:")
    out += equation(
        "\\text{Tension}_1 = \\frac{\\text{Weight}_1}{\\text{AF}_1} = " +
            "\\frac{${texNum(result.shareALevel, 1)}}{${texNum(af, 3)}} = " +
            "${texNum(result.tensionALevel, 1)}\\ \\text{kg}"
    )
    out += equation(
        "\\text{Tension}_2 = \\frac{\\text{Weight}_2}{\\text{AF}_2} = " +
            "\\frac{${texNum(result.shareBLevel, 1)}}{${texNum(af, 3)}} = " +
            "${texNum(result.tensionBLevel, 1)}\\ \\text{kg}"
    )
    out += subheading("Case B — Dynamic Weight Share on Crane 1 ($tilt0° Tilt):")
    out += equation(
        "\\text{Weight}_1 = \\text{Load} \\times " +
            "\\frac{\\text{ND}_2}{\\text{L}_{new}} = ${texNum(load, 1)} \\times " +
            "\\frac{${texNum(result.distBNew, 3)}}{${texNum(result.spanNew, 3)}} = " +
            "${texNum(result.shareATilt, 1)}\\ \\text{kg}"
    )
    out += subheading("Case B — Dynamic Weight Share on Crane 2 ($tilt0° Tilt):")
    out += equation(
        "\\text{Weight}_2 = \\text{Load} \\times " +
            "\\frac{\\text{ND}_1}{\\text{L}_{new}} = ${texNum(load, 1)} \\times " +
            "\\frac{${texNum(result.distANew, 3)}}{${texNum(result.spanNew, 3)}} = " +
            "${texNum(result.shareBTilt, 1)}\\ \\text{kg}"
    )
    out += subheading("Case B — Sling Line Tension at $tilt0° Dynamic Slant Lift:")
    out += equation(
        "\\text{Tension}_1 = \\frac{\\text{Weight}_1}{\\text{AF}_1} = " +
            "\\frac{${texNum(result.shareATilt, 1)}}{${texNum(af, 3)}} = " +
            "${texNum(result.tensionATilt, 1)}\\ \\text{kg}"
    )
    out += equation(
        "\\text{Tension}_2 = \\frac{\\text{Weight}_2}{\\text{AF}_2} = " +
            "\\frac{${texNum(result.shareBTilt, 1)}}{${texNum(af, 3)}} = " +
            "${texNum(result.tensionBTilt, 1)}\\ \\text{kg}"
    )
    val summaryRows = mutableListOf(
        "Tension, crane 1 (level)" to fmt(result.tensionALevel, 1, "kg"),
        "Tension, crane 2 (level)" to fmt(result.tensionBLevel, 1, "kg"),
        "Tension, crane 1 (tilted)" to fmt(result.tensionATilt, 1, "kg"),
        "Tension, crane 2 (tilted)" to fmt(result.tensionBTilt, 1, "kg"),
    )
    if (hasVertical) {
        // Only the aligned pivot method can be rotated to vertical, and when it is,
        // the whole load ends up on crane 2.  That case has to be worked here, not
        // merely mentioned: on this method it is the case that sizes crane 2's
        // sling, so a reader who skipped it would pick a sling too small by half.
        out += subheading("Case C — Weight Distribution at 90° Vertical Rotation:")
        out += p(
            "Lug 2 sits under the COG on the level baseline, so the load can be " +
                "rotated all the way to vertical without either hook passing under " +
                "the COG. At 90° the COG is above crane 2's hook and beside crane " +
                "1's, which puts the whole weight on crane 2 and none on crane 1."
        )
        out += equation(
            "\\text{Weight}_1 = 0\\ \\text{kg}, \\qquad " +
                "\\text{Weight}_2 = \\text{Load} = ${texNum(load, 1)}\\ \\text{kg}"
        )
        out += equation(
            "\\text{Tension}_1 = 0\\ \\text{kg}, \\qquad " +
                "\\text{Tension}_2 = \\frac{\\text{Weight}_2}{\\text{AF}_2} = " +
                "\\frac{${texNum(result.shareBVertical, 1)}}{${texNum(af, 3)}} = " +
                "${texNum(result.tensionBVertical, 1)}\\ \\text{kg}"
        )
        summaryRows += "Tension, crane 1 (90° vertical)" to
            fmt(result.tensionAVertical, 1, "kg")
        summaryRows += "Tension, crane 2 (90° vertical)" to
            fmt(result.tensionBVertical, 1, "kg")
    } else {
        out += p(
            "This configuration cannot be rotated to vertical: the span reaches " +
                "nothing at ${fmt(result.maxTiltAngle, 2, "°")}, so there is no " +
                "90° case to rate. The two cases above are the whole of it."
        )
    }
    out += table(summaryRows)

    out += heading(firstNumber + 3, "Final Capacity & Equipment Selection")
    out += p(
        "Each crane must carry the highest of its own case tensions. The " +
            "governing case is not the same crane in either direction, so each " +
            "crane is quoted against the case that sizes it."
    )
    val rfAf = "${texNum(reeve, 2)} \\times ${texNum(af, 3)}"
    for ((tag, letter) in listOf("a" to "1", "b" to "2")) {
        val required = if (tag == "a") result.requiredWllA else result.requiredWllB
        val governing = caseWords(if (tag == "a") result.governingCaseA else result.governingCaseB)
        // The case subscripts are assembled outside the equation: a LaTeX escape
        // cannot sit inside a Kotlin template without doubling, and the thin spaces
        // and degree signs here are exactly what needs it.
        val caseLevel = "$letter,\\,0^\\circ"
        val caseTilt = "$letter,\\,$tilt0^\\circ"
        val caseVert = "$letter,\\,90^\\circ"
        val terms = mutableListOf(
            "\\frac{\\text{Weight}_{$caseLevel}}{$rfAf}",
            "\\frac{\\text{Weight}_{$caseTilt}}{$rfAf}",
        )
        val shareLevel = if (tag == "a") result.shareALevel else result.shareBLevel
        val shareTilt = if (tag == "a") result.shareATilt else result.shareBTilt
        val values = mutableListOf(
            "\\frac{${texNum(shareLevel, 1)}}{$rfAf}",
            "\\frac{${texNum(shareTilt, 1)}}{$rfAf}",
        )
        if (hasVertical) {
            terms += "\\frac{\\text{Weight}_{$caseVert}}{$rfAf}"
            val shareVertical = if (tag == "a") {
                result.shareAVertical ?: 0.0
            } else {
                result.shareBVertical ?: 0.0
            }
            values += "\\frac{${texNum(shareVertical, 1)}}{$rfAf}"
        }
        out += subheading(
            "Minimum Required Sling WLL for Crane $letter " +
                "(${if (tag == "a") "Left" else "Right"} Hook Path):"
        )
        out += equation(
            "\\text{Min WLL}_{$letter} = " +
                "\\max\\left(${terms.joinToString(", ")}\\right) = " +
                "\\max\\left(${values.joinToString(", ")}\\right) = " +
                "${texNum(required, 1)}\\ \\text{kg}"
        )
        out += p(
            "Governed by the highest value: the <b>$governing</b> case governs " +
                "crane $letter, requiring ${fmt(required, 1, "kg")}."
        )
    }
    out += subheading("Governing Case Summary:")
    out += table(
        listOf(
            "Minimum required sling WLL, crane 1" to
                "${fmt(result.requiredWllA, 1, "kg")} — governed by the " +
                "${caseWords(result.governingCaseA)} case",
            "Minimum required sling WLL, crane 2" to
                "${fmt(result.requiredWllB, 1, "kg")} — governed by the " +
                "${caseWords(result.governingCaseB)} case",
            "Governing crane" to
                "Crane ${governingCrane(result)} — ${fmt(result.requiredWll, 1, "kg")}",
        )
    )
    out += subheading("Installed Sling Tag Rating Check:")
    var checked = false
    for ((tag, letter) in listOf("a" to "1", "b" to "2")) {
        val block = if (tag == "a") {
            installedTagBlock(
                tagWll = result.tagWllA,
                ratedLegs = result.ratedLegsA,
                tagAngleFactor = result.tagAngleFactorA,
                legCapacity = result.legCapacityA,
                requiredWll = result.requiredWllA,
                utilisation = result.utilisationA,
                label = letter,
            )
        } else {
            installedTagBlock(
                tagWll = result.tagWllB,
                ratedLegs = result.ratedLegsB,
                tagAngleFactor = result.tagAngleFactorB,
                legCapacity = result.legCapacityB,
                requiredWll = result.requiredWllB,
                utilisation = result.utilisationB,
                label = letter,
            )
        }
        if (block.isEmpty()) continue
        checked = true
        out += block
    }
    if (checked) {
        val note = nonuniformRatingLegsNote(
            listOf(
                Triple("Crane 1", result.slingLegsA, result.ratedLegsA),
                Triple("Crane 2", result.slingLegsB, result.ratedLegsB),
            )
        )
        if (note.isNotEmpty()) out += note
    } else {
        out += p(
            "Not calculated — enter each crane's installed sling tag WLL on " +
                "the Tandem tab to complete the equipment check."
        )
    }
    out += subheading("Crane Rated Capacity Check (75% Allowable):")
    out += p(
        "The rated capacity is the figure read from the crane's own load chart " +
            "at the working radius, and it is the baseline this check is made " +
            "against. Usage is the share of that charted capacity the hook path " +
            "takes, so it is the demand over the charted figure and nothing else. " +
            "At or below ${texNum(CRANE_USAGE_RATIO * 100, 0)}% the crane is " +
            "within its allowable window; above the charted capacity it is over it."
    )
    var rated = false
    for ((tag, letter) in listOf("a" to "1", "b" to "2")) {
        val usage = if (tag == "a") result.craneUsageA else result.craneUsageB
        if (usage == null) continue
        rated = true
        val craneRated = if (tag == "a") result.craneRatedA else result.craneRatedB
        val required = if (tag == "a") result.requiredWllA else result.requiredWllB
        out += equation(
            "\\text{Usage}_{$letter} = \\frac{\\text{Min WLL}_{$letter}}" +
                "{\\text{Rated Capacity}_{$letter}} \\times 100\\% = " +
                "\\frac{${texNum(required, 1)}}{${texNum(craneRated, 1)}} " +
                "\\times 100\\% = ${texNum(usage, 2)}\\%"
        )
        // The band is stated in the Crane tab's own words, from the one classifier
        // both tabs read, so a reviewer comparing the two screens finds the same
        // verdict on the same figure.
        val state = craneUsageBand(usage)
        // The rated capacity is deliberately not repeated in this sentence: it is
        // the denominator of the equation directly above, and stating it twice makes
        // the reader check that the two agree.
        val tail = when (state) {
            UsageState.WITHIN -> "."
            UsageState.CAUTION ->
                " – above the 75% allowable window, so the charted figure has " +
                    "to be re-checked at the working radius before the lift."
            UsageState.OVER ->
                " – over its charted capacity, so the hook path has to be " +
                    "re-planned against a crane of greater charted capacity at " +
                    "this radius."
        }
        out += p(
            "Crane $letter is <b>${state.wording}</b> at ${fmt(usage, 2, "%")} of " +
                "its charted capacity$tail"
        )
    }
    if (!rated) {
        out += p(
            "Not calculated — enter each crane's load chart capacity on the " +
                "Tandem tab to complete the 75% allowable check. A tandem lift is " +
                "carried by two independent cranes, so each hook path is checked " +
                "against its own booked rating."
        )
    }
    out += subheading("Configured Setup SWL:")
    out += equation(
        "\\text{Setup SWL} = \\text{Total Load Weight} = " +
            "${texNum(result.setupSwl, 0)}\\ \\text{kg}"
    )

    // The document closes with a comparison table.  It is the one place the shift
    // the tilt causes is stated as a single before-and-after, so a reviewer can see
    // the transfer from crane 1 to crane 2 at a glance.
    out += heading(firstNumber + 4, "Note")
    out += subheading("Effect of the Declared Tilt on Each Hook")
    out += p(
        "The same lift at the two declared load angles, for comparison. The level " +
            "baseline is the more onerous case for crane 1 and the tilt is the more " +
            "onerous case for crane 2; sizing either crane from the other case " +
            "would understate it."
    )
    out += "<table><thead><tr><th>Load angle (tilt)</th>" +
        "<th>Crane 1 tension (left side)</th>" +
        "<th>Crane 2 tension (right side)</th>" +
        "<th>Hook height differential</th></tr></thead><tbody>"
    out += "<tr><td>0° (level lift)</td>" +
        "<td>${fmt(result.tensionALevel / 1000.0, 2, "t")}</td>" +
        "<td>${fmt(result.tensionBLevel / 1000.0, 2, "t")}</td>" +
        "<td>0.000 m (level)</td></tr>"
    out += "<tr><td>$tilt0° (slanted lift)</td>" +
        "<td>${fmt(result.tensionATilt / 1000.0, 2, "t")}</td>" +
        "<td>${fmt(result.tensionBTilt / 1000.0, 2, "t")}</td>" +
        "<td>${fmt(result.heightDifference, 3, "m")} (crane 1 higher)</td></tr>"
    out += "</tbody></table>"
    val transfer = abs(result.shareBTilt - result.shareBLevel)
    if (result.fullRotation) {
        out += p(
            "Declared tilt direction: crane 1 lifts faster, so the cargo slants " +
                "downwards towards crane 2. That transfers ${fmt(transfer, 1, "kg")} " +
                "onto crane 2 and releases the same weight from crane 1. The hooks " +
                "must finish ${fmt(result.heightDifference, 3, "m")} apart in " +
                "height to hold the declared tilt. On this configuration the " +
                "transfer continues if the rotation is carried past $tilt0°: at " +
                "90° the whole ${fmt(result.loadWeight, 0, "kg")} sits on crane 2."
        )
    } else {
        out += p(
            "Declared tilt direction: crane 1 lifts faster, so the cargo slants " +
                "downwards towards crane 2. That transfers ${fmt(transfer, 1, "kg")} " +
                "onto crane 2 and releases the same weight from crane 1, and the " +
                "span closing by ${fmt(span - result.spanNew, 3, "m")} does the same " +
                "thing by shortening crane 2's lever arm. The hooks must finish " +
                "${fmt(result.heightDifference, 3, "m")} apart in height to hold " +
                "the declared tilt."
        )
    }
    return out
}
