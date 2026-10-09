package com.zhafrisbs.liftingplancalculator

import com.zhafrisbs.liftingplancalculator.core.Asym2LegResult
import com.zhafrisbs.liftingplancalculator.core.TandemResult
import com.zhafrisbs.liftingplancalculator.core.asymSections
import com.zhafrisbs.liftingplancalculator.core.exampleNonuniformInputs
import com.zhafrisbs.liftingplancalculator.core.nonuniformSections
import com.zhafrisbs.liftingplancalculator.core.solveNonuniform
import com.zhafrisbs.liftingplancalculator.core.tandemSections
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the nonuniform and tandem report blocks to the reference.
 *
 * Every expected string was printed by running the original Python:
 *
 *     .venv/bin/python -c "from lifting_calculator import nonuniform as n; \
 *         from lifting_calculator.report_1leg import _nonuniform_asym_sections; \
 *         print(''.join(_nonuniform_asym_sections( \
 *             n.solve_nonuniform(n.example_inputs('asym_2leg_shortening'), 2000.0))))"
 *
 * Both use the documents' own worked cases: the asymmetric bridle at 2 t over a
 * 5.0 m span, and the tandem lift at 60 t with the COG 3 m from crane 1 and 5 m
 * from crane 2, 2 m below the baseline, at a 35 degree tilt.
 */
class ReportNonuniformTest {

    private val asym = solveNonuniform(
        exampleNonuniformInputs("asym_2leg_shortening"), 2000.0,
    ) as Asym2LegResult

    private val tandem = solveNonuniform(
        exampleNonuniformInputs("tandem_aligned_pivot"), 60000.0,
    ) as TandemResult

    private fun asymHtml(): String = asymSections(asym).joinToString("")
    private fun tandemHtml(): String = tandemSections(tandem, 1, "Tandem Lift").joinToString("")

    // -----------------------------------------------------------------------
    // The asymmetric bridle
    // -----------------------------------------------------------------------

    @Test
    fun theAsymmetricBridleReportMatchesTheReference() {
        val html = asymHtml()
        assertTrue(html.contains("<h2>1. NONUNIFORM LOAD</h2>"))
        assertTrue(
            html.contains(
                "<h2>2. GEOMETRIC &amp; HEADROOM CALCULATIONS (SHORTENING METHOD " +
                    "WITH ELEVATION DELTA)</h2>"
            )
        )

        // The step's direction is spelled out beside its magnitude, so a reader
        // never has to work out which pick point moved.
        assertTrue(
            html.contains(
                "<tr><th>Vertical height difference between pick points</th>" +
                    "<td>0.500 m (pick point 2 higher)</td></tr>"
            )
        )

        assertTrue(
            html.contains(
                """\[\text{X}_2 = \text{Total Span} - \text{X}_1 = 5.000 - 3.500 = """ +
                    """1.500\ \text{m}\]"""
            )
        )
        assertTrue(
            html.contains(
                """\[\text{Length}_{1} = \text{Standard Length} = 7.000\ \text{m}\]"""
            )
        )
        // The long leg alone fixes the hook's height...
        assertTrue(
            html.contains(
                """\[\text{Y}_{1} = \sqrt{\text{Length}^2 - \text{X}_{1}^2} = """ +
                    """\sqrt{7.000^2 - 3.500^2} = 6.062\ \text{m}\]"""
            )
        )
        // ...and the far pick point's drop follows from the signed step.  The step
        // is printed as a magnitude with the sign derived from the two drops, so no
        // line can ever read "6.062 - -0.500".
        assertTrue(
            html.contains(
                """\[\text{Y}_{2} = \text{Y}_{1} - \text{Height Difference} = """ +
                    """6.062 - 0.500 = 5.562\ \text{m}\]"""
            )
        )
        assertTrue(
            html.contains(
                """\[\text{Length}_{2} = \sqrt{\text{Y}_{2}^2 + \text{X}_{2}^2} = """ +
                    """\sqrt{5.562^2 + 1.500^2} = 5.761\ \text{m}\]"""
            )
        )
        assertTrue(
            html.contains(
                """\[\text{Shortening} = \text{Standard Length} - """ +
                    """\text{Required Length} = 7.000 - 5.761 = 1.239\ \text{m}\]"""
            )
        )
    }

