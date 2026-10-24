package com.zhafrisbs.liftingplancalculator.core

import java.util.Locale

/**
 * Reading a crane load chart out of a workbook, and the rules that decide what an
 * imported chart does to the form.
 *
 * A port of `lifting_calculator/kivy_ui/excel_state.py`.  The parsing itself is
 * [Xlsx] + [ExcelLoader]; what this adds is the two answers that are *decisions*
 * rather than parsing, and both of them were wrong in the reference's first
 * version because they were left to a default:
 *
 *  * **Which boom column a matrix chart is read from.**  A matrix workbook holds a
 *    different capacity for every boom length, and the reference first read column
 *    zero — the *shortest* boom — while the desktop's selector opens on the
 *    **longest**.  On the operator's own chart that was 8,650 kg reported where the
 *    desktop says 2,850 kg, at a boom nobody chose.  A capacity read at the wrong
 *    boom is not a cosmetic difference: it is a figure the operator never checked.
 *  * **Which working radius an imported chart settles on.**  The reference first
 *    left the radius wherever the form already had it, so a chart read at the
 *    demo's leftover 8.00 m instead of the radius the desktop computes.
 *
 * Both rules are the desktop's, read out of `gui.py` rather than re-derived, and
 * they are the reason this code is a pure function over plain data rather than
 * something inside the screen: a rule written in a view can only be read, and a
 * rule that can only be read can only be hoped for.
 */

/** A workbook that could not be read, in words an operator can act on. */
class ExcelImportException(message: String) : Exception(message)

/**
 * What `openpyxl` reports when the file is not a workbook at all: a renamed `.xls`,
 * an HTML table saved with the wrong extension, a workbook encrypted by Excel.
 * The library's own wording is a zipfile error, which means nothing to the person
 * holding the phone.
 */
private const val NOT_A_WORKBOOK =
    "That file is not an Excel workbook. Load charts are read from .xlsx files; a " +
        "chart from an older .xls or .xlsm file has to be saved as .xlsx first."

/**
 * A ceiling on the workbook's size.  A load chart is a table; a hundred megabytes
 * of spreadsheet is not one, and the archive is expanded in memory.
 */
const val MAX_CHART_BYTES: Int = 24 * 1024 * 1024

object ExcelImport {

    // -----------------------------------------------------------------------
    // Reading
    // -----------------------------------------------------------------------

    /**
     * Parse a workbook already in memory.
     *
     * Takes bytes rather than a path because Android hands back a `content://` URI
     * from the document picker and there is no filesystem path behind it to open.
     * The reference needed no change for this — its loader passes its argument
     * straight to the workbook library, which accepts a file-like object — and
     * neither does this one.
     *
     * Every failure becomes [ExcelImportException], including the ones that are not
     * [CalcException]: this is called from a tap handler, where an uncaught
     * exception would take the app down mid-lift.
     */
    @Throws(ExcelImportException::class)
    fun readChart(bytes: ByteArray): LoadChartData {
        if (bytes.isEmpty()) throw ExcelImportException("That file was empty.")
        if (bytes.size > MAX_CHART_BYTES) {
            throw ExcelImportException(
                "That file is ${bytes.size / 1048576} MB, too large to be a load " +
                    "chart. Load charts are small tables."
            )
        }
        return try {
            ExcelLoader.loadChart(Xlsx.read(bytes))
        } catch (exc: CalcException) {
            // The loader's own wording, re-raised as this type.  The text is kept
            // verbatim — it is already written for this failure — but the *type* is
            // unified, because a caller that has to catch two exception types to
            // report one failure is a caller that will eventually catch only one.
            throw ExcelImportException(exc.message ?: NOT_A_WORKBOOK)
        } catch (exc: java.util.zip.ZipException) {
            throw ExcelImportException(NOT_A_WORKBOOK)
        } catch (exc: Exception) {
            val message = exc.message.orEmpty()
            if (message.contains("zip", ignoreCase = true)) {
                throw ExcelImportException(NOT_A_WORKBOOK)
            }
            throw ExcelImportException("That workbook could not be read: $message")
        }
    }

