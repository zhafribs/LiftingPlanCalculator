package com.zhafrisbs.liftingplancalculator

import com.zhafrisbs.liftingplancalculator.core.HITCH_ORDER
import com.zhafrisbs.liftingplancalculator.core.HITCH_SPECS
import com.zhafrisbs.liftingplancalculator.core.nextSectionNumber
import com.zhafrisbs.liftingplancalculator.core.geometrySection
import com.zhafrisbs.liftingplancalculator.core.sectionInputs
import com.zhafrisbs.liftingplancalculator.core.solveDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the uniform report's geometry and input sections to the reference's HTML.
 *
 * Every expected string was printed by running the original Python:
 *
 *     .venv/bin/python -c "from lifting_calculator import sling_1leg as s; \
 *         from lifting_calculator.report_1leg import _geometry_section, _hitch_info; \
 *         print(_geometry_section(s.solve_defaults('round_basket', 2000.0), \
 *             _hitch_info(s.solve_defaults('round_basket', 2000.0))))"
 *
 * These are *whole equations*, not fragments, because the equations are the evidence
 * the plan rests on: a substituted value that no longer produces the answer beside
 * it is exactly the class of bug this file has to catch, and it is a bug the
 * reference documents having shipped twice.
 */
class ReportUniformTest {

    private fun geometry(hitch: String): String {
        val result = solveDefaults(hitch, 2000.0)
        return geometrySection(result, HITCH_SPECS.getValue(result.hitch))
    }

    private fun inputs(hitch: String): String {
        val result = solveDefaults(hitch, 2000.0)
        return sectionInputs(result, HITCH_SPECS.getValue(result.hitch))
    }

    // -----------------------------------------------------------------------
    // Section 1 -- lift input parameters
    // -----------------------------------------------------------------------

    @Test
    fun theInputSectionMatchesTheReference() {
        val html = inputs("direct_hook")
        assertTrue(html.contains("<h2>1. LIFT INPUT PARAMETERS</h2>"))
        assertTrue(
            html.contains(
                "<tr><th>Sling Configuration</th>" +
                    "<td>1-Leg Sling Direct hook Hitch (1 Effective Leg)</td></tr>"
            )
        )
        assertTrue(html.contains("<tr><th>Sling assembly count</th><td>1 continuous sling</td></tr>"))
        assertTrue(html.contains("<tr><th>Sling Total Length</th><td>7.0 m</td></tr>"))
        assertTrue(
            html.contains(
                "<tr><th>Load Dimensions</th>" +
                    "<td>Not required for a direct vertical hitch</td></tr>"
            )
        )
        // The sentence that stops the design load being read as the sling load.
        assertTrue(
            html.contains(
                "Load Weight is the gross load at hook. The 75% allowable window " +
                    "applies to the crane load chart only and is shown in the crane " +
                    "section."
            )
        )
    }

    @Test
    fun aNestedInputSectionNamesItsPilesAndItsSecondSling() {
        val html = inputs("two_leg_nested_3leg")
        assertTrue(
            html.contains(
                "<tr><th>Sling Configuration</th><td>3-Leg Sling in Each Leg of " +
                    "2-Leg Sling Hitch (6 Effective Legs)</td></tr>"
            )
        )
        assertTrue(html.contains("<tr><th>Sling assembly count</th><td>2-sling bridle set</td></tr>"))
        assertTrue(html.contains("<tr><th>No of Piles</th><td>3</td></tr>"))
        assertTrue(html.contains("<tr><th>Second Sling Total Length</th><td>0.600 m</td></tr>"))
        assertTrue(html.contains("<tr><th>Secondary Pick Spacing</th><td>0.200 m</td></tr>"))
        assertTrue(html.contains("<tr><th>Sling Legs per Pile</th><td>2</td></tr>"))
    }

    @Test
    fun aTwoLegNestedSystemHasNoPileRow() {
        // Two inline legs means two piles, and the reference documents only state a
        // pile count once there are more than two -- so the row is absent rather than
        // reading "2".
        val html = inputs("two_leg_nested_2leg")
        assertTrue(html.contains("<tr><th>Second Sling Total Length</th><td>0.600 m</td></tr>"))
        assertTrue(!html.contains("No of Piles"))
        assertTrue(!html.contains("Sling Legs per Pile"))
    }

    // -----------------------------------------------------------------------
    // Section 2 -- geometry, arrangement by arrangement
    // -----------------------------------------------------------------------

