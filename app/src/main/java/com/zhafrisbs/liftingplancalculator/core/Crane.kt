package com.zhafrisbs.liftingplancalculator.core

import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * The Crane tab's inputs and its calculation.
 *
 * A port of `lifting_calculator/kivy_ui/crane_state.py`.  The reference keeps the
 * calculation out of the view module for a reason worth carrying across: a rule
 * that lives in a widget can only be read, and a rule that can only be read can
 * only be hoped for.  Here that means the whole capacity check is exercisable by a
 * unit test, with the device only ever drawing the answer.
 *
 * **Two charts are looked up and one wins.**
 *
 *  * [CraneInputs.rows] is the *manual entry* — **one** [ChartRow] and only ever
 *    one, because a load chart *entry* is one charted point.  The reference had a
 *    release where the phone grew a row per charted radius, so importing a chart
 *    made the form nineteen lines long; the figures were all correct, which is why
 *    it survived two verified builds.
 *  * [CraneInputs.chartPoints] is a workbook's own points, empty for a chart typed
 *    by hand.
 *
 * They are looked up separately and the winner is chosen by the reference's own
 * rule, not by whichever was filled in last.
 */

/** A crane input that cannot be solved, in words an operator can act on. */
class CraneInputException(message: String) : Exception(message)

/**
 * The manual chart row's own columns, in the reference's order and with its own
 * captions: radius (m), rated load (kg), boom angle (deg), boom length (m).
 */
enum class ChartColumn(
    val key: String,
    val caption: String,
    val unit: String,
    val decimals: Int,
    val min: Double,
    val max: Double,
) {
    RADIUS("radius", "Radius", "m", 2, 0.0, 200.0),
    CAPACITY("capacity", "Rated load", "kg", 0, 0.0, 1.0e7),
    ANGLE("angle", "Boom angle", "deg", 1, 0.0, 90.0),
    BOOM("boom", "Boom length", "m", 2, 0.0, 200.0),
}

/**
 * One line of the load chart entered by hand.
 *
 * Radius, rated load, boom angle, boom length — and **there is only ever one of
 * them**.  A form that grew a row per radius was claiming to be an entry list the
 * desktop does not have.
 */
data class ChartRow(
    val radius: Double = 0.0,
    val capacity: Double = 0.0,
    val angle: Double = 0.0,
    val boom: Double = 0.0,
) {
    /**
     * `(radius, capacity)` for the lookup, or null for a row not in use.
     *
     * A row is in use when both a radius and a capacity are entered.  A half-filled
     * row is not an error and not a zero-capacity chart point: it is a line the
     * operator has not finished, and treating it as a charted value at 0 kg would
     * fail the whole lift for a row still being typed.
     */
    fun pair(): Pair<Double, Double>? =
        if (radius > 0 && capacity > 0) radius to capacity else null
}

