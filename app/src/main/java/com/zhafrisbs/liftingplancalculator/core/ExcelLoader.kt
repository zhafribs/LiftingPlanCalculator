package com.zhafrisbs.liftingplancalculator.core

import kotlin.math.abs

/**
 * Load-chart parsing, ported from `lifting_calculator/excel_loader.py`.
 *
 * Two common crane load-chart layouts are supported, and the layout is detected
 * rather than chosen:
 *
 *  1. **Column format** — a header row plus radius / load / boom-length columns,
 *     with the headers recognised by their common names.
 *  2. **Matrix format** — boom lengths across the top row, working radius in the
 *     first column, and rated capacity in the cells:
 *     ```
 *     Radius | 9.8   | 13.3  | 16.9  | ...
 *     3      | 20    | 17.5  | 14.5  | ...
 *     ```
 *     Capacities in this layout are assumed to be metric tons when the values are
 *     small (<= 2000) and kg otherwise, and are converted to kg internally.
 *
 * Every rule here is the reference's, transcribed rather than re-derived,
 * including the parts that look odd.  The `_COLUMN_RULES` table is order-sensitive
 * and has an entry that can never match (`"safe working load"` contains spaces,
 * and the value it is compared against has had every non-alphanumeric character
 * removed) — kept verbatim, because "fixing" it would make the two readers
 * disagree about a heading one of them recognises and the other does not.
 */

/** One parsed row.  A column-format row carries [load]; a matrix row carries [capacities]. */
data class ChartDataRow(
    val radius: Double?,
    val load: Double? = null,
    val boom: Double? = null,
    val capacities: List<Double?> = emptyList(),
)

/**
 * A parsed load chart table.
 *
 * [origin] is a human-readable note of where in the workbook the chart was found,
 * kept for the log rather than the screen: the label shows the file and the unit,
 * and adding a sheet name to it would make the two readers' labels differ.
 */
class LoadChartData(
    val rows: List<ChartDataRow>,
    val headerRow: Int,
    val columnMap: Map<String, Int>,
    val origin: String,
    val matrix: Boolean = false,
    val booms: List<Double> = emptyList(),
    val unit: String = "kg",
) {
    /**
     * Rated capacity (kg) at a working radius, from the load column — or, for a
     * matrix chart, the selected boom-length column.
     */
    fun capacityAt(radius: Double, boomLength: Double? = null): Double {
        val pairs = if (matrix) {
            val index = boomIndex(boomLength)
            rows.mapNotNull { row ->
                val r = row.radius ?: return@mapNotNull null
                val c = row.capacities.getOrNull(index) ?: return@mapNotNull null
                r to c
            }
        } else {
            rows.mapNotNull { row ->
                val r = row.radius ?: return@mapNotNull null
                val l = row.load ?: return@mapNotNull null
                r to l
            }
        }
        return Calc.lookupChartCapacity(pairs, radius)
    }

    /**
     * Which boom column to read, by nearest match.
     *
     * A nearest match rather than an equality test because a column header is a
     * floated number read out of a cell, and `27.5` written in a spreadsheet is not
     * reliably `27.5` when it arrives here.
     */
    fun boomIndex(boomLength: Double?): Int {
        if (booms.isEmpty()) return 0
        if (boomLength == null) return 0
        var best = 0
        var bestDiff = Double.MAX_VALUE
        booms.forEachIndexed { index, boom ->
            val diff = abs(boom - boomLength)
            if (diff < bestDiff) {
                best = index
                bestDiff = diff
            }
        }
        return best
    }
}

object ExcelLoader {

    /** Canonical name -> the column that holds it.  Ordered; the first match wins. */
    private val COLUMN_RULES: List<Pair<String, String>> = listOf(
        "radius" to "radius",
        "workingradius" to "radius",
        "r" to "radius",
        "load" to "load",
        "ratedload" to "load",
        "capacit" to "load",
        "safe working load" to "load",
        "swl" to "load",
        "boom" to "boom",
        "boomlength" to "boom",
        "boompositio" to "boom",
        "mainsection" to "boom",
        "length" to "boom",
    )