    @Test
    fun thePerLegTensionAndRequiredWllMatchTheReference() {
        val html = asymHtml()
        // The share is a statics result, not a leg count: the leg on the short side
        // of the COG carries the larger share.
        assertTrue(
            html.contains(
                """\[\text{Weight}_2 = \text{Load} \times """ +
                    """\frac{\text{X}_1}{\text{Total Span}} = 2{,}000.0 \times """ +
                    """\frac{3.500}{5.000} = 1{,}400.0\ \text{kg}\]"""
            )
        )
        assertTrue(html.contains("Tension in Sling 1 (Longer Leg):"))
        assertTrue(html.contains("Tension in Sling 2 (Shorter Leg):"))
        assertTrue(
            html.contains(
                """\[\text{Tension}_2 = \frac{\text{Weight}_2}{\text{AF}_2} = """ +
                    """\frac{1{,}400.0}{0.966} = 1{,}449.3\ \text{kg}\]"""
            )
        )
        // Two required WLLs, one per leg, and the governing one named.
        assertTrue(html.contains("Minimum Required Sling WLL for Sling 1 (Tag Rating):"))
        assertTrue(html.contains("Minimum Required Sling WLL for Sling 2 (Tag Rating):"))
        assertTrue(
            html.contains(
                """\[\text{Min WLL}_{2} = \frac{\text{Weight}_{2}}""" +
                    """{\text{RF} \times \text{AF}_{2}} = \frac{1{,}400.0}""" +
                    """{1.00 \times 0.966} = 1{,}449.3\ \text{kg}\]"""
            )
        )
        assertTrue(
            html.contains("<tr><th>Governing leg</th><td>Sling 2 \u2014 1,449.3 kg</td></tr>")
        )
        assertTrue(
            html.contains(
                """\[\text{Setup SWL} = \text{Load Weight} = 2{,}000\ \text{kg}\]"""
            )
        )
    }

    @Test
    fun aMissingTagSaysTheCheckWasNotDoneRatherThanPassingIt() {
        // Not the same statement as a pass.  Saying nothing would let a reviewer
        // read the section as complete when the check was never performed.
        val html = asymHtml()
        assertTrue(html.contains("Installed Tag Rating Check:"))
        assertTrue(
            html.contains(
                "Not calculated \u2014 enter the installed tag WLL for each leg on " +
                    "the Nonuniform Load tab to complete the equipment check."
            )
        )
        assertTrue(!html.contains("<span class='pass'>"))
    }

    // -----------------------------------------------------------------------
    // The tandem lift
    // -----------------------------------------------------------------------

    @Test
    fun theTandemGeometryMatchesTheReference() {
        val html = tandemHtml()
        assertTrue(html.contains("<h2>1. TANDEM LIFT</h2>"))
        assertTrue(
            html.contains(
                "<h2>2. GEOMETRIC &amp; HEADROOM CALCULATIONS (DYNAMIC TILT METHOD)</h2>"
            )
        )
        assertTrue(
            html.contains(
                """\[\text{Angle}_1 = \text{Angle}_2 = 0.00^\circ\ \text{from vertical}\]"""
            )
        )
        assertTrue(
            html.contains(
                """\[\text{ND}_1 = \text{h} \times \sin\theta + \text{D}_1 \times """ +
                    """\cos\theta = 2.000 \times \sin(35.00^\circ) + 3.000 \times """ +
                    """\cos(35.00^\circ) = 3.605\ \text{m}\]"""
            )
        )
        // The aligned pivot's crane 2 does not migrate, so its projection is the
        // plain one -- and this is the only place the two methods differ.
        assertTrue(
            html.contains(
                """\[\text{ND}_2 = \text{D}_2 \times \cos\theta = 5.000 \times """ +
                    """\cos(35.00^\circ) = 4.096\ \text{m}\]"""
            )
        )
        assertTrue(
            html.contains(
                """\[\text{L}_{new} = \text{ND}_1 + \text{ND}_2 = 3.605 + 4.096 = """ +
                    """7.701\ \text{m}\]"""
            )
        )
        assertTrue(
            html.contains(
                """\[\text{Height Difference} = \text{Total Span} \times \sin\theta """ +
                    """= 8.000 \times \sin(35.00^\circ) = 4.589\ \text{m}\]"""
            )
        )
    }

