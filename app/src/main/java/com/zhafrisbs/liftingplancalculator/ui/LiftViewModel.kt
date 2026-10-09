package com.zhafrisbs.liftingplancalculator.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.zhafrisbs.liftingplancalculator.core.ChartRow
import com.zhafrisbs.liftingplancalculator.core.Crane
import com.zhafrisbs.liftingplancalculator.core.CraneInputs
import com.zhafrisbs.liftingplancalculator.core.ExcelImport
import com.zhafrisbs.liftingplancalculator.core.LiftTotals
import com.zhafrisbs.liftingplancalculator.core.LoadChartData
import com.zhafrisbs.liftingplancalculator.core.LoadItem
import com.zhafrisbs.liftingplancalculator.core.NonuniformInputs
import com.zhafrisbs.liftingplancalculator.core.OneLegInputs
import com.zhafrisbs.liftingplancalculator.core.ReportInputs
import com.zhafrisbs.liftingplancalculator.core.TackleItem
import com.zhafrisbs.liftingplancalculator.core.TandemResult
import com.zhafrisbs.liftingplancalculator.core.manualRadius
import com.zhafrisbs.liftingplancalculator.core.solveNonuniform
import com.zhafrisbs.liftingplancalculator.core.solveOneLeg

/**
 * The one lift this session is working on.
 *
 * A [ViewModel] rather than `remember` state so the lift survives a rotation and
 * the system's configuration changes — losing a half-entered plan because the
 * phone was turned sideways is the kind of thing that makes an app untrustworthy
 * on site.
 *
 * The lists are Compose-observable, so every figure downstream recomputes when a
 * line changes.  Updates are by [LoadItem.id] rather than by index because a
 * deletion shifts every index after it, and a row being typed into would then be
 * written back to the wrong line.
 */
class LiftViewModel : ViewModel() {

    val loadItems = mutableStateListOf(LoadItem(name = "Load"))

    /**
     * Tackle opens with **one empty line** rather than none.
     *
     * A section that opens collapsed to a heading and a "tap + to add" note makes
     * the operator find the control before they can use it.  One empty line puts
     * the fields in front of them, and still contributes nothing to the total,
     * because a zero-weight line weighs zero.
     */
    val tackleItems = mutableStateListOf(TackleItem())

    /**
     * The crane tab's inputs.
     *
     * Blank on open, like every other form here: the reference deliberately moved
     * from opening on a worked example to opening empty, because a plan built on
     * top of a seeded 2,000 kg lift carries numbers nobody entered.
     */
    var craneInputs by mutableStateOf(CraneInputs())
        private set

    /**
     * The workbook behind the crane tab, or null for a hand-typed chart.
     *
     * Held here rather than in the screen so a rotation does not lose it: losing a
     * loaded chart because the phone turned sideways would take the operator's
     * figures with it, and re-picking the file is not something to ask for twice.
     *
     * The *inputs* mirror it -- [CraneInputs.chartBooms] and its neighbours are
     * plain data the calculation reads -- while the chart object sits beside them
     * because re-reading a different boom column needs the whole table.
     */
    var craneChart by mutableStateOf<LoadChartData?>(null)
        private set

    fun updateCraneInputs(inputs: CraneInputs) {
        craneInputs = inputs
    }

    /** Import a workbook: the boom, the radius and the mirrored entry follow from it. */
    fun importCraneChart(chart: LoadChartData, name: String, gross: Double) {
        craneChart = chart
        craneInputs = ExcelImport.importedInputs(craneInputs, chart, name, gross)
    }

    /** Read the chart from a different boom column, re-suggesting the radius. */
    fun selectCraneBoom(boom: Double, gross: Double) {
        val chart = craneChart ?: return
        craneInputs = ExcelImport.boomChanged(craneInputs, chart, boom, gross)
    }

    /**
     * A hand-typed change to the chart entry.
     *
     * Drops the workbook entirely -- a deliberate narrowing of the desktop, which
     * keeps its chart loaded and only flips a flag.  Keeping it here would leave a
     * boom selector populated over a chart that is no longer in charge, which is a
     * control that cannot do anything.
     */
    fun editCraneChartRow(row: ChartRow) {
        craneChart = null
        val forgot = ExcelImport.forgetChart(craneInputs).copy(rows = listOf(row))
        // The working radius follows a single complete entry, so an operator who
        // types 12 m is not shown the capacity at the 16 m the chart was left on --
        // a figure that agrees with the chart and is still the wrong one.
        val followed = manualRadius(forgot.rows, forgot.workingRadius)
        craneInputs = if (followed != null) forgot.copy(workingRadius = followed) else forgot
    }

    /**
     * The operator picked a working radius.
     *
     * Re-mirrors the manual entry when a workbook is loaded: the entry is *this
     * selection written down*, so a radius change that did not reach it would leave
     * the two load-chart views disagreeing about the same lift.
     */
    fun selectCraneRadius(radius: Double) {
        val chart = craneChart
        craneInputs = if (chart != null) {
            ExcelImport.radiusChanged(craneInputs, chart, radius)
        } else {
            craneInputs.copy(workingRadius = radius)
        }
    }

    fun clearCrane() {
        craneChart = null
        craneInputs = CraneInputs()
    }

    // -----------------------------------------------------------------------
    // Uniform Load
    // -----------------------------------------------------------------------

