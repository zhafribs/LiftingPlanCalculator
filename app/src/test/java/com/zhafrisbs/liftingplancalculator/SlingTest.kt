package com.zhafrisbs.liftingplancalculator

import com.zhafrisbs.liftingplancalculator.core.ANGLE_BASIS_EXTERNAL
import com.zhafrisbs.liftingplancalculator.core.ANGLE_BASIS_INTERNAL
import com.zhafrisbs.liftingplancalculator.core.HITCH_ORDER
import com.zhafrisbs.liftingplancalculator.core.HITCH_SPECS
import com.zhafrisbs.liftingplancalculator.core.OneLegInputs
import com.zhafrisbs.liftingplancalculator.core.SlingInputError
import com.zhafrisbs.liftingplancalculator.core.chokeReductionFactor
import com.zhafrisbs.liftingplancalculator.core.normaliseAngle
import com.zhafrisbs.liftingplancalculator.core.ratedLegs
import com.zhafrisbs.liftingplancalculator.core.solveDefaults
import com.zhafrisbs.liftingplancalculator.core.solveOneLeg
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the sling engine to the reference.
 *
 * Every expected number below was printed by running the original Python over the
 * same inputs:
 *
 *     .venv/bin/python -c "from lifting_calculator import sling_1leg as s; \
 *         print(s.solve_defaults('two_leg_round_choke', 2000.0))"
 *
 * A figure is asserted at 1e-6, which is far tighter than the reference documents
 * print (three decimals) and loose enough for the two languages' floating point to
 * agree without arguing.
 *
 * The arrangement table is the load-bearing test in this file: nineteen distinct
 * geometries, each with its own wrap length, effective leg count and rating rule,
 * and a transcription slip in any one of them shows up as a wrong tension rather
 * than as a plausible-looking screen.
 */
class SlingTest {

    private companion object {
        const val LOAD = 2000.0
        const val TOL = 1e-6
    }

    private data class Expected(
        val hitch: String,
        val angleFactor: Double,
        val tensionEach: Double,
        val requiredWll: Double,
        val hookToLoad: Double,
        val effectiveLegs: Int,
        val ratingLegs: Int,
        val angle: Double,
    )

    private val reference = listOf(
        Expected("direct_hook", 1.0, 2000.0, 2000.0, 7.0, 1, 1, 0.0),
        Expected("round_choke", 1.0, 2000.0, 2666.6666666666665, 3.9357576155998326, 1, 1, 0.0),
        Expected("rect_choke", 1.0, 2000.0, 4000.0, 5.653589838486225, 1, 1, 0.0),
        Expected(
            "round_basket", 0.983, 1017.293997965412, 1017.293997965412,
            2.1684536053803085, 2, 1, 10.613857356461029,
        ),
        Expected(
            "rect_basket", 0.998, 1002.0040080160321, 2004.0080160320642,
            2.9939999999999998, 2, 1, 3.822553729274344,
        ),
        Expected(
            "vertical_round_basket", 1.0, 1000.0, 1000.0,
            2.2146018366025517, 2, 1, 0.0,
        ),
        Expected("vertical_rect_basket", 1.0, 1000.0, 2000.0, 2.7, 2, 1, 0.0),
        Expected(
            "two_leg_direct", 0.934, 1070.663811563169, 1070.663811563169,
            6.538, 2, 2, 20.924832427638318,
        ),
        Expected(
            "two_leg_round_choke", 0.772, 1295.3367875647668, 1727.1157167530225,
            2.980026563014584, 2, 2, 39.43494713747446,
        ),
        Expected(
            "two_leg_rect_choke", 0.897, 1114.8272017837235, 2229.654403567447,
            4.967239281902605, 2, 2, 26.24413735040033,
        ),
        Expected(
            "two_leg_round_basket", 0.383, 1305.4830287206266, 1305.4830287206266,
            0.5396925034187774, 4, 2, 67.06468259794082,
        ),
        Expected(
            "two_leg_rect_basket", 0.552, 905.7971014492753, 1811.5942028985505,
            1.6560000000000001, 4, 2, 56.44269023807929,
        ),
        Expected(
            "two_leg_vertical_round_basket", 0.983, 508.646998982706,
            508.646998982706, 2.1684536053803085, 4, 2, 0.0,
        ),
        Expected(
            "two_leg_vertical_rect_basket", 0.998, 501.00200400801606,
            1002.0040080160321, 2.694, 4, 2, 0.0,
        ),
        Expected(
            "two_leg_nested_2leg", 0.935, 1069.51871657754, 1069.51871657754,
            7.09852, 4, 2, 20.833669881768763,
        ),
        Expected(
            "two_leg_nested_3leg", 0.934, 1070.663811563169, 1070.663811563169,
            7.066644, 6, 2, 20.90803884369347,
        ),
        Expected(
            "two_leg_nested_4leg", 0.933, 1071.8113612004286, 1071.8113612004286,
            7.01616, 8, 2, 21.042580058290127,
        ),
        Expected(
            "three_leg_direct", 0.895, 744.8789571694599, 1117.31843575419,
            6.265000000000001, 3, 2, 26.447941163157644,
        ),
        Expected(
            "four_leg_direct", 0.923, 541.7118093174431, 722.2824124232575,
            6.461, 4, 3, 22.622300890389113,
        ),
    )

