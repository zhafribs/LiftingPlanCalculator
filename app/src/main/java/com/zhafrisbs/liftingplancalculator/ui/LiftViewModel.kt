package com.zhafrisbs.liftingplancalculator.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.zhafrisbs.liftingplancalculator.core.ANGLE_MAX
import com.zhafrisbs.liftingplancalculator.core.ANGLE_MIN
import com.zhafrisbs.liftingplancalculator.core.CONFIG_BOOM
import com.zhafrisbs.liftingplancalculator.core.CONFIG_BOOM_EXT_JIB
import com.zhafrisbs.liftingplancalculator.core.CONFIG_BOOM_JIB
import com.zhafrisbs.liftingplancalculator.core.CONFIGS
import com.zhafrisbs.liftingplancalculator.core.ChartRow
import com.zhafrisbs.liftingplancalculator.core.Crane
import com.zhafrisbs.liftingplancalculator.core.CraneGraphState
import com.zhafrisbs.liftingplancalculator.core.CraneInputs
import com.zhafrisbs.liftingplancalculator.core.CraneResult
import com.zhafrisbs.liftingplancalculator.core.DEFAULT_STATE
import com.zhafrisbs.liftingplancalculator.core.ExcelImport
import com.zhafrisbs.liftingplancalculator.core.JibOption
import com.zhafrisbs.liftingplancalculator.core.LiftTotals
import com.zhafrisbs.liftingplancalculator.core.LoadChartData
import com.zhafrisbs.liftingplancalculator.core.LoadItem
import com.zhafrisbs.liftingplancalculator.core.NonuniformInputs
import com.zhafrisbs.liftingplancalculator.core.OneLegInputs
import com.zhafrisbs.liftingplancalculator.core.ReportInputs
import com.zhafrisbs.liftingplancalculator.core.TackleItem
import com.zhafrisbs.liftingplancalculator.core.TandemResult
import com.zhafrisbs.liftingplancalculator.core.angleForWorkingRadius
import com.zhafrisbs.liftingplancalculator.core.clamp
import com.zhafrisbs.liftingplancalculator.core.manualRadius
import com.zhafrisbs.liftingplancalculator.core.solveNonuniform
import com.zhafrisbs.liftingplancalculator.core.solveOneLeg
import com.zhafrisbs.liftingplancalculator.core.tipForState
import com.zhafrisbs.liftingplancalculator.core.workingRadius
import kotlin.math.abs
import kotlin.math.floor

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
        solveCrane()
    }

    /** Import a workbook: the boom, the radius and the mirrored entry follow from it. */
    fun importCraneChart(chart: LoadChartData, name: String, gross: Double) {
        craneChart = chart
        craneInputs = ExcelImport.importedInputs(craneInputs, chart, name, gross)
        solveCrane()
    }

    /** Read the chart from a different boom column, re-suggesting the radius. */
    fun selectCraneBoom(boom: Double, gross: Double) {
        val chart = craneChart ?: return
        craneInputs = ExcelImport.boomChanged(craneInputs, chart, boom, gross)
        solveCrane()
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
        solveCrane()
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
        solveCrane()
    }

    /**
     * The crane's own answer, solved once and read by both tabs.
     *
     * Solved here rather than in the screen because the Graph tab's angle feeds
     * straight back into it: with a jib fitted the capacity is looked up *at* that
     * angle, so the two tabs are two views of one check rather than two checks that
     * agree until one of them is refreshed.  The reference solves into a single
     * `store.solved` for the same reason.
     *
     * Null only when the inputs cannot be solved at all -- a negative lift weight,
     * which the form cannot produce and the engine still refuses.
     */
    var craneResult by mutableStateOf<CraneResult?>(null)
        private set

    /**
     * Solve the crane, and mirror the answer into the manual entry.
     *
     * **Call this after every write to [craneInputs], [loadItems] or [tackleItems].**
     * The gross is half of the check, so a line of tackle weighs on the answer as much
     * as the chart does, and an answer left stale here would be a stale answer on the
     * report too.  The reference's store has the same shape: every `update` schedules a
     * solve, and the screens only ever draw `solved`.
     */
    private fun solveCrane() {
        val gross = LiftTotals.of(loadItems, tackleItems).gross
        val result = runCatching { Crane.solve(craneInputs, gross) }.getOrNull()
        craneResult = result
        result?.let(::mirrorSolvedRow)
    }

    /**
     * Write the solved figures into the manual entry.
     *
     * The reference's own block in `crane.js`'s `render`, and it is there so the entry,
     * the check and the report read the same thing: the entry is a *view* of the selected
     * chart point as much as it is somewhere to type one.
     *
     * Only the new-format workbook gets this treatment -- the one carrying a crane
     * position -- because a hand-typed chart has no workbook for the entry to be a view
     * of, and the entry is then the operator's own.
     *
     * **Every write is conditional on the value differing, and that guard is
     * load-bearing rather than an optimisation.**  The mirror runs *inside* the solve and
     * writes the state the solve reads, which is the one arrangement in which a standing
     * write closes the solve cycle into a loop -- and the reference's store, which
     * schedules a solve for every `update`, guards its own mirror for exactly that
     * reason.  The guard is what makes the cycle's next pass find nothing to do.  It is
     * the desktop's own, and it is kept on every write this function makes.
     */
    private fun mirrorSolvedRow(result: CraneResult) {
        val inputs = craneInputs
        if (inputs.chartPosition == null) return
        val jibActive = inputs.jibActive
        val row = inputs.rows.firstOrNull() ?: ChartRow()
        val angle = result.boomAngleDeg?.takeIf { it != 0.0 }?.let(::round2)
        val radius = if (jibActive) result.radiusM.takeIf { it > 0.0 }?.let(::round2) else null
        val capacity =
            if (jibActive) result.chartCapacityKg?.takeIf { it != 0.0 }?.let(::round2) else null
        val boom = if (jibActive) inputs.chartBoom?.takeIf { it != 0.0 } else null
        val mirrored = row.copy(
            radius = radius ?: row.radius,
            capacity = capacity ?: row.capacity,
            angle = angle ?: row.angle,
            boom = boom ?: row.boom,
        )
        val rowDiffers = listOfNotNull(
            angle?.let { abs(row.angle - it) > TOLERANCE },
            radius?.let { abs(row.radius - it) > TOLERANCE },
            capacity?.let { abs(row.capacity - it) > TOLERANCE },
            boom?.let { abs(row.boom - it) > TOLERANCE },
        ).any { it }
        // The working radius belongs to the assembly once a jib is fitted, not to the
        // chart picker: keeping the shared value on the derived reach is what lets a
        // configuration switch -- or a return to the main boom -- continue from where
        // the hook actually is.
        val followedRadius = radius?.takeIf { abs(inputs.workingRadius - it) > TOLERANCE }
        val followsRadius = followedRadius != null
        if (rowDiffers || followsRadius) {
            craneInputs = inputs.copy(
                rows = inputs.rows.mapIndexed { index, existing ->
                    if (index == 0) mirrored else existing
                },
                workingRadius = followedRadius ?: inputs.workingRadius,
            )
        }
    }

    fun clearCrane() {
        craneChart = null
        craneInputs = CraneInputs()
        // The diagram is drawn from the crane, so clearing the crane clears it too: a
        // working range still drawn to a chart nobody has loaded would be the one figure
        // on the Graph tab that could not be trusted.  The sync's bookkeeping goes with
        // it, because a remembered workbook over a cleared crane is a state the desktop
        // cannot reach, and it would suppress the next workbook's pull.
        forgetGraphSync()
        graphState = DEFAULT_STATE
        solveCrane()
    }

    // -----------------------------------------------------------------------
    // The Graph tab, and the crane state the two tabs share
    // -----------------------------------------------------------------------

    /**
     * The working-range diagram's inputs.
     *
     * Here rather than in the Graph screen because it is **not the graph's alone**.
     * The desktop keeps one `state.crane` that both tabs read and write, and that one
     * state is what makes the diagram follow the workbook the Crane tab loaded; the
     * screen `remember`ed its own copy, seeded from the desktop's defaults, so neither
     * tab could see the other's figures at all.
     *
     * It is also here for the reason the rest of this ViewModel is: a rotation rebuilds
     * the screen, and a diagram that came back at 45 degrees after the operator had
     * dragged it to 60 would be the same fault as a half-entered lift being lost.
     */
    var graphState by mutableStateOf(DEFAULT_STATE)
        private set

    /**
     * Whether the operator has dragged the diagram away from the workbook.
     *
     * The desktop's `deviated`: it is what offers "Reset to Crane Tab" in place of the
     * card's note, and what keeps a re-solve of the *same* crane inputs from undoing the
     * drag.  State rather than a `remember`, for the same reason [graphState] is: the
     * flag and the position have to survive a rotation together, or the diagram would
     * come back deviated with no way of saying so.
     */
    var graphDeviated by mutableStateOf(false)
        private set

    // --- the workbook link's bookkeeping ------------------------------------
    //
    // The reference's `workbookMode` / `workbookKey` / `workbookChassis` / `deviated`,
    // in one region because they are one mechanism: what the diagram was last pulled
    // toward, whether a workbook is in charge of it at all, and whether the operator has
    // since dragged away from it.  Together they are what stops the sync from fighting
    // the operator, so a reader needs to be able to take them in at once.

    /** Whether a workbook is in charge of the diagram (the reference's `workbookMode`). */
    private var workbookMode = false

    /**
     * The parts of the crane tab's input that move the *whole* diagram: where the crane
     * stands, how long the boom is, and which jib is fitted to it.
     *
     * The reference keeps this beside the reading so it can tell a change that moves the
     * diagram from one that only moves the figure it is read at -- and so the *view*
     * knows whether to re-fit, from its own `chassisChanged && autoFit`.  The camera is
     * the screen's, which re-fits whenever [graphState] changes, so only the first of
     * those two questions is asked here.
     */
    private var workbookChassis: WorkbookChassis? = null

    /**
     * The one figure the diagram is *read* at: the jib chart's boom angle, or the main
     * boom's working radius.
     *
     * The reference joins this onto the chassis to make a single `workbookKey`; the two
     * halves are held apart here so the sync can say which of them moved.  Which one it
     * is depends on the configuration -- and the configuration is part of the chassis, so
     * a switch from one to the other cannot be mistaken for a reading that held still.
     */
    private var workbookReading: Double? = null

    /** The chassis as a value: two of these are the same when they say the same thing. */
    private data class WorkbookChassis(
        val x: Double,
        val y: Double,
        val boom: Double,
        val config: String,
        val jib: Double,
        val offset: Double,
    )

    /** Forget the workbook the diagram was following (the reference's own four clears). */
    private fun forgetGraphSync() {
        workbookMode = false
        workbookChassis = null
        workbookReading = null
        graphDeviated = false
    }

    /**
     * Pull the diagram toward the workbook the Crane tab is showing.
     *
     * The reference's `syncFromWorkbook`.  A re-pull happens only when one of the crane
     * tab's own figures changed, or when the diagram is not deviated: once the operator
     * has dragged away from the workbook, a re-solve of the *same* inputs has to leave
     * their drag alone, which is the whole point of [graphDeviated].
     *
     * [fit] is the caller's auto-fit setting -- the reference's `autoFit`, in its own
     * `applyWorkbook(src, chassisChanged && autoFit)`.  There is no camera in a ViewModel
     * to move, so the decision is *returned* rather than acted on: true means the
     * diagram's chassis moved while [fit] allowed a re-fit, and the screen -- which owns
     * the camera, and re-fits whenever [graphState] changes -- is the one that can use it.
     *
     * @return whether the caller should re-fit its own view.
     */
    fun applyWorkbook(fit: Boolean): Boolean {
        val inputs = craneInputs
        val position = inputs.chartPosition
        val boom = inputs.chartBoom ?: 0.0
        if (inputs.chartSource != "excel" || position == null || boom <= 0.0) {
            forgetGraphSync()
            return false
        }
        workbookMode = true
        val config = inputs.jibConfig.ifEmpty { CONFIG_BOOM }
        val jibActive = config == CONFIG_BOOM_JIB || config == CONFIG_BOOM_EXT_JIB
        val chassis = WorkbookChassis(
            x = position.first,
            y = position.second,
            boom = boom,
            config = config,
            jib = if (jibActive) inputs.jibLength ?: 0.0 else 0.0,
            offset = if (jibActive) inputs.jibOffset ?: 0.0 else 0.0,
        )
        // The input side of the sync differs by configuration: the main-boom chart is
        // read by working radius, the jib charts by boom angle.
        val reading = if (jibActive) inputs.boomAngleDeg ?: 0.0 else inputs.workingRadius
        val chassisChanged = chassis != workbookChassis
        if (chassisChanged || reading != workbookReading || !graphDeviated) {
            workbookChassis = chassis
            workbookReading = reading
            patchGraphFromWorkbook(chassis, inputs)
            graphDeviated = false
            return fit && chassisChanged
        }
        return false
    }

    /**
     * The reference's `applyWorkbook(src, fit)`: the diagram's own patch.
     *
     * The angle is the subtle part, and it is subtle because the two charts are read
     * differently.  The main-boom chart is read by working radius, so the angle is
     * *derived* from the radius; the jib charts are read by boom angle, so with a jib
     * fitted the angle is the *input* and the radius follows from the geometry.
     *
     * A radius the geometry cannot solve leaves the angle exactly as it was rather than
     * falling back to zero: the reference's own `catch` says so, and a zero here would
     * draw a horizontal boom and call it the operator's lift.
     */
    private fun patchGraphFromWorkbook(chassis: WorkbookChassis, inputs: CraneInputs) {
        val jibActive = chassis.config == CONFIG_BOOM_JIB || chassis.config == CONFIG_BOOM_EXT_JIB
        var next = graphState.copy(
            config = if (chassis.config in CONFIGS) chassis.config else CONFIG_BOOM,
            craneX = chassis.x,
            craneY = chassis.y,
            // A workbook with no boom column leaves the length the diagram already has,
            // rather than dropping it to zero.
            boomLength = if (chassis.boom > 0.0) chassis.boom else graphState.boomLength,
        )
        if (next.config == CONFIG_BOOM_JIB) next = next.copy(jibLength = chassis.jib)
        if (next.config == CONFIG_BOOM_EXT_JIB) next = next.copy(extJibLength = chassis.jib)
        next = next.copy(jibOffset = chassis.offset)

        val published = inputs.boomAngleDeg ?: 0.0
        val angle = if (jibActive && published > 0.0) {
            round2(clamp(published, ANGLE_MIN, ANGLE_MAX))
        } else {
            // The chart's working radius is measured from the x = 0 axis, so the
            // horizontal reach from the crane is the radius less the crane's own X.
            val reach = inputs.workingRadius - chassis.x
            if (reach > 0.0 && next.boomLength > 0.0) {
                runCatching {
                    angleForWorkingRadius(
                        next.boomLength,
                        chassis.jib,
                        chassis.offset,
                        reach,
                    )
                }.map { round2(clamp(it, ANGLE_MIN, ANGLE_MAX)) }.getOrNull()
            } else {
                null
            }
        }
        angle?.let { next = next.copy(angle = it) }
        graphState = next
    }

    /**
     * The angle the diagram is drawn at, published back to the Crane tab.
     *
     * The reference's `publishAngle`.  Without a jib the angle comes *from* a live
     * working radius, so there is nothing to publish until one is entered; with one the
     * angle is the input and the working radius it reaches is published alongside it, so
     * a configuration switch -- or a return to the main boom -- continues from where the
     * hook actually is.
     *
     * **Every write below is conditional on the value differing, and that guard is
     * load-bearing rather than an optimisation.**  This runs on every sync, and a sync
     * runs whenever the Crane tab's inputs change -- so an unconditional write here would
     * change an input, which would sync again, which would write again.  That is a loop
     * with no arithmetic in it and nothing to show for itself; the guard is what ends it.
     */
    fun publishAngle() {
        if (!workbookMode) return
        val inputs = craneInputs
        val jibActive = inputs.jibActive
        if (!jibActive && inputs.workingRadius <= 0.0) return
        val angle = graphState.angle
        if (angle <= 0.0) return
        val derivedRadius =
            if (jibActive && !graphDeviated) round2(workingRadius(tipForState(graphState))) else null
        val row = inputs.rows.firstOrNull()
        val published = inputs.boomAngleDeg
        val anglePublished = published != null && abs(published - angle) < TOLERANCE
        val rowMirrored = row == null || abs(row.angle - angle) < TOLERANCE
        val radiusPublished =
            derivedRadius == null || abs(inputs.workingRadius - derivedRadius) < TOLERANCE
        if (anglePublished && rowMirrored && radiusPublished) return
        craneInputs = inputs.copy(
            boomAngleDeg = angle,
            rows = inputs.rows.mapIndexed { index, existing ->
                if (index == 0) existing.copy(angle = angle) else existing
            },
            workingRadius = derivedRadius ?: inputs.workingRadius,
        )
        // The published angle is an input to the check -- with a jib fitted the capacity
        // is looked up *at* it -- so the solve has to follow the write.
        solveCrane()
    }

    /**
     * Forget the deviation and pull the diagram back to the Crane tab.
     *
     * The reference's `resetToCrane`: clearing what the diagram was last pulled toward is
     * what forces the re-pull, because the sync only pulls when an input changed or the
     * diagram is not deviated.  The angle is published afterwards for the reason the sync
     * publishes it -- the two tabs are one state, and the operator has just asked for the
     * diagram to be the Crane tab's.
     */
    fun resetGraphToCrane() {
        workbookChassis = null
        workbookReading = null
        graphDeviated = false
        applyWorkbook(fit = true)
        publishAngle()
    }

    /**
     * The operator changed the diagram's own controls.
     *
     * The reference's `apply(patch, { user: true })`: an edit on this screen marks a
     * deviation and publishes the angle, so the Crane tab follows the diagram the moment
     * it is dragged rather than at the next re-sync.  A patch from the workbook itself
     * goes through [applyWorkbook] instead, and marks nothing.
     */
    fun updateGraphState(state: CraneGraphState) {
        if (workbookMode) graphDeviated = true
        graphState = state
        publishAngle()
    }

    /**
     * The operator dragged the crane marker to ([x], [y]) on the axis.
     *
     * The reference's `emitCrane`, minus the clamp and the one-decimal rounding, which
     * belong to the screen that knows the view's own world window.  The "did it actually
     * move" guard is the reference's: a drag that has not yet moved the marker must not
     * publish an angle.
     */
    fun updateGraphCranePosition(x: Double, y: Double) {
        if (x == graphState.craneX && y == graphState.craneY) return
        updateGraphState(graphState.copy(craneX = x, craneY = y))
    }

    /**
     * The operator picked a boom angle from the jib chart's own angles.
     *
     * The reference's `store.setBoomAngle`: the angle is the jib charts' *input*, so
     * setting it here is what lets the Crane tab drive the jib check without the Graph
     * tab having been opened at all.  The manual entry's angle is written with it, because
     * the entry is this selection written down.
     */
    fun setBoomAngle(angle: Double) {
        craneInputs = craneInputs.copy(
            boomAngleDeg = angle,
            rows = craneInputs.rows.mapIndexed { index, row ->
                if (index == 0) row.copy(angle = angle) else row
            },
        )
        solveCrane()
    }

    /**
     * The operator chose a jib configuration, or one of its offset angles.
     *
     * The reference's `store.chooseJib`, which is the one place both tabs' controls meet:
     * the configuration picks its jib -- the plain jib is the workbook's shortest table,
     * the extended jib its longest -- so the only figure left to choose is the offset
     * angle that jib allows, and an offset it does not allow falls back to its first.
     *
     * The selection then goes through the engine's own [ExcelImport.jibChanged], which
     * re-suggests the boom angle for the new table; the diagram is re-synced after it
     * because it is drawn from the same configuration, and a control that changed one of
     * the two tabs silently would be a lie.
     */
    fun chooseJib(config: String? = null, offset: Double? = null) {
        val chart = craneChart ?: return
        val inputs = craneInputs
        val nextConfig = config ?: inputs.jibConfig
        val length: Double?
        val nextOffset: Double?
        if (nextConfig == CONFIG_BOOM) {
            length = null
            nextOffset = null
        } else {
            val lengths = inputs.chartJibs.map(JibOption::length).toSortedSet()
            length = if (nextConfig == CONFIG_BOOM_EXT_JIB) {
                lengths.lastOrNull()
            } else {
                lengths.firstOrNull()
            }
            val allowed = if (length == null) {
                emptyList()
            } else {
                inputs.chartJibs
                    .filter { abs(it.length - length) < TOLERANCE }
                    .map { it.offset }
                    .sorted()
            }
            val chosen = offset ?: inputs.jibOffset
            nextOffset = if (chosen == null || allowed.none { abs(it - chosen) < TOLERANCE }) {
                allowed.firstOrNull()
            } else {
                chosen
            }
        }
        val gross = LiftTotals.of(loadItems, tackleItems).gross
        craneInputs = ExcelImport.jibChanged(inputs, chart, nextConfig, length, nextOffset, gross)
        solveCrane()
        applyWorkbook(fit = true)
        publishAngle()
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

    // Every one of these re-solves: the gross is half of the crane's check, so a line
    // added, edited or deleted changes the answer the Crane tab and the report show.

    fun addLoadItem() {
        loadItems.add(LoadItem())
        solveCrane()
    }

    fun addTackleItem() {
        tackleItems.add(TackleItem())
        solveCrane()
    }

    fun updateLoadItem(item: LoadItem) {
        val index = loadItems.indexOfFirst { it.id == item.id }
        if (index >= 0) loadItems[index] = item
        solveCrane()
    }

    fun updateTackleItem(item: TackleItem) {
        val index = tackleItems.indexOfFirst { it.id == item.id }
        if (index >= 0) tackleItems[index] = item
        solveCrane()
    }

    fun removeLoadItem(id: Long) {
        loadItems.removeAll { it.id == id }
        solveCrane()
    }

    fun removeTackleItem(id: Long) {
        tackleItems.removeAll { it.id == id }
        solveCrane()
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
        solveCrane()
    }

    /**
     * Solve once at creation.
     *
     * The reference does the same -- "the engine is the source of truth for the initial
     * screen too" -- and it is what lets the Crane tab draw its answer on the first frame
     * instead of waiting for the first keystroke: an empty crane still has something to
     * say, and it says it before anything is typed.
     *
     * Declared last because it reads state declared throughout the class, and a property
     * initialiser runs in declaration order.
     */
    init {
        solveCrane()
    }
}

/**
 * Round to the two decimals both tabs and the report print.
 *
 * The reference's `Math.round(value * 100) / 100`, written out as its own definition --
 * `floor(x + 0.5)`, so a tie rounds **up** -- because that is what JavaScript's
 * `Math.round` is, and the printed figures have to agree with the desktop on the boundary
 * values as well as between them.  Naming a `round` instead would put the tie direction
 * behind whichever way that name happens to break ties.
 */
private fun round2(value: Double): Double = floor(value * 100.0 + 0.5) / 100.0

/**
 * Two figures this close are the same figure.
 *
 * The reference's own `1e-9`, used everywhere it asks "did this actually change".  A
 * looser tolerance would swallow a real change and a tighter one would read the
 * floating-point residue of an identical calculation as a change -- which, on the sync
 * path, is the difference between a settled diagram and one that re-publishes for ever.
 */
private const val TOLERANCE = 1e-9
