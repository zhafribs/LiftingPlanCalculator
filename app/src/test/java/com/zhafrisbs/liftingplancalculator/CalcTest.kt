package com.zhafrisbs.liftingplancalculator

import com.zhafrisbs.liftingplancalculator.core.Calc
import com.zhafrisbs.liftingplancalculator.core.CalcException
import com.zhafrisbs.liftingplancalculator.core.DEFAULT_DENSITY_KG_M3
import com.zhafrisbs.liftingplancalculator.core.Figures
import com.zhafrisbs.liftingplancalculator.core.LiftTotals
import com.zhafrisbs.liftingplancalculator.core.LoadItem
import com.zhafrisbs.liftingplancalculator.core.LoadType
import com.zhafrisbs.liftingplancalculator.core.TackleItem
import com.zhafrisbs.liftingplancalculator.core.UsageState
import com.zhafrisbs.liftingplancalculator.core.craneUsageBand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the ported engine to the reference's arithmetic.
 *
 * The expected values here were produced by running
 * `lifting_calculator/calc.py` directly, so this is a comparison against the
 * reference rather than against a transcription of it.  That distinction is the
 * whole point: a test written from the same reading as the code it checks agrees
 * with it whether or not either is right.
 */
class CalcTest {

    // -- load lines --------------------------------------------------------

    @Test
    fun fixedWeightMultipliesByQuantity() {
        assertEquals(300.0, Calc.fixedWeight(100.0, 3), 1e-9)
        assertEquals(100.0, Calc.fixedWeight(100.0), 1e-9)
    }

    @Test
    fun weightByMetreMultipliesRateLengthAndQuantity() {
        // 5 kg/m over 10 m is 50 kg; two of them is 100 kg.
        assertEquals(50.0, Calc.weightByMetre(5.0, 10.0, 1), 1e-9)
        assertEquals(100.0, Calc.weightByMetre(5.0, 10.0, 2), 1e-9)
    }

    @Test
    fun weightByVolumeMultipliesVolumeDensityAndQuantity() {
        // A 0.5 m3 steel billet at the default density.
        assertEquals(
            0.5 * DEFAULT_DENSITY_KG_M3,
            Calc.weightByVolume(0.5, DEFAULT_DENSITY_KG_M3, 1),
            1e-9,
        )
    }

    @Test
    fun negativeInputsAreRejectedWithTheReferencesWording() {
        assertEquals(
            "Fixed weight cannot be negative.",
            assertThrows(CalcException::class.java) { Calc.fixedWeight(-1.0) }.message,
        )
        assertEquals(
            "Material density must be greater than 0.",
            assertThrows(CalcException::class.java) {
                Calc.weightByVolume(1.0, 0.0)
            }.message,
        )
        assertEquals(
            "Weight per metre and length cannot be negative.",
            assertThrows(CalcException::class.java) {
                Calc.weightByMetre(-1.0, 1.0)
            }.message,
        )
    }

    @Test
    fun aZeroQuantityLineStillCarriesItsWeightOnALoadLineButNotOnTackle() {
        // The two floors are the reference's spin-box floors, and they differ on
        // purpose: a load line floors at 1, a tackle line at 0.
        assertEquals(250.0, Calc.fixedWeight(250.0, 1), 1e-9)
        assertEquals(0.0, TackleItem(weightKg = 250.0, qty = 0).subtotal(), 1e-9)
    }

    @Test
    fun anIncompleteLineWeighsNothingRatherThanFailing() {
        val perMetreWithoutLength = LoadItem(loadType = LoadType.PER_METRE, kgPerMetre = 5.0)
        assertEquals(0.0, perMetreWithoutLength.weight(), 1e-9)
    }

    // -- the lift ----------------------------------------------------------

    @Test
    fun totalsAddLoadAndTackleAndSizeTheCraneAtSeventyFivePercent() {
        val totals = LiftTotals.of(
            loadItems = listOf(LoadItem(weightKg = 1500.0)),
            tackleItems = listOf(TackleItem(weightKg = 50.0, qty = 2)),
        )
        assertEquals(1500.0, totals.load, 1e-9)
        assertEquals(100.0, totals.tackle, 1e-9)
        assertEquals(1600.0, totals.gross, 1e-9)
        assertEquals(1600.0 / 0.75, totals.requiredChartCapacity, 1e-9)
    }

    @Test
    fun theRequiredCapacityIsAlwaysAboveTheGrossSoTheOverFlagStaysOff() {
        // Ported as written.  `required = gross / 0.75` exceeds `gross` for any
        // positive gross, so the reference's "over" flag is unreachable; the
        // assertion records that rather than hiding it.
        val totals = LiftTotals.of(listOf(LoadItem(weightKg = 9000.0)), emptyList())
        assertEquals(false, totals.overLimit)
    }

    @Test
    fun updatingALineKeepsItsIdentitySoAFieldIsNotWrittenToTheWrongRow() {
        val first = LoadItem(name = "Load")
        val second = LoadItem(name = "Load")
        assertTrue(first.id != second.id)
        assertEquals(first.id, first.copy(weightKg = 500.0).id)
    }

    // -- crane capacity ----------------------------------------------------

    @Test
    fun chartUsageBandsFollowTheSeventyFivePercentWindow() {
        assertEquals(UsageState.WITHIN, craneUsageBand(0.0))
        assertEquals(UsageState.WITHIN, craneUsageBand(75.0))
        assertEquals(UsageState.CAUTION, craneUsageBand(75.1))
        assertEquals(UsageState.CAUTION, craneUsageBand(100.0))
        assertEquals(UsageState.OVER, craneUsageBand(100.1))
    }

    @Test
    fun chartLookupInterpolatesBetweenChartedRadii() {
        val chart = listOf(5.0 to 10000.0, 10.0 to 5000.0)
        assertEquals(10000.0, Calc.lookupChartCapacity(chart, 3.0), 1e-9) // below the chart
        assertEquals(10000.0, Calc.lookupChartCapacity(chart, 5.0), 1e-9)
        assertEquals(7500.0, Calc.lookupChartCapacity(chart, 7.5), 1e-9) // midway
        assertEquals(5000.0, Calc.lookupChartCapacity(chart, 10.0), 1e-9)
        assertEquals(5000.0, Calc.lookupChartCapacity(chart, 22.0), 1e-9) // beyond the chart
    }

    // -- formatting --------------------------------------------------------

    @Test
    fun figuresUseACommaGroupingAndADotDecimalRegardlessOfDeviceLocale() {
        assertEquals("1,234.50", Figures.fmt(1234.5, 2))
        assertEquals("1,234.50 kg", Figures.fmt(1234.5, 2, "kg"))
        assertEquals("\u2014", Figures.fmt(null, 2, "kg"))
        assertEquals("0.00 kg", Figures.fmt(0.0, 2, "kg"))
    }
}
