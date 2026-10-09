package com.zhafrisbs.liftingplancalculator.core

import java.util.Locale

/**
 * The HTML report builder, ported from `lifting_calculator/report_1leg.py`.
 *
 * The report is deliberately independent of the UI: [buildReportHtml] takes the
 * solved state and returns a self-contained body that can be rendered by a WebView,
 * a browser, or a test.  The reference's own framing is the one to keep in mind —
 * the structure follows the numbered sections in the 1-Leg reference documents,
 * while keeping the formulas readable when KaTeX is unavailable.
 *
 * **This file is being filled in a section at a time.**  The crane section is here;
 * the uniform, nonuniform and tandem sections follow.  A section that is not yet
 * ported is simply *not offered* by the caller's `include` set, so the page never
 * claims to be a complete plan while it is missing a check — a report that silently
 * omits the sling calculation is worse than one that has not been asked for it.
 *
 * The reference builds these strings from untyped state dictionaries; this builds
 * them from the typed results, so a field that does not exist is a compile error
 * rather than a silently blank row.
 */

/**
 * Crane design load ratio: `W = gross load at hook / 0.75`.
 *
 * This sizes the lift against the crane's load chart.  It is **not** the sling load,
 * and the sling sections use the gross load at hook.  The reference's own comment
 * makes the distinction, and two different 0.75s exist in it.
 */
const val DESIGN_LOAD_RATIO = CRANE_USAGE_RATIO

val REPORT_CSS: String = """
<style>
.report {
    color: #151515;
    background: #fff;
    font-family: "Liberation Serif", "DejaVu Serif", serif;
    font-size: 7pt;
    line-height: 1.2;
}
.report h1 { font-size: 9pt; margin: 0 0 4pt; color: #111; text-align: center; }
.report h2 { font-size: 6pt; margin: 11pt 0 3pt; padding: 2pt 4pt;
    background: #e8edf3; border-bottom: 1px solid #9aa9ba; }
.report h3 { font-size: 6pt; margin: 7pt 0 1pt; }
.report p { margin: 1.5pt 0; }
.report .meta { color: #555; font-size: 5.5pt; margin-bottom: 5pt; }
.report table { border-collapse: collapse; width: 100%; margin: 3pt 0 6pt; }
.report table.kv { table-layout: fixed; }
.report table.kv th { width: 38%; }
.report table.kv td { width: 62%; }
.report td, .report th { border: 1px solid #7c8998; padding: 1pt 2pt;
    vertical-align: top; font-size: 5.5pt; }
.report th { background: #f1f4f7; text-align: left; font-weight: bold; }
.report .equation { margin: 2pt 0 3pt 12pt; font-family: "DejaVu Serif", serif;
    font-size: 5pt; }
.report .equation .fallback { font-family: "Liberation Serif", serif; }
.report .report-footer { margin-top: 14pt; padding-top: 5pt; text-align: center;
    border-top: 1px solid #9aa9ba; color: #555; font-size: 5.5pt; }
/* **A rule was removed here.** The reference's stylesheet carries
   `@media print { .report .report-footer { display: none; } }`, with no comment
   saying why. The effect was that the footer showed in the preview and vanished
   from the printed page -- so the one thing a printed plan could not tell you was
   why the two disagreed, and the screen gave no hint that they would. Rather than
   guess at an intent nobody wrote down, the printed page now carries the same
   footer the preview does. Restore that single line to get the desktop's
   behaviour back. */
.report .note { border: 1px solid #9aa9ba; background: #f7f7f7; padding: 5pt 7pt;
    margin-top: 5pt; }
.report .critical { border: 2px solid #a33; padding: 5pt 7pt; margin: 5pt 0; }
.report .pass { color: #176b3a; font-weight: bold; }
.report .fail { color: #9b1c1c; font-weight: bold; }
.report .small { font-size: 7pt; } .report .note { font-size: 7pt; } .report .status { font-size: 7pt; } .report .meta { font-size: 7pt; } .report .report-footer { font-size: 7pt; } .report p { font-size: 7pt; } .report td, .report th { font-size: 7pt; } .report .equation { font-size: 6.5pt; } .report h1 { font-size: 12pt; } .report h2 { font-size: 9pt; } .report h3 { font-size: 8pt; }
</style>
""".trimIndent()