    @Test
    fun everyArrangementMatchesTheReference() {
        // The table must cover the engine, not a subset of it: a new arrangement
        // added to the order without a row here would otherwise go unpinned.
        assertEquals(HITCH_ORDER, reference.map { it.hitch })

        for (row in reference) {
            val result = solveDefaults(row.hitch, LOAD)
            assertEquals("$row angleFactor", row.angleFactor, result.angleFactor, TOL)
            assertEquals("$row tensionEach", row.tensionEach, result.tensionEach, TOL)
            assertEquals("$row requiredWll", row.requiredWll, result.requiredWll, TOL)
            assertEquals("$row hookToLoad", row.hookToLoad, result.hookToLoad, TOL)
            assertEquals("$row effectiveLegs", row.effectiveLegs, result.effectiveLegs)
            assertEquals("$row ratingLegs", row.ratingLegs, result.ratingLegs)
            assertEquals("$row angle", row.angle, result.angle, TOL)
        }
    }

    @Test
    fun theNestedSystemsReportTheirSecondaryLegs() {
        val two = solveDefaults("two_leg_nested_2leg", LOAD)
        assertEquals(0.986, two.secondaryAf!!, TOL)
        assertEquals(0.922, two.secondaryCombinedAf!!, TOL)
        assertEquals(542.2993492407809, two.secondaryTension!!, TOL)
        assertEquals(9.594068226860463, two.secondaryAngle!!, TOL)
        assertEquals(2, two.secondaryLegs)
        // A 2-leg second sling is rated on both its legs...
        assertEquals(2, two.secondaryRatingLegs)
        assertEquals(0.5916, two.secondaryHeight!!, TOL)
        assertEquals(7.5916, two.totalCombinedLength!!, TOL)
        // ...and a two-leg offset list has nothing to level.
        assertNull(two.pileLevelDifference)
        assertEquals(1, two.secondaryGroups!!.size)

        val three = solveDefaults("two_leg_nested_3leg", LOAD)
        assertEquals(0.881, three.secondaryCombinedAf!!, TOL)
        assertEquals(378.35792659856224, three.secondaryTension!!, TOL)
        assertEquals(3, three.secondaryLegs)
        // A 3-leg second sling is rated on two -- the note's N-1 rule again.
        assertEquals(2, three.secondaryRatingLegs)
        assertEquals(0.5658, three.secondaryHeight!!, TOL)
        assertEquals(0.03420000000000003, three.pileLevelDifference!!, TOL)
        // The governing group is the outermost one, and it is reported first.
        val groups = three.secondaryGroups!!
        assertEquals(2, groups.size)
        assertEquals(0.2, groups[0].offset, TOL)
        assertTrue(groups[0].governing)
        assertFalse(groups[1].governing)

        val four = solveDefaults("two_leg_nested_4leg", LOAD)
        assertEquals(0.808, four.secondaryCombinedAf!!, TOL)
        assertEquals(309.40594059405936, four.secondaryTension!!, TOL)
        assertEquals(4, four.secondaryLegs)
        // And a 4-leg second sling on three.
        assertEquals(3, four.secondaryRatingLegs)
        assertEquals(0.072, four.pileLevelDifference!!, TOL)
        assertEquals(0.3, four.secondaryHeightGoverningOffset!!, TOL)
    }

    @Test
    fun aOneLegSlingIsNeverDeratedByALeftoverTagAngle() {
        // A single leg hangs vertical and takes the tag's full rating.  A 45 degree
        // angle left over from a 2-leg entry must not quietly derate it, so the
        // engine zeroes it rather than trusting the form.
        val result = solveOneLeg(
            OneLegInputs(
                hitch = "direct_hook", slingLength = 7.0,
                tagWll = 5000.0, tagWllAngle = 45.0,
            ),
            LOAD,
        )
        assertEquals(0.0, result.tagWllAngle, TOL)
        assertEquals(1.0, result.tagAngleFactor, TOL)
        assertEquals(5000.0, result.legCapacity!!, TOL)
        assertEquals(40.0, result.utilisation!!, TOL)
        assertEquals("PASS \u2014 configured WLL meets the requirement", result.equipmentStatus)
    }

    @Test
    fun aTwoLegSlingDoesTakeItsRatedAngle() {
        val result = solveOneLeg(
            OneLegInputs(
                hitch = "two_leg_direct", slingLength = 7.0, pickDistance = 5.0,
                tagWll = 5300.0, tagWllAngle = 45.0,
            ),
            LOAD,
        )
        assertEquals(45.0, result.tagWllAngle, TOL)
        assertEquals(0.707, result.tagAngleFactor, TOL)
        assertEquals(3748.2319660537482, result.legCapacity!!, TOL)
        assertEquals(28.56571154153994, result.utilisation!!, TOL)
    }

