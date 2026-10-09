package com.zhafrisbs.liftingplancalculator.core

import java.util.concurrent.atomic.AtomicLong

/**
 * The lift: the load lines and the tackle, and the weight they add up to.
 *
 * A port of `LoadItem` / `TackleItem` from `lifting_calculator/kivy_ui/state.py`.
 * This is the tab that owns the number every other tab is checked against — on
 * the reference the sling tabs do not collect a load at all, so this model has
 * to land before any of them can.
 *
 * Immutable, with [id] carried across `copy`, for two reasons: Compose observes
 * a change by identity, and a text field needs a stable key so a row being typed
 * into is not re-associated with a different line when an earlier one is deleted.
 */

private val nextLineId = AtomicLong(1L)

/**
 * One line of the lift.
 *
 * Every measurement is held whether or not the current type uses it.  Switching a
 * line from per-metre to per-volume and back must not lose what was typed — the
 * reference's row keeps all five spin boxes alive for the same reason.
 */
data class LoadItem(
    val id: Long = nextLineId.getAndIncrement(),
    val name: String = "",
    val loadType: LoadType = LoadType.FIXED,
    override val qty: Int = 1,
    override val weightKg: Double = 0.0,
    override val kgPerMetre: Double = 0.0,
    override val lengthM: Double = 0.0,
    override val volumeM3: Double = 0.0,
    override val densityKgM3: Double = DEFAULT_DENSITY_KG_M3,
) : LoadMeasurements {

    /**
     * The line's total, or 0.0 while it is not yet enterable.
     *
     * A [CalcException] becomes zero rather than propagating.  The reference does
     * the same, and for a good reason: a line being typed into is incomplete most
     * of the time, and treating that as a failure would mean the totals spent the
     * whole of data entry reporting an error.
     */
    fun weight(): Double = try {
        Calc.loadItemWeight(loadType, this)
    } catch (_: CalcException) {
        0.0
    }
}

/** One line of lifting tackle: shackles, strops, the block. */
data class TackleItem(
    val id: Long = nextLineId.getAndIncrement(),
    val description: String = "",
    val qty: Int = 1,
    val weightKg: Double = 0.0,
) {
    fun subtotal(): Double = weightKg * qty
}

/** The Overall Weight figures, in the order the result card renders them. */
data class LiftTotals(
    val load: Double,
    val tackle: Double,
    val gross: Double,
    val requiredChartCapacity: Double,
) {
    /**
     * Whether the minimum crane rating is exceeded.
     *
     * Ported as written, and as written it is **always false**: the required
     * capacity is `gross / 0.75`, which is greater than `gross` for any positive
     * gross.  The reference has the same arithmetic, so the flag is kept — a
     * reader comparing the two surfaces should find them agreeing, and changing
     * the rule here would make this the only place the two differ.
     */
    val overLimit: Boolean
        get() = requiredChartCapacity > 0.0 && gross > requiredChartCapacity

    companion object {
        /**
         * Sum a lift's lines.
         *
         * [requiredChartCapacity] is the one figure here that is not a sum of the
         * others: it is the crane capacity the lift needs under the 75% rule.
         */
        fun of(loadItems: List<LoadItem>, tackleItems: List<TackleItem>): LiftTotals {
            val load = loadItems.sumOf { it.weight() }
            val tackle = tackleItems.sumOf { it.subtotal() }
            val gross = Calc.grossLiftWeight(load, tackle)
            val required = try {
                Calc.requiredChartCapacity(gross)
            } catch (_: CalcException) {
                0.0
            }
            return LiftTotals(load, tackle, gross, required)
        }
    }
}