    /**
     * Parse a chart out of the workbook's sheets, in order.
     *
     * The first sheet that yields a chart wins, which is the reference's behaviour
     * via `wb.worksheets`.  A sheet with nothing on it is skipped rather than
     * ending the search.
     */
    @Throws(CalcException::class)
    fun loadChart(sheets: List<XlsxSheet>, columnMap: Map<String, Int>? = null): LoadChartData {
        for (sheet in sheets) {
            // The reference strips each string cell and turns an empty one into
            // None while it reads, then drops rows that are entirely empty.  Both
            // happen here, on the same values, because the detection below depends
            // on them.
            val rows = sheet.rows
                .map { row -> row.map { cell -> if (cell is String) cell.trim().ifEmpty { null } else cell } }
                .filter { row -> row.any { it != null } }
            if (rows.isEmpty()) continue

            if (columnMap != null) {
                parseColumnRows(rows, 0, columnMap, sheet.title)?.let { return it }
                continue
            }

            val (headerIndex, mapping) = detectHeaderRow(rows)
            if (mapping["radius"] != null && mapping["load"] != null) {
                parseColumnRows(rows, headerIndex ?: 0, mapping, sheet.title)?.let { return it }
            }

            val (matrixRow, booms) = detectMatrix(rows)
            if (matrixRow != null) {
                parseMatrixRows(rows, matrixRow, booms, sheet.title)?.let { return it }
            }
        }
        throw CalcException(
            "Could not find a load chart in this workbook. Make sure it has " +
                "either a header row with radius and load columns, or a boom " +
                "length row across the top with radii in the first column."
        )
    }

    // -----------------------------------------------------------------------
    // Detection
    // -----------------------------------------------------------------------

    /** The first row that looks like a header, and the columns it names. */
    fun detectHeaderRow(rows: List<List<Any?>>, maxRows: Int = 40): Pair<Int?, Map<String, Int>> {
        for (index in 0 until minOf(rows.size, maxRows)) {
            val mapping = LinkedHashMap<String, Int>()
            rows[index].forEachIndexed { column, cell ->
                if (cell == null || text(cell).trim().isEmpty()) return@forEachIndexed
                val canonical = matchHeader(cell) ?: return@forEachIndexed
                if (!mapping.containsKey(canonical)) mapping[canonical] = column
            }
            if (mapping.size >= 2 && (mapping.containsKey("radius") || mapping.containsKey("load"))) {
                return index to mapping
            }
        }
        return null to emptyMap()
    }

    /**
     * Detect a boom-lengths-across-the-top matrix chart.
     *
     * The header row has a radius-like label in column 0 followed by two or more
     * positive numeric boom lengths, and the next row starts with a numeric radius.
     */
    fun detectMatrix(rows: List<List<Any?>>, maxRows: Int = 12): Pair<Int?, List<Double>> {
        for (index in 0 until minOf(rows.size, maxRows)) {
            val row = rows[index]
            if (row.isEmpty()) continue
            val first = row[0] ?: continue
            val norm = normalise(first)
            if (!(norm.isNotEmpty() && (norm.contains("radius") || norm == "r"))) continue

            val booms = mutableListOf<Double>()
            for (cell in row.drop(1)) {
                val value = toDoubleOrNull(cell)
                if (value == null || value <= 0) break
                booms.add(value)
            }
            if (booms.size < 2) continue

            val next = rows.getOrNull(index + 1) ?: emptyList()
            if (next.isNotEmpty() && toDoubleOrNull(next[0]) != null) return index to booms
        }
        return null to emptyList()
    }

    /** Capacity cells <= 2000 are almost certainly metric tons. */
    fun detectUnit(capacities: List<Double>): Pair<String, Double> {
        val largest = capacities.maxOrNull() ?: 0.0
        return if (largest <= 2000) "ton" to 1000.0 else "kg" to 1.0
    }

    // -----------------------------------------------------------------------
    // Parsing
    // -----------------------------------------------------------------------

