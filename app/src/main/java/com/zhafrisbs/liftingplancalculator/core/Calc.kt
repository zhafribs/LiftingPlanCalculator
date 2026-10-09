package com.zhafrisbs.liftingplancalculator.core

/**
 * Calculation engine for the Lifting Plan Calculator.
 *
 * This is a direct port of `lifting_calculator/calc.py` from the reference
 * project.  All masses are handled in kilogram, and — following common rigging
 * convention — a weighed mass is treated as weight-force (kgf) for tension
 * purposes.  The crane is allowed to operate at a maximum of 75% of its rated
 * chart capacity.
 *
 * Two rules govern this file:
 *
 *  1. **The arithmetic is the reference's, not a re-derivation.**  Every
 *     constant and every early return is carried across unchanged, including
 *     the parts that look redundant, so a figure here and a figure on the
 *     desktop are the same figure.  Where the reference has a quirk, the port
 *     keeps it and says so rather than quietly improving it.
 *  2. **Errors are values the UI can show, not crashes.**  Anything a half-filled
 *     form can produce raises [CalcException] with the reference's own wording,
 *     because that sentence is the one an operator already recognises.
 */

/** Raised for invalid inputs so the UI can show a friendly message. */
class CalcException(message: String) : IllegalArgumentException(message)

/** The three ways a load line can be measured, in the reference's declared order. */
enum class LoadType(val key: String, val label: String) {
    FIXED("fixed", "Fixed"),
    PER_METRE("per_metre", "Weight by Metre"),
    PER_VOLUME("per_volume", "Weight by Volume");

    companion object {
        /**
         * Declared order, not alphabetical: the dropdown's order and the
         * fallback for an unknown key both come from here so there is one list.
         */
        val ORDER: List<LoadType> = listOf(FIXED, PER_METRE, PER_VOLUME)

        /** Never throws: an unrecognised key lands on [FIXED], as the reference's
         * `load_type_label` falls back to the raw key it was handed. */
        fun fromKey(key: String): LoadType = ORDER.firstOrNull { it.key == key } ?: FIXED
    }
}

object Calc {

    // -----------------------------------------------------------------------
    // Load item computations (Overall Weight tab)
    // -----------------------------------------------------------------------

    /** Total weight for a fixed-weight item: weight x quantity. */
    fun fixedWeight(weightKg: Double, qty: Int = 1): Double {
        if (weightKg < 0) throw CalcException("Fixed weight cannot be negative.")
        return weightKg * clampQty(qty)
    }

    /** Total weight for a weight-by-metre item: (kg/m * length) * quantity. */
    fun weightByMetre(kgPerMetre: Double, lengthM: Double, qty: Int = 1): Double {
        if (kgPerMetre < 0 || lengthM < 0) {
            throw CalcException("Weight per metre and length cannot be negative.")
        }
        return kgPerMetre * lengthM * clampQty(qty)
    }

    /**
     * Total weight for an item defined by density and volume:
     * volume (m^3) * density (kg/m^3) * quantity.
     */
    fun weightByVolume(volumeM3: Double, densityKgM3: Double, qty: Int = 1): Double {
        if (volumeM3 < 0) throw CalcException("Volume cannot be negative.")
        if (densityKgM3 <= 0) throw CalcException("Material density must be greater than 0.")
        return volumeM3 * densityKgM3 * clampQty(qty)
    }

    /** Dispatch to the measurement's own weight function. */
    fun loadItemWeight(type: LoadType, item: LoadMeasurements): Double = when (type) {
        LoadType.FIXED -> fixedWeight(item.weightKg, item.qty)
        LoadType.PER_METRE -> weightByMetre(item.kgPerMetre, item.lengthM, item.qty)
        LoadType.PER_VOLUME -> weightByVolume(item.volumeM3, item.densityKgM3, item.qty)
    }

    private fun clampQty(qty: Int): Int {
        // The reference coerces with int() first and then range-checks; the Int
        // parameter already guarantees a whole number, so only the floor remains.
        if (qty < 0) throw CalcException("Quantity cannot be negative.")
        return qty
    }