    // -----------------------------------------------------------------------
    // The chart's own shape
    // -----------------------------------------------------------------------

    /**
     * The boom lengths the operator may switch between, in workbook order.
     *
     * Empty for a column-format workbook, which has no boom columns to choose
     * between — and that empty list is what the screen uses to decide whether to
     * show the boom dropdown at all.
     */
    fun availableBooms(chart: LoadChartData): List<Double> {
        if (!chart.matrix || chart.booms.isEmpty()) return emptyList()
        return chart.booms.map { it }
    }

    /**
     * Which boom column the chart is read from.
     *
     * Given a [boom], that one — the nearest match, which is the loader's own
     * resolution rather than an equality test that would disagree on a chart whose
     * column headers are rounded.
     *
     * Given none, **the desktop's default, which is the last entry and not
     * `max()`**.  `_populate_boom_selector` fills its combo in `chart.booms` order
     * and then selects `count() - 1` under the comment *"default to the longest
     * boom extension"*.  The two readings agree on any chart whose booms ascend,
     * which is every real one, and disagree on one that does not — and the rule is
     * *match the desktop*, so the last entry it is.
     */
    fun selectedBoomIndex(chart: LoadChartData, boom: Double? = null): Int {
        if (!chart.matrix || chart.booms.isEmpty()) return 0
        return chart.boomIndex(boom ?: chart.booms.last())
    }

    /** The boom length the chart is read from, or null for a column-format chart. */
    fun selectedBoomLength(chart: LoadChartData, boom: Double? = null): Double? {
        if (!chart.matrix || chart.booms.isEmpty()) return null
        return chart.booms[selectedBoomIndex(chart, boom)]
    }

    /**
     * The radii the operator may pick at this boom, ascending and deduplicated.
     *
     * A radius is offered when that boom has a rated capacity for it, which is the
     * desktop's own test and the reason a chart can offer 16.0 m at one boom and
     * not at another.  Empty means the screen falls back to free entry.
     */
    fun chartRadii(chart: LoadChartData, boom: Double? = null): List<Double> {
        val found: List<Double?> = if (chart.matrix) {
            val index = selectedBoomIndex(chart, boom)
            chart.rows.mapNotNull { row ->
                if (index < row.capacities.size && row.capacities[index] != null) row.radius else null
            }
        } else {
            chart.rows.mapNotNull { row -> if (row.load != null) row.radius else null }
        }
        return found.filterNotNull().toSortedSet().toList()
    }

    /** The charted points at [boom], as `(radius, capacity, boom or null)`. */
    fun usableRows(chart: LoadChartData, boom: Double? = null): List<Triple<Double, Double, Double?>> {
        val index = selectedBoomIndex(chart, boom)
        val boomOut = selectedBoomLength(chart, boom)
        val out = mutableListOf<Triple<Double, Double, Double?>>()
        if (chart.matrix) {
            for (row in chart.rows) {
                val radius = row.radius ?: continue
                val capacity = row.capacities.getOrNull(index) ?: continue
                out.add(Triple(radius, capacity, boomOut))
            }
        } else {
            for (row in chart.rows) {
                val radius = row.radius ?: continue
                val load = row.load ?: continue
                out.add(Triple(radius, load, row.boom))
            }
        }
        return out
    }

    /**
     * The chart at [boom] as `(radius, capacity)` pairs, in workbook order.
     *
     * No interpolation and no rounding: the chart that is read is the chart that
     * was loaded.  A point with a non-positive radius or capacity is dropped rather
     * than carried, because a capacity that does nothing is worse than an absent
     * one.
     */
    @Throws(ExcelImportException::class)
    fun chartPoints(chart: LoadChartData, boom: Double? = null): List<Pair<Double, Double>> {
        val points = usableRows(chart, boom)
            .filter { it.first > 0.0 && it.second > 0.0 }
            .map { it.first to it.second }
        if (points.isEmpty()) {
            throw ExcelImportException(
                "No charted capacities were found in that workbook. It has the " +
                    "shape of a load chart but every row is empty."
            )
        }
        return points
    }

