package com.zhafrisbs.liftingplancalculator

import com.zhafrisbs.liftingplancalculator.core.Crane
import com.zhafrisbs.liftingplancalculator.core.CraneInputs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the jib configuration's check to the reference's own output.
 *
 * With a jib fitted the workbook is read at the **boom angle** rather than the
 * working radius, and the jib table replaces the main-boom chart entirely.  The
 * numbers below are the desktop's own: `crane.rs`, the jib branch near line 359
 * and `implied_boom_angle` near line 459.
 *
 * A test written from the same reading of the source as the code it checks agrees
 * with the code whether or not either is right, so the literals are carried across
 * from the reference verbatim rather than recomputed here.
 */
class CraneJibTest {

    @Test
    fun aJibConfigurationDerivesTheWorkingRadiusFromTheBoomAngle() {
        val inputs = CraneInputs(
            chartPoints = listOf(5.0 to 10000.0, 30.0 to 5000.0),
            workingRadius = 5.0, // not what the jib chart is read at
            chartSource = "excel",
            chartBooms = listOf(34.0),
            chartBoom = 34.0,
            chartPosition = 0.0 to 2.8,
            jibConfig = "boom_ext_jib",
            jibLength = 12.8,
            jibOffset = 0.0,
            chartJibPoints = listOf(45.0 to 400.0, 83.0 to 2000.0),
            boomAngleDeg = 60.0,
        )
        val result = Crane.solve(inputs, 1000.0)
        // 34 cos 60 + 12.8 cos 60 = 23.4 m from the axis.
        assertEquals(23.4, result.radiusM, 1e-9)
        assertEquals(60.0, result.boomAngleDeg!!, 1e-9)
    }

    @Test
    fun aJibConfigurationIsCheckedAgainstTheJibTableAtTheBoomAngle() {
        val inputs = CraneInputs(
            chartPoints = listOf(5.0 to 35000.0, 10.0 to 21000.0),
            workingRadius = 10.0,
            chartSource = "excel",
            chartBooms = listOf(34.0),
            chartBoom = 34.0,
            chartPosition = -1.7 to 2.8,
            jibConfig = "boom_jib",
            jibLength = 7.2,
            jibOffset = 25.0,
            // Jib 7.2 m at 25 deg: 1.8 t at 83 deg, 1.65 t at 72 deg.
            chartJibPoints = listOf(83.0 to 1800.0, 72.0 to 1650.0),
            boomAngleDeg = 72.0,
        )
        val result = Crane.solve(inputs, 1000.0)
        // The jib table wins over the main-boom chart at the same radius.
        assertEquals(1650.0, result.chartCapacityKg!!, 1e-9)
        assertEquals("Excel chart", result.capacitySource)
        assertEquals(72.0, result.boomAngleDeg!!, 1e-9)
    }

    @Test
    fun aJibWithoutAnAnglePublishesNoCapacityRatherThanTheBoomOne() {
        val inputs = CraneInputs(
            chartPoints = listOf(5.0 to 35000.0, 10.0 to 21000.0),
            workingRadius = 0.0,
            chartSource = "excel",
            chartBooms = listOf(34.0),
            chartBoom = 34.0,
            chartPosition = -1.7 to 2.8,
            jibConfig = "boom_jib",
            jibLength = 7.2,
            jibOffset = 25.0,
            chartJibPoints = listOf(83.0 to 1800.0, 72.0 to 1650.0),
        )
        val result = Crane.solve(inputs, 1000.0)
        assertNull(result.chartCapacityKg)
        assertNull(result.capacityUsagePercent)
    }

    @Test
    fun aJibWithoutACranePositionPublishesNoBoomAngle() {
        // The `chartPosition != null` gate in `solve` exists for this case: the
        // implied angle is derived from where the crane sits on the axis, so a
        // workbook that records no position has no axis to derive one from.  The
        // operator still typed a working radius, so without the gate this would
        // report an angle -- and, because the jib chart is read at that angle, a
        // capacity -- that nothing in the workbook supports.
        val inputs = CraneInputs(
            chartPoints = listOf(5.0 to 35000.0, 10.0 to 21000.0),
            workingRadius = 20.0,
            chartSource = "excel",
            chartBooms = listOf(34.0),
            chartBoom = 34.0,
            // chartPosition deliberately left null: no axis to measure from.
            jibConfig = "boom_ext_jib",
            jibLength = 12.8,
            jibOffset = 0.0,
            chartJibPoints = listOf(45.0 to 400.0, 83.0 to 2000.0),
        )
        val result = Crane.solve(inputs, 1000.0)
        assertNull(result.boomAngleDeg)
        // With no angle there is no assembly reach to publish, so the radius stays
        // the one the operator entered rather than a derived one.
        assertEquals(20.0, result.radiusM, 1e-9)
    }

    @Test
    fun aJibAngleIsImpliedFromTheRadiusForTheLowerSolution() {
        // 34 m boom + 12.8 m jib at 0 deg offset: envelope 46.8 m.
        // Radius 23.4 m (crane on the axis) gives the boom at acos(0.5) = 60.
        val inputs = CraneInputs(
            chartBooms = listOf(34.0),
            chartBoom = 34.0,
            chartPosition = 0.0 to 2.8,
            jibConfig = "boom_ext_jib",
            jibLength = 12.8,
            jibOffset = 0.0,
        )
        val angle = Crane.impliedBoomAngle(inputs, 23.4)!!
        assertEquals(60.0, angle, 1e-6)
    }

    @Test
    fun aJibOffsetIsClockwiseAndRaisesTheBoomForTheSameReach() {
        fun withJib(offset: Double) = CraneInputs(
            chartBooms = listOf(34.0),
            chartBoom = 34.0,
            chartPosition = 0.0 to 2.8,
            jibConfig = "boom_ext_jib",
            jibLength = 12.8,
            jibOffset = offset,
        )
        val straight = Crane.impliedBoomAngle(withJib(0.0), 23.4)!!
        val lowered = Crane.impliedBoomAngle(withJib(5.0), 23.4)!!
        // The clockwise offset drops the jib tip, so the boom must rise to
        // keep the same reach: acos(23.4 / 46.7646) + 1.3662 = 61.3419.
        assertEquals(60.0, straight, 1e-9)
        assertEquals(61.34190629216339, lowered, 1e-6)
        assertTrue(lowered > straight)
    }
}
