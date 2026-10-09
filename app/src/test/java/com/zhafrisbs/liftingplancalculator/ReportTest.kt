package com.zhafrisbs.liftingplancalculator

import com.zhafrisbs.liftingplancalculator.core.Crane
import com.zhafrisbs.liftingplancalculator.core.CraneInputs
import com.zhafrisbs.liftingplancalculator.core.CraneResult
import com.zhafrisbs.liftingplancalculator.core.LoadItem
import com.zhafrisbs.liftingplancalculator.core.LiftTotals
import com.zhafrisbs.liftingplancalculator.core.PRINT_MARGIN_MM
import com.zhafrisbs.liftingplancalculator.core.REPORT_CSS
import com.zhafrisbs.liftingplancalculator.core.ReportInputs
import com.zhafrisbs.liftingplancalculator.core.buildReportHtml
import com.zhafrisbs.liftingplancalculator.core.exampleCraneInputs
import com.zhafrisbs.liftingplancalculator.core.fmt
import com.zhafrisbs.liftingplancalculator.core.reportSectionsFor
import com.zhafrisbs.liftingplancalculator.core.texNum
import com.zhafrisbs.liftingplancalculator.core.wrapKatex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the report's crane section to the reference's own HTML.
 *
 * The expected fragments were printed by running the original Python over the same
 * inputs:
 *
 *     .venv/bin/python -c "from lifting_calculator.kivy_ui import crane_state as c; \
 *         from lifting_calculator.report_1leg import build_report_html; \
 *         print(build_report_html((2000.0,2000.0,0.0,[],[]), \
 *             c.solve(c.example_inputs(), 2000.0), {}, include=['crane']))"
 *
 * Fragments rather than whole strings, because the Kotlin CSS block is a raw
 * string and Python's is a triple-quoted one: the two differ by a leading and a
 * trailing newline, which no browser can see, and asserting whole-string equality
 * would pin that difference instead of the content.
 */
class ReportTest {

    private val exampleCrane: CraneResult = Crane.solve(exampleCraneInputs(), 2000.0)

    private fun report(crane: CraneResult?, include: Set<String> = setOf("crane")): String =
        buildReportHtml(
            ReportInputs(
                totals = LiftTotals.of(listOf(LoadItem(weightKg = 2000.0)), emptyList()),
                crane = crane,
            ),
            include = include,
        )

    // -----------------------------------------------------------------------
    // The crane table
    // -----------------------------------------------------------------------

    @Test
    fun theCraneTableCarriesTheReferencesOwnRows() {
        val html = report(exampleCrane)

        // Verbatim from the reference's output, row for row.
        assertTrue(
            html.contains("<tr><th>Crane Model</th><td>Kobelco CKE90G-2</td></tr>")
        )
        assertTrue(
            html.contains("<tr><th>Crane rated capacity</th><td>90,000.00 kg</td></tr>")
        )
        // **Was "Not entered", and that was the desktop's answer, not a mistake.**
        //
        // The reference reads the boom from the workbook's boom *column*, which the
        // desktop does only when a matrix workbook is loaded -- so a hand-typed line
        // printed "Not entered" for a boom length its own row carried.  See
        // [Crane.manualBoom] for why this app reads the row instead; this expectation
        // moves with that decision.
        assertTrue(html.contains("<tr><th>Boom length</th><td>40.00 m</td></tr>"))
        assertTrue(html.contains("<tr><th>Boom Angle</th><td>48.00 \u00b0</td></tr>"))
        assertTrue(html.contains("<tr><th>Working radius</th><td>8.00 m</td></tr>"))
        assertTrue(
            html.contains(
                "<tr><th>Chart capacity at radius</th><td>16,000.00 kg</td></tr>"
            )
        )
        assertTrue(
            html.contains("<tr><th>Gross load at hook</th><td>2,000.00 kg</td></tr>")
        )
    }

    @Test
    fun theChartSourceRowIsADeliberateDivergenceFromTheDesktop() {
        // `report_1leg.py` builds exactly this string and then never prints it --
        // grepping the module for `chart_source` finds one hit, the assignment. Its
        // justification comment ("a capacity with no stated source cannot be checked
        // against one") ended up above the "Gross load at hook" row, which is where
        // the intended row would have gone, so the omission is plainly accidental.
        //
        // The row is restored here. This test names it so the divergence is visible
        // rather than discovered by diffing two printed plans.
        val html = report(exampleCrane)
        assertTrue(html.contains("<tr><th>Chart source</th><td>"))

        // A blank crane has no source, and says so in the same words the capacity
        // row above it uses -- the two agree rather than contradicting each other.
        val blank = report(Crane.solve(CraneInputs(), 2000.0))
        assertTrue(
            blank.contains(
                "<tr><th>Chart source</th><td>Not evaluated</td></tr>"
            )
        )
    }