    /**
     * The working radius the desktop would settle on for this lift, or null.
     *
     * The lift needs `gross / 0.75` of rated capacity, and the radius picked is the
     * **farthest** one whose capacity still meets it — the most reach the chart
     * allows without exceeding the 75% the plan is designed against.  The nearest
     * feasible radius would be safe and would waste reach, which is the opposite of
     * what an operator loading a chart is asking for.
     *
     * Re-asked for every boom, which is the point of the selector: a longer boom
     * carries the same weight further out.
     *
     * Returns null — and the caller must then leave the radius alone — in the two
     * cases the desktop also declines to act.  **No load yet**, so that importing a
     * chart before entering a lift does not choose a radius a later weight would
     * invalidate.  And **nothing meets the requirement**, where snapping to the
     * largest radius would hide the very shortfall the operator needs to see.
     */
    fun suggestRadius(chart: LoadChartData, gross: Double, boom: Double? = null): Double? {
        if (gross <= 0.0) return null
        val required = Calc.requiredChartCapacity(gross)
        val items: List<Pair<Double?, Double?>> = if (chart.matrix) {
            val index = selectedBoomIndex(chart, boom)
            chart.rows.map { row -> row.radius to row.capacities.getOrNull(index) }
        } else {
            chart.rows.map { it.radius to it.load }
        }
        val feasible = items.mapNotNull { (radius, capacity) ->
            if (radius != null && capacity != null && capacity >= required) radius else null
        }
        return feasible.maxOrNull()
    }

    /** The charted capacity at [radius], or null where the desktop has none. */
    fun capacityAt(chart: LoadChartData, radius: Double, boom: Double? = null): Double? {
        if (radius <= 0.0) return null
        return try {
            Calc.lookupChartCapacity(chartPoints(chart, boom), radius)
        } catch (_: CalcException) {
            null
        } catch (_: ExcelImportException) {
            null
        }
    }

    /**
     * The boom of the workbook row nearest [radius], or null.
     *
     * A column-format workbook has no boom column to select from, so the boom for
     * the mirrored entry comes off the row nearest the radius instead.  Rows with
     * no boom of their own are skipped rather than counted as 0 m.
     */
    fun nearestRowBoom(chart: LoadChartData, radius: Double): Double? {
        var best: Double? = null
        var bestDiff = Double.MAX_VALUE
        for (row in chart.rows) {
            val boom = row.boom ?: continue
            val diff = kotlin.math.abs((row.radius ?: continue) - radius)
            if (diff < bestDiff) {
                best = boom
                bestDiff = diff
            }
        }
        return best
    }

    /**
     * What a workbook writes down into the one manual entry, or null.
     *
     * The desktop's *"mirror the current Excel selection (radius, rated load, boom
     * length) into the manual entry so both load-chart views stay in sync"* — three
     * fields, and **not the boom angle**.  A load chart charts radius against rated
     * capacity and does not chart the boom's angle, so there is nothing to mirror
     * and the angle on the entry stays whatever the operator typed.
     *
     * Returns null for the desktop's three declines, each a case where writing
     * something would put a figure on the entry that nobody entered: no chart (the
     * caller's guard), no radius (`<= 0` is a half-typed line, not 0 m), and no
     * capacity there — where writing 0 kg would turn the chart *refusing* a lookup
     * into a charted zero, which is a claim.
     */
    fun mirroredPoint(chart: LoadChartData, radius: Double, boom: Double? = null): Triple<Double, Double, Double?>? {
        if (radius <= 0.0) return null
        val column = if (chart.matrix) boom else null
        val capacity = capacityAt(chart, radius, column) ?: return null
        if (capacity <= 0.0) return null
        return Triple(radius, capacity, if (chart.matrix) boom else nearestRowBoom(chart, radius))
    }

    // -----------------------------------------------------------------------
    // Wording
    // -----------------------------------------------------------------------

