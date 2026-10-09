package com.zhafrisbs.liftingplancalculator

import com.zhafrisbs.liftingplancalculator.core.Asym2LegResult
import com.zhafrisbs.liftingplancalculator.core.NonuniformInputError
import com.zhafrisbs.liftingplancalculator.core.NonuniformInputs
import com.zhafrisbs.liftingplancalculator.core.SCENARIO_ORDER
import com.zhafrisbs.liftingplancalculator.core.TandemResult
import com.zhafrisbs.liftingplancalculator.core.UsageState
import com.zhafrisbs.liftingplancalculator.core.effectiveTagAngle
import com.zhafrisbs.liftingplancalculator.core.exampleNonuniformInputs
import com.zhafrisbs.liftingplancalculator.core.governingCaseWords
import com.zhafrisbs.liftingplancalculator.core.solveNonuniform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the nonuniform engine to the reference.
 *
 * Every expected number was printed by running the original Python over the same
 * inputs:
 *
 *     .venv/bin/python -c "from lifting_calculator import nonuniform as n; \
 *         print(n.solve_nonuniform(n.example_inputs('asym_2leg_shortening'), 2000.0))"
 *
 * The asymmetric case is the document's own worked case (2 t over a 5.0 m span,
 * COG 3.5 m from pick point 1, pick point 2 sitting 0.5 m higher).  Both tandem
 * cases are the two documents' shared worked case — a 60 t load with the COG 3 m
 * from crane 1 and 5 m from crane 2, 2 m below the baseline, at a 35 degree tilt —
 * and they differ only in where lug 2 is welded.
 */
class NonuniformTest {

    private companion object {
        const val ASYM_LOAD = 2000.0
        const val TANDEM_LOAD = 60000.0
        const val TOL = 1e-6
    }

    private fun asym(load: Double = ASYM_LOAD): Asym2LegResult =
        solveNonuniform(exampleNonuniformInputs("asym_2leg_shortening"), load) as Asym2LegResult

    private fun tandem(scenario: String, load: Double = TANDEM_LOAD): TandemResult =
        solveNonuniform(exampleNonuniformInputs(scenario), load) as TandemResult

    // -----------------------------------------------------------------------
    // The asymmetric bridle
    // -----------------------------------------------------------------------

    @Test
    fun theAsymmetricBridleMatchesTheReference() {
        val result = asym()

        // The share follows the moment about the *opposite* pick point, so the leg
        // on the short side of the COG carries the larger share.
        assertEquals(3.5, result.span1, TOL)
        assertEquals(1.5, result.span2, TOL)
        assertEquals(1, result.longLeg)
        assertEquals(2, result.shortLeg)
        assertEquals(600.0, result.share1, TOL)
        assertEquals(1400.0, result.share2, TOL)

        // The long leg keeps the full standard length and alone sets the hook height.
        assertEquals(6.062, result.headroom, TOL)
        assertEquals(6.062, result.drop1, TOL)
        assertEquals(5.562, result.drop2, TOL)
        assertEquals(5.761, result.shortLegLength, TOL)
        assertEquals(1.239, result.shortening, TOL)
        assertEquals(1239.0, result.shorteningMm, TOL)
        assertFalse(result.needsLengthening)
        assertEquals(0.0, result.lengthening, TOL)

        assertEquals(7.0, result.length1, TOL)
        assertEquals(5.761, result.length2, TOL)
        assertEquals(30.000000000000004, result.angle1, TOL)
        assertEquals(15.09210459686422, result.angle2, TOL)
        assertEquals(0.866, result.angleFactor1, TOL)
        assertEquals(0.966, result.angleFactor2, TOL)

        // Tension comes from the angle factor alone; the required WLL takes the
        // reeve factor as well.  They coincide at the published RF of 1.00.
        assertEquals(692.8406466512702, result.tension1, TOL)
        assertEquals(1449.2753623188407, result.tension2, TOL)
        assertEquals(1449.2753623188407, result.requiredWll2, TOL)
        assertEquals(1449.2753623188407, result.maxTension, TOL)
        assertEquals(1449.2753623188407, result.requiredWll, TOL)
        assertEquals(2, result.governingLeg)

        // The document's setup SWL is the whole load: the two legs are one assembly
        // rather than two separately rated slings.
        assertEquals(2000.0, result.setupSwl, TOL)
    }