/** The tab's whole input surface. */
data class CraneInputs(
    val name: String = "",
    /**
     * The crane's booked rating in tonnes.  Published for the plan's header rather
     * than used in the usage: the usage is taken against the *load chart* figure at
     * the working radius, which is what varies with the radius, where this is one
     * figure for the whole crane.
     */
    val capacityT: Double = 0.0,
    /** The manual entry, as the reference holds it: **one** row. */
    val rows: List<ChartRow> = listOf(ChartRow()),
    /** A workbook's charted points at [chartBoom].  Empty for a chart typed by hand. */
    val chartPoints: List<Pair<Double, Double>> = emptyList(),
    val workingRadius: Double = 0.0,
    /**
     * `""` for a chart typed by hand, `"excel"` for one read from a workbook.  The
     * reference spells this as a boolean; a string is used here because it is also
     * what the report prints, and a report field that has to be translated back
     * into a boolean is one that can disagree with it.
     */
    val chartSource: String = "",
    /**
     * The workbook's display name as the document picker reported it, or empty.
     *
     * Held separately from [chartLabel] because the label is *composed* from it and
     * the selected boom, and has to be recomposed every time the boom changes — a
     * label still reading "31 m" over a 27.5 m chart is a specific wrong answer
     * rather than an omission.
     */
    val chartName: String = "",
    /**
     * The composed label the report prints: the name, the unit the capacities are
     * in, and which boom column was read.
     *
     * A value rather than something computed at the point of use, because the
     * report reads it as one.  Empty rather than a placeholder when nothing is
     * loaded: a label reading "no chart" above a hand-typed row is worse than a
     * blank one.
     */
    val chartLabel: String = "",
    /** The boom lengths the operator may switch between, in workbook order. */
    val chartBooms: List<Double> = emptyList(),
    val chartBoom: Double? = null,
    /** The radii charted at [chartBoom], ascending: what the radius dropdown offers. */
    val chartRadii: List<Double> = emptyList(),
    /**
     * The crane's `Crane location on axis (X, Y)` position, when the workbook
     * records one.  Null for every other workbook, and null is what keeps the
     * Graph tab self-contained: the geometry derives its angle from the axis only
     * once a position says where the axis is.
     */
    val chartPosition: Pair<Double, Double>? = null,
    /** The jib tables the workbook offers, as `(length, offset)` pairs, in order. */
    val chartJibs: List<JibOption> = emptyList(),
    /**
     * Which configuration the jib chart is checked at: `"boom"` for the main boom
     * alone, `"boom_jib"` or `"boom_ext_jib"` for one of the jib tables.  The
     * plain `"boom"` sits here rather than an empty string because it is also what
     * a hand-typed chart means, and [jibActive] reads it as the one question worth
     * asking of it.
     */
    val jibConfig: String = "boom",
    /** The selected jib column's length and offset angle, or null on the main boom. */
    val jibLength: Double? = null,
    val jibOffset: Double? = null,
    /** The selected jib column's chart, as `(boom angle, capacity kg)` points. */
    val chartJibPoints: List<Pair<Double, Double>> = emptyList(),
    /**
     * The boom angle the Graph tab publishes, in degrees from horizontal, or null
     * when nothing has published one.  Held here rather than derived so a jib chart
     * can be looked up at the angle the operator last settled on.
     */
    val boomAngleDeg: Double? = null,
) {
    /**
     * Whether a jib configuration is selected, rather than the main boom alone.
     *
     * **Invariant:** `jibConfig != "boom"` implies `chartSource == "excel"`.  The only
     * writer of a non-`boom` configuration is `ExcelImport.jibChanged`, which is
     * reachable only through `chooseJib`, and that returns early unless a workbook
     * chart is loaded.  Four user-visible sites rest on this: the jib card's radios and
     * readouts, the jib capacity row, the capacity plot's suppression while a jib is
     * fitted, and the assembly radius the report prints in place of the working radius.
     * A non-`boom` configuration over a hand-typed chart would break the last of those
     * in particular, publishing an assembly radius no chart stands behind.
     *
     * Deliberately not written as a conjunct (`jibConfig == "boom" || chartSource ==
     * "excel"`): the disallowed state is unreachable, and the reference's own predicate
     * is equally bare.
     */
    val jibActive: Boolean
        get() = jibConfig == "boom_jib" || jibConfig == "boom_ext_jib"

    /** The manual entry's `(radius, capacity)` points, in row order. */
    fun chartPairs(): List<Pair<Double, Double>> = rows.mapNotNull { it.pair() }

    fun filledRows(): List<ChartRow> = rows.filter { it.pair() != null }
}

/**
 * The working radius that follows the manual entry, or null to leave it alone.
 *
 * *"With a single charted radius the working radius always matches that row; with
 * several rows it is only filled in when still unset."*
 *
 * This lives here rather than in the screen because it is worth checking: without
 * it the operator types a radius of 12 m into the entry and is shown the capacity
 * at the 16 m the dropdown was left on.  That figure *agrees with the dropdown*, so
 * it looks right — which is the whole of the problem.
 *
 * A row with a radius but no capacity is not counted: a half-typed entry is a line
 * still being entered, not a radius to move the whole tab to.
 */
fun manualRadius(rows: List<ChartRow>, current: Double): Double? {
    val valid = rows.filter { it.radius > 0 && it.capacity > 0 }.map { it.radius }
    if (valid.isEmpty()) return null
    if (valid.size == 1) return valid[0]
    return if (current <= 0) valid[0] else null
}

/**
 * A worked lift: a 90 t crane with one charted point, read at 8.00 m.
 *
 * A demonstration rather than a port of a figure — and a *one-point* demonstration,
 * because the manual entry is one point and a two-point seed would have to invent a
 * second row the reference cannot have.
 */