    /**
     * The one continuous sling the Uniform Load tab describes.
     *
     * Its opening state is the reference's: everything zero except the choke angle
     * (60 degrees) and the two tag angles (45 degrees), because the operator asked
     * for a blank form and those three are the ordinary figures for almost every
     * lift.  The worked example the *documents* use lives beside it in
     * `EXAMPLE_INPUTS`, built from explicitly-stated values so the opening form and
     * the worked example cannot drift into each other.
     */
    var slingInputs by mutableStateOf(OneLegInputs())
        private set

    fun updateSlingInputs(inputs: OneLegInputs) {
        slingInputs = inputs
    }

    /**
     * The operator picked a different arrangement.
     *
     * Every other value is **kept**, and that is safe rather than lazy: the solver
     * reads only the fields the arrangement declares, so a diameter left over from
     * a round choke is inert under a direct hook rather than wrong.  A leftover tag
     * angle is the one value that could have been wrong, and the engine zeroes that
     * itself for any single-leg arrangement.
     *
     * The alternative -- clearing on every switch -- would ask the operator to
     * retype the sling length and the tag WLL, the two figures that mean the same
     * thing in all nineteen arrangements.
     */
    fun selectSlingHitch(hitch: String) {
        slingInputs = slingInputs.copy(hitch = hitch)
    }

    fun clearSling() {
        slingInputs = OneLegInputs()
    }

    // -----------------------------------------------------------------------
    // Nonuniform Load and Tandem
    // -----------------------------------------------------------------------

    /**
     * The Nonuniform Load tab's inputs.
     *
     * Held separately from [tandemInputs] rather than sharing one scenario-keyed
     * record: the two tabs describe different lifts, and a shared record would mean
     * leaving one tab silently swapped the other's scenario out from under it.
     */
    var nonuniformInputs by mutableStateOf(
        NonuniformInputs(scenario = "asym_2leg_shortening")
    )
        private set

    /** The Tandem tab's inputs.  The scenario is which of the two methods is chosen. */
    var tandemInputs by mutableStateOf(
        NonuniformInputs(scenario = "tandem_aligned_pivot")
    )
        private set

    fun updateNonuniformInputs(inputs: NonuniformInputs) {
        nonuniformInputs = inputs
    }

    fun updateTandemInputs(inputs: NonuniformInputs) {
        tandemInputs = inputs
    }

    /**
     * The operator picked the other tandem method.
     *
     * Every measurement is **kept**: both methods take the same five numbers and
     * differ only in how crane 2's projected distance is resolved, so switching is
     * exactly how an operator compares the two for one and the same lift.  Clearing
     * on the switch would make that comparison impossible.
     */
    fun selectTandemMethod(method: String) {
        tandemInputs = tandemInputs.copy(scenario = method)
    }

    fun clearNonuniform() {
        nonuniformInputs = NonuniformInputs(scenario = "asym_2leg_shortening")
    }

    fun clearTandem() {
        tandemInputs = NonuniformInputs(scenario = tandemInputs.scenario)
    }

    // -----------------------------------------------------------------------
    // The report
    // -----------------------------------------------------------------------

    /**
     * The solved state the report reads.
     *
     * Every tab's result is solved here rather than the tab's own last answer being
     * cached and handed over.  The reference does the latter and says why -- so the
     * report quotes the figures the screen is showing -- but the two only differ if
     * a screen and this function solve from *different* state, and they do not: each
     * reads the same inputs.  Solving here means a report can be produced from any
     * tab without that tab having been visited first, which caching cannot do.
     *
     * A scenario that cannot be solved yet -- a half-filled form, which is the
     * normal state while entering data -- contributes `null` rather than an error,
     * and the builder omits its section.
     */
    fun reportInputs(): ReportInputs {
        val totals = LiftTotals.of(loadItems, tackleItems)
        val nonuniform = runCatching { solveNonuniform(nonuniformInputs, totals.gross) }
        val tandem = runCatching { solveNonuniform(tandemInputs, totals.gross) }
        return ReportInputs(
            totals = totals,
            crane = runCatching { Crane.solve(craneInputs, totals.gross) }.getOrNull(),
            sling = runCatching { solveOneLeg(slingInputs, totals.gross) }.getOrNull(),
            nonuniform = nonuniform.getOrNull(),
            // The failure travels with the absence.  "This section is missing" and
            // "this section is missing because the COG sits outside the span" are
            // different things to tell a reviewer, and only the second one is
            // actionable.
            nonuniformError = nonuniform.exceptionOrNull()?.message,
            tandem = tandem.getOrNull() as? TandemResult,
            tandemError = tandem.exceptionOrNull()?.message,
        )
    }

    fun addLoadItem() {
        loadItems.add(LoadItem())
    }

    fun addTackleItem() {
        tackleItems.add(TackleItem())
    }

    fun updateLoadItem(item: LoadItem) {
        val index = loadItems.indexOfFirst { it.id == item.id }
        if (index >= 0) loadItems[index] = item
    }

    fun updateTackleItem(item: TackleItem) {
        val index = tackleItems.indexOfFirst { it.id == item.id }
        if (index >= 0) tackleItems[index] = item
    }

    fun removeLoadItem(id: Long) {
        loadItems.removeAll { it.id == id }
    }

    fun removeTackleItem(id: Long) {
        tackleItems.removeAll { it.id == id }
    }

    /**
     * Empty both sections.
     *
     * Both at once, because one button that empties *both* sections is the
     * requirement, and clearing them in two steps would leave a form that briefly
     * reports "0 kg" while a tackle line is still drawn.  There is no undo, so
     * the note under the button names what goes and how to add it back.
     */
    fun clear() {
        loadItems.clear()
        tackleItems.clear()
    }
}