    @Test
    fun aFourLegTagIsRatedOnThreeButAOneLegTagOnOne() {
        val tagged = solveNonuniform(
            NonuniformInputs(
                scenario = "asym_2leg_shortening", slingLength = 7.0, pickDistance = 5.0,
                cogFromPick1 = 3.5, pickPoint2Height = 0.5,
                tagWll1 = 5000.0, slingLegs1 = 2, tagWll1Angle = 45.0,
                tagWll2 = 6000.0, slingLegs2 = 4, tagWll2Angle = 30.0,
            ),
            ASYM_LOAD,
        ) as Asym2LegResult

        assertEquals(2, tagged.slingLegs1)
        assertEquals(2, tagged.ratedLegs1)
        assertEquals(0.707, tagged.tagAngleFactor1, TOL)
        assertEquals(3536.0678925035363, tagged.legCapacity1!!, TOL)
        assertEquals(19.59353348729792, tagged.utilisation1!!, TOL)

        // The operator chose a 4-leg tag; the documents' rule rates it on 3.
        assertEquals(4, tagged.slingLegs2)
        assertEquals(3, tagged.ratedLegs2)
        assertEquals(0.866, tagged.tagAngleFactor2, TOL)
        assertEquals(2309.4688221709007, tagged.legCapacity2!!, TOL)
        assertEquals(62.753623188405804, tagged.utilisation2!!, TOL)
        assertEquals(true, tagged.equipmentOk)
    }

    @Test
    fun aSingleLegTagIgnoresItsLeftoverAngle() {
        // A 45 degree angle left over from before the operator chose a single leg
        // would quietly derate the capacity by a third, with nothing on screen to
        // explain it.
        val single = solveNonuniform(
            NonuniformInputs(
                scenario = "asym_2leg_shortening", slingLength = 7.0, pickDistance = 5.0,
                cogFromPick1 = 3.5, pickPoint2Height = 0.5,
                tagWll1 = 5000.0, slingLegs1 = 1, tagWll1Angle = 45.0,
            ),
            ASYM_LOAD,
        ) as Asym2LegResult

        assertEquals(0.0, single.tagWll1Angle, TOL)
        assertEquals(1.0, single.tagAngleFactor1, TOL)
        assertEquals(1, single.ratedLegs1)
        assertEquals(5000.0, single.legCapacity1!!, TOL)
        assertEquals(13.856812933025402, single.utilisation1!!, TOL)

        // The rule itself, read directly.
        assertEquals(0.0, effectiveTagAngle(1, 45.0), TOL)
        assertEquals(45.0, effectiveTagAngle(2, 45.0), TOL)
        assertEquals(45.0, effectiveTagAngle(4, 45.0), TOL)
    }

    // -----------------------------------------------------------------------
    // The tandem lift
    // -----------------------------------------------------------------------

