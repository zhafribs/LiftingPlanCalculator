package com.zhafrisbs.liftingplancalculator

import com.zhafrisbs.liftingplancalculator.core.ChartRow
import com.zhafrisbs.liftingplancalculator.core.CraneInputs
import com.zhafrisbs.liftingplancalculator.core.ExcelImport
import com.zhafrisbs.liftingplancalculator.core.ExcelImportException
import com.zhafrisbs.liftingplancalculator.core.Figures
import com.zhafrisbs.liftingplancalculator.core.LoadChartData
import com.zhafrisbs.liftingplancalculator.ui.screens.chartTable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Pins the workbook reader and the import rules to the reference's own output.
 *
 * Every expectation below was printed by running the original Python against these
 * same tables — `excel_loader.load_chart_from_excel` for the parsing and
 * `kivy_ui/excel_state.py` for the rules:
 *
 *     .venv/bin/python -c "from lifting_calculator.excel_loader import \
 *         load_chart_from_excel; c = load_chart_from_excel('/tmp/matrix.xlsx'); \
 *         print(c.matrix, c.booms, c.unit, c.rows)"
 *
 * The workbooks are built here rather than committed as binaries, but they encode
 * exactly the cell values those Python runs used, down to the header text and the
 * capacity magnitudes that decide the ton-versus-kg detection.  A reader that
 * disagrees with the reference on any of them disagrees about a figure on a lift
 * plan.
 *
 * Two of these assertions exist because the reference got them wrong first and
 * shipped it: the boom column is the **last**, not the greatest, and the suggested
 * radius is the **farthest** feasible one, not the nearest.
 */
class ExcelImportTest {

    // -----------------------------------------------------------------------
    // Fixtures
    // -----------------------------------------------------------------------

    private val matrixRows = listOf(
        listOf("Radius", 9.8, 13.3, 16.9, 20.4, 23.9, 27.5, 31.0),
        listOf(3.0, 20.0, 17.5, 14.5, 12.0, 10.0, 8.5, 7.0),
        listOf(5.0, 18.0, 15.0, 12.5, 10.5, 8.5, 7.0, 5.5),
        listOf(10.0, 12.0, 10.0, 8.0, 6.5, 5.0, 3.5, 2.5),
        listOf(16.0, 8.0, 6.5, 5.0, 4.0, 3.0, 2.0, 1.5),
    )

    private val columnRows = listOf(
        listOf("Radius", "Load", "Boom Length"),
        listOf(5.0, 10000.0, 20.0),
        listOf(10.0, 5000.0, 20.0),
        listOf(15.0, 2500.0, 20.0),
    )

    /** Booms in *descending* order, which is what tells "last" apart from "greatest". */
    private val descendingRows = listOf(
        listOf("Radius", 31.0, 27.5, 9.8),
        listOf(10.0, 2500.0, 3500.0, 12000.0),
        listOf(16.0, 1500.0, 2000.0, 8000.0),
    )

    private fun matrixChart(): LoadChartData =
        ExcelImport.readChart(buildWorkbook("Chart", matrixRows))

    private fun columnChart(): LoadChartData =
        ExcelImport.readChart(buildWorkbook("Chart", columnRows))

    // -----------------------------------------------------------------------
    // Parsing
    // -----------------------------------------------------------------------

    @Test
    fun aMatrixWorkbookParsesToTheReferencesOwnTable() {
        val chart = matrixChart()

        assertEquals(true, chart.matrix)
        assertEquals(listOf(9.8, 13.3, 16.9, 20.4, 23.9, 27.5, 31.0), chart.booms)
        // Capacities are small numbers, so they are tonnes and have been scaled.
        assertEquals("ton", chart.unit)
        assertEquals("Chart.xlsx / Chart sheet / boom length row 1 / unit ton", chart.origin)

        assertEquals(4, chart.rows.size)
        assertEquals(3.0, chart.rows[0].radius!!, 1e-9)
        assertEquals(
            listOf(20000.0, 17500.0, 14500.0, 12000.0, 10000.0, 8500.0, 7000.0),
            chart.rows[0].capacities,
        )
        assertEquals(16.0, chart.rows[3].radius!!, 1e-9)
        assertEquals(
            listOf(8000.0, 6500.0, 5000.0, 4000.0, 3000.0, 2000.0, 1500.0),
            chart.rows[3].capacities,
        )
    }

