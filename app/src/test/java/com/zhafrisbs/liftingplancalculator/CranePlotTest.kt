package com.zhafrisbs.liftingplancalculator

import com.zhafrisbs.liftingplancalculator.ui.screens.capacityPlot
import com.zhafrisbs.liftingplancalculator.ui.screens.niceMax
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the capacity plot's shape, for the reason the workbook table's is pinned: a picture
 * drawn from the wrong points still looks like a chart.
 *
 * The two functions are pure, which is why they can be checked here at all -- there is no
 * UI harness in this project, and the two rules that can be silently wrong are both
 * arithmetic rather than drawing: which points reach the plot, and where the scale runs to.
 */
class CranePlotTest {

    // -----------------------------------------------------------------------
    // Which points are plotted
    // -----------------------------------------------------------------------

    @Test
    fun theChartsOwnPointsArePlottedInRadiusOrder() {
        // A workbook's points arrive in the workbook's order, and a curve drawn in that
        // order doubles back on itself wherever the sheet was not sorted by radius.
        val chart = listOf(10.0 to 2_500.0, 3.0 to 7_000.0, 16.0 to 1_500.0)
        assertEquals(
            listOf(3.0 to 7_000.0, 10.0 to 2_500.0, 16.0 to 1_500.0),
            capacityPlot(chart, emptyList()),
        )
    }

    @Test
    fun aRadiusTheChartAlreadyChartsIsNotPlottedTwice() {
        // The manual entry mirrors the point the check was read at, so with a workbook
        // loaded the entry's radius is usually one the chart already charts.  Plotting it
        // twice is a dot drawn twice for a point there is only one of -- which is what the
        // desktop's own `seen` set exists to prevent.
        val chart = listOf(3.0 to 7_000.0, 10.0 to 2_500.0, 16.0 to 1_500.0)
        val entry = listOf(10.0 to 2_500.0)
        assertEquals(
            listOf(3.0 to 7_000.0, 10.0 to 2_500.0, 16.0 to 1_500.0),
            capacityPlot(chart, entry),
        )
    }

    @Test
    fun anEntryBesideTheChartIsPlotted() {
        // A hand-typed radius the chart does not chart is a point in its own right: the
        // entry is the second chart, and the picture has to show the point being checked
        // rather than only the ones the workbook supplied.
        val chart = listOf(5.0 to 10_000.0, 10.0 to 5_000.0)
        assertEquals(
            listOf(5.0 to 10_000.0, 8.0 to 6_000.0, 10.0 to 5_000.0),
            capacityPlot(chart, listOf(8.0 to 6_000.0)),
        )
    }

    @Test
    fun onePointIsNotAPicture() {
        // A single point is a dot on an empty grid, and the desktop draws nothing at all
        // rather than a chart with nothing to read.  A hand-typed chart is exactly this
        // case: one row is one charted point, and one point is not a curve.
        assertTrue(capacityPlot(emptyList(), listOf(10.0 to 2_500.0)).isEmpty())
        assertTrue(capacityPlot(listOf(10.0 to 2_500.0), emptyList()).isEmpty())
    }

    @Test
    fun aHalfEnteredPointIsNotPlotted() {
        // A row with a radius but no capacity -- or the other way round -- is a line still
        // being typed, not a charted zero.  Plotting it would put a point on the curve that
        // nobody entered and that a reviewer would read as the chart's own.
        assertTrue(capacityPlot(listOf(0.0 to 2_500.0, 10.0 to 0.0), emptyList()).isEmpty())
    }

    // -----------------------------------------------------------------------
    // Where the scale runs to
    // -----------------------------------------------------------------------

    @Test
    fun theAxisRunsToTheNextRoundMaximum() {
        // Four grid divisions over a round maximum is what keeps every grid line a figure
        // an operator can read off.
        assertEquals(2.0, niceMax(1.4), 1e-9)
        assertEquals(5.0, niceMax(4.2), 1e-9)
        assertEquals(10.0, niceMax(8.9), 1e-9)
        assertEquals(20.0, niceMax(16.0), 1e-9)
        assertEquals(50_000.0, niceMax(28_000.0), 1e-9)
        // On the boundary, and round rather than up: a maximum already at a round figure is
        // not pushed to the next one.
        assertEquals(2.0, niceMax(2.0), 1e-9)
        assertEquals(5.0, niceMax(5.0), 1e-9)
        // Never zero: a scale of zero cannot be divided by, and the desktop's own fallback
        // of one says the same.
        assertEquals(1.0, niceMax(0.0), 1e-9)
    }
}