    @Test
    fun theAlignedPivotTandemMatchesTheReference() {
        val result = tandem("tandem_aligned_pivot")

        // The span between the hooks is the sum of the two COG distances, not a
        // third independent measurement.
        assertEquals(8.0, result.span, TOL)
        assertEquals(3.605, result.distANew, TOL)
        assertEquals(4.096, result.distBNew, TOL)
        assertEquals(7.701, result.spanNew, TOL)
        assertEquals(1.147, result.cogShift, TOL)
        assertEquals(4.589, result.heightDifference, TOL)
        // Lug 2 is inline with the COG, so it does not migrate on its own account.
        assertEquals(0.0, result.lug2Travel, TOL)
        // This is the method that can be stood right up on end.
        assertEquals(90.0, result.maxTiltAngle, TOL)
        assertEquals(1.0, result.angleFactor, TOL)

        // Case A, the level baseline: a 60/40 split by the lever arms.
        assertEquals(37500.0, result.shareALevel, TOL)
        assertEquals(22500.0, result.shareBLevel, TOL)
        // Case B, the declared tilt.
        assertEquals(31912.738605375926, result.shareATilt, TOL)
        assertEquals(28087.261394624074, result.shareBTilt, TOL)
        // Case C, the full 90 degrees: crane 2 carries all of it.
        assertEquals(0.0, result.shareAVertical!!, TOL)
        assertEquals(60000.0, result.shareBVertical!!, TOL)

        // Each crane is governed by whichever of its own cases demands most -- and
        // that is not the same case for the two cranes.
        assertEquals(37500.0, result.requiredWllA, TOL)
        assertEquals("level", result.governingCaseA)
        assertEquals(60000.0, result.requiredWllB, TOL)
        assertEquals("vertical", result.governingCaseB)
        assertEquals(60000.0, result.maxTension, TOL)
        assertEquals(60000.0, result.requiredWll, TOL)
    }

    @Test
    fun theTopLugTandemMatchesTheReference() {
        val result = tandem("tandem_top_lug")

        assertEquals(8.0, result.span, TOL)
        assertEquals(3.605, result.distANew, TOL)
        // Lug 2 first migrates inward by Y*tan(theta), and is then projected.
        assertEquals(1.4, result.lug2Travel, TOL)
        assertEquals(2.949, result.distBNew, TOL)
        assertEquals(6.554, result.spanNew, TOL)
        assertEquals(1.147, result.cogShift, TOL)
        // The span closes at the angle where tan(theta) = D2 / Y.
        assertEquals(68.19859051364818, result.maxTiltAngle, TOL)

        assertEquals(26997.25358559658, result.shareATilt, TOL)
        assertEquals(33002.74641440342, result.shareBTilt, TOL)
        assertEquals(37500.0, result.requiredWllA, TOL)
        assertEquals("level", result.governingCaseA)
        assertEquals(33002.74641440342, result.requiredWllB, TOL)
        assertEquals("tilt", result.governingCaseB)
        // The level case demands more of crane 1 than the tilt does, so the tilt is
        // not what sizes this crane.
        assertEquals(37500.0, result.requiredWll, TOL)
    }

    @Test
    fun theTwoTandemMethodsDifferOnlyInCraneTwo() {
        val aligned = tandem("tandem_aligned_pivot")
        val top = tandem("tandem_top_lug")

        // Crane 1's lug is on the side of the load in both methods, so its geometry
        // is identical.  Only crane 2 parts company.
        assertEquals(aligned.distANew, top.distANew, TOL)
        assertEquals(aligned.cogShift, top.cogShift, TOL)
        assertEquals(aligned.shareALevel, top.shareALevel, TOL)
        assertEquals(aligned.requiredWllA, top.requiredWllA, TOL)

        assertEquals(0.0, aligned.lug2Travel, TOL)
        assertEquals(1.4, top.lug2Travel, TOL)
        // Same five numbers, very different answer for crane 2: 60,000 kg against
        // 33,003 kg, and that is the whole reason the method is a choice.
        assertEquals(60000.0, aligned.requiredWllB, TOL)
        assertEquals(33002.74641440342, top.requiredWllB, TOL)
    }

    @Test
    fun theVerticalCaseOnlyExistsForTheMethodThatCanReachIt() {
        val aligned = tandem("tandem_aligned_pivot")
        val top = tandem("tandem_top_lug")

        assertEquals(0.0, aligned.shareAVertical!!, TOL)
        assertEquals(60000.0, aligned.shareBVertical!!, TOL)

        // Publishing a 0.0 for crane 1 alone would be a number for a rotation that
        // cannot happen, and a reader could not tell it apart from a real one.
        assertNull(top.shareAVertical)
        assertNull(top.shareBVertical)
        assertNull(top.requiredAVertical)
        assertNull(top.requiredBVertical)
        assertTrue(aligned.fullRotation)
        assertFalse(top.fullRotation)
    }