    @Test
    fun aMatrixWorkbookIsLookedUpAtTheNamedBoom() {
        val chart = matrixChart()
        assertEquals(3500.0, chart.capacityAt(10.0, boomLength = 27.5), 1e-9)
        assertEquals(2500.0, chart.capacityAt(10.0, boomLength = 31.0), 1e-9)
    }

    @Test
    fun aColumnWorkbookParsesToTheReferencesOwnTable() {
        val chart = columnChart()

        assertEquals(false, chart.matrix)
        assertEquals(emptyList<Double>(), chart.booms)
        assertEquals("kg", chart.unit)
        assertEquals("Chart.xlsx / Chart sheet / header row 1", chart.origin)
        assertEquals(3, chart.rows.size)
        assertEquals(5.0, chart.rows[0].radius!!, 1e-9)
        assertEquals(10000.0, chart.rows[0].load!!, 1e-9)
        assertEquals(20.0, chart.rows[0].boom!!, 1e-9)
        assertEquals(5000.0, chart.capacityAt(10.0), 1e-9)
    }

    @Test
    fun aFileThatIsNotAWorkbookIsRefusedInWordsAnOperatorCanActOn() {
        assertEquals(
            "That file was empty.",
            assertThrows(ExcelImportException::class.java) {
                ExcelImport.readChart(ByteArray(0))
            }.message,
        )
        // Random bytes are not a zip, let alone a workbook.
        val failure = assertThrows(ExcelImportException::class.java) {
            ExcelImport.readChart("this is not a spreadsheet".toByteArray())
        }
        assertTrue(failure.message!!.contains("not an Excel workbook"))
    }

    // -----------------------------------------------------------------------
    // The import rules
    // -----------------------------------------------------------------------

    @Test
    fun theBoomColumnDefaultsToTheLastOneAndNotTheGreatest() {
        val chart = ExcelImport.readChart(buildWorkbook("Chart", descendingRows))

        assertEquals(listOf(31.0, 27.5, 9.8), ExcelImport.availableBooms(chart))
        // The reference's `_populate_boom_selector` selects `count() - 1` under the
        // comment "default to the longest boom extension".  On this workbook the
        // last entry is 9.8 m while `max()` is 31.0 m, so the two readings differ
        // and only one of them is the desktop's.
        assertEquals(2, ExcelImport.selectedBoomIndex(chart))
        assertEquals(9.8, ExcelImport.selectedBoomLength(chart)!!, 1e-9)
    }

    @Test
    fun aColumnWorkbookHasNoBoomToSelect() {
        val chart = columnChart()
        assertEquals(emptyList<Double>(), ExcelImport.availableBooms(chart))
        assertNull(ExcelImport.selectedBoomLength(chart))
        assertEquals(0, ExcelImport.selectedBoomIndex(chart))
    }

    @Test
    fun theOfferedRadiiAreTheOnesThisBoomHasACapacityFor() {
        val chart = matrixChart()
        assertEquals(listOf(3.0, 5.0, 10.0, 16.0), ExcelImport.chartRadii(chart, 31.0))
        assertEquals(listOf(3.0, 5.0, 10.0, 16.0), ExcelImport.chartRadii(chart, 27.5))
        assertEquals(listOf(5.0, 10.0, 15.0), ExcelImport.chartRadii(columnChart()))
    }

    @Test
    fun theSuggestedRadiusIsTheFarthestThatStillCarriesTheLift() {
        val chart = matrixChart()

        // The whole point of the rule: a longer boom carries the same weight
        // further out, so the answer moves with the boom.
        assertEquals(5.0, ExcelImport.suggestRadius(chart, 2000.0)!!, 1e-9)
        assertEquals(10.0, ExcelImport.suggestRadius(chart, 2000.0, 27.5)!!, 1e-9)
        assertEquals(16.0, ExcelImport.suggestRadius(chart, 2000.0, 9.8)!!, 1e-9)
    }

    @Test
    fun aDeclinedSuggestionIsNullRatherThanZero() {
        val chart = matrixChart()
        // No load yet: importing before entering a lift must not choose a radius a
        // later weight would invalidate.
        assertNull(ExcelImport.suggestRadius(chart, 0.0))
        // Nothing on the chart carries it: snapping to the largest radius would
        // hide the very shortfall the operator needs to see.
        assertNull(ExcelImport.suggestRadius(chart, 50000.0))
    }

    @Test
    fun theOfferedRadiusForAColumnWorkbookComesFromItsOwnRows() {
        assertEquals(10.0, ExcelImport.suggestRadius(columnChart(), 2000.0)!!, 1e-9)
    }

