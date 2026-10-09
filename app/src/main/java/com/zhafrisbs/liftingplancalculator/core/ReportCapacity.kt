package com.zhafrisbs.liftingplancalculator.core

import java.util.Locale

/**
 * The rest of the uniform-lift report: the rigging factors, the final capacity and
 * equipment selection, and the notes.
 *
 * Two of the reference's own corrections are visible here, and both are kept
 * because the comment naming them is the reference's:
 *
 *  * **`installedTagBlock` exists once, not twice.**  It was written for the
 *    asymmetric slings and then copied for the tandem cranes, and the copy named
 *    its denominator "Tag WLL" while substituting a *derated* capacity — so a
 *    2,000 kg tag was checked against a 1,414.2 kg figure that arrived from
 *    nowhere.  One rule about one number, in one place.
 *  * **A leg count that changes is explained.**  The form asks how many legs each
 *    tag is *made of*; the capacity divides by how many that rating is *declared
 *    on*, and the documents' rule makes those different for a 3- or 4-leg tag.
 *    Without a note, a form reading 4 beside a formula reading 3 reads as an
 *    arithmetic error rather than as a rule.
 */

/** The four arrangements a choke reduction applies to. */
internal val CHOKE_KEYS = listOf(
    "round_choke", "rect_choke", "two_leg_round_choke", "two_leg_rect_choke",
)

/** The arrangements whose factor is the product of a longitudinal and a transverse one. */
private val TWO_LEG_BASKET_KEYS = listOf(
    "two_leg_round_basket", "two_leg_rect_basket",
    "two_leg_vertical_round_basket", "two_leg_vertical_rect_basket",
)

/**
 * The leg count a sling tag is rated on, as the reference documents state it under
 * their note heading.  The rule drives the denominator of the capacity and
 * minimum-WLL equations.
 */
private val RATING_LEG_NOTE = listOf(
    "If 4-Leg Sling, use Sling Legs = 3",
    "If 3-Leg Sling, use Sling Legs = 2",
    "If 2-Leg Sling, use Sling Legs = 2",
)

/**
 * Section 3: the angle factor, the reeve factor and the leg tension.
 *
 * The 75% crane window is deliberately absent — it sizes the lift against the crane
 * and is reported in the crane section — so every figure here is against the gross
 * load the hook actually carries.
 */