    @Test
    fun theAlignedPivotCanBeStoodFullyUpright() {
        // At exactly 90 degrees the aligned method's crane 2 projection closes to
        // zero — and for *this* method that is Case C, not a tip-over, so it is the
        // answer rather than a refusal.
        val result = solveNonuniform(
            NonuniformInputs(
                scenario = "tandem_aligned_pivot",
                distA = 3.0, distB = 5.0, cogHeight = 2.0, tiltAngle = 90.0,
            ),
            TANDEM_LOAD,
        ) as TandemResult

        assertEquals(0.0, result.distBNew, TOL)
        assertEquals(2.0, result.spanNew, TOL)
        assertEquals(0.0, result.shareATilt, TOL)
        assertEquals(60000.0, result.shareBTilt, TOL)
        assertEquals(60000.0, result.requiredWllB, TOL)
    }

    @Test
    fun withNoTiltTheTwoCranesSplitByTheirLeverArmsAndTheLugDoesNotMatter() {
        // At zero tilt the two methods are the same lift: the lug position only
        // matters once the load leans.
        val result = solveNonuniform(
            NonuniformInputs(
                scenario = "tandem_top_lug",
                distA = 3.0, distB = 5.0, cogHeight = 2.0, tiltAngle = 0.0,
            ),
            TANDEM_LOAD,
        ) as TandemResult

        assertEquals(5.0, result.distBNew, TOL)
        assertEquals(0.0, result.lug2Travel, TOL)
        assertEquals(8.0, result.spanNew, TOL)
        assertEquals(37500.0, result.requiredWllA, TOL)
        assertEquals(22500.0, result.requiredWllB, TOL)
        // Level and tilt are the same case here, and the first listed one wins.
        assertEquals("level", result.governingCaseB)
    }

    @Test
    fun eachCraneIsBookedAgainstItsOwnRating() {
        val result = solveNonuniform(
            NonuniformInputs(
                scenario = "tandem_aligned_pivot",
                distA = 3.0, distB = 5.0, cogHeight = 2.0, tiltAngle = 35.0,
                craneRatedA = 50000.0, craneRatedB = 68000.0,
            ),
            TANDEM_LOAD,
        ) as TandemResult

        assertEquals(75.0, result.craneUsageA!!, TOL)
        assertEquals(88.23529411764706, result.craneUsageB!!, TOL)
        // Three bands, not two: "within 75%", "between 75% and the chart" and "over
        // the chart" are three different things to tell an operator.
        assertEquals(UsageState.WITHIN, result.craneUsageStateA)
        assertEquals(UsageState.CAUTION, result.craneUsageStateB)
        assertEquals(true, result.craneRiskFlag)
        // The sling tags alone decide the verdict, and none were entered.
        assertNull(result.equipmentOk)
    }

    @Test
    fun theGoverningCaseIsNamedInWords() {
        assertEquals("level baseline", governingCaseWords("level"))
        assertEquals("declared tilt", governingCaseWords("tilt"))
        assertEquals("full 90\u00b0 vertical rotation", governingCaseWords("vertical"))
        assertEquals("\u2014", governingCaseWords(null))
    }

    // -----------------------------------------------------------------------
    // Refusals
    // -----------------------------------------------------------------------

    @Test
    fun theCogMustSitBetweenThePickPoints() {
        for (cog in listOf(0.0, 6.0)) {
            val failure = assertThrows(NonuniformInputError::class.java) {
                solveNonuniform(
                    NonuniformInputs(
                        scenario = "asym_2leg_shortening", slingLength = 7.0,
                        pickDistance = 5.0, cogFromPick1 = cog, pickPoint2Height = 0.5,
                    ),
                    ASYM_LOAD,
                )
            }
            assertEquals(
                "The COG must sit between the two pick points: enter a position " +
                    "greater than 0 m and less than the 5.000 m span.",
                failure.message,
            )
        }
    }

