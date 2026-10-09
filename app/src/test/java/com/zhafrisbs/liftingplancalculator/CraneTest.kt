package com.zhafrisbs.liftingplancalculator

import com.zhafrisbs.liftingplancalculator.core.ChartRow
import com.zhafrisbs.liftingplancalculator.core.Crane
import com.zhafrisbs.liftingplancalculator.core.CraneInputException
import com.zhafrisbs.liftingplancalculator.core.CraneInputs
import com.zhafrisbs.liftingplancalculator.core.UsageState
import com.zhafrisbs.liftingplancalculator.core.exampleCraneInputs
import com.zhafrisbs.liftingplancalculator.core.manualRadius
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Pins the crane check to the reference's own output.
 *
 * Every expectation below was printed by running
 * `lifting_calculator/kivy_ui/crane_state.py` directly:
 *
 *     .venv/bin/python -c "from lifting_calculator.kivy_ui import crane_state as c; \
 *         print(c.solve(c.example_inputs(), 2000.0))"
 *
 * That is the point of this file.  A test written from the same reading of the
 * source as the code it checks agrees with the code whether or not either is
 * right — so the numbers here come from executing the original, not from
 * transcribing it.
 */
class CraneTest {

    private val example = exampleCraneInputs()

    @Test
    fun theWorkedExamplePublishesTheReferencesOwnFigures() {
        val result = Crane.solve(example, 2000.0)

        assertEquals("Kobelco CKE90G-2", result.craneName)
        assertEquals(90.0, result.craneCapacityT, 1e-9)
        assertEquals(90000.0, result.craneCapacityKg, 1e-9)
        assertEquals(8.0, result.radiusM, 1e-9)
        assertEquals(true, result.chartLoaded)
        assertNull(result.chartPath)
        // 8.00 m is below the only charted radius of 10.00 m, so the lookup falls
        // back to the closest charted value rather than refusing.
        assertEquals(16000.0, result.chartCapacityKg!!, 1e-9)
        assertEquals(12.5, result.capacityUsagePercent!!, 1e-9)
        assertEquals("Manual chart", result.capacitySource)
        assertEquals(
            "WITHIN LIMIT \u2014 12.50% of capacity at working radius",
            result.capacityStatus,
        )
        assertEquals(UsageState.WITHIN, result.capacityBand)
        assertEquals("#2e7d52", result.capacityColour)
        // **A deliberate divergence from the desktop, pinned here so it is visible.**
        //
        // The sentence this replaces read "a load chart does not chart the boom's
        // angle or a matrix boom, so the manual chart has neither" -- directly above
        // an assertion that the angle *is* 48.  The comment and its own next line
        // disagreed, which is how the gap survived: `assertNull(boomM)` was carried
        // over from the reference without anyone asking why it was null, and the
        // answer (the desktop's `boom_m` comes from the workbook's boom *column*,
        // which a hand-typed chart does not have) was mistaken for "a manual chart
        // has no boom".
        //
        // The row does carry one, and reads it nowhere else.  Restore
        // `inputs.chartBoom` alone in `Crane.solve` and this becomes null again.
        assertEquals(40.0, result.boomM ?: Double.NaN, 1e-9)
        assertEquals(48.0, result.boomAngleDeg!!, 1e-9)
        assertEquals(1, result.chartRows)
    }

    @Test
    fun aWorkbookIsInterpolatedAndWinsWhenTheOperatorHasNotTypedInTheEntry() {
        val inputs = CraneInputs(
            chartPoints = listOf(5.0 to 10000.0, 10.0 to 5000.0),
            workingRadius = 7.5,
            chartSource = "excel",
        )
        val result = Crane.solve(inputs, 2000.0)

        assertEquals(7500.0, result.chartCapacityKg!!, 1e-9) // midway between the two
        assertEquals(26.666666666666668, result.capacityUsagePercent!!, 1e-9)
        assertEquals("Excel chart", result.capacitySource)
        assertEquals(UsageState.WITHIN, result.capacityBand)
        assertEquals(2, result.chartRows)
    }

    @Test
    fun excelOnlyWinsWhenACapacityWasActuallyFoundAtTheRadius() {
        // A workbook with no usable point at this radius must not claim a source it
        // did not supply -- the reference's own rule.
        val inputs = CraneInputs(
            rows = listOf(ChartRow(radius = 12.0, capacity = 8000.0)),
            chartPoints = listOf(5.0 to 10000.0, 10.0 to 5000.0),
            workingRadius = 7.5,
            chartSource = "excel",
        )
        val result = Crane.solve(inputs, 2000.0)

        assertEquals("Excel chart", result.capacitySource)
        assertEquals(7500.0, result.chartCapacityKg!!, 1e-9)
    }

    @Test
    fun theManualEntryIsTheChartWhenNoWorkbookIsInCharge() {
        // Same inputs as above but with no workbook flag: the typed line wins, and
        // the radius falls back to the entry's own radius because the working radius
        // is set... so this pins the working radius being used.
        val inputs = CraneInputs(
            rows = listOf(ChartRow(radius = 12.0, capacity = 8000.0)),
            chartPoints = listOf(5.0 to 10000.0, 10.0 to 5000.0),
            workingRadius = 7.5,
        )
        val result = Crane.solve(inputs, 2000.0)

        assertEquals("Manual chart", result.capacitySource)
        assertEquals(8000.0, result.chartCapacityKg!!, 1e-9)
        assertEquals(1, result.chartRows)
    }

    @Test
    fun aNegativeLiftWeightIsRefusedWithTheReferencesWording() {
        assertEquals(
            "The lift weight cannot be negative.",
            assertThrows(CraneInputException::class.java) {
                Crane.solve(example, -1.0)
            }.message,
        )
    }

    @Test
    fun anEmptyChartReportsThatThereIsNothingToCheckAgainst() {
        val result = Crane.solve(CraneInputs(), 2000.0)
        assertEquals(false, result.chartLoaded)
        assertNull(result.chartCapacityKg)
        assertNull(result.capacityUsagePercent)
        assertEquals("Enter the load chart to check capacity.", result.capacityStatus)
    }

    @Test
    fun theWorkingRadiusFollowsASingleCompleteEntryAndIgnoresAHalfTypedOne() {
        assertEquals(
            12.0,
            manualRadius(listOf(ChartRow(radius = 12.0, capacity = 8000.0)), 0.0)!!,
            1e-9,
        )
        // A radius with no capacity is a line still being typed, not a radius to
        // move the whole tab to.
        assertNull(manualRadius(listOf(ChartRow(radius = 12.0)), 0.0))
        assertNull(manualRadius(listOf(ChartRow()), 0.0))
    }

    @Test
    fun aZeroBoomAngleIsNotEnteredRatherThanZeroDegrees() {
        // The reference publishes 0 as absent: a report row reading "0.0 deg" beside
        // a blank angle box would be the app inventing a figure.
        assertNull(Crane.manualAngle(10.0, listOf(ChartRow(radius = 10.0, capacity = 100.0))))
    }
}