    /** Total weight seen at the crane hook (kg) = net load + rigging. */
    fun grossLiftWeight(loadTotal: Double, tackleTotal: Double): Double {
        if (loadTotal < 0 || tackleTotal < 0) throw CalcException("Weights cannot be negative.")
        return loadTotal + tackleTotal
    }

    // -----------------------------------------------------------------------
    // Crane capacity (Crane tab)
    // -----------------------------------------------------------------------

    /**
     * Minimum rated crane capacity (kg) so [gross] uses at most the crane usage
     * ratio (75%) of the chart capacity.  This is the figure to look up in the
     * crane's load chart.
     */
    fun requiredChartCapacity(gross: Double, usageRatio: Double = CRANE_USAGE_RATIO): Double {
        if (gross < 0) throw CalcException("Total lift weight cannot be negative.")
        if (usageRatio <= 0.0 || usageRatio > 1.0) {
            throw CalcException("Usage ratio must be between 0 and 1.")
        }
        return gross / usageRatio
    }

    /** How much of the crane's rated chart capacity is in use (%). */
    fun chartUsagePercent(gross: Double, chartCapacity: Double): Double {
        if (gross < 0) throw CalcException("Total lift weight cannot be negative.")
        if (chartCapacity <= 0) throw CalcException("Crane chart capacity must be greater than 0.")
        return gross / chartCapacity * 100.0
    }

    /** Design weight used for rigging checks = gross lift / crane ratio. */
    fun designWeight(gross: Double): Double = requiredChartCapacity(gross, CRANE_USAGE_RATIO)

    /**
     * Look up the rated capacity (kg) on a load chart at [radius].
     *
     * Below the smallest charted radius the capacity falls back to the closest
     * charted value; beyond the largest it falls back to the most distant value
     * (conservative).  Between two charted radii a linear interpolation is used.
     */
    fun lookupChartCapacity(rows: List<Pair<Double, Double>>, radius: Double): Double {
        val chart = rows.filter { it.first >= 0.0 }.sortedBy { it.first }
        if (chart.isEmpty()) throw CalcException("Load chart is empty - nothing to look up.")
        chart.forEach { (_, capacity) ->
            if (capacity <= 0) throw CalcException("Load chart contains a non-positive capacity.")
        }
        if (radius < 0) throw CalcException("Working radius cannot be negative.")

        if (radius <= chart.first().first) return chart.first().second
        if (radius >= chart.last().first) return chart.last().second

        var lo = chart.first()
        var hi = chart.last()
        for (point in chart) {
            if (point.first <= radius) lo = point
            if (point.first >= radius) {
                hi = point
                break
            }
        }
        if (lo.first == hi.first || lo.second == hi.second) return lo.second
        val t = (radius - lo.first) / (hi.first - lo.first)
        return lo.second + t * (hi.second - lo.second)
    }
}

/** The measurements one load line carries, regardless of which type is active. */
interface LoadMeasurements {
    val qty: Int
    val weightKg: Double
    val kgPerMetre: Double
    val lengthM: Double
    val volumeM3: Double
    val densityKgM3: Double
}

/** kg/m^3 assumed for a new per-volume line: the reference's steel default. */
const val DEFAULT_DENSITY_KG_M3 = 7850.0

/** The crane may be loaded to this fraction of its rated chart capacity. */
const val CRANE_USAGE_RATIO = 0.75

/** How a crane's usage is banded.  The colour for each lives in the UI layer. */
enum class UsageState(val key: String, val wording: String) {
    WITHIN("within", "WITHIN LIMIT"),
    CAUTION("caution", "CAUTION"),
    OVER("over", "WARNING"),
}

/**
 * Classify a usage percentage into its band.
 *
 * One classifier for every surface that shows a crane's usage — the reference
 * wrote this twice and the two had already drifted, which is exactly the failure
 * a single classifier prevents.
 */
fun craneUsageBand(usagePercent: Double): UsageState = when {
    usagePercent <= CRANE_USAGE_RATIO * 100 -> UsageState.WITHIN
    usagePercent <= 100.0 -> UsageState.CAUTION
    else -> UsageState.OVER
}
