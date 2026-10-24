package com.zhafrisbs.liftingplancalculator.core

import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * The working-range geometry, ported from the desktop's `ui/js/graph/geometry.js`.
 *
 * Pure arithmetic with no Android dependency, for the reason the reference keeps
 * it out of the UI: the whole of the diagram is decided here.  If a figure is wrong
 * it is wrong *here*, and it can be checked here.
 *
 * The desktop deliberately diverged from the original `crane_graph/geometry.py`
 * that both descend from: its jib offset **lowers** the jib (clockwise) and its
 * working radius is measured from the `x = 0` axis rather than from the crane.
 * This module follows the desktop, not the Python.
 *
 * **The unit convention is the desktop's and is not negotiable.**  Lengths are
 * metres, angles are degrees measured counter-clockwise from +X, Y points up, and
 * `y = 0` is ground level.  The crane sits at a pivot `(x, y)` on those axes and
 * the boom rises from it at a boom angle; in the jib configurations the jib is
 * pinned to the boom tip at a fixed offset, so boom + jib is a **rigid body**
 * rotating about the pivot.  That rigidity is what makes the tip trace a circular
 * arc, and it is why the tip's position is computed once at boom angle 0 and then
 * rotated, rather than being rebuilt from trigonometry at the current angle.
 *
 * Errors are values the UI can show rather than crashes: every failure below is a
 * [GeometryException] carrying the reference's own sentence, because that is the
 * wording an operator already recognises.
 */

/** Raised for invalid inputs so the Graph tab can show a friendly message. */
class GeometryException(message: String) : IllegalArgumentException(message)

/** One point in world metres: X is the working radius, Y the height above ground. */
data class GraphPoint(val x: Double, val y: Double)

const val CONFIG_BOOM = "boom"
const val CONFIG_BOOM_JIB = "boom_jib"
const val CONFIG_BOOM_EXT_JIB = "boom_ext_jib"

/** The three configurations, in the desktop's declared order rather than sorted. */
internal val CONFIGS: List<String> = listOf(CONFIG_BOOM, CONFIG_BOOM_JIB, CONFIG_BOOM_EXT_JIB)

/** The dropdown's captions, verbatim from the reference. */
internal val CONFIG_LABELS: Map<String, String> = mapOf(
    CONFIG_BOOM to "Boom length",
    CONFIG_BOOM_JIB to "Boom length + jib",
    CONFIG_BOOM_EXT_JIB to "Boom length + extended jib",
)

/**
 * The boom angle's absolute mechanical limits, in degrees from horizontal.
 *
 * Angular limits and *not* a preference: the reference clamps every angle the
 * operator drags or types into this window, so a lift above 85 degrees is not
 * something this app can express.
 */
internal const val ANGLE_MIN = 0.0
internal const val ANGLE_MAX = 85.0

/**
 * Constrain [value] to the inclusive range `[lo, hi]`.
 *
 * A reversed range is an error rather than a silently empty one: `lo > hi` has no
 * solutions, and returning `lo` would hide a caller's arithmetic mistake behind a
 * plausible-looking number.
 */
internal fun clamp(value: Double, lo: Double, hi: Double): Double {
    if (lo > hi) throw GeometryException("clamp: lower bound exceeds upper bound.")
    return if (value < lo) lo else if (value > hi) hi else value
}

/**
 * Wrap an angle into `[-180, 180)`.
 *
 * Floored modulo, not the truncated remainder Kotlin's `%` would give: Python's
 * `%` is floored, and the desktop's own numbers depend on it.  `-190` must come
 * back as `170`, where a truncated remainder would produce `-190` and put the tip
 * a full turn away from where the finger is.
 */
internal fun normalizeAngle(angleDeg: Double): Double = (angleDeg + 180.0).mod(360.0) - 180.0

/**
 * The jib length in metres for the selected configuration.
 *
 * `boom` fits no jib at all, `boom_jib` the ordinary jib, `boom_ext_jib` the
 * extended one.  The *selection* is a string rather than a boolean because the
 * reference identifies the configuration by name throughout, and two booleans
 * would allow a fourth state the desktop cannot be in.
 */
