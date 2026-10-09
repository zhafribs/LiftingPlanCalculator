package com.zhafrisbs.liftingplancalculator

import com.zhafrisbs.liftingplancalculator.core.HITCH_ORDER
import com.zhafrisbs.liftingplancalculator.core.HITCH_SPECS
import com.zhafrisbs.liftingplancalculator.core.LiftTotals
import com.zhafrisbs.liftingplancalculator.core.LoadItem
import com.zhafrisbs.liftingplancalculator.core.OneLegInputs
import com.zhafrisbs.liftingplancalculator.core.ReportInputs
import com.zhafrisbs.liftingplancalculator.core.buildReportHtml
import com.zhafrisbs.liftingplancalculator.core.capacitySection
import com.zhafrisbs.liftingplancalculator.core.factorsSection
import com.zhafrisbs.liftingplancalculator.core.notesSection
import com.zhafrisbs.liftingplancalculator.core.showsNotes
import com.zhafrisbs.liftingplancalculator.core.solveOneLeg
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the uniform report's factors, capacity and notes sections to the reference.
 *
 * Every expected string was printed by running the original Python:
 *
 *     .venv/bin/python -c "from lifting_calculator import sling_1leg as s; \
 *         from lifting_calculator.report_1leg import _factors_section, _hitch_info; \
 *         r = s.solve_one_leg(s.OneLegInputs('direct_hook', 7.0, tag_wll=5000.0), 2000.0); \
 *         print(_factors_section(r, _hitch_info(r)))"
 *
 * A tag WLL is entered throughout, because the capacity and utilisation blocks are
 * exactly the ones that render "Not entered" without one — and those blocks are
 * where the reference documents having shipped a denominator that arrived from
 * nowhere.
 */
class ReportCapacityTest {

    private fun solve(inputs: OneLegInputs) = solveOneLeg(inputs, 2000.0)

    private fun sections(
        inputs: OneLegInputs,
    ): Triple<String, String, String> {
        val r = solve(inputs)
        val spec = HITCH_SPECS.getValue(r.hitch)
        return Triple(
            factorsSection(r, spec),
            capacitySection(r, spec),
            notesSection(r, spec),
        )
    }

    private val directHook = OneLegInputs(
        hitch = "direct_hook", slingLength = 7.0, tagWll = 5000.0,
    )
    private val twoLegDirect = OneLegInputs(
        hitch = "two_leg_direct", slingLength = 7.0, pickDistance = 5.0,
        tagWll = 5300.0, tagWllAngle = 45.0,
    )
    private val roundChoke = OneLegInputs(
        hitch = "round_choke", slingLength = 7.0, diameter = 1.0, chokeAngle = 60.0,
        tagWll = 5000.0,
    )
    private val nested3 = OneLegInputs(
        hitch = "two_leg_nested_3leg", slingLength = 7.0, pickDistance = 5.4,
        secondaryLength = 0.6, secondarySpread = 0.2,
        tagWll = 5000.0, secondaryTagWll = 3000.0,
    )

    private fun reportWith(inputs: OneLegInputs): String = buildReportHtml(
        ReportInputs(
            totals = LiftTotals.of(listOf(LoadItem(weightKg = 2000.0)), emptyList()),
            sling = solve(inputs),
        ),
        include = setOf("uniform"),
    )

    // -----------------------------------------------------------------------
    // Section 3 -- factors and tension
    // -----------------------------------------------------------------------

    @Test
    fun theSingleLegFactorsMatchTheReference() {
        val (factors, _, _) = sections(directHook)
        assertTrue(factors.contains("<h2>3. RIGGING FACTORS &amp; TENSION CALCULATIONS</h2>"))
        assertTrue(factors.contains("""\[\text{AF} = \cos(0.00^\circ) = 1.000\]"""))
        assertTrue(factors.contains("""\[\text{RF}_{Base} = 1.00\]"""))
        assertTrue(factors.contains("Tension in Each Effective Legs:"))
        assertTrue(
            factors.contains(
                """\[\text{Tension} = \frac{\text{Load Weight}}""" +
                    """{\text{Effective Legs} \times \text{AF}} = \frac{2{,}000}""" +
                    """{1 \times 1.000} = 2{,}000.0\ \text{kg}\]"""
            )
        )
    }