    @Test
    fun aBridleIsRatedOnOneLegFewerThanItHas() {
        // The reference documents: "If 4-Leg Sling, use Sling Legs = 3",
        // "If 3-Leg Sling, use Sling Legs = 2", "If 2-Leg Sling, use Sling Legs = 2".
        assertEquals(1, ratedLegs(1))
        assertEquals(2, ratedLegs(2))
        assertEquals(2, ratedLegs(3))
        assertEquals(3, ratedLegs(4))
        assertEquals(4, ratedLegs(5))

        // Derived from the leg count, so it cannot drift from it.
        assertFalse(HITCH_SPECS.getValue("direct_hook").needsTagAngle)
        assertFalse(HITCH_SPECS.getValue("round_choke").needsTagAngle)
        assertTrue(HITCH_SPECS.getValue("two_leg_direct").needsTagAngle)
        assertTrue(HITCH_SPECS.getValue("four_leg_direct").needsTagAngle)
    }

    @Test
    fun theChokeReductionTableKeepsItsBoundaries() {
        // Each boundary belongs to the row *below* it, which is the reading that
        // makes the documents' own internal-60/external-120 case come out at 1.00.
        assertEquals(1.00, chokeReductionFactor(150.0), TOL)
        assertEquals(1.00, chokeReductionFactor(120.0), TOL)
        assertEquals(0.87, chokeReductionFactor(119.0), TOL)
        assertEquals(0.87, chokeReductionFactor(90.0), TOL)
        assertEquals(0.74, chokeReductionFactor(89.0), TOL)
        assertEquals(0.74, chokeReductionFactor(60.0), TOL)
        assertEquals(0.62, chokeReductionFactor(59.0), TOL)
        assertEquals(0.62, chokeReductionFactor(30.0), TOL)
        assertEquals(0.49, chokeReductionFactor(29.0), TOL)
        assertEquals(0.49, chokeReductionFactor(10.0), TOL)
    }

    @Test
    fun theTwoAngleBasesAreTwoReadingsOfOneAngle() {
        // The documents call the angle from hook to load external; the angle inside
        // the choke is measured from vertical.  Internal 60 and external 120 name
        // the same picture, and the reference's own worked example enters the first.
        val internal = normaliseAngle(60.0, ANGLE_BASIS_INTERNAL)
        assertEquals(120.0, internal.first, TOL)
        assertEquals(60.0, internal.second, TOL)

        val external = normaliseAngle(120.0, ANGLE_BASIS_EXTERNAL)
        assertEquals(120.0, external.first, TOL)
        assertEquals(60.0, external.second, TOL)

        assertThrows(SlingInputError::class.java) {
            normaliseAngle(60.0, "sideways")
        }
        assertThrows(SlingInputError::class.java) {
            normaliseAngle(0.0, ANGLE_BASIS_INTERNAL)
        }
        assertThrows(SlingInputError::class.java) {
            normaliseAngle(180.0, ANGLE_BASIS_INTERNAL)
        }
    }

    @Test
    fun anArrangementThatCannotReachTheHookSaysSo() {
        // A 1 m sling on a 5.4 m pick spacing is not a lift, and the message has to
        // say which way out rather than reporting a negative tension.
        val failure = assertThrows(SlingInputError::class.java) {
            solveOneLeg(
                OneLegInputs(hitch = "three_leg_direct", slingLength = 1.0, pickDistance = 5.4),
                LOAD,
            )
        }
        assertEquals(
            "The 3-leg bridle cannot reach the hook with this sling length " +
                "and pick-point spacing.",
            failure.message,
        )
    }

    @Test
    fun aSlingTooShortForTheWrapNamesTheLengthItNeeds() {
        val failure = assertThrows(SlingInputError::class.java) {
            solveOneLeg(
                OneLegInputs(hitch = "round_choke", slingLength = 3.0, diameter = 1.0),
                LOAD,
            )
        }
        // The figure in the message is the published one, at three decimals.
        assertEquals(
            "The sling is too short to complete the round choke; " +
                "at least 3.142 m is required.",
            failure.message,
        )
    }

    @Test
    fun aRectangularChokeFlareMustStayInsideTheVertical() {
        // An internal angle of 90 degrees is the flare lying flat: the sling would
        // run along the load rather than around it.
        assertThrows(SlingInputError::class.java) {
            solveOneLeg(
                OneLegInputs(
                    hitch = "rect_choke", slingLength = 7.0, side = 0.3, bottom = 0.4,
                    chokeAngle = 90.0,
                ),
                LOAD,
            )
        }
    }

    @Test
    fun aMissingShapeMeasurementIsNamedRatherThanTreatedAsZero() {
        // A round-load arrangement with no diameter is a form half filled, and the
        // message has to name the field rather than failing on a division.
        val failure = assertThrows(SlingInputError::class.java) {
            solveOneLeg(OneLegInputs(hitch = "round_basket", slingLength = 7.0), LOAD)
        }
        assertEquals("Load diameter must be greater than 0.", failure.message)
    }
}