internal fun factorsSection(result: SlingResult, spec: HitchSpec): String {
    val key = spec.key
    val weight = result.loadWeight
    val angle = result.angle
    val af = result.angleFactor
    val rf = result.baseReeveFactor
    val reduction = result.chokeReductionFactor
    val combined = result.combinedReeveFactor
    val eff = result.effectiveLegs
    val tension = result.tensionEach
    val out = mutableListOf(heading(3, "Rigging Factors & Tension Calculations"))

    if (result.nested != 0) {
        val afLong = result.afLong ?: 1.0
        val secondaryAf = result.secondaryAf ?: 1.0
        val secondaryCombined = result.secondaryCombinedAf ?: 1.0
        val nestedLabels = secondaryLabels(result, result.nested)
        out += subheading("Longitudinal Sling Angle Factor (AF Long):")
        out += equation(
            "\\text{AF}_{Long} = \\cos(${texDeg(result.angleLong)}) = ${texNum(afLong, 3)}"
        )
        if (nestedLabels.isNotEmpty()) {
            // The 3- and 4-leg nested documents publish one factor per group.
            for ((group, label) in nestedLabels) {
                out += subheading(
                    "Transverse Sling Angle Factor for ${label.token} Legs " +
                        "(AF Trans ${label.token}):"
                )
                out += equation(
                    "\\text{AF}_{\\text{Trans ${label.token}}} = " +
                        "\\cos(${texDeg(group.angle)}) = ${texNum(group.af, 3)}"
                )
                out += subheading(
                    "Combined Sling Angle Factor (AF) for ${label.name} Secondary Legs:"
                )
                out += equation(
                    "\\text{AF}_{\\text{Combined ${label.token}}} = " +
                        "\\text{AF}_{Long} \\times " +
                        "\\text{AF}_{\\text{Trans ${label.token}}} = " +
                        "${texNum(afLong, 3)} \\times ${texNum(group.af, 3)} = " +
                        "${texNum(group.combinedAf, 3)}"
                )
            }
        } else {
            out += subheading("Transverse Sling Angle Factor (AF Trans):")
            out += equation(
                "\\text{AF}_{Trans} = \\cos(${texDeg(result.secondaryAngle)}) = " +
                    "${texNum(secondaryAf, 3)}"
            )
            out += subheading("Combined Sling Angle Factor (AF Combined):")
            out += equation(
                "\\text{AF}_{Combined} = \\text{AF}_{Long} \\times " +
                    "\\text{AF}_{Trans} = ${texNum(afLong, 3)} \\times " +
                    "${texNum(secondaryAf, 3)} = ${texNum(secondaryCombined, 3)}"
            )
        }
    } else if (key in TWO_LEG_BASKET_KEYS) {
        val afLong = result.afLong ?: 1.0
        val afTrans = result.afTrans ?: 1.0
        out += subheading("Longitudinal Sling Angle Factor (AF Long):")
        out += equation(
            "\\text{AF}_{Long} = \\cos(${texDeg(result.angleLong)}) = ${texNum(afLong, 3)}"
        )
        out += subheading("Transverse Basket Angle Factor (AF Trans):")
        out += equation(
            "\\text{AF}_{Trans} = \\cos(${texDeg(result.angleTrans)}) = ${texNum(afTrans, 3)}"
        )
        out += subheading("Combined Sling Angle Factor (AF):")
        out += equation(
            "\\text{AF} = \\text{AF}_{Long} \\times \\text{AF}_{Trans} = " +
                "${texNum(afLong, 3)} \\times ${texNum(afTrans, 3)} = ${texNum(af, 3)}"
        )
    } else {
        out += subheading("Sling Angle Factor (AF):")
        if (key in CHOKE_KEYS) {
            out += p("The main lifting leg stands perfectly vertical (0\u00b0).")
        }
        out += if (key == "round_choke" || key == "rect_choke") {
            equation("\\text{AF} = \\cos(0^\\circ) = 1.0")
        } else {
            equation("\\text{AF} = \\cos(${texDeg(angle)}) = ${texNum(af, 3)}")
        }
    }

    out += subheading("Base Reeve Factor (RF):")
    out += equation("\\text{RF}_{Base} = ${texNum(rf, 2)}")
    if (key in CHOKE_KEYS) {
        out += subheading("Choke Angle Reduction Factor:")
        out += equation("\\text{Reduction Factor} = ${texNum(reduction, 2)}")
        out += subheading("Combined Reeve Factor:")
        out += equation(
            "\\text{Combined RF} = \\text{RF}_{Base} \\times " +
                "\\text{Reduction Factor} = ${texNum(rf, 2)} \\times " +
                "${texNum(reduction, 2)} = ${texNum(combined, 4)}"
        )
    }

    if (result.nested != 0) {
        val secondaryTension = result.secondaryTension ?: 0.0
        val secondaryCombined = result.secondaryCombinedAf ?: 1.0
        val nestedLabels = secondaryLabels(result, result.nested)
        val secondaryLegs = result.secondaryLegs ?: 1
        out += subheading("Tension in Each First Sling Leg (2 Master Legs Total):")
        out += equation(
            "\\text{Tension}_1 = \\frac{\\text{Load Weight}}" +
                "{\\text{Sling Legs}_1 \\times \\text{AF}_{Long}} = " +
                "\\frac{${texNum(weight, 0)}}{2 \\times ${texNum(af, 3)}} = " +
                "${texNum(tension, 1)}\\ \\text{kg}"
        )
        if (nestedLabels.isNotEmpty()) {
            // The 3- and 4-leg nested documents state one tension per group.
            for ((group, label) in nestedLabels) {
                out += subheading("Tension in ${label.phrase}:")
                out += equation(
                    "\\text{Tension}_{\\text{Secondary ${label.token}}} = " +
                        "\\frac{\\text{Load Weight}}" +
                        "{\\text{Sling Legs}_1 \\times \\text{Sling Legs}_2 \\times " +
                        "\\text{AF}_{\\text{Combined ${label.token}}}} = " +
                        "\\frac{${texNum(weight, 0)}}{2 \\times $secondaryLegs " +
                        "\\times ${texNum(group.combinedAf, 3)}} = " +
                        "${texNum(group.tension, 1)}\\ \\text{kg}"
                )
            }
        } else {
            out += subheading(
                "Tension in Each Second Sling Leg ($eff Secondary Legs Total):"
            )
            out += equation(
                "\\text{Tension}_2 = \\frac{\\text{Load Weight}}" +
                    "{\\text{Sling Legs}_1 \\times \\text{Sling Legs}_2 \\times " +
                    "\\text{AF}_{Combined}} = " +
                    "\\frac{${texNum(weight, 0)}}{2 \\times $secondaryLegs " +
                    "\\times ${texNum(secondaryCombined, 3)}} = " +
                    "${texNum(secondaryTension, 1)}\\ \\text{kg}"
            )
        }
    } else {
        // The 4-leg reference document states the ideal-distribution assumption
        // instead of counting the effective legs.
        out += subheading(
            when {
                key == "four_leg_direct" ->
                    "Tension in Each Sling Leg (Assuming ideal distribution " +
                        "across $eff Legs):"
                spec.bridle -> "Tension in Each Sling Leg ($eff Effective Legs Total):"
                else -> "Tension in Each Effective Legs:"
            }
        )
        out += equation(
            "\\text{Tension} = \\frac{\\text{Load Weight}}" +
                "{\\text{Effective Legs} \\times \\text{AF}} = " +
                "\\frac{${texNum(weight, 0)}}{$eff \\times ${texNum(af, 3)}} = " +
                "${texNum(tension, 1)}\\ \\text{kg}"
        )
    }
    return out.joinToString("")
}

