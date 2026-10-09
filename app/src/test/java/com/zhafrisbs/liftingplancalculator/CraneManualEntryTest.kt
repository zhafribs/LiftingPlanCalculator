package com.zhafrisbs.liftingplancalculator

import com.zhafrisbs.liftingplancalculator.core.ChartRow
import com.zhafrisbs.liftingplancalculator.core.Crane
import com.zhafrisbs.liftingplancalculator.core.CraneInputs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The crane's capacity check against a **hand-typed** line.
 *
 * The manual entry is one *point* with four columns — radius, rated load, boom angle,
 * boom length — and all four have to arrive in the result.  Three of them did: the
 * radius and the load drive the lookup, and the angle was read off the row.  The boom
 * was read from the *workbook* instead, so on a hand-typed chart it came back null and
 * the card showed a dash beside a number the operator had just entered.
 *
 * That mismatch is the reason this file exists.  Half the row was right, which is what
 * made the omission hard to see — a card with one wrong row in it reads as a card that
 * is still filling in, not as a bug.
 */
class CraneManualEntryTest {

    @Test
    fun aHandTypedLineCarriesAllFourOfItsColumnsIntoTheResult() {
        val result = Crane.solve(
            CraneInputs(
                name = "tadano",
                capacityT = 20.0,
                rows = listOf(
                    ChartRow(radius = 10.0, capacity = 1600.0, angle = 48.0, boom = 31.0),
                ),
                workingRadius = 10.0,
            ),
            gross = 500.0,
        )

        assertEquals(31.0, result.boomM ?: Double.NaN, 1e-9)
        assertEquals(48.0, result.boomAngleDeg ?: Double.NaN, 1e-9)
        assertEquals(1600.0, result.chartCapacityKg ?: Double.NaN, 1e-9)
        assertEquals(10.0, result.radiusM, 1e-9)
        assertEquals("Manual chart", result.capacitySource)
    }

    @Test
    fun theBoomIsLookedUpAtTheSameRadiusTheRestOfTheRowIsReadAt() {
        // The entry is a point, so the boom belongs to a row rather than to the tab.
        // With no working radius typed, the solve falls back to the row's own — and
        // the boom has to follow it there, or it would be looked up somewhere the
        // capacity was not.
        val result = Crane.solve(
            CraneInputs(
                rows = listOf(
                    ChartRow(radius = 6.0, capacity = 9000.0, boom = 20.0),
                    ChartRow(radius = 10.0, capacity = 1600.0, boom = 31.0),
                ),
                workingRadius = 0.0,
            ),
            gross = 500.0,
        )
        assertEquals(6.0, result.radiusM, 1e-9)
        assertEquals(20.0, result.boomM ?: Double.NaN, 1e-9)
    }

    @Test
    fun aWorkbookSuppliesTheBoomWhenItIsTheChartInCharge() {
        // When a workbook is loaded it wins, and it supplies the boom — the manual
        // entry beside it is a mirror of the workbook's own selection, which is what
        // the import writes.
        val result = Crane.solve(
            CraneInputs(
                name = "Kobelco CKE90G-2",
                capacityT = 90.0,
                rows = listOf(ChartRow(radius = 10.0, capacity = 16000.0, boom = 40.0)),
                workingRadius = 10.0,
                chartSource = "excel",
                chartPoints = listOf(10.0 to 16000.0),
                chartBoom = 40.0,
            ),
            gross = 2000.0,
        )
        assertEquals(40.0, result.boomM ?: Double.NaN, 1e-9)
        assertEquals("Excel chart", result.capacitySource)
    }

    @Test
    fun anUnsetBoomIsAbsentRatherThanZeroMetres() {
        // Zero is "not entered", not "a 0 m boom": the field starts empty, and a card
        // reading `0.00 m` would be the app answering a question nobody asked.  The
        // same is true of the angle, which has behaved this way all along.
        val result = Crane.solve(
            CraneInputs(
                rows = listOf(ChartRow(radius = 10.0, capacity = 1600.0, angle = 48.0)),
                workingRadius = 10.0,
            ),
            gross = 500.0,
        )
        assertNull(result.boomM)
        assertEquals(48.0, result.boomAngleDeg ?: Double.NaN, 1e-9)
    }
}