// ---------------------------------------------------------------------------
// Formatting
// ---------------------------------------------------------------------------

internal fun numberOr(value: Double?, default: Double = 0.0): Double =
    if (value == null || !value.isFinite()) default else value

/**
 * A figure with a thousands separator, or an em dash for "nothing to show".
 *
 * A null is *not* zero.  The reference prints "—" for an absent figure and "0.00"
 * for a measured zero, and the two mean different things on a lift plan.
 */
fun fmt(value: Double?, places: Int = 3, unit: String = ""): String {
    if (value == null) return "\u2014"
    val text = String.format(Locale.US, "%,.${places}f", numberOr(value))
    return if (unit.isNotEmpty()) "$text $unit" else text
}

fun plain(value: Double?, places: Int = 3): String = fmt(value, places)

/** Number formatting that survives being read as TeX: thousands separators are braced. */
fun texNum(value: Double?, places: Int = 2): String =
    fmt(value, places).replace(",", "{,}")

fun texDeg(value: Double?, places: Int = 2): String =
    String.format(Locale.US, "%,.${places}f", numberOr(value)) + "^\\circ"

/**
 * One KaTeX expression, in display mode.
 *
 * The reference's `_equation` takes a `fallback` too and **never renders it** — its
 * own docstring says so: *"fallback is accepted for call-site compatibility but
 * intentionally not rendered"*.  It is dropped here rather than transcribed, which
 * removes around six hundred lines of prose that could never reach the page.  A
 * reader comparing the two files will find the LaTeX identical and the dead strings
 * absent, which is the honest state of affairs.
 */
fun equation(latex: String): String =
    "<div class=\"equation\">\\[$latex\\]</div>"

fun heading(number: Int, title: String): String =
    "<h2>$number. ${escape(title.uppercase())}</h2>"

fun subheading(title: String): String = "<h3>${escape(title)}</h3>"

/** A two-column key/value table, the report's only table shape. */
fun table(rows: List<Pair<String, String>>): String {
    val out = StringBuilder("<table class=\"kv\"><tbody>")
    for ((key, value) in rows) {
        out.append("<tr><th>").append(escape(key)).append("</th>")
        out.append("<td>").append(escape(value)).append("</td></tr>")
    }
    out.append("</tbody></table>")
    return out.toString()
}

fun p(text: String, css: String = ""): String {
    val style = if (css.isNotEmpty()) " style=\"$css\"" else ""
    return "<p$style>$text</p>"
}

fun labelValue(label: String, value: String): String =
    p("<b>${escape(label)}:</b> ${escape(value)}")

fun labelOrNotEntered(value: String?): String =
    if (value.isNullOrEmpty()) "Not entered" else value

/** `html.escape` with quoting on, which is what the reference uses. */
private fun escape(text: String): String = text
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&#x27;")

// ---------------------------------------------------------------------------
// The crane design load
// ---------------------------------------------------------------------------

/**
 * Crane design load `W = gross load at hook / 0.75`.
 *
 * This sizes the lift against the crane's load chart and is reported in the crane
 * section only.  It is not the sling load: the sling sections work from the gross
 * load at hook.  `W` depends only on the Overall Weight tab, so it stays available
 * even when the Uniform Load inputs are incomplete.
 */
fun designLoadFromGross(gross: Double): Double =
    if (gross <= 0) 0.0 else gross / DESIGN_LOAD_RATIO

// ---------------------------------------------------------------------------
// The document
// ---------------------------------------------------------------------------

/**
 * Everything the report can draw on.
 *
 * A holder rather than six positional parameters, because the sections are being
 * added a few at a time and a call site with six positional arguments is one
 * reordering away from printing the tandem numbers under a crane heading.
 */