internal fun effectiveJibLength(
    config: String,
    jibLength: Double,
    extJibLength: Double,
): Double {
    if (config == CONFIG_BOOM) return 0.0
    val length = when (config) {
        CONFIG_BOOM_JIB -> jibLength
        CONFIG_BOOM_EXT_JIB -> extJibLength
        else -> throw GeometryException("Unknown configuration: '$config'")
    }
    if (length < 0) throw GeometryException("Jib length cannot be negative.")
    return length
}

/** World coordinates of the boom tip. */
internal fun boomTip(pivot: GraphPoint, boomLength: Double, angleDeg: Double): GraphPoint {
    if (boomLength < 0) throw GeometryException("Boom length cannot be negative.")
    val a = Math.toRadians(angleDeg)
    return GraphPoint(
        pivot.x + boomLength * cos(a),
        pivot.y + boomLength * sin(a),
    )
}

/**
 * World coordinates of the free end of the boom + jib assembly.
 *
 * With no jib this is the boom tip; otherwise it is the jib tip, the jib being
 * pinned at the boom tip and offset **clockwise** from the boom axis by
 * [jibOffsetDeg], so a positive offset *lowers* the jib below the boom line.
 */
internal fun assemblyTip(
    pivot: GraphPoint,
    boomLength: Double,
    jibLength: Double,
    jibOffsetDeg: Double,
    angleDeg: Double,
): GraphPoint {
    val tip = boomTip(pivot, boomLength, angleDeg)
    if (jibLength <= 0) return tip
    val a = Math.toRadians(angleDeg - jibOffsetDeg)
    return GraphPoint(
        tip.x + jibLength * cos(a),
        tip.y + jibLength * sin(a),
    )
}

/**
 * The tip's position relative to the pivot at boom angle 0, in the assembly frame.
 *
 * The boom + jib assembly is rigid, so the world tip is simply this offset rotated
 * by the boom angle.  Computing it once here is what makes the envelope arc a
 * circle: the distance from the pivot to the tip cannot change as the angle sweeps,
 * so the radius below and the tip above are two views of one number.
 *
 * The jib offset is clockwise, so its vertical component is **negative**.
 */
internal fun tipOffsetFromPivot(
    boomLength: Double,
    jibLength: Double,
    jibOffsetDeg: Double,
): GraphPoint {
    if (boomLength < 0 || jibLength < 0) {
        throw GeometryException("Boom and jib lengths cannot be negative.")
    }
    val a = Math.toRadians(jibOffsetDeg)
    return GraphPoint(
        boomLength + jibLength * cos(a),
        -jibLength * sin(a),
    )
}

/** The distance from pivot to assembly tip, which is the envelope arc's radius (m). */
internal fun envelopeRadius(boomLength: Double, jibLength: Double, jibOffsetDeg: Double): Double {
    val (vx, vy) = tipOffsetFromPivot(boomLength, jibLength, jibOffsetDeg)
    return hypot(vx, vy)
}

/**
 * Points along the tip envelope arc from [angleMin] to [angleMax].
 *
 * [steps] is the number of *intervals*, so the result holds `steps + 1` points: the
 * reference's `range(steps + 1)` includes both ends, and the two arc ends are where
 * the envelope's own angle labels are drawn.
 */
internal fun envelopePoints(
    pivot: GraphPoint,
    radius: Double,
    angleMin: Double,
    angleMax: Double,
    steps: Int = 72,
): List<GraphPoint> {
    if (radius < 0) throw GeometryException("Envelope radius cannot be negative.")
    if (steps < 1) throw GeometryException("Envelope needs at least one step.")
    return (0..steps).map { i ->
        val a = Math.toRadians(angleMin + (angleMax - angleMin) * i / steps)
        GraphPoint(
            pivot.x + radius * cos(a),
            pivot.y + radius * sin(a),
        )
    }
}

/**
 * The boom angle that places the assembly tip on [target].
 *
 * The inverse of [assemblyTip], and the whole of what dragging the tip handle does.
 * The result is normalized to `[-180, 180)`; **callers must clamp it to
 * `[ANGLE_MIN, ANGLE_MAX]`** rather than this function doing so, because a target
 * below the horizon is a legitimate answer to "what angle points there" and the
 * desktop's caller is also the one that knows the mechanical limits.
 *
 * A zero-length assembly has no direction to point in, so it is refused instead of
 * returning `atan2(0, 0)`'s arbitrary zero.
 */