    @Test
    fun theChokeFactorTableAndItsNotesMatchTheReference() {
        val (factors, capacity, notes) = sections(roundChoke)

        // A choke's main lifting leg stands vertical, so its factor is exactly 1.0
        // and is printed as 1.0 rather than as a rounded cosine.
        assertTrue(factors.contains("""\[\text{AF} = \cos(0^\circ) = 1.0\]"""))
        assertTrue(factors.contains("The main lifting leg stands perfectly vertical (0\u00b0)."))
        assertTrue(factors.contains("""\[\text{RF}_{Base} = 0.75\]"""))
        assertTrue(factors.contains("""\[\text{Reduction Factor} = 1.00\]"""))
        assertTrue(
            factors.contains(
                """\[\text{Combined RF} = \text{RF}_{Base} \times """ +
                    """\text{Reduction Factor} = 0.75 \times 1.00 = 0.7500\]"""
            )
        )

        // The required WLL divides by the *combined* factor, not the base one.
        assertTrue(
            capacity.contains(
                """\[\text{Minimum WLL} = \frac{\text{Load Weight}}""" +
                    """{\text{Sling Legs} \times \text{Combined RF} \times \text{AF}} """ +
                    """= \frac{2{,}000}{1 \times 0.7500 \times 1.000} = 2{,}666.7\ \text{kg}\]"""
            )
        )

        assertTrue(notes.contains("<h2>5. NOTE</h2>"))
        assertTrue(notes.contains("Choke Notes"))
        assertTrue(
            notes.contains(
                "(1) Percent of sling rated capacity in a choker hitch. The rating " +
                    "reduction is applied to the tag WLL, not to the resolved " +
                    "main-leg tension."
            )
        )
        assertTrue(
            notes.contains("Selected external angle: 120 \u00b0 \u2014 reduction factor 1.00.")
        )
        // The whole reduction table, verbatim.
        assertTrue(notes.contains("<tr><td>120\u00b0 and over</td><td>1.00</td></tr>"))
        assertTrue(notes.contains("<tr><td>90\u00b0 to under 120\u00b0</td><td>0.87</td></tr>"))
        assertTrue(notes.contains("<tr><td>60\u00b0 to under 90\u00b0</td><td>0.74</td></tr>"))
        assertTrue(notes.contains("<tr><td>30\u00b0 to under 60\u00b0</td><td>0.62</td></tr>"))
        assertTrue(notes.contains("<tr><td>Below 30\u00b0</td><td>0.49</td></tr>"))
    }

    @Test
    fun theNestedFactorsPublishOneRowPerLegGroup() {
        val (factors, _, _) = sections(nested3)
        assertTrue(factors.contains("""\[\text{AF}_{Long} = \cos(20.91^\circ) = 0.934\]"""))
        // Outermost group first, and its factor is the product of the published pair.
        assertTrue(
            factors.contains("Transverse Sling Angle Factor for Outer Legs (AF Trans Outer):")
        )
        assertTrue(
            factors.contains(
                """\[\text{AF}_{\text{Trans Outer}} = \cos(19.47^\circ) = 0.943\]"""
            )
        )
        assertTrue(
            factors.contains("Combined Sling Angle Factor (AF) for Outer Secondary Legs:")
        )
        assertTrue(
            factors.contains(
                """\[\text{AF}_{\text{Combined Outer}} = \text{AF}_{Long} \times """ +
                    """\text{AF}_{\text{Trans Outer}} = 0.934 \times 0.943 = 0.881\]"""
            )
        )
        // The middle leg of a 3-pile row hangs straight down, so its factor is 1.000.
        assertTrue(factors.contains("Transverse Sling Angle Factor for Mid Legs (AF Trans Mid):"))
        assertTrue(factors.contains("Combined Sling Angle Factor (AF) for Middle Secondary Legs:"))
        // The tension heading uses the documents' phrase, not the label.
        assertTrue(factors.contains("Tension in the Sloped Outer Secondary Legs:"))
        assertTrue(
            factors.contains(
                """\[\text{Tension}_{\text{Secondary Outer}} = """ +
                    """\frac{\text{Load Weight}}{\text{Sling Legs}_1 \times """ +
                    """\text{Sling Legs}_2 \times \text{AF}_{\text{Combined Outer}}} = """ +
                    """\frac{2{,}000}{2 \times 3 \times 0.881} = 378.4\ \text{kg}\]"""
            )
        )
    }