    @Test
    fun theDirectHookGeometryMatchesTheReference() {
        val html = geometry("direct_hook")
        assertTrue(html.contains("<h2>2. GEOMETRIC &amp; HEADROOM CALCULATIONS</h2>"))
        assertTrue(html.contains("""\[\text{Wrap Length} = 0.000\ \text{m}\]"""))
        assertTrue(
            html.contains(
                """\[\text{Free Leg Length} = \text{Total Length} - \text{Wrap Length} = """ +
                    """7.0 - 0.000 = 7.000\ \text{m}\]"""
            )
        )
        assertTrue(html.contains("""\[\text{Angle} = 0.0^\circ\]"""))
        assertTrue(
            html.contains(
                """\[\text{Height}_{Hook-Load} = \text{Free Leg Length} = 7.000\ \text{m}\]"""
            )
        )
    }

    @Test
    fun theRoundBasketGeometryMatchesTheReference() {
        val html = geometry("round_basket")
        assertTrue(
            html.contains(
                """\[\text{Arc Length} = \pi \times \text{Radius} = \pi \times 0.500 """ +
                    """= 1.571\ \text{m}\]"""
            )
        )
        assertTrue(
            html.contains(
                """\[\text{Angle} = \sin^{-1}\left(\frac{\text{Radius}}""" +
                    """{\text{Free Leg Length}}\right) = \sin^{-1}\left(""" +
                    """\frac{0.500}{2.715}\right) = 10.61^\circ\]"""
            )
        )
    }

    @Test
    fun theBasketHeightCarriesEveryTermOfItsFormula() {
        // The reference shipped this bug: the `- Radius` was in the formula and
        // absent from the arithmetic, so the sum printed beside the answer did not
        // produce it -- 2.715 x 0.983 is 2.669, not the 2.168 shown, and the missing
        // 0.500 was the difference. The substituted values have to cover every term.
        val html = geometry("round_basket")
        assertTrue(
            html.contains(
                """\[\text{Height}_{Hook-Center} = \text{Free Leg Length} \times """ +
                    """\cos(10.61^\circ) - \text{Radius} = 2.715 \times 0.983 - 0.500 """ +
                    """= 2.168\ \text{m}\]"""
            )
        )
    }

    @Test
    fun theRoundChokeHeightStatesItsDerivation() {
        // The reference shipped this too: this row printed only the answer,
        // "Height = 0.077 m", while every other height in the same section showed
        // its formula -- so the one figure a reader is most likely to want to check
        // was the one they could not.
        val html = geometry("round_choke")
        assertTrue(
            html.contains(
                """\[Y = \frac{\text{Radius}}{\sin(\text{Choke Internal Angle})} """ +
                    """= \frac{0.500}{\sin(60^\circ)} = 0.577\ \text{m}\]"""
            )
        )
        assertTrue(
            html.contains(
                """\[\text{Height}_{Choke-Load} = Y - \text{Radius} = 0.577 - 0.500 """ +
                    """= 0.077\ \text{m}\]"""
            )
        )
        assertTrue(
            html.contains(
                """\[\text{Height}_{Hook-Choke} = \text{Total Length} - """ +
                    """\text{Perimeter} = 7.0 - 3.142 = 3.858\ \text{m}\]"""
            )
        )
    }

    @Test
    fun theTwoLegDirectGeometryMatchesTheReference() {
        val html = geometry("two_leg_direct")
        assertTrue(
            html.contains(
                """\[\text{Angle} = \sin^{-1}\left(\frac{\text{Distance}/2}""" +
                    """{\text{Free Leg Length}}\right) = \sin^{-1}\left(""" +
                    """\frac{2.500}{7.000}\right) = 20.92^\circ\]"""
            )
        )
        assertTrue(
            html.contains(
                """\[\text{Height}_{Hook-Load} = \text{Free Leg Length} \times """ +
                    """\cos(20.92^\circ) = 7.000 \times 0.934 = 6.538\ \text{m}\]"""
            )
        )
    }

    @Test
    fun theThreeLegBridleGeometryMatchesTheReference() {
        // A 3-leg bridle spreads over a circle, so its radius is the pick distance
        // over root three -- a different derivation from the 4-leg rectangle.
        val html = geometry("three_leg_direct")
        assertTrue(
            html.contains(
                """\[\text{Radius Spread} = \frac{\text{Pick Distance}}{\sqrt{3}} """ +
                    """= \frac{5.400}{\sqrt{3}} = 3.118\ \text{m}\]"""
            )
        )
        assertTrue(
            html.contains("Sling Contact Perimeter Wrap:")
        )
    }

