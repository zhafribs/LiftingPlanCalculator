package com.zhafrisbs.liftingplancalculator.core

/**
 * The Graph tab's whole input surface, replacing the reference's `state` dict.
 *
 * A data class rather than a `Map<String, Any>`: the desktop's dictionary is typed
 * only by convention, and the arithmetic in `geometry.py` reaches into it with
 * string keys, so a key that is misspelled or missing is a `KeyError` at run time
 * on the desktop and a compile error here.
 *
 * Immutable, and every figure is a `Double` even where an `Int` would do, because
 * the reference's spin boxes are continuous and a whole-number cast here would be
 * a second, silently different set of angles.
 *
 * The defaults are [DEFAULT_STATE]'s, exactly: 30 m boom, 12 m jib, 24 m extended
 * jib, no offset, 45 degrees, a 0-80 degree envelope, and the crane at the origin.
 */
data class CraneGraphState(
    val config: String = CONFIG_BOOM,
    val boomLength: Double = 30.0,
    val jibLength: Double = 12.0,
    val extJibLength: Double = 24.0,
    val jibOffset: Double = 0.0,
    val angle: Double = 45.0,
    val envMin: Double = 0.0,
    val envMax: Double = 80.0,
    val craneX: Double = 0.0,
    val craneY: Double = 0.0,
)

/**
 * The reference's `DEFAULT_STATE`, as the state it describes.
 *
 * A single shared instance rather than a factory, because it is never mutated —
 * [CraneGraphState] is a data class and every edit produces a copy — so a caller
 * that wants the opening form cannot get a half-modified one by accident.
 */
internal val DEFAULT_STATE: CraneGraphState = CraneGraphState()