internal fun angleForTip(
    pivot: GraphPoint,
    boomLength: Double,
    jibLength: Double,
    jibOffsetDeg: Double,
    target: GraphPoint,
): Double {
    val (vx, vy) = tipOffsetFromPivot(boomLength, jibLength, jibOffsetDeg)
    if (hypot(vx, vy) < 1e-9) throw GeometryException("Boom length must be greater than 0.")
    val base = Math.toDegrees(atan2(vy, vx))
    val targetAng = Math.toDegrees(atan2(target.y - pivot.y, target.x - pivot.x))
    return normalizeAngle(targetAng - base)
}

/**
 * Working radius (m) = the tip's horizontal distance from the `x = 0` axis.
 *
 * The basis the load chart's radii are measured from, and **not** the crane's own
 * position: a crane set off the axis reads the same tip differently.  This is the
 * desktop's convention, which the original Python did not share.
 */
internal fun workingRadius(tip: GraphPoint): Double = tip.x

/**
 * The boom angle that gives the assembly a working radius of [radius].
 *
 * The inverse of the horizontal reach, and the upper solution -- the tip above the
 * pivot.  With no jib this reduces to `acos(radius / boomLength)`.  Ported verbatim
 * from the desktop's own function: its `ratio` is clamped to 1, so a radius at or
 * beyond the envelope still answers rather than asking for the `acos` of a value
 * greater than one.
 */
internal fun angleForWorkingRadius(
    boomLength: Double,
    jibLength: Double,
    jibOffsetDeg: Double,
    radius: Double,
): Double {
    val (vx, vy) = tipOffsetFromPivot(boomLength, jibLength, jibOffsetDeg)
    val envelope = hypot(vx, vy)
    if (envelope < 1e-9) throw GeometryException("Boom length must be greater than 0.")
    if (radius < 0) throw GeometryException("Working radius cannot be negative.")
    val base = Math.toDegrees(atan2(vy, vx))
    val ratio = minOf(1.0, radius / envelope)
    return normalizeAngle(Math.toDegrees(acos(ratio)) - base)
}

/** Tip height (m) above ground level (`y = 0`), negative below it. */
internal fun tipHeight(tip: GraphPoint): Double = tip.y

/** World coordinates of the active tip for a whole state. */
internal fun tipForState(state: CraneGraphState): GraphPoint {
    val pivot = GraphPoint(state.craneX, state.craneY)
    val jib = effectiveJibLength(state.config, state.jibLength, state.extJibLength)
    return assemblyTip(pivot, state.boomLength, jib, state.jibOffset, state.angle)
}

/**
 * The derived values the readout panel shows.
 *
 * A data class rather than the reference's dict: the reference builds a dictionary
 * of whatever keys its two callers wanted, and a typed record is what makes a
 * missing figure a compile error instead of a blank row.
 *
 * [belowGround] is a *judgement* rather than a measurement, which is why it is
 * carried alongside the height rather than left to the caller to recompute — the
 * desktop colours the panel red from this flag, and two places deriving it from
 * `tipHeight < 0` would be two places to get it wrong.
 */
data class CraneReadouts(
    val config: String,
    val jibActive: Boolean,
    val jibLength: Double,
    val pivot: GraphPoint,
    val boomEnd: GraphPoint,
    val tip: GraphPoint,
    val workingRadius: Double,
    val tipHeight: Double,
    val boomTipHeight: Double,
    val heightAboveCrane: Double,
    val envelopeRadius: Double,
    val belowGround: Boolean,
)

/** The readout panel's figures for a whole state. */
internal fun readouts(state: CraneGraphState): CraneReadouts {
    val pivot = GraphPoint(state.craneX, state.craneY)
    val jib = effectiveJibLength(state.config, state.jibLength, state.extJibLength)
    val tip = assemblyTip(pivot, state.boomLength, jib, state.jibOffset, state.angle)
    val boomEnd = boomTip(pivot, state.boomLength, state.angle)
    return CraneReadouts(
        config = state.config,
        jibActive = jib > 0,
        jibLength = jib,
        pivot = pivot,
        boomEnd = boomEnd,
        tip = tip,
        workingRadius = workingRadius(tip),
        tipHeight = tipHeight(tip),
        boomTipHeight = tipHeight(boomEnd),
        heightAboveCrane = tip.y - pivot.y,
        envelopeRadius = envelopeRadius(state.boomLength, jib, state.jibOffset),
        belowGround = tip.y < 0,
    )
}