    @Test
    fun theNestedGeometryMatchesTheReference() {
        val html = geometry("two_leg_nested_3leg")
        assertTrue(html.contains("""\[\text{Free Leg Length}_1 = 7.000\ \text{m}\]"""))
        assertTrue(html.contains("""\[\text{Free Leg Length}_2 = 0.600\ \text{m}\]"""))

        // One transverse angle per leg group, outermost first, and the middle leg of
        // a 3-pile row hangs straight down.
        assertTrue(
            html.contains(
                """\[\text{Angle}_{\text{Trans Outer}} = \sin^{-1}\left(""" +
                    """\frac{\text{Offset}}{\text{Free Leg Length}_2}\right) = """ +
                    """\sin^{-1}\left(\frac{0.200}{0.600}\right) = 19.47^\circ\]"""
            )
        )
        assertTrue(
            html.contains(
                """\[\text{Angle}_{\text{Trans Mid}} = \sin^{-1}\left(""" +
                    """\frac{\text{Offset}}{\text{Free Leg Length}_2}\right) = """ +
                    """\sin^{-1}\left(\frac{0.000}{0.600}\right) = 0.00^\circ\]"""
            )
        )
        // The captions are the documents' own, and the ampersand is escaped. The
        // closing bracket belongs to the subheading's own template rather than to
        // the caption, so it sits between the last digit and the colon.
        assertTrue(
            html.contains(
                "Transverse Sling Leg Angle from Vertical " +
                    "(Outer Secondary Legs 1 &amp; 3):"
            )
        )
        assertTrue(
            html.contains(
                "Transverse Sling Leg Angle from Vertical " +
                    "(Middle Secondary Leg 2):"
            )
        )

        // The height is the governing (outermost) leg's own angle, not the worst
        // angle overall -- the two coincide here but the token says which is meant.
        assertTrue(
            html.contains(
                """\[\text{Height}_{Sling2} = \text{Free Leg Length}_2 \times """ +
                    """\cos(\text{Angle}_{Trans\,Outer}) = 0.600 \times 0.943 """ +
                    """= 0.566\ \text{m}\]"""
            )
        )
        assertTrue(
            html.contains(
                """\[\text{Level Difference} = \text{Free Leg Length}_2 \times """ +
                    """\left(\cos(\text{Angle}_{Trans\,Mid}) - """ +
                    """\cos(\text{Angle}_{Trans\,Outer})\right) = 0.600 \times """ +
                    """(1.000 - 0.943) = 0.034\ \text{m}\]"""
            )
        )
        assertTrue(
            html.contains(
                """\[\text{Total Combined} = \text{Free Leg Length}_1 + """ +
                    """\text{Height}_{Sling2} = 7.000 + 0.566 = 7.566\ \text{m}\]"""
            )
        )
        // The nested documents' own caption for the total headroom.
        assertTrue(
            html.contains("Total Vertical Height (Hook to Top of RC Piles Headroom):")
        )
        assertTrue(
            html.contains(
                """\[\text{Height}_{Hook-Load} = \text{Total Combined} \times """ +
                    """\cos(20.91^\circ) = 7.566 \times 0.934 = 7.067\ \text{m}\]"""
            )
        )
    }

    // -----------------------------------------------------------------------
    // Coverage
    // -----------------------------------------------------------------------

    @Test
    fun everyArrangementProducesAGeometrySectionOfItsOwn() {
        // Nineteen branches, and the last one is a fall-through rather than a match:
        // a new arrangement added to the engine without a branch here would silently
        // render the rectangular choke's geometry under its own heading.
        for (hitch in HITCH_ORDER) {
            val html = geometry(hitch)
            assertTrue("$hitch heading", html.contains("<h2>2. GEOMETRIC &amp; HEADROOM CALCULATIONS</h2>"))
            assertTrue("$hitch equations", html.contains("<div class=\"equation\">\\["))
            // Every branch ends with a headroom figure.
            assertTrue("$hitch headroom", html.contains("Height}_{Hook"))
        }
    }

    @Test
    fun theSectionNumberOfTheNextBlockIsCountedNotAssumed() {
        // The uniform section emits a fifth heading (its notes) only for the
        // arrangements that need one, so a fixed offset would number the next block
        // wrong for exactly those.
        assertEquals(1, nextSectionNumber(emptyList()))
        assertEquals(2, nextSectionNumber(listOf("<h2>1. LIFT INPUT PARAMETERS</h2>")))
        assertEquals(
            3,
            nextSectionNumber(
                listOf(
                    "<h2>1. LIFT INPUT PARAMETERS</h2>",
                    "<h2>2. GEOMETRIC &amp; HEADROOM CALCULATIONS</h2>",
                )
            )
        )
        // Unnumbered markup does not move the counter.
        assertEquals(
            2,
            nextSectionNumber(listOf("<h1>Lifting Plan Calculation</h1>", "<h2>1. ONE</h2>"))
        )
    }
}