    @Test
    fun theMirroredEntryCarriesThreeFieldsAndNeverTheAngle() {
        val chart = matrixChart()
        val mirrored = ExcelImport.mirroredPoint(chart, 5.0)
        assertNotNull(mirrored)
        // (radius, capacity, boom).  A matrix chart has a boom; a column chart
        // takes it off the nearest row instead.
        assertEquals(5.0, mirrored!!.first, 1e-9)
        assertEquals(5500.0, mirrored.second, 1e-9)
        assertNull(mirrored.third)

        val column = ExcelImport.mirroredPoint(columnChart(), 10.0)
        assertEquals(10.0, column!!.first, 1e-9)
        assertEquals(5000.0, column.second, 1e-9)
        assertEquals(20.0, column.third!!, 1e-9)

        // A half-typed radius is a line still being entered, not 0 m.
        assertNull(ExcelImport.mirroredPoint(chart, 0.0))
    }

    @Test
    fun theLabelNamesTheFileTheUnitAndTheBoom() {
        val chart = matrixChart()
        assertEquals(
            "Chart.xlsx (capacity in ton) \u2014 read from boom 31 m",
            ExcelImport.chartLabel(chart, "Chart.xlsx"),
        )
        assertEquals(
            "Chart.xlsx (capacity in ton) \u2014 read from boom 27.5 m",
            ExcelImport.chartLabel(chart, "Chart.xlsx", 27.5),
        )
        // A column chart has no boom to name, so the note is absent rather than
        // saying "boom 0 m".
        assertEquals(
            "column.xlsx (capacity in kg)",
            ExcelImport.chartLabel(columnChart(), "column.xlsx"),
        )
    }

    // -----------------------------------------------------------------------
    // What an import does to the form
    // -----------------------------------------------------------------------

    @Test
    fun importingChoosesTheDesktopBoomAndTheDesktopRadius() {
        val chart = matrixChart()
        val after = ExcelImport.importedInputs(
            CraneInputs(workingRadius = 8.0), chart, "Chart.xlsx", 2000.0,
        )

        assertEquals("excel", after.chartSource)
        assertEquals(31.0, after.chartBoom!!, 1e-9)
        assertEquals(listOf(3.0, 5.0, 10.0, 16.0), after.chartRadii)
        // 5.00 m: not the 8.00 m the form was left on, and not the nearest radius.
        assertEquals(5.0, after.workingRadius, 1e-9)
        assertEquals(
            listOf(3.0 to 7000.0, 5.0 to 5500.0, 10.0 to 2500.0, 16.0 to 1500.0),
            after.chartPoints,
        )
        // The one manual entry is a mirror of the selection, not the chart.
        assertEquals(1, after.rows.size)
        assertEquals(5.0, after.rows[0].radius, 1e-9)
        assertEquals(5500.0, after.rows[0].capacity, 1e-9)
    }

    @Test
    fun switchingBoomReAsksTheRadiusAndTheFigures() {
        val chart = matrixChart()
        val imported = ExcelImport.importedInputs(CraneInputs(), chart, "Chart.xlsx", 2000.0)
        val switched = ExcelImport.boomChanged(imported, chart, 27.5, 2000.0)

        assertEquals(27.5, switched.chartBoom!!, 1e-9)
        assertEquals(10.0, switched.workingRadius, 1e-9)
        assertEquals(
            listOf(3.0 to 8500.0, 5.0 to 7000.0, 10.0 to 3500.0, 16.0 to 2000.0),
            switched.chartPoints,
        )
        // The label follows the selection -- a label still reading "31 m" over a
        // 27.5 m chart would be a specific wrong answer rather than an omission.
        assertEquals(
            "Chart.xlsx (capacity in ton) \u2014 read from boom 27.5 m",
            switched.chartLabel,
        )
    }

    @Test
    fun typingInTheEntryDropsTheWorkbookEntirely() {
        val chart = matrixChart()
        val imported = ExcelImport.importedInputs(CraneInputs(), chart, "Chart.xlsx", 2000.0)
        val typed = ExcelImport.forgetChart(imported)

        assertEquals(emptyList<Pair<Double, Double>>(), typed.chartPoints)
        assertEquals("", typed.chartSource)
        assertEquals(emptyList<Double>(), typed.chartBooms)
        assertNull(typed.chartBoom)
        assertEquals(emptyList<Double>(), typed.chartRadii)
        assertEquals(1, typed.rows.size)
    }