// ---------------------------------------------------------------------------
// Section 4 -- final capacity and equipment selection
// ---------------------------------------------------------------------------

/** The nested documents number the master and secondary slings 1 and 2. */
private val LABEL_INDEX = mapOf("First" to 1, "Second" to 2)

/**
 * The "Sling Leg Capacity Each" block for one installed tag.
 *
 * [label] names the sling in a nested system ("First"/"Second"); an empty label
 * keeps the single-sling wording of the reference documents.
 */
private fun tagCapacityBlock(
    tagWll: Double,
    ratingLegs: Int,
    tagAngleFactor: Double,
    legCapacity: Double?,
    label: String = "",
): String {
    val suffix = if (label.isNotEmpty()) " for $label Sling" else ""
    val index = if (label in LABEL_INDEX) "_${LABEL_INDEX.getValue(label)}" else ""
    val name = if (label.isNotEmpty()) "$label Sling Leg Capacity Per" else "Sling Leg Capacity Per"
    val out = mutableListOf(subheading("Sling Leg Capacity Each$suffix (Tag Rating):"))
    if (legCapacity == null) {
        out += p(
            "<b>Installed tag WLL:</b> Not entered \u2014 enter the sling tag rating " +
                "and its rated angle to check the $ratingLegs-leg sling leg capacity."
        )
        return out.joinToString("")
    }
    out += equation(
        "\\text{$name} = " +
            "\\frac{\\text{Installed Tag WLL}$index}" +
            "{\\text{Sling Legs}$index \\times \\cos(\\text{Tag WLL Angle}$index)} = " +
            "\\frac{${texNum(tagWll, 0)}}{$ratingLegs \\times ${texNum(tagAngleFactor, 3)}} = " +
            "${texNum(legCapacity, 1)}\\ \\text{kg}"
    )
    return out.joinToString("")
}