    /**
     * Which boom column a matrix chart is read from, or empty for a column chart.
     *
     * **Recomputed on every boom change**, which is what makes it worth saying:
     * a label still reading "31 m" over a 27.5 m chart is worse than no label,
     * because it is a specific wrong answer rather than an omission.
     */
    fun matrixNote(chart: LoadChartData, boom: Double? = null): String {
        val selected = selectedBoomLength(chart, boom) ?: return ""
        return " \u2014 read from boom ${trimmed(selected)} m"
    }

    /**
     * The line shown under the chart, in the desktop's own words.
     *
     * The desktop shows `path (capacity in kg)`.  There is no path to show — the
     * document picker deliberately does not reveal one — so the file's display name
     * stands in, and the unit is carried across because it decides whether a
     * charted "20" means 20 kg or 20 tonnes.
     */
    fun chartLabel(chart: LoadChartData, name: String, boom: Double? = null): String {
        val unit = chart.unit.ifEmpty { "kg" }
        return "$name (capacity in $unit)${matrixNote(chart, boom)}"
    }

    /**
     * A whole number without its trailing zeros, the way Python's `%g` renders it.
     *
     * Java's `%g` is close but pads to six significant digits — it gives "31.0000"
     * where the desktop prints "31" — so the padding comes off here rather than in
     * the label, or the two surfaces would disagree about a figure on a lift plan.
     */
    private fun trimmed(value: Double): String {
        val formatted = String.format(Locale.US, "%g", value)
        return if (formatted.contains('.')) formatted.trimEnd('0').trimEnd('.') else formatted
    }

    // -----------------------------------------------------------------------
    // The jib
    // -----------------------------------------------------------------------

    /**
     * The jib tables a workbook offers, as `(length, offset)` pairs in order.
     *
     * Folded from the parsed [JibColumn]s down to the distinct pairs that pick one,
     * because that pair — not the whole column with its chart — is what a selector
     * lists and what [jibPoints] is later asked for.  A duplicate is dropped rather
     * than counted: a jib sheet that writes the same heading twice is one table.
     */
    fun jibOptions(chart: LoadChartData): List<JibOption> {
        val options = mutableListOf<JibOption>()
        for (column in chart.jibColumns) {
            val option = JibOption(column.length, column.offset)
            if (!options.contains(option)) options.add(option)
        }
        return options
    }

    /**
     * The selected jib column's `(boom angle, capacity kg)` points, or empty.
     *
     * Matched on **both** the length and the offset, because a length alone picks
     * between tables that share it and differ by their offset angle.  A half-made
     * selection — either figure null — is no selection at all and yields empty
     * rather than a table nobody chose.
     *
     * A tolerance rather than an equality test, exactly as the loader's own
     * `boomIndex` does: a length or offset is a number read out of a cell, and
     * `25.0` written in a spreadsheet is not reliably `25.0` when it arrives here.
     */
    fun jibPoints(
        chart: LoadChartData,
        length: Double?,
        offset: Double?,
    ): List<Pair<Double, Double>> {
        if (length == null || offset == null) return emptyList()
        val column = chart.jibColumns.firstOrNull { candidate ->
            kotlin.math.abs(candidate.length - length) < 1e-9 &&
                kotlin.math.abs(candidate.offset - offset) < 1e-9
        } ?: return emptyList()
        return column.points
    }

    /**
     * The boom angle an operator would settle on for this lift, or null.
     *
     * The jib's counterpart of [suggestRadius]: the lift needs `gross / 0.75` of
     * rated capacity — that ratio read off [Calc], not typed again here, so the two
     * suggestions cannot drift apart — and the angle picked is the **farthest
     * reach** whose capacity still meets it.  A jib reaches further the *lower* the
     * boom, so the farthest reach is the **smallest** charted angle that passes.
     *
     * A separate function from [suggestRadius] rather than one shared body, because
     * a jib table is read against the boom's angle where the main chart is read
     * against a radius: the same rule over a different axis.
     *
     * Returns null for the two cases the desktop also declines.  **No load yet**, so
     * that choosing a jib before entering a lift does not pick an angle a later
     * weight would invalidate.  And **nothing meets the requirement**, where
     * snapping to a charted angle would hide the very shortfall the operator needs
     * to see.
     */
    fun suggestJibAngle(points: List<Pair<Double, Double>>, gross: Double): Double? {
        if (gross <= 0.0) return null
        val required = Calc.requiredChartCapacity(gross)
        return points.filter { it.second >= required }.map { it.first }.minOrNull()
    }