    @Test
    fun allThreeCasesAreWorkedAndTheVerticalOneIsNotSkipped() {
        val html = tandemHtml()
        assertTrue(html.contains("Case A — Sling Line Tension at 0° Level Lift:"))
        assertTrue(html.contains("Case B — Sling Line Tension at 35° Dynamic Slant Lift:"))
        // On this method the 90 degree case is the one that sizes crane 2's sling,
        // so a reader who skipped it would pick a sling too small by half.
        assertTrue(html.contains("Case C — Weight Distribution at 90° Vertical Rotation:"))
        assertTrue(
            html.contains(
                """\[\text{Tension}_1 = 0\ \text{kg}, \qquad \text{Tension}_2 = """ +
                    """\frac{\text{Weight}_2}{\text{AF}_2} = \frac{60{,}000.0}""" +
                    """{1.000} = 60{,}000.0\ \text{kg}\]"""
            )
        )
        // And the summary table carries all six tensions, not just the tilted pair.
        assertTrue(html.contains("<tr><th>Tension, crane 1 (90° vertical)</th><td>0.0 kg</td></tr>"))
        assertTrue(
            html.contains("<tr><th>Tension, crane 2 (90° vertical)</th><td>60,000.0 kg</td></tr>")
        )
    }

    @Test
    fun eachCraneTakesTheHighestOfItsOwnCases() {
        val html = tandemHtml()
        assertTrue(html.contains("Minimum Required Sling WLL for Crane 1 (Left Hook Path):"))
        assertTrue(html.contains("Minimum Required Sling WLL for Crane 2 (Right Hook Path):"))
        // The two cranes do not share a governing case, which is the whole reason
        // the geometry is solved in two passes.
        assertTrue(
            html.contains(
                """\[\text{Min WLL}_{2} = \max\left(\frac{\text{Weight}_{2,\,0^\circ}}""" +
                    """{1.00 \times 1.000}, \frac{\text{Weight}_{2,\,35^\circ}}""" +
                    """{1.00 \times 1.000}, \frac{\text{Weight}_{2,\,90^\circ}}""" +
                    """{1.00 \times 1.000}\right) = \max\left(""" +
                    """\frac{22{,}500.0}{1.00 \times 1.000}, """ +
                    """\frac{28{,}087.3}{1.00 \times 1.000}, """ +
                    """\frac{60{,}000.0}{1.00 \times 1.000}\right) = 60{,}000.0\ \text{kg}\]"""
            )
        )
        assertTrue(
            html.contains(
                "<tr><th>Minimum required sling WLL, crane 1</th><td>37,500.0 kg " +
                    "\u2014 governed by the 0\u00b0 level baseline case</td></tr>"
            )
        )
        assertTrue(
            html.contains(
                "<tr><th>Minimum required sling WLL, crane 2</th><td>60,000.0 kg " +
                    "\u2014 governed by the 90\u00b0 vertical rotation case</td></tr>"
            )
        )
        assertTrue(
            html.contains("<tr><th>Governing crane</th><td>Crane 2 \u2014 60,000.0 kg</td></tr>")
        )
    }