fun exampleCraneInputs(): CraneInputs = CraneInputs(
    name = "Kobelco CKE90G-2",
    capacityT = 90.0,
    rows = listOf(ChartRow(radius = 10.0, capacity = 16000.0, angle = 48.0, boom = 40.0)),
    workingRadius = 8.0,
)

/** The crane's capacity check at the working radius. */
data class CraneResult(
    val craneName: String,
    val craneCapacityT: Double,
    val craneCapacityKg: Double,
    val radiusM: Double,
    val chartLoaded: Boolean,
    val chartPath: String?,
    val chartCapacityKg: Double?,
    val capacityUsagePercent: Double?,
    val capacitySource: String,
    val capacityStatus: String,
    val capacityBand: UsageState?,
    val capacityColour: String,
    val boomM: Double?,
    val boomAngleDeg: Double?,
    val chartRows: Int,
)

object Crane {

    /** The band colours, as the reference publishes them for the report to reuse. */
    private const val COLOUR_WITHIN = "#2e7d52"
    private const val COLOUR_CAUTION = "#b06a1e"
    private const val COLOUR_OVER = "#b23b3b"

    /** Body colour for a figure that is not a judgement, as the desktop's neutral. */
    private const val COLOUR_NEUTRAL = "#2a3347"

    /**
     * The crane's capacity check at the working radius.
     *
     * The winner of the two charts is chosen by the reference's own rule:
     * `use_manual = has_manual and (not from_excel or no_workbook_points) and
     * manual_capacity is not None`.  Here `chartSource` *is* the flag — `"excel"`
     * means the workbook is in charge and the manual entry is a mirror of it,
     * anything else means the operator's own line is the chart.
     */
    @Throws(CraneInputException::class)
    fun solve(inputs: CraneInputs, gross: Double): CraneResult {
        if (gross < 0) throw CraneInputException("The lift weight cannot be negative.")

        val capacityKg = if (inputs.capacityT > 0) inputs.capacityT * 1000.0 else 0.0
        val radius = inputs.workingRadius

        // The workbook at the working radius.
        var chartCapacity: Double? = null
        if (inputs.chartPoints.isNotEmpty() && radius > 0) {
            try {
                chartCapacity = Calc.lookupChartCapacity(inputs.chartPoints, radius)
            } catch (exc: CalcException) {
                // The reference swallows this into a bare null.  Here the message
                // is kept, because on a phone an unexplained blank row is the
                // hardest kind of failure to act on.
                throw CraneInputException(exc.message ?: "The load chart could not be read.")
            }
        }

        val manualPairs = inputs.chartPairs()
        // The radius looked up at: the working radius, or the manual entry's own
        // first radius when there is none.  With no working radius entered it falls
        // back to the first charted radius rather than to zero, which keeps a chart
        // lookup meaningful the moment a row is typed.
        val effectiveRadius =
            if (radius > 0) radius else manualPairs.firstOrNull()?.first ?: 0.0

        var manualCapacity: Double? = null
        if (manualPairs.isNotEmpty() && effectiveRadius > 0) {
            try {
                manualCapacity = Calc.lookupChartCapacity(manualPairs, effectiveRadius)
            } catch (exc: CalcException) {
                throw CraneInputException(exc.message ?: "The load chart could not be read.")
            }
        }

        val useManual = manualPairs.isNotEmpty() &&
            (inputs.chartSource != "excel" || inputs.chartPoints.isEmpty()) &&
            manualCapacity != null
        if (useManual) chartCapacity = manualCapacity

        // The boom angle: the Graph tab's published figure when there is one, else
        // the angle the assembly geometry implies for the working radius (so a jib
        // chart can be checked before the graph is opened), else the manual row's
        // own angle -- the original behaviour.
        //
        // The gate on [CraneInputs.chartPosition] is load-bearing, not a formality:
        // a workbook with no crane position has no axis to derive an angle from, so
        // without it a position-less workbook would start reporting a boom angle it
        // reports none for today.
        val publishedAngle = inputs.boomAngleDeg?.takeIf { it > 0.0 }
        val impliedAngle =
            if (inputs.chartPosition != null) impliedBoomAngle(inputs, effectiveRadius) else null
        val boomAngle = publishedAngle
            ?: impliedAngle
            ?: manualAngle(effectiveRadius, inputs.rows)

        // A jib configuration is checked against the workbook's jib table at the
        // boom angle, and that table replaces the main-boom chart entirely: a
        // radius lookup would read a column that no longer applies once a jib is
        // fitted.  With no angle yet there is nothing honest to publish, so this
        // branch publishes null rather than the boom chart's figure.
        if (inputs.jibActive && inputs.chartSource == "excel" &&
            inputs.chartJibPoints.isNotEmpty()
        ) {
            chartCapacity = boomAngle?.let { angle ->
                try {
                    Calc.lookupChartCapacity(inputs.chartJibPoints, angle)
                } catch (exc: CalcException) {
                    throw CraneInputException(exc.message ?: "The load chart could not be read.")
                }
            }
        }

        val usage = if (chartCapacity != null && gross > 0) {
            Calc.chartUsagePercent(gross, chartCapacity)
        } else {
            null
        }

        var band: UsageState? = null
        var status = "Enter the load chart to check capacity."
        var colour = COLOUR_NEUTRAL
        if (usage != null) {
            band = craneUsageBand(usage)
            status = "${band.wording} \u2014 ${Figures.fmt(usage, 2)}% of capacity at working radius"
            colour = when (band) {
                UsageState.WITHIN -> COLOUR_WITHIN
                UsageState.CAUTION -> COLOUR_CAUTION
                UsageState.OVER -> COLOUR_OVER
            }
        } else if (chartCapacity != null && gross <= 0) {
            status = "Enter the lift weight to check capacity."
        }

        return CraneResult(
            craneName = inputs.name,
            craneCapacityT = inputs.capacityT,
            craneCapacityKg = capacityKg,
            // A jib chart is read at the boom angle, so the working radius is the
            // assembly's reach at that angle, measured from the x = 0 axis.  Only a
            // jib configuration derives it this way; with no jib the working radius
            // is still whatever the operator entered.
            radiusM = if (inputs.jibActive) {
                boomAngle?.let { assemblyRadius(inputs, it) } ?: effectiveRadius
            } else {
                effectiveRadius
            },
            // True whenever *either* chart holds a point, so "loaded" keeps meaning
            // "there is something to look up" rather than "from Excel".
            chartLoaded = manualPairs.isNotEmpty() || inputs.chartPoints.isNotEmpty(),
            // The reference puts the *path* here because it has one.  The phone
            // publishes the display name it showed instead — same slot, same purpose.
            chartPath = inputs.chartLabel.ifEmpty { null },
            chartCapacityKg = chartCapacity,
            capacityUsagePercent = usage,
            // The reference's own two words, and its own rule for choosing between
            // them: Excel only wins when a capacity was actually found at the
            // working radius.  A workbook whose nearest point is unusable says
            // nothing, rather than claiming a source it did not supply.
            capacitySource = when {
                useManual -> "Manual chart"
                inputs.chartSource == "excel" && chartCapacity != null -> "Excel chart"
                else -> ""
            },
            capacityStatus = status,
            capacityBand = band,
            capacityColour = colour,
            // The boom follows the same rule the capacity does: **whichever chart is
            // in charge supplies it.**
            //
            // **This is a deliberate divergence from the desktop.**  The reference
            // reads `inputs.chart_boom` here, and its own note records why:
            // *"chart_boom is set only when a matrix workbook is loaded, which is the
            // desktop's own condition, so the None still means what it means
            // there."*  `gui.py` publishes `boom_combo.currentData() if chart and
            // chart.matrix else None` -- so the desktop, too, shows "Not entered" for
            // a hand-typed line's boom length even though the row carries one.
            //
            // The reason to differ: that leaves the desktop reading the manual row's
            // **angle** and not its **boom** -- two columns of the same chart point,
            // treated differently -- and the column it *did* fix is the angle.  On a
            // phone, a field that is filled in and then ignored reads as a fault,
            // which is exactly how it was reported.  Both are read from the row here.
            // Restore `inputs.chartBoom` alone to match the desktop exactly.
            boomM = if (useManual) {
                manualBoom(effectiveRadius, inputs.rows)
            } else {
                inputs.chartBoom
            },
            boomAngleDeg = boomAngle,
            chartRows = if (useManual) manualPairs.size else inputs.chartPoints.size,
        )
    }