    /**
     * The inputs after the operator changes the jib configuration or selection.
     *
     * The boom angle is re-suggested for the new table — the farthest reach that
     * still meets the 75% rule — the same way the working radius is suggested for
     * the main boom.  With no lift to check against it stays unset and the graph's
     * geometry supplies it.
     *
     * A configuration that is neither `"boom_jib"` nor `"boom_ext_jib"` — an empty
     * string included — becomes `"boom"`: the reference's own normalisation, kept
     * rather than replaced by an assumption about what a caller passes.
     */
    fun jibChanged(
        inputs: CraneInputs,
        chart: LoadChartData,
        config: String,
        length: Double?,
        offset: Double?,
        gross: Double,
    ): CraneInputs {
        val normalised =
            if (config == "boom_jib" || config == "boom_ext_jib") config else "boom"
        val active = normalised != "boom"
        val points = if (active) jibPoints(chart, length, offset) else emptyList()
        val suggested = if (active) suggestJibAngle(points, gross) else null

        // The manual entry's own angle is written from the suggestion, and 0.0 —
        // *not entered* — when there is none.  An angle left over from the table
        // that was in charge a moment ago would be a figure the operator never
        // entered for the one now selected.
        val rows = inputs.rows.toMutableList()
        if (rows.isNotEmpty()) {
            rows[0] = rows[0].copy(angle = suggested ?: 0.0)
        }

        return inputs.copy(
            rows = rows,
            jibConfig = normalised,
            jibLength = if (active) length else null,
            jibOffset = if (active) offset else null,
            chartJibPoints = points,
            boomAngleDeg = suggested,
        )
    }

    // -----------------------------------------------------------------------
    // What an import does to the form
    // -----------------------------------------------------------------------

    /**
     * The inputs after importing [chart].
     *
     * The whole consequence of an import, in one place, so the screen only has to
     * call it: the boom defaults to the desktop's column, the radius is the
     * desktop's suggestion, the workbook's points are published, and the one manual
     * entry is *mirrored* from the selection rather than replaced by the chart.
     */
    fun importedInputs(
        inputs: CraneInputs,
        chart: LoadChartData,
        name: String,
        gross: Double,
    ): CraneInputs {
        val booms = availableBooms(chart)
        val boom = booms.lastOrNull()
        return applied(inputs, chart, name, boom, gross)
    }

    /** The inputs after the operator picks a different boom. */
    fun boomChanged(
        inputs: CraneInputs,
        chart: LoadChartData,
        boom: Double,
        gross: Double,
    ): CraneInputs = applied(inputs, chart, inputs.chartName, boom, gross)

    private fun applied(
        inputs: CraneInputs,
        chart: LoadChartData,
        name: String,
        boom: Double?,
        gross: Double,
    ): CraneInputs {
        val suggested = suggestRadius(chart, gross, boom)
        // A declined suggestion leaves the radius where it was.  Unguarded, a
        // decline would snap the radius to 0 m and the tab would report a capacity
        // at 0 m -- a plausible figure for a wrong one.
        val radius = suggested ?: inputs.workingRadius
        val points = runCatching { chartPoints(chart, boom) }.getOrDefault(emptyList())
        val mirrored = mirroredPoint(chart, radius, boom)
        val rows = mirrorInto(inputs.rows, mirrored)

        return inputs.copy(
            rows = rows,
            chartPoints = points,
            chartSource = "excel",
            chartName = name,
            chartLabel = chartLabel(chart, name, boom),
            chartBooms = availableBooms(chart),
            chartBoom = boom,
            chartRadii = chartRadii(chart, boom),
            workingRadius = radius,
            // The chart's own extras, and a reset to the main boom: an import — and
            // a boom change, which re-applies through here — opens on the main boom
            // alone.  `chartJibs` is *published*, not cleared, so the tables the
            // workbook offers stay selectable at every boom.
            chartPosition = chart.position,
            chartJibs = jibOptions(chart),
            jibConfig = "boom",
            jibLength = null,
            jibOffset = null,
            chartJibPoints = emptyList(),
            boomAngleDeg = null,
        )
    }

