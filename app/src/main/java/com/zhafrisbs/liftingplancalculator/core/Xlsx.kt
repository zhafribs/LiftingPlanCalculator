package com.zhafrisbs.liftingplancalculator.core

import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import javax.xml.parsers.SAXParserFactory

/**
 * A minimal reader for `.xlsx` workbooks, with **no third-party dependency**.
 *
 * The reference project reads charts with `openpyxl`.  There is no equivalent on
 * Android that is worth its weight here: Apache POI is several megabytes and
 * drags in `java.awt` and XML APIs that need desugaring, which would multiply an
 * app that currently ships at 1.3 MB for the sake of reading one small table.
 *
 * An `.xlsx` is a ZIP of XML parts, and a load chart only needs three of them, so
 * this reads exactly those and ignores the rest: the shared string table, the
 * workbook's sheet list, and each sheet's cells.  No styling, no formulas (the
 * cached value is read), no merged cells, no charts.
 *
 * **The representation is deliberately Python-shaped.**  A cell is a [String] or a
 * [Double] or null, mirroring the `str` / numeric / `None` that `openpyxl` hands
 * `excel_loader.py`.  That is what lets the loader port below be a transcription
 * rather than a re-interpretation — and it is why the loader, not this reader,
 * does the stripping that the reference does while it builds its rows.
 *
 * Parsing is SAX rather than DOM: a sheet can hold thousands of rows and DOM would
 * hold the whole document at once for no benefit.  `javax.xml.parsers` is used
 * rather than `android.util.Xml` so this is exercisable by an ordinary JVM unit
 * test, where the Android framework classes are stubs that throw.
 */

/** One worksheet: its title, and its cells as rows of [String] / [Double] / null. */
class XlsxSheet(val title: String, val rows: List<List<Any?>>)

object Xlsx {

    /** The most rows any one sheet is read for, matching the reference's `max_row`. */
    const val DEFAULT_MAX_ROWS = 2000

    /** Read every sheet in the workbook, in the workbook's own order. */
    fun read(bytes: ByteArray, maxRows: Int = DEFAULT_MAX_ROWS): List<XlsxSheet> {
        val entries = unzip(bytes)
        val shared = entries["xl/sharedStrings.xml"]?.let { parseSharedStrings(it) } ?: emptyList()

        val declared = entries["xl/workbook.xml"]?.let { parseWorkbook(it) } ?: emptyList()
        val rels = entries["xl/_rels/workbook.xml.rels"]
            ?.let { parseRelationships(it) } ?: emptyMap()

        val sheets = mutableListOf<XlsxSheet>()
        declared.forEachIndexed { index, (name, relId) ->
            val path = resolveSheetPath(rels[relId], index)
            val xml = entries[path] ?: return@forEachIndexed
            sheets.add(XlsxSheet(name, parseSheet(xml, shared, maxRows)))
        }

        // A workbook with no readable sheet list is still worth trying: some files
        // name their sheets only in the rels, and dropping the lot would report
        // "no load chart" for a workbook that has one.
        if (sheets.isEmpty()) {
            entries.keys
                .filter { it.startsWith("xl/worksheets/") && it.endsWith(".xml") }
                .sorted()
                .forEachIndexed { index, path ->
                    sheets.add(XlsxSheet("Sheet${index + 1}", parseSheet(entries.getValue(path), shared, maxRows)))
                }
        }
        return sheets
    }

    // -----------------------------------------------------------------------
    // Archive
    // -----------------------------------------------------------------------

    /**
     * The archive's entries.
     *
     * **Throws for something that is not an archive at all**, rather than returning
     * nothing.  `ZipInputStream` answers a bad stream by reporting no entries, so a
     * PNG handed to this reader would come back as a workbook with no sheets and be
     * reported as *"could not find a load chart in this workbook"* — an instruction
     * to go and fix the chart's layout when the real problem is the file's type.
     * Raising a [ZipException] here is what lets the caller say the one thing that
     * is true and useful: that this is not an Excel file.
     */
    private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        val out = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                val buffer = ByteArrayOutputStream()
                zip.copyTo(buffer)
                out[entry.name] = buffer.toByteArray()
            }
        }
        if (out.isEmpty()) throw ZipException("Not a zip archive")
        return out
    }

    /**
     * The worksheet part a relationship points at.
     *
     * A target is relative to `xl/` unless it is absolute, and the absolute form
     * is written with a leading slash by some producers.  Falling back to the
     * conventional `sheetN.xml` name keeps a workbook readable when its rels part
     * is missing or unhelpful.
     */
    private fun resolveSheetPath(target: String?, index: Int): String = when {
        target == null -> "xl/worksheets/sheet${index + 1}.xml"
        target.startsWith("/") -> target.removePrefix("/")
        else -> "xl/" + target.removePrefix("./")
    }

    // -----------------------------------------------------------------------
    // Parts
    // -----------------------------------------------------------------------

    private fun parseWith(handler: DefaultHandler, bytes: ByteArray) {
        val factory = SAXParserFactory.newInstance()
        // The file comes from the user's storage, so external entities are shut
        // off.  Wrapped because a parser is free to reject a feature it does not
        // implement, and a workbook that would otherwise read must not fail over
        // a hardening flag.
        runCatching {
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            factory.isNamespaceAware = false
        }
        val parser = factory.newSAXParser()
        parser.parse(InputSource(ByteArrayInputStream(bytes)), handler)
    }

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val handler = SharedStringsHandler()
        parseWith(handler, bytes)
        return handler.strings
    }

    private fun parseWorkbook(bytes: ByteArray): List<Pair<String, String>> {
        val handler = WorkbookHandler()
        parseWith(handler, bytes)
        return handler.sheets
    }

    private fun parseRelationships(bytes: ByteArray): Map<String, String> {
        val handler = RelationshipsHandler()
        parseWith(handler, bytes)
        return handler.rels
    }

    private fun parseSheet(bytes: ByteArray, shared: List<String>, maxRows: Int): List<List<Any?>> {
        val handler = SheetHandler(shared, maxRows)
        parseWith(handler, bytes)
        return handler.rows
    }
}