/**
 * The "WLL Utilisation" block.
 *
 * [forSling] names the sling in a nested system, so the heading reads
 * "WLL Utilisation for First Sling (Tag Rating):".  [needsAngle] is false for a
 * single leg, which hangs vertical and is rated at no angle.
 */
private fun utilisationBlock(
    required: Double,
    capacityLabel: String,
    capacity: Double?,
    utilisation: Double?,
    index: String = "",
    check: String = "",
    forSling: String = "",
    needsAngle: Boolean = true,
): String {
    val prefix = if (forSling.isNotEmpty()) " for $forSling Sling" else ""
    val out = mutableListOf(subheading("WLL Utilisation$prefix (Tag Rating):"))
    if (capacity == null || utilisation == null) {
        out += p(
            "Not calculated \u2014 enter the sling tag rating" +
                (if (needsAngle) " and its rated angle" else "") +
                " to complete the equipment check."
        )
        return out.joinToString("")
    }
    out += equation(
        "\\text{WLL Utilisation}$index = " +
            "\\frac{\\text{Minimum WLL}$index}{$capacityLabel} \\times 100\\% = " +
            "\\frac{${texNum(required, 1)}}{${texNum(capacity, 1)}} " +
            "\\times 100\\% = ${
                String.format(Locale.US, "%.2f", utilisation)
            }\\%"
    )
    val pass = utilisation <= 100.0
    val cls = if (pass) "pass" else "fail"
    val word = if (pass) "PASS" else "FAIL"
    // A nested system reports two ratings, so name the sling in the check line.
    out += p(
        "<b>Tag rating check$check:</b> <span class='$cls'>$word</span> \u2014 the " +
            "sling leg is ${String.format(Locale.US, "%.2f", utilisation)}% utilised."
    )
    return out.joinToString("")
}