    // -----------------------------------------------------------------------
    // The equations
    // -----------------------------------------------------------------------

    @Test
    fun theDesignLoadEquationIsTheReferencesOwnString() {
        val html = report(exampleCrane)
        assertTrue(html.contains("<h3>Crane design load for the 75% allowable check, W:</h3>"))
        assertTrue(
            html.contains(
                """\[\text{W} = \frac{\text{Gross load at hook}}{0.75} = """ +
                    """\frac{2{,}000.00}{0.75} = 2{,}666.67\ \text{kg}\]"""
            )
        )
    }

    @Test
    fun theUsageEquationIsTheReferencesOwnString() {
        val html = report(exampleCrane)
        assertTrue(html.contains("<h3>75% Allowable:</h3>"))
        assertTrue(
            html.contains(
                """\[\text{Usage} = \frac{\text{Gross load at hook}}""" +
                    """{\text{Chart capacity at radius}} \times 100\% = """ +
                    """\frac{2{,}000.00}{16{,}000.00} \times 100\% = 12.5\%\]"""
            )
        )
        assertTrue(
            html.contains(
                "<p>WITHIN LIMIT \u2014 12.50% of capacity at working radius</p>"
            )
        )
    }

    // -----------------------------------------------------------------------
    // An empty form
    // -----------------------------------------------------------------------

    @Test
    fun aBlankCraneSaysNotEnteredRatherThanZero() {
        val blank = Crane.solve(CraneInputs(), 2000.0)
        val html = report(blank)

        // "Not entered" and "0.00" mean different things on a lift plan, and the
        // reference distinguishes them. The working radius here is 0.0, which is
        // "not entered" rather than "0 metres".
        assertTrue(html.contains("<tr><th>Crane Model</th><td>Not entered</td></tr>"))
        assertTrue(
            html.contains("<tr><th>Working radius</th><td>Not entered</td></tr>")
        )
        // The capacity is a different absence: the chart was never read, so it has
        // not been evaluated rather than not entered.
        assertTrue(
            html.contains(
                "<tr><th>Chart capacity at radius</th><td>Not evaluated</td></tr>"
            )
        )
        assertEquals("Enter the load chart to check capacity.", blank.capacityStatus)
        assertTrue(html.contains("<p>Enter the load chart to check capacity.</p>"))
    }

    @Test
    fun aChartWithNoCapacityOmitsTheUsageEquation() {
        // The design load still prints -- it depends only on the Overall Weight tab,
        // so it stays available while the chart is missing -- but an equation whose
        // numerator has no denominator is not printed at all rather than printed
        // with a zero.
        val html = report(Crane.solve(CraneInputs(), 2000.0))
        assertTrue(html.contains("""\text{W} = \frac{\text{Gross load at hook}}{0.75}"""))
        assertFalse(html.contains("""\text{Usage} ="""))
    }

    // -----------------------------------------------------------------------
    // The include set
    // -----------------------------------------------------------------------

    @Test
    fun aSectionIsGatedOnTheIncludeSet() {
        // A report that carries a section nobody asked for is as wrong as one that
        // omits a section somebody did. The scaffold is always present.
        val nothing = report(exampleCrane, include = emptySet())
        assertTrue(nothing.contains("<h1>Lifting Plan Calculation</h1>"))
        assertFalse(nothing.contains("Kobelco CKE90G-2"))
        assertFalse(nothing.contains("<table"))

        val craneOnly = report(exampleCrane, include = setOf("crane"))
        assertTrue(craneOnly.contains("Kobelco CKE90G-2"))
    }

    @Test
    fun theSetupNoteAppearsOnlyWhenThereIsNoLoad() {
        val withLoad = report(exampleCrane)
        assertFalse(withLoad.contains("Setup note:"))

        val withoutLoad = buildReportHtml(
            ReportInputs(totals = LiftTotals.of(emptyList(), emptyList()), crane = exampleCrane),
            include = setOf("crane"),
        )
        assertTrue(withoutLoad.contains("<b>Setup note:</b>"))
    }

    // -----------------------------------------------------------------------
    // Formatting
    // -----------------------------------------------------------------------

    @Test
    fun aNullFigureIsNotAZero() {
        // Only a genuine absence gets the dash. Zero is a measured value and prints
        // as one -- "0.00 kg" and "\u2014" mean different things on a lift plan.
        assertEquals("\u2014", fmt(null))
        assertEquals("0.00", fmt(0.0, 2))
        assertEquals("1,239.00 kg", fmt(1239.0, 2, "kg"))

        // A non-finite number is **not** a third case. The reference's `_number`
        // maps NaN and infinity to its default of zero before formatting, so both
        // print as "0.00" rather than as the dash. That is the reference's own
        // choice and it is kept, though it does mean an infinity arriving here
        // would be indistinguishable from a measured zero.
        assertEquals("0.00", fmt(Double.NaN, 2))
        assertEquals("0.00", fmt(Double.POSITIVE_INFINITY, 2))
    }

