package com.zhafrisbs.liftingplancalculator.core

import java.util.Locale

/**
 * Number formatting shared by every screen and, later, by the printed report.
 *
 * A direct port of `lifting_calculator/figures.py`.  The reference keeps this in
 * one place for a stated reason: the phone's result card and the desktop's show
 * the same figures, and a second implementation of "three decimals, thousands
 * separated, an em dash for nothing" would differ somewhere nobody looks until a
 * figure reads differently on the two screens.
 *
 * [Locale.US] is deliberate rather than incidental.  Python's `format()` is
 * locale-independent, so pinning the locale here is what makes `1,234.50` come
 * out the same on a device set to a comma-decimal locale — and a lifting plan
 * whose decimal separator changed with the phone's settings would be unreadable
 * next to the paper chart it is checked against.
 */
object Figures {

    /** What a figure that has not been entered shows as. */
    const val NOTHING: String = "\u2014"

    /** Format a figure, or return an em dash when there is nothing to show. */
    fun fmt(value: Double?, decimals: Int = 3, unit: String = ""): String {
        if (value == null) return NOTHING
        val text = String.format(Locale.US, "%,.${decimals}f", value)
        return if (unit.isEmpty()) text else "$text $unit"
    }
}