    @Test
    fun theSlingMustSpanTheLongerLeverArm() {
        val failure = assertThrows(NonuniformInputError::class.java) {
            solveNonuniform(
                NonuniformInputs(
                    scenario = "asym_2leg_shortening", slingLength = 7.0,
                    pickDistance = 15.0, cogFromPick1 = 8.0,
                ),
                ASYM_LOAD,
            )
        }
        assertEquals(
            "The 7.000 m sling is too short to reach 8.000 m across. Use a longer " +
                "sling or a shorter span.",
            failure.message,
        )
    }

    @Test
    fun aStepCanLiftAPickPointAboveHookLevel() {
        val failure = assertThrows(NonuniformInputError::class.java) {
            solveNonuniform(
                NonuniformInputs(
                    scenario = "asym_2leg_shortening", slingLength = 7.0,
                    pickDistance = 5.0, cogFromPick1 = 3.5, pickPoint2Height = 7.0,
                ),
                ASYM_LOAD,
            )
        }
        assertEquals(
            "A 7.000 m step leaves pick point 2 at or above hook level, so the " +
                "sling would have to run uphill. Reduce the step or use a longer sling.",
            failure.message,
        )
    }

    @Test
    fun theTopLugSpanRunsOutBeforeVertical() {
        val failure = assertThrows(NonuniformInputError::class.java) {
            solveNonuniform(
                NonuniformInputs(
                    scenario = "tandem_top_lug",
                    distA = 3.0, distB = 5.0, cogHeight = 2.0, tiltAngle = 80.0,
                ),
                TANDEM_LOAD,
            )
        }
        assertEquals(
            "At a 80.00 degree tilt with the COG 2.000 m below the baseline, " +
                "crane 2's projected distance closes to nothing (the span runs out " +
                "at 68.2 degrees). The load would tip about crane 2 instead of " +
                "hanging from both hooks; reduce the tilt angle or the COG depth.",
            failure.message,
        )
    }

    @Test
    fun aBlankFormIsRefusedByNameRatherThanByDivision() {
        // The form opens on zeros, so this is the very first state an operator can
        // be in -- and it has to read as a sentence rather than as an arithmetic
        // failure.
        val failure = assertThrows(NonuniformInputError::class.java) {
            solveNonuniform(NonuniformInputs(), ASYM_LOAD)
        }
        assertEquals("Sling length must be greater than zero.", failure.message)

        val tandem = assertThrows(NonuniformInputError::class.java) {
            solveNonuniform(
                NonuniformInputs(scenario = "tandem_aligned_pivot"),
                TANDEM_LOAD,
            )
        }
        assertEquals("Distance from crane 1 hook to COG must be greater than zero.", tandem.message)
    }

    @Test
    fun theTiltAndTheCogDepthHaveTheirOwnBounds() {
        assertEquals(
            "The cargo tilt angle must be between 0 and 90 degrees; 95.00 was entered.",
            assertThrows(NonuniformInputError::class.java) {
                solveNonuniform(
                    NonuniformInputs(
                        scenario = "tandem_aligned_pivot",
                        distA = 3.0, distB = 5.0, cogHeight = 2.0, tiltAngle = 95.0,
                    ),
                    TANDEM_LOAD,
                )
            }.message,
        )
        assertEquals(
            "The COG depth cannot be negative: measure it downward from the line " +
                "joining the two lifting points.",
            assertThrows(NonuniformInputError::class.java) {
                solveNonuniform(
                    NonuniformInputs(
                        scenario = "tandem_aligned_pivot",
                        distA = 3.0, distB = 5.0, cogHeight = -1.0, tiltAngle = 35.0,
                    ),
                    TANDEM_LOAD,
                )
            }.message,
        )
    }

    @Test
    fun everyScenarioInTheRegistryCanBeSolved() {
        // The registry is a list of what the app can do, so an entry in it that no
        // solver answers would be a tab that cannot open.
        for (scenario in SCENARIO_ORDER) {
            val result = solveNonuniform(exampleNonuniformInputs(scenario), ASYM_LOAD)
            assertEquals(scenario, result.scenario)
            assertTrue(result.scenarioLabel.isNotEmpty())
        }
    }
}
