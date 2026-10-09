package com.zhafrisbs.liftingplancalculator.core

import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The uniform-lift report sections, ported from the same functions in
 * `lifting_calculator/report_1leg.py`.
 *
 * Two of the reference's reported bugs are visible in this file, and both are kept
 * fixed because the fix *is* the reference's — the comments naming them are its own:
 *
 *  * **The choke-to-load height prints its derivation.**  It used to print only the
 *    answer, `Height = 0.077 m`, while every other height in the same section showed
 *    its formula and substituted values — so the one figure a reader is most likely
 *    to want to check was the one they could not.
 *  * **The basket height's substituted values cover every term of its formula.**
 *    The `- Radius` (or `- Side`) was in the formula and absent from the arithmetic,
 *    so the sum printed beside the answer did not produce it: 3.715 × 0.915 × 0.991
 *    is 3.369, not the 2.869 shown, and the missing 0.500 was the difference.
 */

// ---------------------------------------------------------------------------
// The nested systems' per-group labels
// ---------------------------------------------------------------------------

/**
 * One transverse leg group of a nested arrangement.
 *
 * The nested reference documents report one row per group, and the four fields are
 * the four the documents' own table carries:
 *
 *  * [name] heads the combined-factor row -- "AF for **Outer** Secondary Legs".
 *  * [caption] names the group in the geometry section's angle heading.
 *  * [token] is the equation subscript, which is what a reader matches against the
 *    factor printed above it.
 *  * [phrase] names the group in the tension heading, which is a sentence rather
 *    than a label: "Tension in **the Sloped Outer Secondary Legs**".
 */
internal data class SecondaryLabel(
    val name: String,
    val caption: String,
    val token: String,
    val phrase: String,
)

private val SECONDARY_GROUPS: Map<Int, List<SecondaryLabel>> = mapOf(
    3 to listOf(
        SecondaryLabel(
            name = "Outer",
            caption = "Outer Secondary Legs 1 & 3",
            token = "Outer",
            phrase = "the Sloped Outer Secondary Legs",
        ),
        SecondaryLabel(
            name = "Middle",
            caption = "Middle Secondary Leg 2",
            token = "Mid",
            phrase = "the Straight Middle Secondary Legs",
        ),
    ),
    4 to listOf(
        SecondaryLabel(
            name = "Outer",
            caption = "Outer Secondary Legs 1 & 4",
            token = "Outer",
            phrase = "the Sloped Outer Secondary Legs (Piles 1 & 4)",
        ),
        SecondaryLabel(
            name = "Inner",
            caption = "Inner Secondary Legs 2 & 3",
            token = "Inner",
            phrase = "the Sloped Inner Secondary Legs (Piles 2 & 3)",
        ),
    ),
)

/** The nested documents state the total headroom with these captions. */
private val NESTED_HEIGHT_CAPTION: Map<Int, String> = mapOf(
    2 to "Total Vertical Height (Hook to Top of RC Piles Headroom):",
    3 to "Total Vertical Height (Hook to Top of RC Piles Headroom):",
    4 to "Total Vertical Height (Hook to Top of RC Piles):",
)

/**
 * The per-group labels for a nested arrangement, outermost first.
 *
 * Empty when the group count does not match the arrangement's caption list, which
 * is the reference's own guard: a mismatch means the two disagree about how many
 * groups there are, and printing the captions anyway would label a row with the
 * wrong leg.
 */
internal fun secondaryLabels(
    result: SlingResult,
    nested: Int,
): List<Pair<SecondaryGroup, SecondaryLabel>> {
    val groups = result.secondaryGroups.orEmpty()
    val labels = SECONDARY_GROUPS[nested].orEmpty()
    if (groups.size != labels.size || groups.size < 2) return emptyList()
    return groups.zip(labels)
}

// ---------------------------------------------------------------------------
// Section 1 -- lift input parameters
// ---------------------------------------------------------------------------

/** How the arrangement reads as a sling configuration. */
internal fun configurationLabel(spec: HitchSpec): String {
    val legs = "${spec.effectiveLegs} Effective Leg" +
        if (spec.effectiveLegs != 1) "s" else ""
    return if (spec.legCount > 1) {
        "${spec.label} Hitch ($legs)"
    } else {
        "1-Leg Sling ${spec.label} Hitch ($legs)"
    }
}

/**
 * The rows describing the load or the pick points.
 *
 * The reference documents state one decimal place for these lengths, and the 4-leg
 * bridle states its pick-point rectangle as two separate rows.
 */
internal fun dimensionRows(result: SlingResult, spec: HitchSpec): List<Pair<String, String>> {
    if (spec.roundLoad) {
        val diameter = result.diameter
        return listOf(
            "Load Dimensions" to
                "Round (Diameter = ${plain(diameter, 1)} m / " +
                "Radius = ${plain(diameter / 2.0, 1)} m)"
        )
    }
    if (spec.rectangularLoad) {
        return listOf(
            "Load Dimensions" to
                "Rectangular (Side = ${plain(result.side, 1)} m, " +
                "Bottom = ${plain(result.bottom, 1)} m)"
        )
    }
    if (spec.shape == "bridle3") {
        return listOf(
            "Distance Between Pick Points (Uniform)" to
                "${plain(result.pickDistance, 1)} m"
        )
    }
    if (spec.shape == "bridle4") {
        return listOf(
            "Distance Between Pick Points (Length)" to
                "${plain(result.pickLength, 1)} m",
            "Distance Between Pick Points (Width)" to
                "${plain(result.pickWidth, 1)} m",
        )
    }
    if (spec.nested != 0) {
        return listOf("Load Dimensions" to "Not required for the nested sling system")
    }
    return listOf("Load Dimensions" to "Not required for a direct vertical hitch")
}

