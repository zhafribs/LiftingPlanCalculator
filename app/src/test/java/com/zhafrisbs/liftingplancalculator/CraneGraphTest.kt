package com.zhafrisbs.liftingplancalculator

import com.zhafrisbs.liftingplancalculator.core.ANGLE_MAX
import com.zhafrisbs.liftingplancalculator.core.ANGLE_MIN
import com.zhafrisbs.liftingplancalculator.core.CONFIG_BOOM
import com.zhafrisbs.liftingplancalculator.core.CONFIG_BOOM_EXT_JIB
import com.zhafrisbs.liftingplancalculator.core.CONFIG_BOOM_JIB
import com.zhafrisbs.liftingplancalculator.core.CONFIG_LABELS
import com.zhafrisbs.liftingplancalculator.core.CONFIGS
import com.zhafrisbs.liftingplancalculator.core.DEFAULT_STATE
import com.zhafrisbs.liftingplancalculator.core.GeometryException
import com.zhafrisbs.liftingplancalculator.core.GraphPoint
import com.zhafrisbs.liftingplancalculator.core.angleForTip
import com.zhafrisbs.liftingplancalculator.core.angleForWorkingRadius
import com.zhafrisbs.liftingplancalculator.core.assemblyTip
import com.zhafrisbs.liftingplancalculator.core.boomTip
import com.zhafrisbs.liftingplancalculator.core.clamp
import com.zhafrisbs.liftingplancalculator.core.effectiveJibLength
import com.zhafrisbs.liftingplancalculator.core.envelopePoints
import com.zhafrisbs.liftingplancalculator.core.envelopeRadius
import com.zhafrisbs.liftingplancalculator.core.normalizeAngle
import com.zhafrisbs.liftingplancalculator.core.readouts
import com.zhafrisbs.liftingplancalculator.core.tipForState
import com.zhafrisbs.liftingplancalculator.core.tipOffsetFromPivot
import com.zhafrisbs.liftingplancalculator.core.workingRadius
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the working-range geometry to the desktop's own output.
 *
 * Every expectation below was printed by executing the desktop's own
 * `ui/js/graph/geometry.js` under `node` — the module is plain ES-module
 * JavaScript with no Qt or browser dependency, so it runs directly:
 *
 *     node --input-type=module -e "import * as geo from \
 *         '/mnt/backup/Appimage Projects/lifting-plan-calculator-v2/ui/js/graph/geometry.js'; \
 *         console.log(geo.readouts(geo.DEFAULT_STATE))"
 *
 * The desktop deliberately diverged from the original `crane_graph/geometry.py`
 * that this project's earlier code followed: its jib offset **lowers** the jib and
 * its working radius is measured from the `x = 0` axis rather than from the crane.
 * The literals here therefore come from *executing the desktop module*, not from
 * either source's prose, so a test written from the same reading of the code as the
 * code it checks cannot agree with a shared mistake.  The full-precision figures
 * (`21.213203435596427`) are raw output, not tidied up: rounding them would throw
 * away exactly the agreement being asserted.
 */
class CraneGraphTest {

    /** A configuration to round-trip, so the three are exercised rather than the one. */
    private data class Case(
        val boom: Double,
        val jib: Double,
        val offset: Double,
        val angle: Double,
    )

    private val origin = GraphPoint(0.0, 0.0)

    // -----------------------------------------------------------------------
    // Constants and defaults
    // -----------------------------------------------------------------------

    @Test
    fun theConstantsAreTheReferencesOwn() {
        assertEquals(listOf("boom", "boom_jib", "boom_ext_jib"), CONFIGS)
        assertEquals("Boom length", CONFIG_LABELS[CONFIG_BOOM])
        assertEquals("Boom length + jib", CONFIG_LABELS[CONFIG_BOOM_JIB])
        assertEquals("Boom length + extended jib", CONFIG_LABELS[CONFIG_BOOM_EXT_JIB])
        assertEquals(0.0, ANGLE_MIN, 0.0)
        assertEquals(85.0, ANGLE_MAX, 0.0)
    }

    @Test
    fun theDefaultStateIsTheReferencesDefaultState() {
        // The reference's `DEFAULT_STATE` dict, key for key.
        assertEquals(CONFIG_BOOM, DEFAULT_STATE.config)
        assertEquals(30.0, DEFAULT_STATE.boomLength, 0.0)
        assertEquals(12.0, DEFAULT_STATE.jibLength, 0.0)
        assertEquals(24.0, DEFAULT_STATE.extJibLength, 0.0)
        assertEquals(0.0, DEFAULT_STATE.jibOffset, 0.0)
        assertEquals(45.0, DEFAULT_STATE.angle, 0.0)
        assertEquals(0.0, DEFAULT_STATE.envMin, 0.0)
        assertEquals(80.0, DEFAULT_STATE.envMax, 0.0)
        assertEquals(0.0, DEFAULT_STATE.craneX, 0.0)
        assertEquals(0.0, DEFAULT_STATE.craneY, 0.0)
    }