    private fun parseColumnRows(
        rows: List<List<Any?>>,
        headerRow: Int,
        mapping: Map<String, Int>,
        title: String,
    ): LoadChartData? {
        val radiusColumn = mapping["radius"] ?: return null
        val loadColumn = mapping["load"] ?: return null
        val boomColumn = mapping["boom"]

        var loadUnit = 1.0
        val norm = normalise(headerText(rows, headerRow, loadColumn))
        if (norm.contains("ton") || norm == "t" || norm == "mt") loadUnit = 1000.0

        val dataRows = mutableListOf<ChartDataRow>()
        for (row in rows.drop(headerRow + 1)) {
            val radius = toDoubleOrNull(row.getOrNull(radiusColumn)) ?: continue
            val load = toDoubleOrNull(row.getOrNull(loadColumn)) ?: continue
            val boom = if (boomColumn != null && boomColumn < row.size) {
                toDoubleOrNull(row[boomColumn])
            } else {
                null
            }
            dataRows.add(ChartDataRow(radius = radius, load = load * loadUnit, boom = boom))
        }
        if (dataRows.isEmpty()) return null
        return LoadChartData(
            rows = dataRows,
            headerRow = headerRow,
            columnMap = mapping,
            origin = "$title.xlsx / $title sheet / header row ${headerRow + 1}",
        )
    }

    private fun parseMatrixRows(
        rows: List<List<Any?>>,
        headerRow: Int,
        booms: List<Double>,
        title: String,
    ): LoadChartData? {
        val dataRows = mutableListOf<ChartDataRow>()
        val cellValues = mutableListOf<Double>()

        for (row in rows.drop(headerRow + 1)) {
            val radius = toDoubleOrNull(row.getOrNull(0)) ?: continue
            val capacities = mutableListOf<Double?>()
            booms.forEachIndexed { index, _ ->
                val column = index + 1
                val value = if (column < row.size) toDoubleOrNull(row[column]) else null
                capacities.add(value)
                if (value != null) cellValues.add(value)
            }
            dataRows.add(ChartDataRow(radius = radius, capacities = capacities))
        }
        if (dataRows.isEmpty()) return null

        val (unit, multiplier) = detectUnit(cellValues)
        val scaled = dataRows.map { row ->
            row.copy(capacities = row.capacities.map { it?.times(multiplier) })
        }
        return LoadChartData(
            rows = scaled,
            headerRow = headerRow,
            columnMap = emptyMap(),
            origin = "$title.xlsx / $title sheet / boom length row ${headerRow + 1} / unit $unit",
            matrix = true,
            booms = booms,
            unit = unit,
        )
    }

    private fun headerText(rows: List<List<Any?>>, headerRow: Int, column: Int): Any? {
        if (headerRow in rows.indices && column < rows[headerRow].size) {
            return rows[headerRow][column]
        }
        return null
    }

    // -----------------------------------------------------------------------
    // Normalisation — the reference's own, including its quirks
    // -----------------------------------------------------------------------

    /** Lowercase, then every non-alphanumeric character removed. */
    fun normalise(value: Any?): String =
        text(value).trim().lowercase().replace(Regex("[^a-z0-9]+"), "")

    /** Whether a heading names one of the canonical columns, and which. */
    fun matchHeader(value: Any?): String? {
        val norm = normalise(value)
        if (norm.isEmpty()) return null
        for ((matcher, canonical) in COLUMN_RULES) {
            if (norm.contains(matcher) || matcher.contains(norm)) return canonical
        }
        return null
    }

    /**
     * A cell as a number, or null.
     *
     * A comma-grouped figure is un-grouped first, because a chart pasted out of a
     * formatted report carries `1,250` rather than `1250`.
     */
    fun toDoubleOrNull(value: Any?): Double? = when (value) {
        null -> null
        is Double -> value
        is String -> {
            val text = value.trim().replace(",", "")
            if (text.isEmpty()) null else text.toDoubleOrNull()
        }
        else -> null
    }

    /** A cell as text, the way Python's `str()` would render it. */
    private fun text(value: Any?): String = when (value) {
        null -> ""
        is String -> value
        is Double ->
            // `str(3.0)` is "3.0" in Python only because openpyxl hands back an
            // int for an integer cell.  A whole number is rendered without a
            // decimal point so a heading like "9" normalises the same way.
            if (value.isFinite() && value == Math.floor(value)) value.toLong().toString() else value.toString()
        else -> value.toString()
    }
}
