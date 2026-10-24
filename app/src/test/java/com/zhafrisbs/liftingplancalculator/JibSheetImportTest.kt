package com.zhafrisbs.liftingplancalculator

import com.zhafrisbs.liftingplancalculator.core.ExcelLoader
import com.zhafrisbs.liftingplancalculator.core.XlsxSheet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the jib sheet and the crane position to the desktop reader's own fixtures.
 *
 * Every row below is transcribed cell for cell from `src-tauri/src/excel.rs`'s
 * test module — `the_new_format_position_is_read_from_its_label`,
 * `the_jib_sheet_parses_to_length_by_offset_tables` and
 * `a_workbook_carries_its_position_and_jib_tables_into_the_chart`.  They are
 * copied rather than reinvented, so the ported reader and the desktop reader are
 * asked the same questions about the same cells, down to the degree signs and the
 * `-1.7,2.8` text that decides a position.
 *
 * Sheets are handed to [ExcelLoader.loadChart] as [XlsxSheet]s rather than through
 * a built workbook: this task adds a second *pass* over already-read sheets, and
 * the reader that produces them (`Xlsx`) is unchanged and pinned by
 * `ExcelImportTest`.
 */
class JibSheetImportTest {

    /** The matrix chart the desktop's `new_format_rows` is built on. */
    private val matrixRows: List<List<Any?>> = listOf(
        listOf("Radius", 9.8, 13.3, 16.9, 20.4, 23.9, 27.5, 31.0),
        listOf(3.0, 20.0, 17.5, 14.5, 12.0, 10.0, 8.5, 7.0),
        listOf(5.0, 18.0, 15.0, 12.5, 10.5, 8.5, 7.0, 5.5),
        listOf(10.0, 12.0, 10.0, 8.0, 6.5, 5.0, 3.5, 2.5),
        listOf(16.0, 8.0, 6.5, 5.0, 4.0, 3.0, 2.0, 1.5),
    )

    /** `new_format_rows`: a main chart, then a position, then a jib table on one sheet. */
    private val newFormatRows: List<List<Any?>> = matrixRows + listOf(
        listOf("Crane location on axis (X, Y)"),
        listOf("-1.7,2.8"),
        listOf("Boom angle (°)", "Jib 7.2 m", "", "", "Jib 12.8 m"),
        listOf("", "5°", "25°", "45°", "5°", "25°", "45°"),
        listOf(83.0, 3.0, 1.8, 1.3, 2.0, 1.05, 0.7),
        listOf(45.0, 0.6, 0.55, 0.55, 0.5, 0.45, 0.4),
    )

    /** The desktop's own jib-sheet fixture, title rows and all. */
    private val jibSheetRows: List<List<Any?>> = listOf(
        listOf("TOTAL RATED LOADS — With outriggers fully extended (360°), Unit: ton"),
        listOf(
            "TR-350M-1  |  JIB load chart  |  rows = boom angle (°), " +
                "columns = jib length × jib offset angle",
        ),
        listOf("Boom angle (°)", "Jib 7.2 m", null, null, "Jib 12.8 m"),
        listOf(null, "5°", "25°", "45°", "5°", "25°", "45°"),
        listOf(83.0, 3.0, 1.8, 1.3, 2.0, 1.05, 0.7),
        listOf(45.0, 0.6, 0.55, 0.55, 0.5, 0.45, 0.4),
    )

    // -----------------------------------------------------------------------
    // The crane position
    // -----------------------------------------------------------------------

    @Test
    fun theNewFormatPositionIsReadFromItsLabel() {
        // Directly below the label.
        val below: List<List<Any?>> = listOf(
            listOf("TOTAL RATED LOADS"),
            listOf("Crane location on axis (X, Y)", null),
            listOf("-1.7,2.8"),
        )
        assertEquals(-1.7 to 2.8, ExcelLoader.findPosition(below))

        // To the label's right, as one `x, y` text.
        val inline: List<List<Any?>> = listOf(
            listOf("Crane location on axis (X, Y)", "-2.5, 1.0"),
        )
        assertEquals(-2.5 to 1.0, ExcelLoader.findPosition(inline))

        // Below and to the right of the label.
        val belowRight: List<List<Any?>> = listOf(
            listOf("Crane location on axis (X, Y)", null),
            listOf(null, "-1.7,2.8"),
        )
        assertEquals(-1.7 to 2.8, ExcelLoader.findPosition(belowRight))

        // As two numeric cells directly below.
        val twoCells: List<List<Any?>> = listOf(
            listOf("Crane location on axis (X, Y)"),
            listOf(-1.7, 2.8),
        )
        assertEquals(-1.7 to 2.8, ExcelLoader.findPosition(twoCells))

        // Text with no label is not a position.
        assertNull(ExcelLoader.findPosition(listOf(listOf("nothing here"))))
    }