    @Test
    fun aCraneWithNoChartCapacitySaysSoRatherThanShowingZeroPercent() {
        val html = tandemHtml()
        assertTrue(html.contains("Crane Rated Capacity Check (75% Allowable):"))
        assertTrue(
            html.contains(
                "Not calculated \u2014 enter each crane's load chart capacity on " +
                    "the Tandem tab to complete the 75% allowable check. A tandem " +
                    "lift is carried by two independent cranes, so each hook path " +
                    "is checked against its own booked rating."
            )
        )
        assertTrue(!html.contains("""\text{Usage}"""))
    }

    @Test
    fun theTandemBlockClosesWithTheDocumentsComparisonNote() {
        val html = tandemHtml()
        assertTrue(html.contains("<h2>5. NOTE</h2>"))
        assertTrue(html.contains("Effect of the Declared Tilt on Each Hook"))
        // Tonnes in the comparison table, because that is the unit the document's
        // own closing table uses.
        assertTrue(html.contains("<td>37.50 t</td>"))
        assertTrue(html.contains("<td>22.50 t</td>"))
        assertTrue(html.contains("<td>31.91 t</td>"))
        assertTrue(html.contains("<td>28.09 t</td>"))
        assertTrue(html.contains("<td>0.000 m (level)</td>"))
        assertTrue(html.contains("<td>4.589 m (crane 1 higher)</td>"))
    }

    // -----------------------------------------------------------------------
    // The reasons a block can be absent
    // -----------------------------------------------------------------------

    @Test
    fun aBlockThatWasNeverSetUpSaysWhichTabToComplete() {
        // Four distinct absences, four distinct sentences.  One shared "not set up"
        // would tell the reader to go and fill in a form that is already full.
        val noState = nonuniformSections(null, 1).joinToString("")
        assertTrue(
            noState.contains(
                "No nonuniform load has been set up. Complete the Nonuniform Load " +
                    "tab to include it in this plan."
            )
        )

        val withError = nonuniformSections(
            null, 1, error = "The COG must sit between the two pick points.",
        ).joinToString("")
        assertTrue(
            withError.contains(
                "The COG must sit between the two pick points. Complete the " +
                    "Nonuniform Load tab to include it in this plan."
            )
        )

        // The tandem block names its own tab, not the nonuniform one.
        val tandemMissing = nonuniformSections(null, 4, "Tandem Lift").joinToString("")
        assertTrue(tandemMissing.contains("<h2>4. TANDEM LIFT</h2>"))
        assertTrue(
            tandemMissing.contains(
                "No tandem lift has been set up. Complete the Tandem Lift tab to " +
                    "include it in this plan."
            )
        )
    }

    @Test
    fun aSolvedExampleWithNoRealLoadIsRefused() {
        // The forms seed themselves with the documents' worked examples, so a solved
        // scenario is not by itself evidence that a lift was set up. The hook load
        // arriving from the Overall Weight tab is what marks it as real.
        val zeroLoad = nonuniformSections(asym, 1, title = "Nonuniform Load").joinToString("")
        assertTrue(zeroLoad.contains("1. NONUNIFORM LOAD"))

        val unloaded = solveNonuniform(exampleNonuniformInputs("asym_2leg_shortening"), 0.0)
        val refused = nonuniformSections(unloaded, 1).joinToString("")
        assertTrue(
            refused.contains(
                "No nonuniform load has been set up. Enter a load weight on the " +
                    "Overall Weight tab and complete the Nonuniform Load tab to " +
                    "include it in this plan."
            )
        )
        // And it does not go on to print the geometry anyway.
        assertTrue(!refused.contains("Height Difference"))
    }

    @Test
    fun theTwoBlocksNumberThemselvesFromWhereverTheyStart() {
        // A tandem plan that started at 1 while a uniform block above it ended at 5
        // would give the page two section 1s.
        val fromSix = tandemSections(tandem, firstNumber = 6, title = "Tandem Lift")
        assertTrue(fromSix.first().contains("<h2>6. TANDEM LIFT</h2>"))
        assertTrue(fromSix.joinToString("").contains("<h2>10. NOTE</h2>"))
        assertEquals(5, fromSix.count { it.startsWith("<h2>") })
    }
}