    // -----------------------------------------------------------------------
    // The three configurations
    // -----------------------------------------------------------------------

    @Test
    fun eachConfigurationPicksItsOwnJibLength() {
        // A default state carries both a 12 m jib and a 24 m extended jib, and the
        // configuration decides which one — if any — is actually fitted.
        assertEquals(0.0, effectiveJibLength(CONFIG_BOOM, 12.0, 24.0), 0.0)
        assertEquals(12.0, effectiveJibLength(CONFIG_BOOM_JIB, 12.0, 24.0), 0.0)
        assertEquals(24.0, effectiveJibLength(CONFIG_BOOM_EXT_JIB, 12.0, 24.0), 0.0)
    }

    @Test
    fun theDefaultStateReadsOutTheReferencesOwnFigures() {
        val r = readouts(DEFAULT_STATE)

        assertEquals(CONFIG_BOOM, r.config)
        assertFalse(r.jibActive)
        assertEquals(0.0, r.jibLength, 0.0)
        assertEquals(0.0, r.pivot.x, 0.0)
        assertEquals(0.0, r.pivot.y, 0.0)
        assertEquals(21.213203435596427, r.boomEnd.x, 1e-12)
        assertEquals(21.213203435596423, r.boomEnd.y, 1e-12)
        assertEquals(21.213203435596427, r.tip.x, 1e-12)
        assertEquals(21.213203435596423, r.tip.y, 1e-12)
        assertEquals(21.213203435596427, r.workingRadius, 1e-12)
        assertEquals(21.213203435596423, r.tipHeight, 1e-12)
        assertEquals(21.213203435596423, r.boomTipHeight, 1e-12)
        assertEquals(21.213203435596423, r.heightAboveCrane, 1e-12)
        assertEquals(30.0, r.envelopeRadius, 1e-12)
        assertFalse(r.belowGround)
    }

    @Test
    fun aJibChangesTheTipAndTheEnvelopeRadius() {
        val r = readouts(DEFAULT_STATE.copy(config = CONFIG_BOOM_JIB, jibOffset = 15.0, angle = 30.0))

        assertTrue(r.jibActive)
        assertEquals(12.0, r.jibLength, 0.0)
        assertEquals(25.98076211353316, r.boomEnd.x, 1e-12)
        assertEquals(14.999999999999998, r.boomEnd.y, 1e-12)
        assertEquals(37.57187202900198, r.tip.x, 1e-12)
        assertEquals(18.105828541230245, r.tip.y, 1e-12)
        assertEquals(37.57187202900198, r.workingRadius, 1e-12)
        assertEquals(18.105828541230245, r.heightAboveCrane, 1e-12)
        assertEquals(41.706913035228695, r.envelopeRadius, 1e-12)
        assertFalse(r.belowGround)
    }

    @Test
    fun theExtendedJibUsesItsOwnLength() {
        val r = readouts(
            DEFAULT_STATE.copy(config = CONFIG_BOOM_EXT_JIB, jibOffset = -10.0, angle = 60.0),
        )

        assertTrue(r.jibActive)
        assertEquals(24.0, r.jibLength, 0.0)
        assertEquals(15.000000000000004, r.boomEnd.x, 1e-12)
        assertEquals(25.980762113533157, r.boomEnd.y, 1e-12)
        assertEquals(23.208483439816057, r.tip.x, 1e-12)
        assertEquals(48.53338501239496, r.tip.y, 1e-12)
        assertEquals(23.208483439816057, r.workingRadius, 1e-12)
        assertEquals(53.79705535006148, r.envelopeRadius, 1e-12)
    }

    @Test
    fun tipForStateFollowsTheConfigurationAndTheOffset() {
        val tip = tipForState(DEFAULT_STATE.copy(config = CONFIG_BOOM_JIB, jibOffset = 15.0, angle = 30.0))
        assertEquals(37.57187202900198, tip.x, 1e-12)
        assertEquals(18.105828541230245, tip.y, 1e-12)
    }

    // -----------------------------------------------------------------------
    // The jib offset and the envelope radius
    // -----------------------------------------------------------------------