    // -----------------------------------------------------------------------
    // Section 4 -- final capacity
    // -----------------------------------------------------------------------

    @Test
    fun aSingleLegPrintsNoTagCapacityBlock() {
        // A single leg carries the whole load, so there is no derate to demonstrate:
        // it is rated straight against the tag WLL, and no "Sling Leg Capacity Each"
        // block is printed for it.
        val (_, capacity, _) = sections(directHook)
        assertTrue(capacity.contains("<h2>4. FINAL CAPACITY &amp; EQUIPMENT SELECTION</h2>"))
        assertTrue(!capacity.contains("Sling Leg Capacity Each"))
        assertTrue(
            capacity.contains(
                """\[\text{WLL Utilisation} = \frac{\text{Minimum WLL}}""" +
                    """{\text{Installed tag WLL}} \times 100\% = \frac{2{,}000.0}""" +
                    """{5{,}000.0} \times 100\% = 40.00\%\]"""
            )
        )
        assertTrue(
            capacity.contains(
                "<b>Tag rating check:</b> <span class='pass'>PASS</span> \u2014 the " +
                    "sling leg is 40.00% utilised."
            )
        )
    }

    @Test
    fun theMultiLegCapacityBlockDerivesItsDenominator() {
        val (_, capacity, _) = sections(twoLegDirect)
        assertTrue(capacity.contains("Sling Leg Capacity Each (Tag Rating):"))
        // The denominator is derived, not asserted: 5,300 / (2 x 0.707).
        assertTrue(
            capacity.contains(
                """\[\text{Sling Leg Capacity Per} = """ +
                    """\frac{\text{Installed Tag WLL}}""" +
                    """{\text{Sling Legs} \times \cos(\text{Tag WLL Angle})} = """ +
                    """\frac{5{,}300}{2 \times 0.707} = 3{,}748.2\ \text{kg}\]"""
            )
        )
        // The heading names the leg count the rating is declared on.
        assertTrue(capacity.contains("Minimum Required Sling WLL (Tag Rating for Sling Legs = 2):"))
        // And the utilisation divides by that capacity, naming it as the capacity
        // rather than as the tag's WLL.
        assertTrue(
            capacity.contains(
                """\[\text{WLL Utilisation} = \frac{\text{Minimum WLL}}""" +
                    """{\text{Sling Leg Capacity Per}} \times 100\% = """ +
                    """\frac{1{,}070.7}{3{,}748.2} \times 100\% = 28.57\%\]"""
            )
        )
    }