data class ReportInputs(
    val totals: LiftTotals,
    val crane: CraneResult? = null,
    val sling: SlingResult? = null,
    val nonuniform: NonuniformResult? = null,
    /** Why the nonuniform lift could not be solved, or null when it could. */
    val nonuniformError: String? = null,
    val tandem: TandemResult? = null,
    /** Why the tandem lift could not be solved, or null when it could. */
    val tandemError: String? = null,
)

/** The sections a report may carry, in the builder's own source order. */
val REPORT_SECTIONS: List<String> = listOf("crane", "uniform", "nonuniform", "tandem")

/**
 * The sections one tab's report carries, in the report's own order.
 *
 * **The order comes from [REPORT_SECTIONS], not from the caller**, because
 * [buildReportHtml] emits its sections in *its* source order and merely gates them
 * on membership -- so a caller's order is not observable in the page at all.  A
 * tuple that reads the way the page reads is kept anyway, because it is easier to
 * check against a printed plan by eye.
 *
 * ``includeCrane`` is the one question a sling tab's button asks, and a tab whose
 * own section *is* the crane does not ask it: a checkbox that adds a section the
 * report already carries is a control that cannot do anything.  This lives here
 * rather than in the button for the reference's own reason -- a rule written in a
 * widget can only be read, and a rule that can only be read can only be hoped for.
 */
fun reportSectionsFor(own: String, includeCrane: Boolean): List<String> {
    // Deduplicated, so a caller passing `includeCrane` for the Crane tab gets its
    // one section rather than two copies of it.
    val chosen = setOf(own) + if (includeCrane) setOf("crane") else emptySet()
    return REPORT_SECTIONS.filter { it in chosen }
}

/**
 * The selected report sections as HTML.
 *
 * Missing or incomplete state is rendered as an explicit setup message rather than
 * raising while the operator is still entering data — a report is asked for from a
 * form that may be half filled, and refusing to produce one would be the wrong
 * answer to "show me where I am".
 */
fun buildReportHtml(
    inputs: ReportInputs,
    include: Set<String> = REPORT_SECTIONS.toSet(),
): String {
    val selected = include
    val gross = inputs.totals.gross
    val parts = mutableListOf(REPORT_CSS, "<div class=\"report\">")
    parts.add("<h1>Lifting Plan Calculation</h1>")

    if ("crane" in selected && inputs.crane != null) {
        parts.add(craneSection(inputs.crane, gross))
    }

    // The uniform block: five sections, four of them always and the fifth only for
    // the arrangements that carry a note.  A half-filled form cannot solve, so it
    // contributes nothing rather than a page of dashes.
    val sling = inputs.sling
    if ("uniform" in selected && sling != null) {
        val spec = HITCH_SPECS.getValue(sling.hitch)
        parts.add(sectionInputs(sling, spec))
        parts.add(geometrySection(sling, spec))
        parts.add(factorsSection(sling, spec))
        parts.add(capacitySection(sling, spec))
        // Numbered after whatever came before, so the next block never claims a
        // number this one already used.  The count is taken rather than assumed
        // because this block emits a fifth heading only sometimes.
        if (showsNotes(spec)) parts.add(notesSection(sling, spec))
    }

    // A nonuniform block is always emitted when it has been asked for, even when the
    // tab cannot solve: its "not set up" sentence is the answer to "why is this
    // section missing", and an absent section says nothing at all.  Numbered from
    // whatever came before, so the two blocks never both claim section 2.
    if ("nonuniform" in selected) {
        parts.addAll(
            nonuniformSections(
                result = inputs.nonuniform,
                firstNumber = nextSectionNumber(parts),
                error = inputs.nonuniformError,
            )
        )
    }
    if ("tandem" in selected) {
        parts.addAll(
            nonuniformSections(
                result = inputs.tandem,
                firstNumber = nextSectionNumber(parts),
                title = "Tandem Lift",
                error = inputs.tandemError,
            )
        )
    }

    if (gross <= 0.0) {
        parts.add(
            p(
                "<b>Setup note:</b> Add the load and complete the Overall Weight " +
                    "tab before issuing this plan.",
                "border:2px solid #a33; padding:5pt 7pt; margin:5pt 0;",
            )
        )
    }

    parts.add(
        "<div class=\"report-footer\">This app is design and develop by " +
            "Zhafri Syazwi <span class=\"email-icon\">&#9993;</span> " +
            "zhafribs@gmail.com</div>"
    )
    parts.add("</div>")
    return parts.joinToString("")
}