    /**
     * The inputs after the operator picks a different working radius.
     *
     * **The mirror follows, and that is the whole point of it.**  The manual entry
     * is *this selection written down* — the desktop's own comment is "so both
     * load-chart views stay in sync" — so a radius change that did not reach the
     * entry would leave the two views disagreeing about the same lift, which is the
     * one thing the mirror exists to prevent.
     *
     * The suggested radius is deliberately **not** re-asked here.  This radius is
     * the operator's own choice, and re-suggesting would override the figure they
     * just picked with one the chart prefers.
     */
    fun radiusChanged(inputs: CraneInputs, chart: LoadChartData, radius: Double): CraneInputs {
        val mirrored = mirroredPoint(chart, radius, inputs.chartBoom)
        // `copy` carries the jib fields untouched: a radius change leaves the same
        // jib configuration in place.  The published boom angle is cleared instead,
        // because it belonged to the old radius and the geometry derives the new
        // one — the reference's own reset in `radius_changed`.
        return inputs.copy(
            workingRadius = radius,
            rows = mirrorInto(inputs.rows, mirrored),
            boomAngleDeg = null,
        )
    }

    /**
     * Write a mirrored point into the one manual entry.
     *
     * Three fields and never the boom angle: a load chart charts radius against
     * rated capacity and does not chart the angle, so the entry keeps whatever the
     * operator typed there.
     *
     * A null point leaves the entry **untouched** rather than blanking it.  The
     * reference's declines are all cases where writing something would put a figure
     * on the entry that nobody entered — no radius, or no capacity at that radius,
     * where writing 0 kg would turn the chart *refusing* a lookup into a charted
     * zero, which is a claim.
     */
    private fun mirrorInto(
        rows: List<ChartRow>,
        mirrored: Triple<Double, Double, Double?>?,
    ): List<ChartRow> {
        if (mirrored == null) return rows
        val entry = rows.firstOrNull() ?: ChartRow()
        return listOf(
            entry.copy(
                radius = mirrored.first,
                capacity = mirrored.second,
                // The desktop's `if boom is not None` guard: a column-format
                // workbook has no boom of its own, and blanking the entry's would
                // lose the operator's own figure.
                boom = mirrored.third ?: entry.boom,
            )
        )
    }

    /**
     * The inputs after the operator types in the manual entry.
     *
     * **This drops the workbook entirely — boom and all — and that is a deliberate
     * narrowing of the desktop**, which keeps its chart loaded and only flips a
     * flag.  On a phone, keeping the chart would leave a boom selector populated
     * over a chart that is no longer in charge: a control that cannot do anything.
     * The narrowing is named here so a later reader does not read it as a bug, and
     * so parity is not "restored" without asking.
     */
    fun forgetChart(inputs: CraneInputs): CraneInputs = inputs.copy(
        rows = listOf(inputs.rows.firstOrNull() ?: ChartRow()),
        chartPoints = emptyList(),
        chartSource = "",
        chartName = "",
        chartLabel = "",
        chartBooms = emptyList(),
        chartBoom = null,
        chartRadii = emptyList(),
        // **Everything the workbook supplied goes**, the jib included — where an
        // import or a boom change only resets the *selection* and leaves the jib
        // tables on offer.  `jibConfig` returns to "boom" rather than to the
        // reference's empty string, because "boom" is this port's spelling of the
        // main boom and the two words differ only in how they read.  A null angle
        // and position keep the Graph tab self-contained until a chart arrives.
        chartPosition = null,
        chartJibs = emptyList(),
        jibConfig = "boom",
        jibLength = null,
        jibOffset = null,
        chartJibPoints = emptyList(),
        boomAngleDeg = null,
    )
}