    @Test
    fun aNestedLiftNamesBothSlingsThroughout() {
        val (_, capacity, notes) = sections(nested3)
        assertTrue(capacity.contains("Sling Leg Capacity Each for First Sling (Tag Rating):"))
        assertTrue(capacity.contains("Sling Leg Capacity Each for Second Sling (Tag Rating):"))
        assertTrue(
            capacity.contains(
                """\[\text{First Sling Leg Capacity Per} = """ +
                    """\frac{\text{Installed Tag WLL}_1}""" +
                    """{\text{Sling Legs}_1 \times \cos(\text{Tag WLL Angle}_1)} = """ +
                    """\frac{5{,}000}{2 \times 0.707} = 3{,}536.1\ \text{kg}\]"""
            )
        )
        // The second sling's own tag, at its own rated leg count.
        assertTrue(
            capacity.contains(
                """\[\text{Second Sling Leg Capacity Per} = """ +
                    """\frac{\text{Installed Tag WLL}_2}""" +
                    """{\text{Sling Legs}_2 \times \cos(\text{Tag WLL Angle}_2)} = """ +
                    """\frac{3{,}000}{2 \times 0.707} = 2{,}121.6\ \text{kg}\]"""
            )
        )
        // Two ratings means two check lines, each naming its sling.
        assertTrue(
            capacity.contains("<b>Tag rating check (first sling):</b> <span class='pass'>PASS</span>")
        )
        assertTrue(
            capacity.contains("<b>Tag rating check (second sling):</b> <span class='pass'>PASS</span>")
        )
        // The rule is stated for both counts, because a 3-leg second sling is rated
        // on two and that is not the number the form asked for.
        assertTrue(
            notes.contains(
                "Applied in this report: the first sling is a 2-leg sling, so the " +
                    "rule above gives Sling Legs = 2 for the first sling's leg " +
                    "capacity, and the second sling is a 3-leg sling, so the same " +
                    "rule gives Sling Legs = 2 for the second sling's leg capacity."
            )
        )
    }

    @Test
    fun everyArrangementCarriesTheTagRatingRuleWhenItHasMoreThanOneLeg() {
        val (_, _, notes) = sections(twoLegDirect)
        assertTrue(notes.contains("No choke reduction applies to the selected arrangement."))
        assertTrue(notes.contains("To Calculate Sling Leg Capacity Each (Tag Rating):"))
        assertTrue(notes.contains("If 4-Leg Sling, use Sling Legs = 3"))
        assertTrue(notes.contains("If 3-Leg Sling, use Sling Legs = 2"))
        assertTrue(notes.contains("If 2-Leg Sling, use Sling Legs = 2"))
    }

    @Test
    fun sectionFiveIsShownExactlyForTheArrangementsThatHaveOne() {
        // The reference's own rule: the choke arrangements carry the reduction table
        // and every multi-leg arrangement carries the tag-rating rule. The
        // single-sling documents have no section 5 -- and there are five of them.
        val silent = HITCH_ORDER.filter { !showsNotes(HITCH_SPECS.getValue(it)) }
        assertEquals(
            listOf(
                "direct_hook",
                "round_basket",
                "rect_basket",
                "vertical_round_basket",
                "vertical_rect_basket",
            ),
            silent,
        )
    }

    // -----------------------------------------------------------------------
    // The whole uniform block
    // -----------------------------------------------------------------------

    @Test
    fun theWholeSlingBlockNumbersItsSectionsInOrder() {
        // Five headings, numbered 1 to 5 with no gaps and no repeats -- and that
        // numbering is what the next block reads to continue from.
        val html = reportWith(twoLegDirect)
        for (n in 1..5) {
            assertTrue("section $n", html.contains("<h2>$n. "))
        }
        assertEquals(5, Regex("<h2>\\d+\\.").findAll(html).count())
        assertTrue(html.contains("<h2>2. GEOMETRIC &amp; HEADROOM CALCULATIONS</h2>"))
        assertTrue(html.contains("<h2>5. NOTE</h2>"))
    }

    @Test
    fun aHalfFilledFormContributesNoSlingSectionAtAll() {
        // The block is gated on there being a solve, so a form that cannot solve
        // adds nothing rather than a page of dashes under a heading.
        val html = buildReportHtml(
            ReportInputs(
                totals = LiftTotals.of(listOf(LoadItem(weightKg = 2000.0)), emptyList()),
                sling = null,
            ),
            include = setOf("uniform"),
        )
        assertTrue(!html.contains("LIFT INPUT PARAMETERS"))
        assertTrue(html.contains("<h1>Lifting Plan Calculation</h1>"))
    }
}