    @Test
    fun changingTheWorkingRadiusWritesThroughToTheEntry() {
        val chart = matrixChart()
        // An angle the operator typed, so the assertion that it survives is real.
        val withAngle = CraneInputs(rows = listOf(ChartRow(angle = 48.0)))
        val imported = ExcelImport.importedInputs(withAngle, chart, "Chart.xlsx", 2000.0)

        // The import settled on 5.00 m; the operator picks 3.00 m instead.
        val changed = ExcelImport.radiusChanged(imported, chart, 3.0)

        assertEquals(3.0, changed.workingRadius, 1e-9)
        assertEquals(3.0, changed.rows[0].radius, 1e-9)
        assertEquals(7000.0, changed.rows[0].capacity, 1e-9)
        assertEquals(31.0, changed.rows[0].boom, 1e-9)
        // A load chart does not chart the boom's angle, so a chart never supplies
        // one and the operator's own is untouched by any of this.
        assertEquals(48.0, changed.rows[0].angle, 1e-9)
    }

    @Test
    fun aRadiusChangeReLooksUpTheCapacityRatherThanKeepingTheOldOne() {
        val chart = matrixChart()
        val imported = ExcelImport.importedInputs(CraneInputs(), chart, "Chart.xlsx", 2000.0)

        assertEquals(7000.0, ExcelImport.radiusChanged(imported, chart, 3.0).rows[0].capacity, 1e-9)
        assertEquals(1500.0, ExcelImport.radiusChanged(imported, chart, 16.0).rows[0].capacity, 1e-9)
        // 7.00 m is not charted; the lookup interpolates between the 5 m and 10 m
        // points rather than refusing.
        assertEquals(4300.0, ExcelImport.radiusChanged(imported, chart, 7.0).rows[0].capacity, 1e-9)

        // The same radius at a different boom is a different capacity.
        val atShortBoom = ExcelImport.boomChanged(imported, chart, 27.5, 2000.0)
        assertEquals(8500.0, ExcelImport.radiusChanged(atShortBoom, chart, 3.0).rows[0].capacity, 1e-9)
    }

    @Test
    fun aRadiusWithNoChartableCapacityLeavesTheEntryAlone() {
        val chart = matrixChart()
        val imported = ExcelImport.importedInputs(CraneInputs(), chart, "Chart.xlsx", 2000.0)
        val unchangedEntry = ExcelImport.radiusChanged(imported, chart, 0.0)

        // Writing a 0 kg capacity would be the chart *refusing* a lookup turned
        // into a charted zero, which is a claim nobody made.
        assertEquals(imported.rows[0].radius, unchangedEntry.rows[0].radius, 1e-9)
        assertEquals(imported.rows[0].capacity, unchangedEntry.rows[0].capacity, 1e-9)
    }

    // -----------------------------------------------------------------------
    // The workbook, as the tab lists it
    // -----------------------------------------------------------------------

    @Test
    fun aMatrixWorkbookListsTheColumnTheAppIsReading() {
        // The reference's desktop lists the whole imported chart.  On a phone a
        // matrix workbook is listed **one column at a time**, because the operator's
        // own has 133 cells and burying the seven that matter is not the same as
        // showing the chart.
        val (headers, rows) = chartTable(matrixChart(), 31.0)
        assertEquals(listOf("Radius", "Capacity"), headers)
        assertEquals(4, rows.size)
        assertEquals(listOf("3.00 m", "7,000.00 kg"), rows[0])
        assertEquals(listOf("5.00 m", "5,500.00 kg"), rows[1])
        assertEquals(listOf("10.00 m", "2,500.00 kg"), rows[2])
        assertEquals(listOf("16.00 m", "1,500.00 kg"), rows[3])
    }

    @Test
    fun theListedColumnFollowsTheBoomSelector() {
        // The table and the figures above it must not be able to disagree about
        // which column is in charge, so the table asks the same `boomIndex` the
        // solve does rather than working out its own.
        val chart = matrixChart()
        val atShortBoom = chartTable(chart, 27.5).second
        assertEquals(listOf("3.00 m", "8,500.00 kg"), atShortBoom[0])
        assertEquals(listOf("5.00 m", "7,000.00 kg"), atShortBoom[1])
        assertEquals(listOf("10.00 m", "3,500.00 kg"), atShortBoom[2])
        assertEquals(listOf("16.00 m", "2,000.00 kg"), atShortBoom[3])

        // **The invariant that matters**: whatever boom is asked for, the table shows
        // the same column the *lookup* reads.  A table and a lookup that could
        // disagree about which column is in charge is the exact fault this shares
        // `boomIndex` with the solve to avoid.
        //
        // A null boom is worth including because the two defaults are **not** the
        // same thing: `LoadChartData.boomIndex(null)` is the *loader's* default --
        // column 0 -- while the app's default is the *last* column.  The import
        // always writes a real boom, so a matrix chart never reaches the table with
        // a null; if it ever did, the table would still follow the lookup rather
        // than the app's preference.
        for (asked in listOf(9.8, 20.4, 31.0, null)) {
            val listed = chartTable(chart, asked).second
            for ((index, radius) in listOf(3.0, 5.0, 10.0, 16.0).withIndex()) {
                assertEquals(
                    "boom $asked at $radius",
                    Figures.fmt(chart.capacityAt(radius, asked), 2, "kg"),
                    listed[index][1],
                )
            }
        }
    }