/** Section 1: what the sling is and what it is carrying. */
internal fun sectionInputs(result: SlingResult, spec: HitchSpec): String {
    val weight = result.loadWeight
    val length = result.slingLength
    val parts = mutableListOf(heading(1, "Lift Input Parameters"))
    val rows = mutableListOf(
        "Load Weight" to fmt(weight, 1, "kg"),
        "Sling Configuration" to configurationLabel(spec),
        "Sling assembly count" to when {
            spec.bridle -> "1 bridle sling assembly"
            spec.legCount == 1 -> "1 continuous sling"
            else -> "${spec.legCount}-sling bridle set"
        },
        "Sling Total Length" to fmt(length, 1, "m"),
    )
    rows += dimensionRows(result, spec)
    if (spec.needsPickDistance && !spec.bridle) {
        rows += "Distance Between Pick Points" to fmt(result.pickDistance, 1, "m")
    }
    if (result.nested != 0) {
        // A secondary system of more than two inline legs is described by its pile
        // count, as in the reference documents.
        val piles = result.secondaryLegs ?: 0
        if (piles > 2) rows += "No of Piles" to piles.toString()
        rows += "Second Sling Total Length" to fmt(result.secondaryLength, 3, "m")
        rows += "Secondary Pick Spacing" to fmt(result.secondarySpread, 3, "m")
        if (piles > 2) rows += "Sling Legs per Pile" to "2"
    }
    if (spec.needsAngle) {
        val internal = result.internalAngle
        val external = result.externalAngle
        if (result.angleBasis == ANGLE_BASIS_EXTERNAL) {
            rows += "Choke Angle (External)" to
                "${fmt(external, 0, "\u00b0")} - from hook to the load"
        } else {
            rows += "Choke Internal Angle" to
                "${fmt(internal, 0, "\u00b0")} from vertical"
        }
    }
    parts += table(rows)
    parts += p(
        "Load Weight is the gross load at hook. The 75% allowable window applies " +
            "to the crane load chart only and is shown in the crane section."
    )
    return parts.joinToString("")
}

// ---------------------------------------------------------------------------
// Section 2 -- geometry and headroom
// ---------------------------------------------------------------------------

/**
 * The choke-to-top-of-load height, stating its derivation.
 *
 * The formulas here are the solver's own: `radius / sin(internal) - radius` for a
 * round choke and `(bottom / 2) / tan(internal)` for a rectangular one.  The
 * external angle below 90 degrees is the case where the rule declares the choke
 * flush against the load, and saying that is clearer than a bare zero.
 */
private fun chokeHeightEquation(
    key: String,
    result: SlingResult,
    gap: Double,
    internal: Double,
    external: Double,
): String {
    if (external < 90.0) {
        return equation(
            "\\text{Height}_{Choke-Load} = 0.000\\ \\text{m}\\quad" +
                "(\\text{external angle} < 90^\\circ\\text{, choke flush to the load})"
        )
    }
    if (key == "two_leg_round_choke") {
        val radius = result.diameter / 2.0
        return equation(
            "\\text{Height}_{Choke-Load} = \\frac{\\text{Radius}}{\\sin(\\theta)}" +
                " - \\text{Radius} = " +
                "\\frac{${texNum(radius, 3)}}" +
                "{\\sin(${texNum(internal, 0)}^\\circ)} - " +
                "${texNum(radius, 3)} = ${texNum(gap, 3)}\\ \\text{m}"
        )
    }
    val bottom = result.bottom
    return equation(
        "\\text{Height}_{Choke-Load} = \\frac{\\text{Bottom}/2}{\\tan(\\theta)} = " +
            "\\frac{${texNum(bottom / 2.0, 3)}}" +
            "{\\tan(${texNum(internal, 0)}^\\circ)} = ${texNum(gap, 3)}\\ \\text{m}"
    )
}