    @Test
    fun theJibOffsetMovesTheTipOffTheBoomAxis() {
        // At zero offset the jib lies along the boom, so the assembly is a straight
        // 42 m member and the envelope is a 42 m circle.  Any offset folds the jib
        // away from that axis: the tip leaves it, and the radius *shrinks* even
        // though the jib has not.
        val alongAxis = tipOffsetFromPivot(30.0, 12.0, 0.0)
        assertEquals(42.0, alongAxis.x, 1e-12)
        assertEquals(0.0, alongAxis.y, 1e-12)

        val offset = tipOffsetFromPivot(30.0, 12.0, 15.0)
        assertEquals(41.59110991546882, offset.x, 1e-12)
        assertEquals(-3.105828541230249, offset.y, 1e-12)

        assertEquals(42.0, envelopeRadius(30.0, 12.0, 0.0), 1e-12)
        assertEquals(41.706913035228695, envelopeRadius(30.0, 12.0, 15.0), 1e-12)
        assertEquals(53.79705535006148, envelopeRadius(30.0, 24.0, -10.0), 1e-12)
    }

    @Test
    fun anUnfittedJibLeavesTheEnvelopeAtTheBoomLength() {
        assertEquals(30.0, envelopeRadius(30.0, 0.0, 0.0), 1e-12)
    }

    @Test
    fun theEnvelopeArcHasOnePointPerStepAndSpansItsOwnAngles() {
        val points = envelopePoints(origin, 42.0, 0.0, 80.0, steps = 72)
        // 72 intervals means 73 points: both ends belong to the arc, and the two ends
        // are where the envelope's angle labels are drawn.
        assertEquals(73, points.size)
        assertEquals(42.0, points.first().x, 1e-12)
        assertEquals(0.0, points.first().y, 1e-12)
        assertEquals(7.293223462011078, points.last().x, 1e-12)
        assertEquals(41.361925626512736, points.last().y, 1e-12)
    }

    // -----------------------------------------------------------------------
    // Dragging the tip: the inverse
    // -----------------------------------------------------------------------

    @Test
    fun angleForTipRoundTripsEveryConfiguration() {
        val cases = listOf(
            Case(30.0, 12.0, 15.0, 30.0),
            Case(30.0, 24.0, -10.0, 60.0),
            Case(30.0, 0.0, 0.0, 45.0),
        )
        for (case in cases) {
            val tip = assemblyTip(origin, case.boom, case.jib, case.offset, case.angle)
            assertEquals(
                case.angle,
                angleForTip(origin, case.boom, case.jib, case.offset, tip),
                1e-9,
            )
        }
    }

    @Test
    fun withNoJibTheAssemblyTipIsTheBoomTip() {
        assertEquals(
            boomTip(origin, 30.0, 45.0),
            assemblyTip(origin, 30.0, 0.0, 0.0, 45.0),
        )
    }

    @Test
    fun aTargetBelowThePivotGivesANegativeAngleForTheCallerToClamp() {
        // The drag handler clamps to [ANGLE_MIN, ANGLE_MAX]; the geometry must still
        // answer, because "what angle points there" has an answer even when the
        // mechanical limits forbid it.
        assertEquals(
            -45.0,
            angleForTip(origin, 30.0, 0.0, 0.0, GraphPoint(10.0, -10.0)),
            1e-12,
        )
    }

    @Test
    fun normalizeAngleWrapsIntoMinusOneEightyToOneEighty() {
        // Floored modulo, as Python's `%` is: a truncated remainder would send a
        // target a full turn away from where the finger actually is.
        assertEquals(-170.0, normalizeAngle(190.0), 1e-12)
        assertEquals(170.0, normalizeAngle(-190.0), 1e-12)
        assertEquals(-10.0, normalizeAngle(350.0), 1e-12)
    }

    @Test
    fun clampConstrainsToTheInclusiveRange() {
        assertEquals(5.0, clamp(5.0, 0.0, 10.0), 0.0)
        assertEquals(0.0, clamp(-1.0, 0.0, 10.0), 0.0)
        assertEquals(10.0, clamp(11.0, 0.0, 10.0), 0.0)
    }

    // -----------------------------------------------------------------------
    // Below ground
    // -----------------------------------------------------------------------

    @Test
    fun aTipBelowGroundIsFlaggedAndItsHeightGoesNegative() {
        // A crane set into a cutting, with the boom laid out horizontally: the tip is
        // six metres *below* the datum.  The flag is what the readout colours red.
        val r = readouts(DEFAULT_STATE.copy(angle = 0.0, craneY = -5.0))

        assertTrue(r.belowGround)
        assertEquals(-5.0, r.tipHeight, 1e-12)
        assertEquals(0.0, r.heightAboveCrane, 1e-12)
        assertEquals(-5.0, r.boomEnd.y, 1e-12)
        assertEquals(30.0, r.workingRadius, 1e-12)
        assertEquals(30.0, r.envelopeRadius, 1e-12)
    }