/** Section 4: what the sling has to be rated at, and whether the tag meets it. */
internal fun capacitySection(result: SlingResult, spec: HitchSpec): String {
    val key = spec.key
    val weight = result.loadWeight
    val af = result.angleFactor
    val rf = result.baseReeveFactor
    val combined = result.combinedReeveFactor
    val required = result.requiredWll
    val utilisation = result.utilisation
    val capacity = result.legCapacity
    val ratingLegs = result.ratingLegs
    val nested = result.nested != 0
    val out = mutableListOf(heading(4, "Final Capacity & Equipment Selection"))

    // The multi-leg reference documents open section 4 with the installed tag
    // rating: the capacity of a single sling leg and the leg count the rating is
    // checked against.
    if (spec.legCount > 1) {
        if (nested) {
            out += tagCapacityBlock(
                tagWll = result.tagWll,
                ratingLegs = result.ratingLegs,
                tagAngleFactor = result.tagAngleFactor,
                legCapacity = result.legCapacity,
                label = "First",
            )
            out += tagCapacityBlock(
                tagWll = result.secondaryTagWll,
                ratingLegs = result.secondaryRatingLegs ?: 0,
                tagAngleFactor = result.secondaryTagAngleFactor,
                legCapacity = result.secondaryLegCapacity,
                label = "Second",
            )
        } else {
            out += tagCapacityBlock(
                tagWll = result.tagWll,
                ratingLegs = result.ratingLegs,
                tagAngleFactor = result.tagAngleFactor,
                legCapacity = result.legCapacity,
            )
        }
    }

    if (nested) {
        val secondaryRequired = result.secondaryRequiredWll ?: 0.0
        val secondaryCombined = result.secondaryCombinedAf ?: 1.0
        val secondaryCapacity = result.secondaryLegCapacity
        // The secondary sling is rated for its own leg count, so its heading states
        // that count rather than the master bridle's.
        val secondaryLegs = result.secondaryLegs ?: 1
        out += subheading("Minimum Required Sling WLL for First Sling (Tag Rating):")
        out += equation(
            "\\text{Minimum WLL}_1 = \\frac{\\text{Load Weight}}" +
                "{\\text{Sling Legs}_1 \\times \\text{RF} \\times \\text{AF}_{Long}} = " +
                "\\frac{${texNum(weight, 0)}}{$ratingLegs \\times ${texNum(rf, 2)} " +
                "\\times ${texNum(af, 3)}} = ${texNum(required, 1)}\\ \\text{kg}"
        )
        out += utilisationBlock(
            required, "\\text{First Sling Leg Capacity Per}", capacity, utilisation,
            index = "_1", check = " (first sling)", forSling = "First",
        )
        out += subheading("Minimum Required Sling WLL for Second Sling (Tag Rating):")
        out += equation(
            "\\text{Minimum WLL}_2 = \\frac{\\text{Load Weight}}" +
                "{\\text{Sling Legs}_1 \\times \\text{Sling Legs}_2 \\times " +
                "\\text{RF} \\times \\text{AF}_{Combined}} = " +
                "\\frac{${texNum(weight, 0)}}{$ratingLegs \\times $secondaryLegs " +
                "\\times ${texNum(rf, 2)} \\times ${texNum(secondaryCombined, 3)}} = " +
                "${texNum(secondaryRequired, 1)}\\ \\text{kg}"
        )
        out += utilisationBlock(
            secondaryRequired, "\\text{Second Sling Leg Capacity Per}",
            secondaryCapacity, result.secondaryUtilisation,
            index = "_2", check = " (second sling)", forSling = "Second",
        )
    } else {
        // The single-sling documents keep the leg count in this heading; only the
        // nested documents name the rating instead.
        out += subheading(
            if (spec.legCount > 1) {
                "Minimum Required Sling WLL (Tag Rating for Sling Legs = $ratingLegs):"
            } else {
                "Minimum Required Sling WLL (Tag Rating):"
            }
        )
        if (key in CHOKE_KEYS) {
            out += equation(
                "\\text{Minimum WLL} = \\frac{\\text{Load Weight}}" +
                    "{\\text{Sling Legs} \\times \\text{Combined RF} \\times \\text{AF}} = " +
                    "\\frac{${texNum(weight, 0)}}{$ratingLegs \\times " +
                    "${texNum(combined, 4)} \\times ${texNum(af, 3)}} = " +
                    "${texNum(required, 1)}\\ \\text{kg}"
            )
        } else {
            out += equation(
                "\\text{Minimum WLL} = \\frac{\\text{Load Weight}}" +
                    "{\\text{Sling Legs} \\times \\text{RF} \\times \\text{AF}} = " +
                    "\\frac{${texNum(weight, 0)}}{$ratingLegs \\times ${texNum(rf, 2)} " +
                    "\\times ${texNum(af, 3)}} = ${texNum(required, 1)}\\ \\text{kg}"
            )
        }
        if (spec.legCount > 1) {
            out += utilisationBlock(
                required, "\\text{Sling Leg Capacity Per}", capacity, utilisation,
            )
        } else {
            // A single leg hangs vertical, so there is no derate to show and its
            // capacity is the installed tag WLL itself.  The utilisation is
            // therefore taken straight against the tag rating.
            out += utilisationBlock(
                required, "\\text{Installed tag WLL}", capacity, utilisation,
                needsAngle = false,
            )
        }
    }

    out += subheading("Configured Setup SWL:")
    out += equation(
        "\\mathbf{\\text{Configured Setup SWL}} = \\text{Load Weight} = " +
            "${texNum(weight, 0)}\\ \\text{kg}"
    )
    // A single leg carries the whole load, so the report prints no "Sling Leg
    // Capacity Each" block for it: there is no derate to demonstrate.  It still
    // rates the entered tag, and the 1-leg documents carry the same "WLL
    // Utilisation (Tag Rating)" block as the 2-leg ones.
    return out.joinToString("")
}

// ---------------------------------------------------------------------------
// Section 5 -- notes
// ---------------------------------------------------------------------------