    @Test
    fun aColumnWorkbookListsItsOwnThreeColumnsIncludingTheBoom() {
        // A column-format workbook carries a boom per row, so the table keeps the
        // desktop's own three columns -- the boom is a column rather than something
        // the selector has to pick.
        val (headers, rows) = chartTable(columnChart(), null)
        assertEquals(listOf("Radius", "Load", "Boom"), headers)
        assertEquals(3, rows.size)
        assertEquals(listOf("5.00 m", "10,000.00 kg", "20.00 m"), rows[0])
        assertEquals(listOf("10.00 m", "5,000.00 kg", "20.00 m"), rows[1])
        assertEquals(listOf("15.00 m", "2,500.00 kg", "20.00 m"), rows[2])
    }
}

// ---------------------------------------------------------------------------
// A minimal .xlsx writer, so the fixtures are self-contained
// ---------------------------------------------------------------------------

/**
 * Build one sheet as a real workbook: a zip of the parts a reader needs.
 *
 * Strings go through a shared-string table, which is how every real producer writes
 * them — including `openpyxl` — so the reader's shared-string path is the one
 * exercised here rather than a shortcut that skips it.
 */
private fun buildWorkbook(title: String, rows: List<List<Any?>>): ByteArray {
    val shared = mutableListOf<String>()
    val index = mutableMapOf<String, Int>()
    fun intern(text: String): Int = index.getOrPut(text) { shared.add(text); shared.size - 1 }

    val sheet = StringBuilder()
    sheet.append("""<?xml version="1.0" encoding="UTF-8"?><worksheet><sheetData>""")
    rows.forEachIndexed { rowIndex, row ->
        sheet.append("""<row r="${rowIndex + 1}">""")
        row.forEachIndexed { column, value ->
            val ref = "${columnName(column)}${rowIndex + 1}"
            when (value) {
                null -> sheet.append("""<c r="$ref"/>""")
                is String -> sheet.append("""<c r="$ref" t="s"><v>${intern(value)}</v></c>""")
                is Double -> sheet.append("""<c r="$ref"><v>$value</v></c>""")
            }
        }
        sheet.append("</row>")
    }
    sheet.append("</sheetData></worksheet>")

    val sharedXml = StringBuilder("""<?xml version="1.0" encoding="UTF-8"?><sst>""")
    shared.forEach { text -> sharedXml.append("<si><t>").append(escape(text)).append("</t></si>") }
    sharedXml.append("</sst>")

    val parts = linkedMapOf(
        "[Content_Types].xml" to """<?xml version="1.0" encoding="UTF-8"?><Types/>""",
        "_rels/.rels" to """<?xml version="1.0" encoding="UTF-8"?><Relationships/>""",
        "xl/workbook.xml" to
            """<?xml version="1.0" encoding="UTF-8"?><workbook><sheets>""" +
            """<sheet name="${escape(title)}" sheetId="1" r:id="rId1"/>""" +
            """</sheets></workbook>""",
        "xl/_rels/workbook.xml.rels" to
            """<?xml version="1.0" encoding="UTF-8"?><Relationships>""" +
            """<Relationship Id="rId1" Target="worksheets/sheet1.xml"/>""" +
            """</Relationships>""",
        "xl/worksheets/sheet1.xml" to sheet.toString(),
        "xl/sharedStrings.xml" to sharedXml.toString(),
    )

    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zip ->
        parts.forEach { (name, xml) ->
            zip.putNextEntry(ZipEntry(name))
            zip.write(xml.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }
    return out.toByteArray()
}

private fun columnName(index: Int): String {
    var value = index + 1
    val name = StringBuilder()
    while (value > 0) {
        val remainder = (value - 1) % 26
        name.insert(0, ('A' + remainder))
        value = (value - 1) / 26
    }
    return name.toString()
}

private fun escape(text: String): String = text
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