    @Test
    fun thousandsSeparatorsAreBracedSoTeXCanReadThem() {
        // A bare comma inside a KaTeX group is a punctuation mark, not a digit
        // separator, so "2,000.00" would typeset as two numbers.
        assertEquals("2{,}000.00", texNum(2000.0, 2))
        assertEquals("999.50", texNum(999.5, 2))
    }

    // -----------------------------------------------------------------------
    // The wrapper
    // -----------------------------------------------------------------------

    @Test
    fun theWrapperLoadsABundledKatexAndNothingRemote() {
        val page = wrapKatex("<p>body</p>")

        // Relative paths, which resolve against the APK's own assets through the
        // base URL the screen passes. Nothing here can reach the network -- and the
        // app declares no INTERNET permission, so that is structural.
        assertTrue(page.contains("<link rel=\"stylesheet\" href=\"katex.min.css\">"))
        assertTrue(page.contains("<script src=\"katex.min.js\"></script>"))
        assertTrue(page.contains("<script src=\"auto-render.min.js\"></script>"))
        assertFalse(page.contains("http://"))
        assertFalse(page.contains("https://"))

        // The margin lives in the stylesheet because the print framework drops
        // `setMinMargins` on this device -- measured, not assumed.
        assertTrue(page.contains("@page { size: A4; margin: ${PRINT_MARGIN_MM}mm; }"))

        // A typesetting failure records itself on the body rather than throwing into
        // the host, so the page still displays with the delimiters visible.
        assertTrue(page.contains("data-katex-error"))
        assertTrue(page.contains("throwOnError: false"))

        // The delimiters KaTeX is asked to look for, with the backslashes intact.
        assertTrue(page.contains("{left: \"\\\\(\", right: \"\\\\)\", display: false}"))
        assertTrue(page.contains("{left: \"\\\\[\", right: \"\\\\]\", display: true}"))

        assertTrue(page.contains("<p>body</p>"))
    }

    @Test
    fun theFooterSurvivesThePrintStylesheet() {
        // The reference's stylesheet hides the footer in print:
        //
        //     @media print { .report .report-footer { display: none; } }
        //
        // with no comment saying why.  That is why a printed plan came out without
        // the attribution line while the preview showed it -- and nothing on the
        // page said which of the two was meant, so it read as a bug rather than as
        // a rule.  The declaration is gone, so the printed page carries the same
        // footer the preview does.
        //
        // This test is written against the *absence* of the rule on purpose.  A
        // future port that copies the reference's stylesheet across verbatim would
        // otherwise reintroduce it silently, and the symptom is one nobody sees
        // until a plan has been printed and handed over.
        //
        // **Comments are stripped first**, and that is not a convenience: a CSS
        // comment cannot hide anything, and the note left in the rule's place quotes
        // the removed line so a reader can put it back.  Asserting on the raw text
        // would have failed on that note rather than on the rule -- which is exactly
        // what it did the first time this ran.
        val rules = REPORT_CSS.replace(
            Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL),
            "",
        )
        assertFalse(
            "no print-media rule may hide the footer",
            rules.contains("@media"),
        )
        // The footer's own styling is still there -- it is the print override that
        // went, not the element.
        assertTrue(rules.contains(".report .report-footer"))
        assertTrue(rules.contains("text-align: center;"))
    }

    // -----------------------------------------------------------------------
    // Which sections a button's report carries
    // -----------------------------------------------------------------------

    @Test
    fun aTabsReportCarriesItsOwnSectionAndTheCrane() {
        // The Crane tab's own section *is* the crane, so it has nothing to offer
        // beside itself -- no checkbox, and asking for one changes nothing.
        assertEquals(listOf("crane"), reportSectionsFor("crane", includeCrane = true))
        assertEquals(listOf("crane"), reportSectionsFor("crane", includeCrane = false))

        // A sling tab carries the crane by default, because the crane is booked
        // whichever arrangement is used...
        assertEquals(
            listOf("crane", "uniform"),
            reportSectionsFor("uniform", includeCrane = true),
        )
        // ...and its arrangement on its own when the box is unticked.
        assertEquals(listOf("uniform"), reportSectionsFor("uniform", includeCrane = false))

        // The order is the builder's, not the caller's. The builder emits its
        // sections in its own source order and merely gates them on membership, so
        // a caller's order is not observable in the page at all.
        assertEquals(
            listOf("crane", "tandem"),
            reportSectionsFor("tandem", includeCrane = true),
        )
        assertEquals(listOf("nonuniform"), reportSectionsFor("nonuniform", includeCrane = false))
    }
}