/**
 * Whether section 5 applies.
 *
 * Shown for the choke arrangements, which carry the choke reduction table, and for
 * every multi-leg arrangement, which carries the tag-rating leg rule.  The
 * single-sling documents have no section 5.
 */
internal fun showsNotes(spec: HitchSpec): Boolean =
    spec.key in CHOKE_KEYS || spec.legCount > 1

/** Section 5: the choke reduction table and the tag-rating leg rule. */
internal fun notesSection(result: SlingResult, spec: HitchSpec): String {
    val out = mutableListOf(heading(5, "Note"), subheading("Choke Notes"))
    if (spec.key !in CHOKE_KEYS) {
        out += p("No choke reduction applies to the selected arrangement.")
    } else {
        val reduction = result.chokeReductionFactor
        val external = result.externalAngle
        out += p(
            "(1) Percent of sling rated capacity in a choker hitch. The rating " +
                "reduction is applied to the tag WLL, not to the resolved " +
                "main-leg tension."
        )
        out += "<table><thead><tr><th>External choke angle</th>" +
            "<th>Capacity multiplier</th></tr></thead><tbody>"
        out += "<tr><td>120\u00b0 and over</td><td>1.00</td></tr>"
        out += "<tr><td>90\u00b0 to under 120\u00b0</td><td>0.87</td></tr>"
        out += "<tr><td>60\u00b0 to under 90\u00b0</td><td>0.74</td></tr>"
        out += "<tr><td>30\u00b0 to under 60\u00b0</td><td>0.62</td></tr>"
        out += "<tr><td>Below 30\u00b0</td><td>0.49</td></tr>"
        out += "</tbody></table>"
        out += p(
            "Selected external angle: ${fmt(external, 0, "\u00b0")} \u2014 " +
                "reduction factor ${String.format(Locale.US, "%.2f", reduction)}."
        )
    }
    if (spec.legCount > 1) {
        out += subheading("To Calculate Sling Leg Capacity Each (Tag Rating):")
        out += RATING_LEG_NOTE.map { p(it) }
        if (spec.nested != 0) out += nestedRatingNote(result, spec)
    }
    return out.joinToString("")
}

/**
 * The note line naming the rated leg count of a nested system.
 *
 * A nested document carries two capacity equations, one for the master sling at the
 * hook and one for the second sling at each master leg, so the note has to say which
 * count the rule gives for each.  The second sling is itself a 2-, 3- or 4-leg
 * sling, so the same rule applies to it.
 */
private fun nestedRatingNote(result: SlingResult, spec: HitchSpec): String {
    val first = result.ratingLegs
    val second = result.secondaryRatingLegs
    if (second == null) return ""
    return p(
        "Applied in this report: the first sling is a ${spec.legCount}-leg " +
            "sling, so the rule above gives Sling Legs = $first for the first " +
            "sling's leg capacity, and the second sling is a ${spec.nested}-leg " +
            "sling, so the same rule gives Sling Legs = $second for the second " +
            "sling's leg capacity."
    )
}

// ---------------------------------------------------------------------------
// Shared with the nonuniform and tandem blocks
// ---------------------------------------------------------------------------

/**
 * The installed tag's leg capacity and the utilisation that divides by it.
 *
 * One block for the two asymmetric slings *and* the two tandem cranes.  It was
 * written twice: the tandem copy named its denominator "Tag WLL" while substituting
 * the derated capacity, and printed no derivation for the number it divided by, so
 * a 2,000 kg tag was checked against a 1,414.2 kg figure that arrived from nowhere.
 * One rule about one number, in one place.
 *
 * Returns an empty list when no capacity was entered, which is the caller's signal
 * that there is nothing to say.
 */