/**
 * `<si>` entries, whose text is split across `<r><t>` runs in rich text.
 *
 * Every `<t>` inside one `<si>` is concatenated, which is how a shared string
 * that was styled mid-word still reads as one word rather than as its fragments.
 */
private class SharedStringsHandler : DefaultHandler() {
    val strings = mutableListOf<String>()
    private val text = StringBuilder()
    private var inItem = false
    private var inText = false

    override fun startElement(uri: String?, localName: String?, qName: String, attrs: Attributes?) {
        when (qName) {
            "si" -> {
                inItem = true
                text.setLength(0)
            }
            "t" -> if (inItem) inText = true
        }
    }

    override fun characters(ch: CharArray, start: Int, length: Int) {
        if (inText) text.appendRange(ch, start, start + length)
    }

    override fun endElement(uri: String?, localName: String?, qName: String) {
        when (qName) {
            "t" -> inText = false
            "si" -> {
                inItem = false
                strings.add(text.toString())
            }
        }
    }
}

/** `<sheet name="..." r:id="rIdN"/>`, in the order the workbook lists them. */
private class WorkbookHandler : DefaultHandler() {
    val sheets = mutableListOf<Pair<String, String>>()

    override fun startElement(uri: String?, localName: String?, qName: String, attrs: Attributes?) {
        if (qName != "sheet" || attrs == null) return
        val name = attrs.getValue("name") ?: return
        val relId = attrs.getValue("r:id") ?: attrs.getValue("id") ?: return
        sheets.add(name to relId)
    }
}

/** `<Relationship Id="rIdN" Target="worksheets/sheetN.xml"/>`. */
private class RelationshipsHandler : DefaultHandler() {
    val rels = mutableMapOf<String, String>()

    override fun startElement(uri: String?, localName: String?, qName: String, attrs: Attributes?) {
        if (qName != "Relationship" || attrs == null) return
        val id = attrs.getValue("Id") ?: return
        val target = attrs.getValue("Target") ?: return
        rels[id] = target
    }
}

/**
 * A sheet's rows.
 *
 * **Cells are addressed by their own reference, not by order.**  `r="C4"` gives
 * the column, and an absent cell is a hole rather than a shift — a sheet whose
 * first row is `A1`, `C1` must read as three columns, not two, or every value
 * after the hole lands one column to the left.  Sparse rows are the norm in
 * hand-made charts.
 *
 * The cell types handled are the ones a load chart can contain: shared strings,
 * inline strings, formula-cached strings, numbers, and booleans.  Errors become
 * null, as `openpyxl` reports them as an error value that `_to_float` rejects.
 */
private class SheetHandler(
    private val shared: List<String>,
    private val maxRows: Int,
) : DefaultHandler() {

    val rows = mutableListOf<List<Any?>>()

    private var current: MutableList<Any?>? = null
    private var column = -1
    private var cellType: String? = null
    private val valueText = StringBuilder()
    private val inlineText = StringBuilder()
    private var inValue = false
    private var inInline = false
    private var inInlineText = false

    override fun startElement(uri: String?, localName: String?, qName: String, attrs: Attributes?) {
        when (qName) {
            "row" -> if (rows.size < maxRows) current = mutableListOf()
            "c" -> {
                cellType = attrs?.getValue("t")
                column = columnIndex(attrs?.getValue("r"))
                valueText.setLength(0)
                inlineText.setLength(0)
            }
            "v" -> inValue = true
            "is" -> inInline = true
            "t" -> if (inInline) inInlineText = true
        }
    }

    override fun characters(ch: CharArray, start: Int, length: Int) {
        if (inValue) valueText.appendRange(ch, start, start + length)
        if (inInlineText) inlineText.appendRange(ch, start, start + length)
    }

    override fun endElement(uri: String?, localName: String?, qName: String) {
        when (qName) {
            "v" -> inValue = false
            "t" -> inInlineText = false
            "is" -> inInline = false
            "c" -> {
                val row = current
                if (row != null && column >= 0) {
                    while (row.size <= column) row.add(null)
                    row[column] = cellValue()
                }
            }
            "row" -> {
                current?.let { rows.add(it) }
                current = null
            }
        }
    }

    private fun cellValue(): Any? = when (cellType) {
        "s" -> {
            val index = valueText.toString().trim().toIntOrNull()
            if (index != null && index in shared.indices) shared[index] else null
        }
        "inlineStr" -> inlineText.toString().ifEmpty { null }
        "str" -> valueText.toString().ifEmpty { null }
        "b" -> if (valueText.toString().trim() == "1") 1.0 else 0.0
        "e" -> null
        else -> valueText.toString().trim().toDoubleOrNull()
    }

    /** `"AB12"` -> 27.  The letters are the column, base-26 with A = 1. */
    private fun columnIndex(reference: String?): Int {
        if (reference.isNullOrEmpty()) return -1
        var value = 0
        for (char in reference) {
            val upper = char.uppercaseChar()
            if (upper !in 'A'..'Z') break
            value = value * 26 + (upper - 'A' + 1)
        }
        return value - 1
    }
}
