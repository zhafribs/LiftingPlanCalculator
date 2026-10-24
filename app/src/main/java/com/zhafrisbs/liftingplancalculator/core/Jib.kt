package com.zhafrisbs.liftingplancalculator.core

/**
 * One jib table parsed out of a workbook's jib sheet: a jib length at an offset
 * angle, charted against boom angle.
 *
 * [points] is `(boom angle in degrees, capacity in kg)`, ascending by angle —
 * the order `ExcelLoader.parseJibSheet` sorts into, because the two readers must
 * agree on which point is "first" even though a lift reads them by lookup rather
 * than by index.
 */
data class JibColumn(
    val length: Double,
    val offset: Double,
    val points: List<Pair<Double, Double>>,
)

/**
 * A jib table a workbook offers, named by the pair that picks it.
 *
 * This is what a selector lists: `jib_options` folds the [JibColumn]s down to
 * the distinct `(length, offset)` pairs, in workbook order.
 */
data class JibOption(
    val length: Double,
    val offset: Double,
)