internal fun installedTagBlock(
    tagWll: Double,
    ratedLegs: Int,
    tagAngleFactor: Double,
    legCapacity: Double?,
    requiredWll: Double,
    utilisation: Double?,
    label: String,
): List<String> {
    if (legCapacity == null) return emptyList()
    val capacityLine: String
    if (ratedLegs == SINGLE_LEG) {
        // A single leg is a definition rather than a derivation: it hangs vertical,
        // so there is no leg count to share with and no angle to derate it by, and
        // the capacity *is* the tag's rating.  Printing "Tag WLL / (1 x cos(0 deg))"
        // would name two quantities the form does not have -- the angle field is
        // gone for a single leg -- and dress the answer as an arithmetic step that
        // was never taken.
        capacityLine = equation(
            "\\text{Capacity}_{$label} = \\text{Tag WLL}_{$label} = " +
                "${texNum(tagWll, 1)}\\ \\text{kg}"
        )
    } else {
        capacityLine = equation(
            // Without this the utilisation's denominator appeared from nowhere.
            // The leg count is in the denominator because the reference documents
            // state the rule as Tag WLL / (Sling Legs x cos(Tag Angle)), which is
            // the same denominator the Uniform Load report already prints.
            "\\text{Capacity}_{$label} = " +
                "\\frac{\\text{Tag WLL}_{$label}}" +
                "{\\text{Sling Legs}_{$label} \\times \\cos(\\text{Tag Angle}_{$label})} = " +
                "\\frac{${texNum(tagWll, 1)}}" +
                "{$ratedLegs \\times ${texNum(tagAngleFactor, 3)}} = " +
                "${texNum(legCapacity, 1)}\\ \\text{kg}"
        )
    }
    return listOf(
        capacityLine,
        equation(
            // "Sling Leg Capacity", not "Tag WLL": the number divided by is the
            // *derated* capacity, not the tag's rating.  Naming the denominator
            // after the input made the same figure read "Tag WLL" on one line and
            // "Sling 1 leg capacity" on the next.
            "\\text{Utilisation}_{$label} = " +
                "\\frac{\\text{Min WLL}_{$label}}" +
                "{\\text{Sling Leg Capacity}_{$label}} \\times 100\\% = " +
                "\\frac{${texNum(requiredWll, 1)}}{${texNum(legCapacity, 1)}} " +
                "\\times 100\\% = ${texNum(utilisation, 2)}\\%"
        ),
    )
}

/**
 * State the leg count each tag rating is declared on.
 *
 * The form asks how many legs each tag is *made of* and the capacity divides by how
 * many that rating is *declared on*, and the documents' rule makes those different
 * for a 3- or 4-leg tag.  Without this note the two numbers sit on the same page
 * with nothing connecting them, and a form reading 4 beside a formula reading 3
 * reads as an error in the arithmetic rather than as the rule.
 *
 * The rule's own wording is reproduced only when it did something — when every
 * tag's declared count equals its leg count, "Sling Legs = 2" beside a 2-leg tag
 * needs no explanation, and three lines of it would be noise.
 */
internal fun nonuniformRatingLegsNote(
    pairs: List<Triple<String, Int?, Int?>>,
): String {
    val applied = mutableListOf<String>()
    var ruleUsed = false
    for ((caption, chosenRaw, ratedRaw) in pairs) {
        val chosen = chosenRaw ?: continue
        val rated = ratedRaw ?: continue
        // A single leg is not an application of this rule -- the rule is stated for
        // bridles, and a 1 maps to 1 -- so it is left out rather than given a
        // sentence saying so.  One leg beside one leg is the absence of a question.
        if (chosen == SINGLE_LEG) continue
        ruleUsed = ruleUsed || chosen != rated
        applied += "$caption's tag is a $chosen-leg sling, so the rule gives " +
            "Sling Legs = $rated for its leg capacity."
    }
    if (applied.isEmpty()) return ""
    var out = ""
    if (ruleUsed) {
        out += subheading("To Calculate Sling Leg Capacity Each (Tag Rating):")
        out += RATING_LEG_NOTE.joinToString("") { p(it) }
    }
    out += p("Applied in this report: " + applied.joinToString(" "))
    return out
}