    /**
     * The boom angle that places the hook at the chart's [radius], from the assembly
     * geometry.  This is the same figure the Graph tab draws, solved from the crane
     * tab's own inputs.
     *
     * Two conventions the workbook fixes:
     *
     *  * the radius is measured from the **x = 0 axis**, not from the crane, so the
     *    horizontal reach from the crane is the radius less its own X;
     *  * the jib offset is **clockwise** from the boom axis (a positive offset lowers
     *    the jib), so the offset vector's vertical part is negative.
     *
     * The upper solution is taken -- the hook above the pivot -- and the result is
     * clamped into `[0, 90]` because a lift below the horizon or past vertical is
     * not something the chart can express.  Null when there is no boom, no radius,
     * no boom length, or the radius sits behind the crane.
     */
    fun impliedBoomAngle(inputs: CraneInputs, radius: Double): Double? {
        if (radius <= 0.0) return null
        val boom = inputs.chartBoom ?: return null
        if (boom <= 0.0) return null
        val craneX = inputs.chartPosition?.first ?: 0.0
        val reach = radius - craneX
        if (reach <= 0.0) return null
        val (jib, offset) = if (inputs.jibActive) {
            (inputs.jibLength ?: 0.0) to (inputs.jibOffset ?: 0.0)
        } else {
            0.0 to 0.0
        }
        val offsetRad = Math.toRadians(offset)
        val vx = boom + jib * cos(offsetRad)
        val vy = -jib * sin(offsetRad)
        val envelope = hypot(vx, vy)
        if (envelope <= 0.0) return null
        val ratio = (reach / envelope).coerceIn(0.0, 1.0)
        val base = Math.toDegrees(atan2(vy, vx))
        return (Math.toDegrees(acos(ratio)) - base).coerceIn(0.0, 90.0)
    }