/** The crane, its boom and the capacity check against the load chart. */
private fun craneSection(crane: CraneResult, gross: Double): String {
    val parts = mutableListOf<String>()

    // The total-load context belongs in the input section so the report is
    // self-contained when printed without the other tabs visible.
    val chartCapacity = crane.chartCapacityKg
    val capacityStatus = crane.capacityStatus
    // Which chart the capacity came from, in the tab's own words ("Manual chart" /
    // "Excel chart"), plus the file when there is one.
    //
    // **This row is not in the reference, and that is a deliberate divergence worth
    // stating.**  `report_1leg.py` computes exactly this string, documents why it
    // matters in a comment — *"the plan may be read on site against a paper load
    // chart, and a capacity with no stated source cannot be checked against one"* —
    // and then never prints it.  The comment ended up above the "Gross load at hook"
    // row instead, which is where the intended row would have gone.  Grepping the
    // module for the name finds one hit: the assignment.  The row is restored here
    // because the justification for it is explicit and the omission is plainly
    // accidental; delete this row to match the desktop exactly.
    val chartSource = listOf(crane.capacitySource, crane.chartPath.orEmpty())
        .filter { it.isNotEmpty() }
        .joinToString(" ")

    parts.add(
        table(
            listOf(
                "Crane Model" to crane.craneName.ifEmpty { "Not entered" },
                "Crane rated capacity" to
                    if (crane.craneCapacityKg != 0.0) fmt(crane.craneCapacityKg, 2, "kg")
                    else "Not entered",
                "Boom length" to
                    if (crane.boomM != null && crane.boomM != 0.0) fmt(crane.boomM, 2, "m")
                    else "Not entered",
                "Boom Angle" to
                    if (crane.boomAngleDeg != null && crane.boomAngleDeg != 0.0) {
                        fmt(crane.boomAngleDeg, 2, "\u00b0")
                    } else {
                        "Not entered"
                    },
                "Working radius" to
                    if (crane.radiusM != 0.0) fmt(crane.radiusM, 2, "m") else "Not entered",
                "Chart capacity at radius" to
                    if (chartCapacity != null) fmt(chartCapacity, 2, "kg") else "Not evaluated",
                "Chart source" to chartSource.ifEmpty { "Not evaluated" },
                "Gross load at hook" to fmt(gross, 2, "kg"),
            )
        )
    )

    // W is a crane load-chart sizing load.  It belongs to the crane check only and
    // is deliberately not used by the sling sections, which work from the gross load
    // at hook.
    val appliedLoad = designLoadFromGross(gross)
    val ratio = texNum(DESIGN_LOAD_RATIO, 2)
    parts.add(subheading("Crane design load for the 75% allowable check, W:"))
    parts.add(
        equation(
            "\\text{W} = \\frac{\\text{Gross load at hook}}{$ratio} = " +
                "\\frac{${texNum(gross, 2)}}{$ratio} = " +
                "${texNum(appliedLoad, 2)}\\ \\text{kg}"
        )
    )
    parts.add(subheading("75% Allowable:"))
    val usage = crane.capacityUsagePercent
    if (chartCapacity != null && chartCapacity != 0.0 && usage != null) {
        parts.add(
            equation(
                "\\text{Usage} = \\frac{\\text{Gross load at hook}}" +
                    "{\\text{Chart capacity at radius}} \\times 100\\% = " +
                    "\\frac{${texNum(gross, 2)}}{${texNum(chartCapacity, 2)}} " +
                    "\\times 100\\% = ${String.format(Locale.US, "%.1f", usage)}\\%"
            )
        )
    }
    parts.add(p(capacityStatus.ifEmpty { "Not evaluated" }))

    return parts.joinToString("")
}