/** Section 2: the wrap, the free leg, the sling angle and the headroom. */
internal fun geometrySection(result: SlingResult, spec: HitchSpec): String {
    val key = spec.key
    val length = result.slingLength
    val wrap = result.wrapLength
    val free = result.freeLegLength
    val angle = result.angle
    val af = result.angleFactor
    val headroom = result.hookToLoad
    val out = mutableListOf(heading(2, "Geometric & Headroom Calculations"))

    // --- The bridles -------------------------------------------------------
    if (key == "three_leg_direct" || key == "four_leg_direct") {
        val radiusSpread: Double
        val radiusLatex: String
        if (key == "three_leg_direct") {
            val pickDistance = result.pickDistance
            radiusSpread = pickDistance / sqrt(3.0)
            radiusLatex = "\\text{Radius Spread} = \\frac{\\text{Pick Distance}}" +
                "{\\sqrt{3}} = \\frac{${texNum(pickDistance, 3)}}" +
                "{\\sqrt{3}} = ${texNum(radiusSpread, 3)}\\ \\text{m}"
        } else {
            val pickLength = result.pickLength
            val pickWidth = result.pickWidth
            radiusSpread = hypot(pickLength / 2.0, pickWidth / 2.0)
            radiusLatex = "\\text{Radius Spread} = " +
                "\\sqrt{\\left(\\frac{\\text{Length}}{2}\\right)^2" +
                " + \\left(\\frac{\\text{Width}}{2}\\right)^2} = " +
                "\\sqrt{\\left(\\frac{${texNum(pickLength, 1)}}{2}\\right)^2" +
                " + \\left(\\frac{${texNum(pickWidth, 1)}}{2}\\right)^2} " +
                "= ${texNum(radiusSpread, 3)}\\ \\text{m}"
        }
        out += subheading("Centroid Radius (Horizontal Distance from Center to Any Pick Point):")
        out += equation(radiusLatex)
        out += subheading("Sling Contact Perimeter Wrap:")
        out += equation("\\text{Wrap Length} = 0.000\\ \\text{m}")
        // The 4-leg reference document names the standing sloped line and calls out
        // the available headroom explicitly.
        out += subheading(
            if (key == "four_leg_direct") {
                "Free Leg Length (Standing Sloped Line per Sling):"
            } else {
                "Free Leg Length:"
            }
        )
        out += equation(
            "\\text{Free Leg Length} = \\text{Total Length} - \\text{Wrap Length} = " +
                "${texNum(length, 1)} - 0.000 = ${texNum(free, 3)}\\ \\text{m}"
        )
        out += subheading("Sling Leg Angle from Vertical:")
        out += equation(
            "\\text{Angle} = \\sin^{-1}\\left(\\frac{\\text{Radius Spread}}" +
                "{\\text{Free Leg Length}}\\right) = " +
                "\\sin^{-1}\\left(\\frac{${texNum(radiusSpread, 3)}}" +
                "{${texNum(free, 3)}}\\right) = ${texDeg(angle)}"
        )
        out += subheading(
            if (key == "four_leg_direct") {
                "Vertical Height (Hook to Top of Load Headroom):"
            } else {
                "Vertical Height (Hook to Top of Load):"
            }
        )
        out += equation(
            "\\text{Height}_{Hook-Load} = \\text{Free Leg Length} \\times " +
                "\\cos(${texDeg(angle)}) = ${texNum(free, 3)} \\times " +
                "${texNum(af, 3)} = ${texNum(headroom, 3)}\\ \\text{m}"
        )
        return out.joinToString("")
    }

    // --- The nested systems ------------------------------------------------
    if (result.nested != 0) {
        val nestedN = result.nested
        val secLen = result.secondaryLength
        val secHeight = result.secondaryHeight ?: 0.0
        // The height is the vertical projection of the governing (outermost) leg, so
        // it carries that group's own angle.
        val secHeightAngle = result.secondaryHeightAngle ?: result.secondaryAngle ?: 0.0
        val labelled = secondaryLabels(result, nestedN)
        val heightToken = if (labelled.size > 1) "Outer" else ""
        val heightLatex =
            "\\text{Height}_{Sling2} = \\text{Free Leg Length}_2 \\times " +
                (if (heightToken.isNotEmpty()) {
                    "\\cos(\\text{Angle}_{Trans\\,Outer})"
                } else {
                    "\\cos(\\text{Angle}_{Trans})"
                }) + " = " +
                "${texNum(secLen, 3)} \\times " +
                "${texNum(kotlin.math.cos(Math.toRadians(secHeightAngle)), 3)} = " +
                "${texNum(secHeight, 3)}\\ \\text{m}"

        out += subheading("Sling Contact Perimeter Wrap:")
        out += equation("\\text{Wrap Length} = 0.000\\ \\text{m}")
        out += subheading("Free Leg Length (First / Master Sling):")
        out += equation("\\text{Free Leg Length}_1 = ${texNum(free, 3)}\\ \\text{m}")
        out += subheading("Free Leg Length (Second / Secondary Sling):")
        out += equation(
            "\\text{Free Leg Length}_2 = ${texNum(result.secondaryLength, 3)}\\ \\text{m}"
        )

        if (labelled.isNotEmpty()) {
            // The 3- and 4-leg nested documents state one transverse angle per leg
            // group, outermost first.
            for ((group, label) in labelled) {
                out += subheading(
                    "Transverse Sling Leg Angle from Vertical (${label.caption}):"
                )
                out += equation(
                    "\\text{Angle}_{\\text{Trans ${label.token}}} = " +
                        "\\sin^{-1}\\left(\\frac{\\text{Offset}}" +
                        "{\\text{Free Leg Length}_2}\\right) = " +
                        "\\sin^{-1}\\left(\\frac{${texNum(group.offset, 3)}}" +
                        "{${texNum(secLen, 3)}}\\right) = ${texDeg(group.angle)}"
                )
            }
        } else {
            out += subheading(
                "Transverse Sling Leg Angle from Vertical (" +
                    if (nestedN == 2) "Second Sling" else "Outer Secondary Legs" + "):"
            )
            out += equation(
                "\\text{Angle}_{Trans} = \\sin^{-1}\\left(\\frac{\\text{Offset}}" +
                    "{\\text{Free Leg Length}_2}\\right) = " +
                    "\\sin^{-1}\\left(\\frac{" +
                    "${texNum(result.secondaryLength * sin(Math.toRadians(result.secondaryAngle ?: 0.0)), 3)}" +
                    "}{${texNum(result.secondaryLength, 3)}}\\right) = " +
                    "${texDeg(result.secondaryAngle)}"
            )
        }

        if (nestedN == 4) {
            val innerOffset = labelled.lastOrNull()?.first?.offset ?: 0.0
            val outerOffset = labelled.firstOrNull()?.first?.offset ?: 0.0
            out += subheading("Rigging Arrangement & Pile Level Tolerance:")
            out += p(
                "There are 4 piles in a row, so the master link hangs between Pile 2 " +
                    "and Pile 3 and there is no vertical centre leg. The 4 secondary " +
                    "legs form two pairs: an inner pair at a ${
                        String.format(java.util.Locale.US, "%.1f", innerOffset)
                    } m offset (Piles 2 & 3) and an outer pair at a ${
                        String.format(java.util.Locale.US, "%.1f", outerOffset)
                    } m offset (Piles 1 & 4). Every leg is cut to the same free length " +
                    "and all four terminate on that one master link, so the pile tops " +
                    "cannot be level: the outer piles must stand higher than the " +
                    "inner piles by the amount derived below. That difference is what " +
                    "lets one leg length serve all four legs. The outer pair is the " +
                    "reference for the secondary height, because it carries the " +
                    "highest tension and gives the lowest angle factor, so it is the " +
                    "conservative case for the master legs."
            )
        } else if (nestedN == 3) {
            val outerOffset = labelled.firstOrNull()?.first?.offset ?: 0.0
            out += subheading("Rigging Arrangement & Pile Level Tolerance:")
            out += p(
                "There are 3 piles in a row, so the master link hangs over the middle " +
                    "pile. The middle secondary leg is vertical and the outer pair " +
                    "slopes at a ${
                        String.format(java.util.Locale.US, "%.1f", outerOffset)
                    } m offset (Piles 1 & 3). All three legs are cut to the same free " +
                    "length and all three terminate on that one master link, so the " +
                    "pile tops cannot be level: the outer piles must stand higher than " +
                    "the middle pile by the amount derived below. The outer pair is " +
                    "the reference for the secondary height, because it carries the " +
                    "highest tension and gives the lowest angle factor."
            )
        } else {
            out += subheading("Rigging Arrangement:")
            out += p(
                "A 2-leg master bridle splits into two 2-leg secondary slings. All " +
                    "four legs reach the one master link at the same offset either " +
                    "side of it, so all four pile tops are level and the secondary " +
                    "height is the same above every pile."
            )
        }

        val governingPiles = when (nestedN) {
            3 -> "Piles 1 & 3"
            4 -> "Piles 1 & 4"
            else -> null
        }
        var heightCaption = "Sling 2 Vertical Height (Top of Load to Master Link)"
        if (governingPiles != null) {
            heightCaption += ": (Governed by the outer sloped leg, $governingPiles, " +
                "the highest piles)"
        }
        out += subheading(heightCaption)
        out += equation(heightLatex)

        val levelGap = result.pileLevelDifference ?: 0.0
        if (levelGap > 0.0 && labelled.size > 1) {
            val innerAf = labelled.last().first.af
            val outerAf = labelled.first().first.af
            val innerToken = if (nestedN == 3) "Mid" else "Inner"
            out += subheading(
                "Required Pile Level Difference (all legs cut to the same free length):"
            )
            out += equation(
                "\\text{Level Difference} = \\text{Free Leg Length}_2 \\times " +
                    "\\left(\\cos(\\text{Angle}_{Trans\\,$innerToken}) - " +
                    "\\cos(\\text{Angle}_{Trans\\,Outer})\\right) = " +
                    "${texNum(secLen, 3)} \\times " +
                    "(${texNum(innerAf, 3)} - ${texNum(outerAf, 3)}) = " +
                    "${texNum(levelGap, 3)}\\ \\text{m}"
            )
        }

        out += subheading("Total Combined Length for Longitudinal Calculation:")
        out += equation(
            "\\text{Total Combined} = \\text{Free Leg Length}_1 + " +
                "\\text{Height}_{Sling2} = " +
                "${texNum(free, 3)} + ${texNum(result.secondaryHeight, 3)} = " +
                "${texNum(result.totalCombinedLength, 3)}\\ \\text{m}"
        )
        out += subheading("Longitudinal Sling Leg Angle from Vertical:")
        out += equation(
            "\\text{Angle}_{Long} = \\sin^{-1}\\left(\\frac{\\text{Master Spread}/2}" +
                "{\\text{Total Combined}}\\right) = " +
                "\\sin^{-1}\\left(\\frac{${texNum(result.pickDistance / 2.0, 3)}}" +
                "{${texNum(result.totalCombinedLength, 3)}}\\right) = ${texDeg(angle)}"
        )
        out += subheading(
            NESTED_HEIGHT_CAPTION[nestedN]
                ?: "Total Vertical Height (Hook to Top of Load):"
        )
        out += equation(
            "\\text{Height}_{Hook-Load} = \\text{Total Combined} \\times " +
                "\\cos(${texDeg(angle)}) = ${texNum(result.totalCombinedLength, 3)} " +
                "\\times ${texNum(af, 3)} = ${texNum(headroom, 3)}\\ \\text{m}"
        )
        return out.joinToString("")
    }

    // --- The 2-leg direct bridle -------------------------------------------
    if (key == "two_leg_direct") {
        val pick = result.pickDistance
        out += subheading("Sling Contact Perimeter Wrap:")
        out += equation("\\text{Wrap Length} = 0.000\\ \\text{m}")
        out += subheading("Free Leg Length (Each Side):")
        out += equation(
            "\\text{Free Leg Length} = \\text{Total Length} - \\text{Wrap Length} = " +
                "${texNum(length, 1)} - 0.000 = ${texNum(free, 3)}\\ \\text{m}"
        )
        out += subheading("Sling Leg Angle from Vertical:")
        out += equation(
            "\\text{Angle} = \\sin^{-1}\\left(\\frac{\\text{Distance}/2}" +
                "{\\text{Free Leg Length}}\\right) = " +
                "\\sin^{-1}\\left(\\frac{${texNum(pick / 2.0, 3)}}" +
                "{${texNum(free, 3)}}\\right) = ${texDeg(angle)}"
        )
        out += subheading("Vertical Height (Hook to Top of Load):")
        out += equation(
            "\\text{Height}_{Hook-Load} = \\text{Free Leg Length} \\times " +
                "\\cos(${texDeg(angle)}) = ${texNum(free, 3)} \\times " +
                "${texNum(af, 3)} = ${texNum(headroom, 3)}\\ \\text{m}"
        )
        return out.joinToString("")
    }

    // --- The 2-leg chokes --------------------------------------------------
    if (key == "two_leg_round_choke" || key == "two_leg_rect_choke") {
        val internal = result.internalAngle ?: 0.0
        val external = result.externalAngle ?: 0.0
        val gap = result.chokeToLoad ?: 0.0
        val lead = result.lead ?: 0.0
        val corner = result.cornerLength ?: 0.0
        val pick = result.pickDistance
        out += subheading("Vertical Angle Outside Choke:")
        out += equation(
            "\\text{Choke External Angle} = 180^\\circ - \\text{Choke Internal Angle} = " +
                "180^\\circ - ${texNum(internal, 0)}^\\circ = ${texNum(external, 0)}^\\circ"
        )
        if (key == "two_leg_rect_choke") {
            out += subheading("Choke-to-Corner Sling Length:")
            out += equation(
                "\\text{Length} = \\frac{\\text{Bottom}/2}{\\sin(\\theta)} = " +
                    "\\frac{${texNum(result.bottom / 2.0, 3)}}" +
                    "{\\sin(${texNum(internal, 0)}^\\circ)} = " +
                    "${texNum(corner, 3)}\\ \\text{m}"
            )
        }
        out += subheading("Total Sling Perimeter Wrap:")
        // Substituted, not just answered: the round form is pi x Diameter and the
        // rectangular one is Bottom + 2 Side + 2 Corner, and both printed the
        // formula and then the result with nothing in between.
        out += equation(
            (if (key == "two_leg_round_choke") {
                "\\text{Perimeter} = \\pi \\times \\text{Diameter} = " +
                    "\\pi \\times ${texNum(result.diameter, 3)} = "
            } else {
                "\\text{Perimeter} = \\text{Bottom} + 2\\text{Side} + " +
                    "2\\text{Corner} = ${texNum(result.bottom, 3)} + " +
                    "2(${texNum(result.side, 3)}) + 2(${texNum(corner, 3)}) = "
            }) + "${texNum(wrap, 3)}\\ \\text{m}"
        )
        out += subheading("Vertical Height (Choke to Top of Load):")
        out += chokeHeightEquation(key, result, gap, internal, external)
        out += subheading("Free Leg Length (Each Sling):")
        out += equation(
            "\\text{Free Leg Length} = \\text{Total Length} - \\text{Perimeter} = " +
                "${texNum(length, 1)} - ${texNum(wrap, 3)} = ${texNum(free, 3)}\\ \\text{m}"
        )
        out += subheading("Sling Leg Angle from Vertical:")
        out += equation(
            "\\text{Angle} = \\sin^{-1}\\left(\\frac{\\text{Distance}/2}" +
                "{\\text{Free Leg Length} + \\text{Choke-Load}}\\right) = " +
                "\\sin^{-1}\\left(\\frac{${texNum(pick / 2.0, 3)}}" +
                "{${texNum(free + gap, 3)}}\\right) = ${texDeg(angle)}"
        )
        out += subheading("Vertical Height (Hook to Choke Point):")
        out += equation(
            "\\text{Height}_{Hook-Choke} = \\text{Free Leg Length} \\times " +
                "\\cos(${texDeg(angle)}) = ${texNum(free, 3)} \\times " +
                "${texNum(af, 3)} = ${texNum(headroom, 3)}\\ \\text{m}"
        )
        out += subheading("Hook-to-Contact / Lead:")
        // Named, not left as a bare number. For a choke the lead *is* the free leg
        // -- the straight part from the hook down to the choke point -- and that is
        // derived in the row above, so repeating the arithmetic here would state one
        // figure twice. Saying they are the same says where the number came from
        // without doing that.
        out += equation(
            "\\text{Lead} = \\text{Free Leg Length} = ${texNum(lead, 3)}\\ \\text{m}"
        )
        return out.joinToString("")
    }

    // --- The 2-leg baskets -------------------------------------------------
    if (key == "two_leg_round_basket" || key == "two_leg_vertical_round_basket" ||
        key == "two_leg_rect_basket" || key == "two_leg_vertical_rect_basket"
    ) {
        val heightSuffix: String
        val heightValue: Double
        val transNumerator: Double
        if (key.endsWith("vertical_round_basket") || key == "two_leg_round_basket") {
            heightSuffix = " - \\text{Radius}"
            heightValue = result.diameter / 2.0
            transNumerator = heightValue
        } else if (key.endsWith("vertical_rect_basket")) {
            heightSuffix = " - \\text{Side}"
            heightValue = result.side
            transNumerator = result.bottom / 2.0
        } else {
            heightSuffix = ""
            heightValue = 0.0
            transNumerator = result.bottom / 2.0
        }

        // The wrap length is derived, not measured, and it printed only its value:
        // "Wrap Length = 1.571 m" with no formula. A round basket cradles half the
        // circumference and a rectangular one lies across the bottom and up both
        // sides, so both have a derivation worth stating -- and the rows around them
        // all state theirs.
        val basketRadius = result.diameter / 2.0
        val basketBottom = result.bottom
        val basketSide = result.side
        val wrapEquation = if (key.endsWith("round_basket")) {
            "\\text{Wrap Length} = \\pi \\times \\text{Radius} = " +
                "\\pi \\times ${texNum(basketRadius, 3)} = ${texNum(wrap, 3)}\\ \\text{m}"
        } else {
            "\\text{Wrap Length} = \\text{Bottom} + 2\\text{Side} = " +
                "${texNum(basketBottom, 3)} + 2(${texNum(basketSide, 3)}) = " +
                "${texNum(wrap, 3)}\\ \\text{m}"
        }

        // The height's substituted values have to cover every term of its formula.
        // They did not: the `- Radius` (or `- Side`) was in the formula and absent
        // from the arithmetic, so the sum printed beside the answer did not produce
        // it. The suffix is appended below for exactly that reason.
        var heightTerms = "${texNum(free, 3)} \\times " +
            "${texNum(result.afLong, 3)} \\times ${texNum(result.afTrans, 3)}"
        if (heightValue != 0.0) {
            heightTerms += " - ${texNum(heightValue, 3)}"
        }

        out += subheading("Sling Contact Perimeter Wrap:")
        out += equation(wrapEquation)
        out += subheading("Free Leg Length (Each Side):")
        out += equation(
            "\\text{Free Leg Length} = \\frac{\\text{Total Length} - \\text{Wrap Length}}" +
                "{\\text{Effective Legs} / \\text{Sling Legs}} = " +
                "\\frac{${texNum(length, 1)} - ${texNum(wrap, 3)}}{2} = " +
                "${texNum(free, 3)}\\ \\text{m}"
        )
        out += subheading("Longitudinal Sling Angle from Vertical:")
        out += equation(
            "\\text{Angle}_{Long} = \\sin^{-1}\\left(\\frac{\\text{Distance}/2}" +
                "{\\text{Free Leg Length}}\\right) = " +
                "\\sin^{-1}\\left(\\frac{${texNum(result.pickDistance / 2.0, 3)}}" +
                "{${texNum(free, 3)}}\\right) = ${texDeg(result.angleLong)}"
        )
        out += subheading("Transverse Basket Angle from Vertical:")
        out += equation(
            "\\text{Angle}_{Trans} = \\sin^{-1}\\left(\\frac{\\text{Radius or Bottom}/2}" +
                "{\\text{Free Leg Length}}\\right) = " +
                "\\sin^{-1}\\left(\\frac{${texNum(transNumerator, 3)}}" +
                "{${texNum(free, 3)}}\\right) = ${texDeg(result.angleTrans)}"
        )
        out += subheading("Vertical Height (Hook to Top of Load):")
        out += equation(
            "\\text{Height}_{Hook-Load} = \\text{Free Leg Length} \\times " +
                "\\cos(\\text{Angle}_{Long}) \\times \\cos(\\text{Angle}_{Trans})" +
                heightSuffix +
                " = $heightTerms = ${texNum(headroom, 3)}\\ \\text{m}"
        )
        return out.joinToString("")
    }

    // --- The single-leg direct hook ----------------------------------------
    if (key == "direct_hook") {
        out += subheading("Sling Contact Perimeter Wrap:")
        out += equation("\\text{Wrap Length} = 0.000\\ \\text{m}")
        out += subheading("Free Leg Length (Standing Lifting Line):")
        out += equation(
            "\\text{Free Leg Length} = \\text{Total Length} - \\text{Wrap Length} = " +
                "${texNum(length, 1)} - 0.000 = ${texNum(free, 3)}\\ \\text{m}"
        )
        out += subheading("Sling Leg Angle from Vertical:")
        out += equation("\\text{Angle} = 0.0^\\circ")
        out += subheading("Vertical Height (Hook to Top of Load):")
        out += equation(
            "\\text{Height}_{Hook-Load} = \\text{Free Leg Length} = ${texNum(free, 3)}\\ \\text{m}"
        )
        return out.joinToString("")
    }

    // --- The single-leg round baskets --------------------------------------
    if (key == "round_basket" || key == "vertical_round_basket") {
        val radius = result.diameter / 2.0
        out += subheading("Sling Contact (Perimeter Wrap):")
        out += equation(
            "\\text{Arc Length} = \\pi \\times \\text{Radius} = " +
                "\\pi \\times ${texNum(radius, 3)} = ${texNum(wrap, 3)}\\ \\text{m}"
        )
        out += subheading("Free Leg Length (Each Side):")
        out += equation(
            "\\text{Free Leg Length} = \\frac{\\text{Total Length} - \\text{Arc Length}}" +
                "{2} = \\frac{${texNum(length, 1)} - ${texNum(wrap, 3)}}{2} " +
                "= ${texNum(free, 3)}\\ \\text{m}"
        )
        if (key == "round_basket") {
            out += subheading("Sling Leg Angle from Vertical:")
            out += equation(
                "\\text{Angle} = \\sin^{-1}\\left(\\frac{\\text{Radius}}" +
                    "{\\text{Free Leg Length}}\\right) = " +
                    "\\sin^{-1}\\left(\\frac{${texNum(radius, 3)}}{${texNum(free, 3)}}\\right) " +
                    "= ${texDeg(angle)}"
            )
            out += subheading("Vertical Height (Hook to Top of Load):")
            out += equation(
                "\\text{Height}_{Hook-Center} = \\text{Free Leg Length} \\times " +
                    "\\cos(${texDeg(angle)}) - \\text{Radius} = " +
                    "${texNum(free, 3)} \\times ${texNum(af, 3)} - ${texNum(radius, 3)} " +
                    "= ${texNum(headroom, 3)}\\ \\text{m}"
            )
        } else {
            out += subheading("Sling Leg Angle from Vertical:")
            out += equation(
                "\\text{Angle} = 0.0^\\circ\\ \\text{(legs go straight up parallel " +
                    "to a spreader bar)}"
            )
            out += subheading("Vertical Height (Hook to Top of Load):")
            out += equation(
                "\\text{Height}_{Hook-Load} = \\text{Free Leg Length} - \\text{Radius} = " +
                    "${texNum(free, 3)} - ${texNum(radius, 3)} = ${texNum(headroom, 3)}\\ \\text{m}"
            )
        }
        return out.joinToString("")
    }

    // --- The single-leg rectangular baskets --------------------------------
    if (key == "rect_basket" || key == "vertical_rect_basket") {
        val side = result.side
        val bottom = result.bottom
        out += subheading("Sling Contact Perimeter Wrap:")
        out += equation(
            "\\text{Wrap Length} = \\text{Bottom} + 2\\text{Side} = " +
                "${texNum(bottom, 3)} + 2(${texNum(side, 3)}) = ${texNum(wrap, 3)}\\ \\text{m}"
        )
        out += subheading("Free Leg Length (Each Side):")
        out += equation(
            "\\text{Free Leg Length} = \\frac{\\text{Total Length} - \\text{Wrap Length}}" +
                "{2} = \\frac{${texNum(length, 1)} - ${texNum(wrap, 3)}}{2} " +
                "= ${texNum(free, 3)}\\ \\text{m}"
        )
        if (key == "rect_basket") {
            out += subheading("Sling Leg Angle from Vertical:")
            out += equation(
                "\\text{Angle} = \\sin^{-1}\\left(\\frac{\\text{Bottom}/2}" +
                    "{\\text{Free Leg Length}}\\right) = " +
                    "\\sin^{-1}\\left(\\frac{${texNum(bottom / 2.0, 3)}}" +
                    "{${texNum(free, 3)}}\\right) = ${texDeg(angle)}"
            )
            out += subheading("Vertical Height (Hook to Top of Load):")
            out += equation(
                "\\text{Height}_{Hook-Load} = \\text{Free Leg Length} \\times " +
                    "\\cos(${texDeg(angle)}) = ${texNum(free, 3)} \\times " +
                    "${texNum(af, 3)} = ${texNum(headroom, 3)}\\ \\text{m}"
            )
        } else {
            out += subheading("Sling Leg Angle from Vertical:")
            out += equation(
                "\\text{Angle} = 0.0^\\circ\\ \\text{(legs go straight up parallel " +
                    "to a spreader bar)}"
            )
            out += subheading("Vertical Height (Hook to Top of Load):")
            out += equation(
                "\\text{Height}_{Hook-Load} = \\text{Free Leg Length} - \\text{Side} = " +
                    "${texNum(free, 3)} - ${texNum(side, 3)} = ${texNum(headroom, 3)}\\ \\text{m}"
            )
        }
        return out.joinToString("")
    }

    // --- The single-leg round choke ----------------------------------------
    if (key == "round_choke") {
        val radius = result.diameter / 2.0
        val internal = result.internalAngle ?: 0.0
        val external = result.externalAngle ?: 0.0
        val lead = result.lead ?: 0.0
        val gap = result.chokeToLoad
        out += subheading("Vertical Angle Outside Choke:")
        out += equation(
            "\\text{Choke External Angle} = 180^\\circ - \\text{Choke Internal Angle} = " +
                "180^\\circ - ${texNum(internal, 0)}^\\circ = ${texNum(external, 0)}^\\circ"
        )
        out += subheading("Total Sling Perimeter Wrap:")
        out += equation(
            "\\text{Perimeter} = \\pi \\times \\text{Diameter} = \\pi \\times " +
                "${texNum(result.diameter, 3)} = ${texNum(wrap, 3)}\\ \\text{m}"
        )
        if (external < 90.0) {
            out += subheading("Operational Rule Declared:")
            out += p(
                "As noted by the inspector/supervisor, if the angle of choke falls " +
                    "below 90 degrees, the sling wraps the load tightly under tension. " +
                    "The choke point is forced flush against the cylinder surface, " +
                    "meaning there is zero floating gap or air space above the cargo."
            )
            out += subheading("Vertical Height (Choke to Top of Load):")
            out += equation("\\text{Height}_{Choke-Load} = 0.000\\ \\text{m}")
        } else {
            val yValue = if (internal != 0.0) {
                radius / sin(Math.toRadians(internal))
            } else {
                radius
            }
            out += subheading("Vertical Height (Choke to Top of Load):")
            out += equation(
                "Y = \\frac{\\text{Radius}}{\\sin(\\text{Choke Internal Angle})} = " +
                    "\\frac{${texNum(radius, 3)}}{\\sin(${texNum(internal, 0)}^\\circ)} " +
                    "= ${texNum(yValue, 3)}\\ \\text{m}"
            )
            out += equation(
                "\\text{Height}_{Choke-Load} = Y - \\text{Radius} = " +
                    "${texNum(yValue, 3)} - ${texNum(radius, 3)} = ${texNum(gap, 3)}\\ \\text{m}"
            )
        }
        out += subheading("Vertical Height (Hook to Choke):")
        out += equation(
            "\\text{Height}_{Hook-Choke} = \\text{Total Length} - \\text{Perimeter} = " +
                "${texNum(length, 1)} - ${texNum(wrap, 3)} = ${texNum(lead, 3)}\\ \\text{m}"
        )
        return out.joinToString("")
    }

    // --- The rectangular choke (the remaining arrangement) ------------------
    val side = result.side
    val bottom = result.bottom
    val internal = result.internalAngle ?: 0.0
    val external = result.externalAngle ?: 0.0
    val corner = result.cornerLength ?: 0.0
    val lead = result.lead ?: 0.0
    val gap = result.chokeToLoad ?: 0.0
    out += subheading("Vertical Angle Outside Choke:")
    out += equation(
        "\\text{Choke External Angle} = 180^\\circ - \\text{Choke Internal Angle} = " +
            "180^\\circ - ${texNum(internal, 0)}^\\circ = ${texNum(external, 0)}^\\circ"
    )
    out += subheading("Choke-to-Corner Sling Length:")
    out += equation(
        "\\text{Length} = \\frac{\\text{Bottom}/2}{\\sin(\\theta)} = " +
            "\\frac{${texNum(bottom / 2.0, 3)}}{\\sin(${texNum(internal, 0)}^\\circ)} " +
            "= ${texNum(corner, 3)}\\ \\text{m}"
    )
    out += subheading("Total Sling Perimeter Wrap:")
    out += equation(
        "\\text{Perimeter} = \\text{Bottom} + 2\\text{Side} + " +
            "2\\text{Choke-to-Corner} = ${texNum(bottom, 3)} + " +
            "2(${texNum(side, 3)}) + 2(${texNum(corner, 3)}) = ${texNum(wrap, 3)}\\ \\text{m}"
    )
    out += subheading("Vertical Height (Choke to Top of Load):")
    out += equation(
        "\\text{Height}_{Choke-Load} = \\frac{\\text{Bottom}/2}{\\tan(\\theta)} = " +
            "\\frac{${texNum(bottom / 2.0, 3)}}{\\tan(${texNum(internal, 0)}^\\circ)} " +
            "= ${texNum(gap, 3)}\\ \\text{m}"
    )
    out += subheading("Vertical Height (Hook to Choke):")
    out += equation(
        "\\text{Height}_{Hook-Choke} = \\text{Total Length} - \\text{Perimeter} = " +
            "${texNum(length, 1)} - ${texNum(wrap, 3)} = ${texNum(lead, 3)}\\ \\text{m}"
    )
    return out.joinToString("")
}

/**
 * The first unused section number for a section about to be built.
 *
 * Numbered sections render as `<h2>N. TITLE</h2>`, so the highest number already
 * emitted is the one to continue from.  Counting rather than assuming a fixed offset
 * matters because the uniform section emits a fifth heading (its notes) only for the
 * arrangements that need one.
 */
internal fun nextSectionNumber(parts: List<String>): Int {
    var highest = 0
    for (part in parts) {
        val match = Regex("<h2>(\\d+)\\.").find(part)
        if (match != null) {
            highest = maxOf(highest, match.groupValues[1].toInt())
        }
    }
    return highest + 1
}