    /**
     * The working radius the assembly reaches at [angle], measured from the x = 0
     * axis.  The forward partner of [impliedBoomAngle], used by the jib charts:
     * their input is the boom angle, so the radius follows from it.
     *
     * The crane's own X is added back in, so the figure is measured from the same
     * axis the chart's radii are.  Null only when there is no usable boom length.
     */
    fun assemblyRadius(inputs: CraneInputs, angle: Double): Double? {
        val boom = inputs.chartBoom ?: return null
        if (boom <= 0.0) return null
        val (jib, offset) = if (inputs.jibActive) {
            (inputs.jibLength ?: 0.0) to (inputs.jibOffset ?: 0.0)
        } else {
            0.0 to 0.0
        }
        val a = Math.toRadians(angle)
        val o = Math.toRadians(offset)
        val reach = boom * cos(a) + jib * cos(a - o)
        val craneX = inputs.chartPosition?.first ?: 0.0
        return craneX + reach
    }

    /**
     * The boom angle of the manual row nearest [radius].
     *
     * Zero is "not entered", not "0 degrees", and a report row reading `0.0 deg`
     * beside a blank angle box would be the app inventing a figure.
     */
    fun manualAngle(radius: Double, rows: List<ChartRow>): Double? {
        val usable = rows.filter { it.pair() != null }
        if (usable.isEmpty() || radius <= 0) return null
        val nearest = usable.minByOrNull { kotlin.math.abs(it.radius - radius) } ?: return null
        return if (nearest.angle != 0.0) nearest.angle else null
    }

    /**
     * The boom length of the manual row nearest [radius], or null when none is set.
     *
     * The same shape as [manualAngle], and for the same reason: the entry is a
     * *point* -- radius, rated load, angle, boom -- so the boom belongs to a row and
     * has to be looked up at the radius the rest of the row was read at.  A separate
     * function rather than one returning both, because the workbook case has a boom
     * and no angle, and a pair would have to invent an angle for it.
     *
     * Zero is "not entered" rather than "a 0 m boom", exactly as it is for the
     * angle: the field starts empty, and a card reading `0.00 m` would be the app
     * answering a question nobody asked.
     */
    fun manualBoom(radius: Double, rows: List<ChartRow>): Double? {
        val usable = rows.filter { it.pair() != null }
        if (usable.isEmpty() || radius <= 0) return null
        val nearest = usable.minByOrNull { kotlin.math.abs(it.radius - radius) } ?: return null
        return if (nearest.boom != 0.0) nearest.boom else null
    }
}