    @Test
    fun aTipExactlyOnTheGroundIsNotBelowIt() {
        // The comparison is strictly less than zero, so a tip resting on the datum is
        // "OK" rather than an alarm.
        val r = readouts(DEFAULT_STATE.copy(angle = 0.0))
        assertFalse(r.belowGround)
        assertEquals(0.0, r.tipHeight, 0.0)
    }

    // -----------------------------------------------------------------------
    // Review focus
    // -----------------------------------------------------------------------

    @Test
    fun theWorkingRadiusIsMeasuredFromTheAxisNotTheCrane() {
        // The desktop measures the load chart's radii from the x = 0 axis, not from
        // the crane's own position.  With the crane off the axis at -1.7 m and the
        // tip 15 m to its right, the working radius is the tip's x -- 13.3 m -- and
        // not the 15 m the pivot-relative basis would have given.
        val pivot = GraphPoint(-1.7, 2.8)
        val tip = boomTip(pivot, 30.0, 60.0)
        assertEquals(13.300000000000004, workingRadius(tip), 1e-12)
        assertNotEquals(15.0, workingRadius(tip), 1e-9)
    }

    @Test
    fun angleForWorkingRadiusIsTheInverseOfTheTipReach() {
        // `angleForWorkingRadius` answers "what boom angle puts the tip at this
        // radius?", the inverse of the horizontal reach.  Checked against the
        // desktop's own output for a 30 m boom and a 23.4 m radius.
        assertEquals(
            56.14156334054405,
            angleForWorkingRadius(30.0, 12.0, 0.0, 23.4),
            1e-12,
        )
    }

    // -----------------------------------------------------------------------
    // Every error the reference raises
    // -----------------------------------------------------------------------

    @Test
    fun everyErrorTheReferenceRaisesIsRaisedWithItsOwnWording() {
        assertEquals(
            "clamp: lower bound exceeds upper bound.",
            assertThrows(GeometryException::class.java) { clamp(5.0, 10.0, 0.0) }.message,
        )
        assertEquals(
            "Unknown configuration: 'bogus'",
            assertThrows(GeometryException::class.java) {
                effectiveJibLength("bogus", 1.0, 2.0)
            }.message,
        )
        assertEquals(
            "Jib length cannot be negative.",
            assertThrows(GeometryException::class.java) {
                effectiveJibLength(CONFIG_BOOM_JIB, -1.0, 2.0)
            }.message,
        )
        assertEquals(
            "Jib length cannot be negative.",
            assertThrows(GeometryException::class.java) {
                effectiveJibLength(CONFIG_BOOM_EXT_JIB, 1.0, -2.0)
            }.message,
        )
        assertEquals(
            "Boom length cannot be negative.",
            assertThrows(GeometryException::class.java) {
                boomTip(GraphPoint(0.0, 0.0), -1.0, 45.0)
            }.message,
        )
        assertEquals(
            "Boom and jib lengths cannot be negative.",
            assertThrows(GeometryException::class.java) {
                tipOffsetFromPivot(-1.0, 1.0, 0.0)
            }.message,
        )
        assertEquals(
            "Boom and jib lengths cannot be negative.",
            assertThrows(GeometryException::class.java) {
                tipOffsetFromPivot(1.0, -1.0, 0.0)
            }.message,
        )
        assertEquals(
            "Envelope radius cannot be negative.",
            assertThrows(GeometryException::class.java) {
                envelopePoints(GraphPoint(0.0, 0.0), -1.0, 0.0, 45.0)
            }.message,
        )
        assertEquals(
            "Envelope needs at least one step.",
            assertThrows(GeometryException::class.java) {
                envelopePoints(GraphPoint(0.0, 0.0), 1.0, 0.0, 45.0, steps = 0)
            }.message,
        )
        assertEquals(
            "Boom length must be greater than 0.",
            assertThrows(GeometryException::class.java) {
                angleForTip(GraphPoint(0.0, 0.0), 0.0, 0.0, 0.0, GraphPoint(1.0, 1.0))
            }.message,
        )
    }

    @Test
    fun anUnknownConfigurationIsRefusedRatherThanFallingBackToNoJib() {
        // `effectiveJibLength` returns 0 for the boom configuration and *throws* for
        // anything it does not recognise.  A silent fallback would turn a typo in the
        // configuration into a crane that appears to have no jib fitted at all.
        assertThrows(GeometryException::class.java) { effectiveJibLength("", 12.0, 24.0) }
    }
}