    // -----------------------------------------------------------------------
    // The jib sheet
    // -----------------------------------------------------------------------

    @Test
    fun theJibSheetParsesToLengthByOffsetTables() {
        val columns = ExcelLoader.parseJibSheet(jibSheetRows)!!

        assertEquals(6, columns.size)
        assertEquals(7.2, columns[0].length, 1e-9)
        assertEquals(5.0, columns[0].offset, 1e-9)
        assertEquals(listOf(45.0 to 600.0, 83.0 to 3000.0), columns[0].points)
        assertEquals(12.8, columns[5].length, 1e-9)
        assertEquals(45.0, columns[5].offset, 1e-9)
        assertEquals(listOf(45.0 to 400.0, 83.0 to 700.0), columns[5].points)

        // A sheet that is not a jib chart stays out of the way.
        val notAJibChart: List<List<Any?>> = listOf(listOf("Radius", 9.8, 13.3))
        assertNull(ExcelLoader.parseJibSheet(notAJibChart))
    }

    @Test
    fun aSheetWhoseTitleMentionsBoomAngleIsNotMistakenForTheHeader() {
        // **Review Focus 1.**  The title's first cell contains "boom angle", but
        // it is not *the* heading: only the row whose first cell normalises to
        // *start with* "boomangle" is.  A reader that tested `contains` would take
        // this title for the header, find no jib group on it and give up.
        val rows: List<List<Any?>> = listOf(
            listOf("Load chart: boom angle (°) down the rows"),
            listOf("Boom angle (°)", "Jib 7.2 m", null, null, "Jib 12.8 m"),
            listOf(null, "5°", "25°", "45°", "5°", "25°", "45°"),
            listOf(83.0, 3.0, 1.8, 1.3, 2.0, 1.05, 0.7),
            listOf(45.0, 0.6, 0.55, 0.55, 0.5, 0.45, 0.4),
        )

        val columns = ExcelLoader.parseJibSheet(rows)!!
        assertEquals(6, columns.size)
        assertEquals(7.2, columns[0].length, 1e-9)
        assertEquals(5.0, columns[0].offset, 1e-9)
        assertEquals(listOf(45.0 to 600.0, 83.0 to 3000.0), columns[0].points)
    }

    // -----------------------------------------------------------------------
    // What a workbook carries into the chart
    // -----------------------------------------------------------------------

    @Test
    fun aWorkbookCarriesItsPositionAndJibTablesIntoTheChart() {
        val chart = ExcelLoader.loadChart(listOf(XlsxSheet("Chart", newFormatRows)))

        assertEquals(-1.7 to 2.8, chart.position)
        assertEquals(6, chart.jibColumns.size)
        val column = chart.jibColumns.first { it.length == 7.2 && it.offset == 25.0 }
        assertEquals(listOf(45.0 to 550.0, 83.0 to 1800.0), column.points)
    }

    @Test
    fun aPositionWithoutJibTablesStillYieldsThePosition() {
        // **Review Focus 2.**  The two extras are found independently: a workbook
        // with a position and no jib sheet still carries the position.
        val rows: List<List<Any?>> = matrixRows + listOf(
            listOf("Crane location on axis (X, Y)"),
            listOf("-1.7,2.8"),
        )
        val chart = ExcelLoader.loadChart(listOf(XlsxSheet("Main Boom", rows)))

        assertEquals(-1.7 to 2.8, chart.position)
        assertTrue(chart.jibColumns.isEmpty())
    }

    @Test
    fun aJibSheetWithoutAPositionStillYieldsTheJibTables() {
        // **Review Focus 2**, the converse — and the whole point of the change:
        // the jib table lives on the *second* sheet, which the first-wins main
        // chart search never reaches.
        val chart = ExcelLoader.loadChart(
            listOf(
                XlsxSheet("Main Boom", matrixRows),
                XlsxSheet("Jib & Extend Jib", jibSheetRows),
            ),
        )

        assertNull(chart.position)
        assertEquals(6, chart.jibColumns.size)
        assertEquals(7.2, chart.jibColumns[0].length, 1e-9)
        assertEquals(5.0, chart.jibColumns[0].offset, 1e-9)
    }
}
