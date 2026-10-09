package com.zhafrisbs.liftingplancalculator.core

import java.util.Locale
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.tan

/**
 * The sling arrangements, ported from `lifting_calculator/sling_1leg.py`.
 *
 * Units are metres, kilograms and degrees.  `loadWeight` is the **gross load at
 * hook**.  The 75% allowable window is a crane load-chart rule and sizes the lift
 * against the crane only; it is deliberately *not* applied here, so the sling is
 * checked against the load the hook actually carries.  (The 0.75 that appears as a
 * choke `baseReeveFactor` below is the separate choker-derate, which the reference
 * documents carry as RF.)
 *
 * Nineteen arrangements are covered: the seven one-leg references, the 2-leg forms,
 * the nested systems, and the 3- and 4-leg bridle direct hitches.  Two things about
 * this file are worth knowing before reading it.
 *
 * **It is a transcription, not a re-derivation.**  Every formula, bound, early
 * return and error message is the reference's.  Where the reference rounds a value
 * before using it, this rounds it too — that is not tidiness, it is the model: the
 * reference documents derive every printed force *from the factor they print*, so
 * a leg tension computed from an unrounded cosine would not match the number in the
 * document it is checked against.
 *
 * **`rint`, not "round".**  Python's `round()` breaks a tie to even, and so does
 * `Math.rint`.  A half-up round would disagree with the reference on the exact
 * boundary values — rare, but the whole point of this file is that the two agree.
 */
class SlingInputError(message: String) : IllegalArgumentException(message)

/** Decimal places the reference documents use for a published sling angle factor. */
const val AF_DECIMALS = 3

/**
 * The reference documents print every length to the millimetre, so a length that
 * another printed line multiplies has to be rounded before it is used.
 */
const val LENGTH_DECIMALS = 3

/**
 * The leg count at which a tag has no working angle to enter.
 *
 * Named because it is read in four places — the form decides whether to show the
 * tag angle field, the solver decides whether to apply one, and the report decides
 * whether there is a denominator to print — and four copies of `== 1` is four
 * places for one of them to disagree with the other three.
 */
const val SINGLE_LEG = 1

const val ANGLE_BASIS_INTERNAL = "internal"
const val ANGLE_BASIS_EXTERNAL = "external"

/** A published angle factor, at the precision the reports use. */
fun publishedAf(value: Double): Double = round(value, AF_DECIMALS)

/** A published length, at the precision the reports use. */
fun publishedLength(value: Double): Double = round(value, LENGTH_DECIMALS)

/**
 * The published product of a longitudinal and transverse factor.
 *
 * The 2-leg basket and nested reference documents publish both factors and then
 * multiply the *published* pair to reach the combined factor.
 */
fun publishedCombinedAf(afLong: Double, afTrans: Double): Double =
    publishedAf(publishedAf(afLong) * publishedAf(afTrans))

/**
 * Round half to even, as Python's `round()` does.
 *
 * A half-up round would disagree with the reference on exactly the boundary values,
 * and the boundary values here are the published factors every printed force is
 * derived from.
 */
private fun round(value: Double, decimals: Int): Double {
    val factor = Math.pow(10.0, decimals.toDouble())
    return Math.rint(value * factor) / factor
}

/**
 * The leg count an installed tag rating is declared on.
 *
 * The reference documents state the count under their note heading: *"If 4-Leg
 * Sling, use Sling Legs = 3"*, *"If 3-Leg Sling, use Sling Legs = 2"* and *"If
 * 2-Leg Sling, use Sling Legs = 2"*.  A 3- or 4-leg bridle is rated for the case
 * where one leg does not take its share, so it is rated on one leg fewer than it
 * has; a 2-leg sling is rated on both.
 */
fun ratedLegs(legs: Int): Int {
    val count = maxOf(1, legs)
    return if (count > 2) count - 1 else count
}

/**
 * The per-leg capacity of an installed tag rating.
 *
 * One sling leg carries the installed tag WLL divided by the rated leg count and
 * the tag's rated angle factor, e.g. 5,300 kg / (2 x 0.707) = 3,748.2 kg.
 */
fun legCapacity(tagWll: Double, legs: Int, tagAf: Double): Double? {
    if (tagWll <= 0 || legs < 1 || tagAf <= 0) return null
    return tagWll / (legs * tagAf)
}

/**
 * The tag-rating utilisation as a percentage.
 *
 * The reference documents divide the *printed* required WLL by the *printed*
 * per-leg capacity, so the published values are used here too.
 */
fun tagUtilisation(requiredWll: Double, legCapacity: Double?): Double? {
    if (legCapacity == null || legCapacity <= 0 || requiredWll <= 0) return null
    return round(requiredWll, 1) / round(legCapacity, 1) * 100.0
}

/**
 * One sling arrangement's declared shape.
 *
 * `baseReeveFactor` is the denominator factor used in the documents' WLL equation.
 * It is not a generic "efficiency" multiplier.
 */
data class HitchSpec(
    val key: String,
    val label: String,
    val shape: String,
    val effectiveLegs: Int,
    val baseReeveFactor: Double,
    val needsAngle: Boolean = false,
    val angleDescription: String = "",
    val roundLoad: Boolean = false,
    val rectangularLoad: Boolean = false,
    val bridle: Boolean = false,
    val legCount: Int = 1,
    val needsPickDistance: Boolean = false,
    val nested: Int = 0,
) {
    /**
     * Whether an installed tag is rated at an angle.
     *
     * The installed tag WLL itself applies to every arrangement, including a single
     * leg.  The *angle* does not: a single leg hangs vertical, so it takes the tag's
     * full rating and there is no per-leg derate to apply.  A 2-leg or bridle
     * arrangement splits the load between legs that splay out from the hook, so the
     * tag's rating is derated by `cos(Tag WLL Angle)`.
     *
     * Derived from [legCount] rather than stored, so it cannot drift.
     */
    val needsTagAngle: Boolean get() = legCount >= 2
}

/** The arrangements, in the order the 1-Leg reference folder uses. */
val HITCH_ORDER: List<String> = listOf(
    "direct_hook",
    "round_choke",
    "rect_choke",
    "round_basket",
    "rect_basket",
    "vertical_round_basket",
    "vertical_rect_basket",
    "two_leg_direct",
    "two_leg_round_choke",
    "two_leg_rect_choke",
    "two_leg_round_basket",
    "two_leg_rect_basket",
    "two_leg_vertical_round_basket",
    "two_leg_vertical_rect_basket",
    "two_leg_nested_2leg",
    "two_leg_nested_3leg",
    "two_leg_nested_4leg",
    "three_leg_direct",
    "four_leg_direct",
)

val HITCH_SPECS: Map<String, HitchSpec> = listOf(
    HitchSpec("direct_hook", "Direct hook", "none", 1, 1.00),
    HitchSpec(
        "round_choke", "Round choke", "round", 1, 0.75,
        needsAngle = true,
        angleDescription = "Choke angle (inside/outside the loop)",
        roundLoad = true,
    ),
    HitchSpec(
        "rect_choke", "Rectangular choke", "rectangular", 1, 0.50,
        needsAngle = true,
        angleDescription = "Choke flare angle from vertical",
        rectangularLoad = true,
    ),
    HitchSpec("round_basket", "Round basket", "round", 2, 2.00, roundLoad = true),
    HitchSpec(
        "rect_basket", "Rectangular basket", "rectangular", 2, 1.00,
        rectangularLoad = true,
    ),
    HitchSpec(
        "vertical_round_basket", "Round vertical basket", "round", 2, 2.00,
        roundLoad = true,
    ),
    HitchSpec(
        "vertical_rect_basket", "Rectangular vertical basket", "rectangular", 2, 1.00,
        rectangularLoad = true,
    ),
    HitchSpec(
        "two_leg_direct", "2-Leg Direct", "two_direct", 2, 1.00,
        legCount = 2, needsPickDistance = true,
    ),
    HitchSpec(
        "two_leg_round_choke", "2-Leg Round Choke", "round", 2, 0.75,
        needsAngle = true, roundLoad = true, legCount = 2, needsPickDistance = true,
    ),
    HitchSpec(
        "two_leg_rect_choke", "2-Leg Rectangular Choke", "rectangular", 2, 0.50,
        needsAngle = true, rectangularLoad = true, legCount = 2, needsPickDistance = true,
    ),
    HitchSpec(
        "two_leg_round_basket", "2-Leg Round Basket", "round", 4, 2.00,
        roundLoad = true, legCount = 2, needsPickDistance = true,
    ),
    HitchSpec(
        "two_leg_rect_basket", "2-Leg Rectangular Basket", "rectangular", 4, 1.00,
        rectangularLoad = true, legCount = 2, needsPickDistance = true,
    ),
    HitchSpec(
        "two_leg_vertical_round_basket", "2-Leg Round Vertical Basket", "round", 4, 2.00,
        roundLoad = true, legCount = 2, needsPickDistance = true,
    ),
    HitchSpec(
        "two_leg_vertical_rect_basket", "2-Leg Rectangular Vertical Basket",
        "rectangular", 4, 1.00,
        rectangularLoad = true, legCount = 2, needsPickDistance = true,
    ),
    HitchSpec(
        "two_leg_nested_2leg", "2-Leg Sling in Each Leg of 2-Leg Sling",
        "nested2", 4, 1.00, legCount = 2, nested = 2, needsPickDistance = true,
    ),
    HitchSpec(
        "two_leg_nested_3leg", "3-Leg Sling in Each Leg of 2-Leg Sling",
        "nested3", 6, 1.00, legCount = 2, nested = 3, needsPickDistance = true,
    ),
    HitchSpec(
        "two_leg_nested_4leg", "4-Leg Sling in Each Leg of 2-Leg Sling",
        "nested4", 8, 1.00, legCount = 2, nested = 4, needsPickDistance = true,
    ),
    HitchSpec(
        "three_leg_direct", "3-Leg Bridle Direct", "bridle3", 3, 1.00,
        bridle = true, legCount = 3, needsPickDistance = true,
    ),
    HitchSpec(
        "four_leg_direct", "4-Leg Bridle Direct", "bridle4", 4, 1.00,
        bridle = true, legCount = 4,
    ),
).associateBy { it.key }

/** The arrangement's declared shape, or a named failure. */
fun hitchSpec(hitch: String): HitchSpec = HITCH_SPECS[hitch]
    ?: throw SlingInputError("Unknown sling arrangement: '$hitch'")

/**
 * User-entered values for one continuous sling.
 *
 * [side] is the rectangular load's height and [bottom] is its length.
 * [chokeAngle] is interpreted according to [angleBasis]: an internal angle is
 * measured from vertical at the choke, an external angle is the angle from the hook
 * to the load.  For the round-choke examples the first case is entered as internal
 * 60 degrees (external 120 degrees).
 *
 * **These defaults are the form's opening state, not the worked example.**  They are
 * all zero except [chokeAngle] (60 degrees) and the two tag angles (45 degrees),
 * because the operator asked for the app to open blank: *"default every value to 0
 * except for angle of choke (60 deg) and wll angle (45 deg)"*.  Those three are kept
 * because they are the ordinary figures for almost every lift, so opening on zero
 * would ask the operator to type an answer they already know.
 *
 * The worked example the **documents** use is [EXAMPLE_INPUTS], built from
 * explicitly-stated values rather than from these defaults, so the two cannot move
 * together by accident.
 */
data class OneLegInputs(
    val hitch: String = "direct_hook",
    val slingLength: Double = 0.0,
    val diameter: Double = 0.0,
    val side: Double = 0.0,
    val bottom: Double = 0.0,
    val chokeAngle: Double = 60.0,
    val angleBasis: String = ANGLE_BASIS_INTERNAL,
    val tagWll: Double = 0.0,
    val tagWllAngle: Double = 45.0,
    val pickDistance: Double = 0.0,
    val pickLength: Double = 0.0,
    val pickWidth: Double = 0.0,
    val secondaryLength: Double = 0.0,
    val secondarySpread: Double = 0.0,
    val secondaryTagWll: Double = 0.0,
    val secondaryTagWllAngle: Double = 45.0,
)

/**
 * The values the reference documents in `Example Calculation/1-Leg` use, plus the
 * 3-/4-leg bridle direct-hitch references.
 */
val EXAMPLE_INPUTS: Map<String, OneLegInputs> = mapOf(
    "direct_hook" to OneLegInputs(hitch = "direct_hook", slingLength = 7.0),
    "round_choke" to OneLegInputs(
        hitch = "round_choke", slingLength = 7.0, diameter = 1.0, chokeAngle = 60.0,
        angleBasis = ANGLE_BASIS_INTERNAL,
    ),
    "rect_choke" to OneLegInputs(
        hitch = "rect_choke", slingLength = 7.0, side = 0.3, bottom = 0.4,
        chokeAngle = 60.0, angleBasis = ANGLE_BASIS_INTERNAL,
    ),
    "round_basket" to OneLegInputs(
        hitch = "round_basket", slingLength = 7.0, diameter = 1.0,
    ),
    "rect_basket" to OneLegInputs(
        hitch = "rect_basket", slingLength = 7.0, side = 0.3, bottom = 0.4,
    ),
    "vertical_round_basket" to OneLegInputs(
        hitch = "vertical_round_basket", slingLength = 7.0, diameter = 1.0,
    ),
    "vertical_rect_basket" to OneLegInputs(
        hitch = "vertical_rect_basket", slingLength = 7.0, side = 0.3, bottom = 0.4,
    ),
    "two_leg_direct" to OneLegInputs(
        hitch = "two_leg_direct", slingLength = 7.0, pickDistance = 5.0,
    ),
    "two_leg_round_choke" to OneLegInputs(
        hitch = "two_leg_round_choke", slingLength = 7.0, diameter = 1.0,
        pickDistance = 5.0, chokeAngle = 60.0, angleBasis = ANGLE_BASIS_INTERNAL,
    ),
    "two_leg_rect_choke" to OneLegInputs(
        hitch = "two_leg_rect_choke", slingLength = 7.0, side = 0.3, bottom = 0.4,
        pickDistance = 5.0, chokeAngle = 60.0, angleBasis = ANGLE_BASIS_INTERNAL,
    ),
    "two_leg_round_basket" to OneLegInputs(
        hitch = "two_leg_round_basket", slingLength = 7.0, diameter = 1.0,
        pickDistance = 5.0,
    ),
    "two_leg_rect_basket" to OneLegInputs(
        hitch = "two_leg_rect_basket", slingLength = 7.0, side = 0.3, bottom = 0.4,
        pickDistance = 5.0,
    ),
    "two_leg_vertical_round_basket" to OneLegInputs(
        hitch = "two_leg_vertical_round_basket", slingLength = 7.0, diameter = 1.0,
        pickDistance = 5.0,
    ),
    "two_leg_vertical_rect_basket" to OneLegInputs(
        hitch = "two_leg_vertical_rect_basket", slingLength = 7.0, side = 0.3,
        bottom = 0.4, pickDistance = 5.0,
    ),
    "two_leg_nested_2leg" to OneLegInputs(
        hitch = "two_leg_nested_2leg", slingLength = 7.0, pickDistance = 5.4,
        secondaryLength = 0.6, secondarySpread = 0.2,
    ),
    "two_leg_nested_3leg" to OneLegInputs(
        hitch = "two_leg_nested_3leg", slingLength = 7.0, pickDistance = 5.4,
        secondaryLength = 0.6, secondarySpread = 0.2,
    ),
    "two_leg_nested_4leg" to OneLegInputs(
        hitch = "two_leg_nested_4leg", slingLength = 7.0, pickDistance = 5.4,
        secondaryLength = 0.6, secondarySpread = 0.2,
    ),
    "three_leg_direct" to OneLegInputs(
        hitch = "three_leg_direct", slingLength = 7.0, pickDistance = 5.4,
    ),
    "four_leg_direct" to OneLegInputs(
        hitch = "four_leg_direct", slingLength = 7.0, pickLength = 5.0, pickWidth = 2.0,
    ),
)

/** The reference inputs for a supported sling arrangement. */
fun exampleInputs(hitch: String): OneLegInputs = EXAMPLE_INPUTS[hitch]
    ?: throw SlingInputError("Unknown sling arrangement: '$hitch'")

/** A number, or a named failure an operator can act on. */
private fun number(
    value: Double,
    name: String,
    positive: Boolean = false,
    nonNegative: Boolean = false,
): Double {
    if (!value.isFinite()) throw SlingInputError("$name must be finite.")
    if (positive && value <= 0) throw SlingInputError("$name must be greater than 0.")
    if (nonNegative && value < 0) throw SlingInputError("$name cannot be negative.")
    return value
}

/**
 * `(externalDegrees, internalDegrees)` for a choke angle.
 *
 * The example documents call the angle from the hook to the load the *external*
 * angle.  The complementary angle inside the choke is measured from vertical and is
 * used for the corner geometry.
 */
fun normaliseAngle(value: Double, basis: String): Pair<Double, Double> {
    val angle = number(value, "Choke angle")
    if (basis != ANGLE_BASIS_INTERNAL && basis != ANGLE_BASIS_EXTERNAL) {
        throw SlingInputError("Angle basis must be internal or external.")
    }
    if (!(angle > 0 && angle < 180)) {
        throw SlingInputError("Choke angle must be between 0 and 180 degrees.")
    }
    return if (basis == ANGLE_BASIS_EXTERNAL) {
        angle to (180.0 - angle)
    } else {
        (180.0 - angle) to angle
    }
}

/**
 * The rating multiplier from the example choke-angle table.
 *
 * The boundary at 120 degrees is treated as the 100% row.  The example documents use
 * 1.00 for their first (internal 60 / external 120) case.
 */
fun chokeReductionFactor(externalAngle: Double): Double {
    val angle = number(externalAngle, "External choke angle")
    if (!(angle > 0 && angle <= 180)) {
        throw SlingInputError("External choke angle must be between 0 and 180 degrees.")
    }
    return when {
        angle >= 120.0 -> 1.00
        angle >= 90.0 -> 0.87
        angle >= 60.0 -> 0.74
        angle >= 30.0 -> 0.62
        else -> 0.49
    }
}

private fun validateShape(spec: HitchSpec, inputs: OneLegInputs) {
    if (spec.roundLoad) number(inputs.diameter, "Load diameter", positive = true)
    if (spec.rectangularLoad) {
        number(inputs.side, "Load side/height", positive = true)
        number(inputs.bottom, "Load bottom/length", positive = true)
    }
    if (spec.needsPickDistance) {
        number(inputs.pickDistance, "Pick-point distance", positive = true)
    }
    if (spec.nested != 0) {
        number(inputs.secondaryLength, "Secondary sling length", positive = true)
        number(inputs.secondarySpread, "Secondary pick spacing", nonNegative = true)
    }
    if (spec.shape == "bridle4") {
        number(inputs.pickLength, "Pick-point length", positive = true)
        number(inputs.pickWidth, "Pick-point width", positive = true)
    }
}

/** One leg group of a nested system, outermost first. */
data class SecondaryGroup(
    val offset: Double,
    val angle: Double,
    val af: Double,
    val combinedAf: Double,
    val tension: Double,
    val governing: Boolean,
)

/** The figures a report derives from, kept together so they cannot drift apart. */
data class SlingFormula(
    val wrap: Double,
    val freeLeg: Double,
    val angle: Double,
    val hookToLoad: Double,
    val tensionEach: Double,
    val maxTension: Double,
    val requiredWll: Double,
)

/**
 * The solved arrangement.
 *
 * The field set mirrors the reference's returned dictionary one-for-one, so a
 * reader comparing the two can follow a figure across.  Typed rather than a map of
 * string keys because the report reads these as values, and a misspelt key would
 * otherwise be a figure that silently never arrives.
 */
data class SlingResult(
    val hitch: String,
    val hitchLabel: String,
    val shape: String,
    val diameter: Double,
    val side: Double,
    val bottom: Double,
    val pickDistance: Double,
    val pickLength: Double,
    val pickWidth: Double,
    val secondaryLength: Double,
    val secondarySpread: Double,
    val nested: Int,
    val chokeAngle: Double,
    val angleBasis: String,
    val loadWeight: Double,
    val slingLength: Double,
    val wrapLength: Double,
    val freeLegLength: Double,
    val angle: Double,
    val angleLong: Double?,
    val angleTrans: Double?,
    val afLong: Double?,
    val afTrans: Double?,
    val angleFactor: Double,
    val baseReeveFactor: Double,
    val chokeReductionFactor: Double,
    val combinedReeveFactor: Double,
    val externalAngle: Double?,
    val internalAngle: Double?,
    val effectiveLegs: Int,
    val slingLegs: Int,
    val tensionEach: Double,
    val maxTension: Double,
    val maxTensionBasis: String,
    val requiredWll: Double,
    val ratingLegs: Int,
    val tagWllAngle: Double,
    val tagAngleFactor: Double,
    val legCapacity: Double?,
    val secondaryTagWll: Double,
    val secondaryTagWllAngle: Double,
    val secondaryTagAngleFactor: Double,
    val secondaryLegs: Int?,
    val secondaryRatingLegs: Int?,
    val secondaryLegCapacity: Double?,
    val secondaryUtilisation: Double?,
    val secondaryTension: Double?,
    val secondaryGroups: List<SecondaryGroup>?,
    val secondaryRequiredWll: Double?,
    val secondaryAngle: Double?,
    val secondaryAf: Double?,
    val secondaryHeight: Double?,
    val secondaryHeightAngle: Double?,
    val pileLevelDifference: Double?,
    val secondaryHeightGoverningOffset: Double?,
    val totalCombinedLength: Double?,
    val secondaryCombinedAf: Double?,
    val documentMaxTension: Double?,
    val documentRequiredWll: Double,
    val tagWll: Double,
    val utilisation: Double?,
    val secondaryEquipmentStatus: String,
    val secondaryEquipmentOk: Boolean?,
    val equipmentStatus: String,
    val equipmentOk: Boolean?,
    val chokeToLoad: Double?,
    val hookToLoad: Double,
    val lead: Double?,
    val cornerLength: Double?,
    val shapeNote: String,
    val formula: SlingFormula,
)

/**
 * Solve one of the reference sling arrangements.
 *
 * The returned [SlingResult] is deliberately rich enough for both the fresh tab and
 * the summary report.  It uses stable, descriptive fields and never relies on a UI.
 */
@Throws(SlingInputError::class)
fun solveOneLeg(inputs: OneLegInputs, loadWeight: Double): SlingResult {
    val spec = hitchSpec(inputs.hitch)
    val weight = number(loadWeight, "Gross load at hook", nonNegative = true)
    val length = number(inputs.slingLength, "Sling total length", positive = true)
    validateShape(spec, inputs)

    // Normalise every numeric value once, so the geometry below cannot leak a type
    // error when the model is used outside a form.
    val normalised = inputs.copy(
        slingLength = length,
        diameter = number(inputs.diameter, "Load diameter", nonNegative = true),
        side = number(inputs.side, "Load side/height", nonNegative = true),
        bottom = number(inputs.bottom, "Load bottom/length", nonNegative = true),
        chokeAngle = number(inputs.chokeAngle, "Choke angle"),
        tagWll = number(inputs.tagWll, "Configured tag WLL", nonNegative = true),
        tagWllAngle = number(inputs.tagWllAngle, "Tag WLL angle"),
        secondaryTagWll = number(
            inputs.secondaryTagWll, "Secondary tag WLL", nonNegative = true,
        ),
        secondaryTagWllAngle = number(inputs.secondaryTagWllAngle, "Secondary tag WLL angle"),
        pickDistance = number(inputs.pickDistance, "Pick-point distance", nonNegative = true),
        pickLength = number(inputs.pickLength, "Pick-point length", nonNegative = true),
        pickWidth = number(inputs.pickWidth, "Pick-point width", nonNegative = true),
        secondaryLength = number(
            inputs.secondaryLength, "Secondary sling length", nonNegative = true,
        ),
        secondarySpread = number(
            inputs.secondarySpread, "Secondary pick spacing", nonNegative = true,
        ),
    )

    var external: Double? = null
    var internal: Double? = null
    if (spec.needsAngle) {
        val (ext, int) = normaliseAngle(normalised.chokeAngle, normalised.angleBasis)
        external = ext
        internal = int
        if ((normalised.hitch == "rect_choke" || normalised.hitch == "two_leg_rect_choke") &&
            !(int > 0.0 && int < 90.0)
        ) {
            throw SlingInputError(
                "Rectangular choke flare must be between 0 and 90 degrees " +
                    "from vertical (the external angle must be above 90 degrees)."
            )
        }
    }
    val reduction = external?.let { chokeReductionFactor(it) } ?: 1.0

    var wrap = 0.0
    var freeLeg = length
    var angle = 0.0
    var hookToLoad = length
    var chokeToLoad: Double? = null
    var cornerLength: Double? = null
    var angleTrans: Double? = null
    var angleLong: Double? = null
    var afLong: Double? = null
    var afTrans: Double? = null
    var precomputedAngleFactor: Double? = null
    var tensionOverride: Double? = null
    var secondaryTension: Double? = null
    var secondaryRequiredWll: Double? = null
    var secondaryAngle: Double? = null
    var secondaryAf: Double? = null
    var secondaryGroups: List<SecondaryGroup>? = null
    var pileLevelDifference: Double? = null
    var governingIndex = 0
    // A lead/contact length is meaningful only for the two choke arrangements;
    // direct and basket hitches are described by their free leg(s) instead.
    var lead: Double? = null
    var shapeNote = ""
    // Set only by the nested branch, and read back under `spec.nested != 0`.
    var offsets: List<Double>? = null
    var heightSling2 = 0.0
    var heightAngle = 0.0
    var totalCombined = 0.0

    when {
        normalised.hitch == "direct_hook" -> {
            shapeNote = "A single vertical leg runs from the hook to the load."
        }

        normalised.hitch == "three_leg_direct" -> {
            val radiusSpread = normalised.pickDistance / kotlin.math.sqrt(3.0)
            val ratio = radiusSpread / length
            if (ratio > 1.0) {
                throw SlingInputError(
                    "The 3-leg bridle cannot reach the hook with this sling length " +
                        "and pick-point spacing."
                )
            }
            angle = Math.toDegrees(asin(ratio))
            hookToLoad = length * publishedAf(cos(Math.toRadians(angle)))
            if (hookToLoad <= 0) {
                throw SlingInputError(
                    "The 3-leg bridle needs a shorter pick-point spacing or a " +
                        "longer sling to provide headroom."
                )
            }
            shapeNote = "Three legs share the load from a uniform pick-point circle."
        }

        normalised.hitch == "four_leg_direct" -> {
            val radiusSpread = hypot(normalised.pickLength / 2.0, normalised.pickWidth / 2.0)
            val ratio = radiusSpread / length
            if (ratio > 1.0) {
                throw SlingInputError(
                    "The 4-leg bridle cannot reach the hook with this sling length " +
                        "and pick-point rectangle."
                )
            }
            angle = Math.toDegrees(asin(ratio))
            hookToLoad = length * publishedAf(cos(Math.toRadians(angle)))
            if (hookToLoad <= 0) {
                throw SlingInputError(
                    "The 4-leg bridle needs a smaller pick-point rectangle or a " +
                        "longer sling to provide headroom."
                )
            }
            shapeNote = "Four legs share the load from the four corners of the " +
                "pick-point rectangle."
        }

        spec.nested != 0 -> {
            val n = spec.nested
            val free2 = normalised.secondaryLength
            val spread = normalised.secondarySpread
            val currentOffsets = when (n) {
                2 -> listOf(spread / 2.0)
                3 -> listOf(0.0, spread)
                else -> listOf(spread / 2.0, 1.5 * spread)
            }
            offsets = currentOffsets
            val transAngles = mutableListOf<Double>()
            val transFactors = mutableListOf<Double>()
            for (offset in currentOffsets) {
                val ratioT = offset / free2
                if (ratioT > 1.0) {
                    throw SlingInputError(
                        "The secondary sling cannot reach the pile spacing entered."
                    )
                }
                transAngles.add(Math.toDegrees(asin(ratioT)))
                transFactors.add(publishedAf(cos(Math.toRadians(transAngles.last()))))
            }
            // Every secondary leg is cut to the same free length and they all meet
            // at one master link, so the pile tops they land on cannot be level.
            // The leg with the largest offset has the shortest drop, so the piles
            // beneath it are the highest, and that is also the leg group carrying
            // the highest tension.  Measure the secondary height to that governing
            // (outermost) group: it gives the shortest combined length, the largest
            // longitudinal angle and the lowest angle factor, so it is the
            // conservative reference for the master legs.
            governingIndex = currentOffsets.indices.maxByOrNull { currentOffsets[it] } ?: 0
            heightSling2 = free2 * transFactors[governingIndex]
            heightAngle = transAngles[governingIndex]
            // Spread between the highest and the lowest pile top under the link.
            pileLevelDifference = free2 * (transFactors.max() - transFactors.min())
            totalCombined = length + heightSling2
            val ratio = (normalised.pickDistance / 2.0) / totalCombined
            if (ratio > 1.0) {
                throw SlingInputError(
                    "The nested sling system cannot reach the hook with this " +
                        "master length and pick-point spread."
                )
            }
            angleLong = Math.toDegrees(asin(ratio))
            afLong = publishedAf(cos(Math.toRadians(angleLong)))
            // The reference documents multiply the published longitudinal and
            // transverse factors to reach the combined secondary-leg factor.
            val combined = transFactors.map { publishedCombinedAf(afLong!!, it) }
            val worstIndex = combined.indices.minByOrNull { combined[it] } ?: 0
            precomputedAngleFactor = afLong
            tensionOverride = weight / (2.0 * afLong)
            secondaryTension = weight / (spec.effectiveLegs * combined[worstIndex])
            secondaryRequiredWll = secondaryTension
            secondaryAngle = transAngles[worstIndex]
            secondaryAf = transFactors[worstIndex]
            // A nested system with more than one transverse offset reports every leg
            // group, outermost first, as the reference documents do.  The governing
            // group is the one with the largest offset.
            secondaryGroups = currentOffsets.indices
                .sortedBy { -currentOffsets[it] }
                .map { i ->
                    SecondaryGroup(
                        offset = currentOffsets[i],
                        angle = transAngles[i],
                        af = transFactors[i],
                        combinedAf = combined[i],
                        tension = weight / (spec.effectiveLegs * combined[i]),
                        governing = i == worstIndex,
                    )
                }
            angle = angleLong
            angleTrans = transAngles[worstIndex]
            afTrans = transFactors[worstIndex]
            // The headroom is a printed line multiplied by a printed factor, so it
            // is worked from the published total length, not the raw sum.
            hookToLoad = publishedLength(totalCombined) * afLong
            shapeNote = "A 2-leg master bridle splits into two secondary slings; " +
                "the highest secondary-leg tension is shown."
        }

        normalised.hitch == "two_leg_direct" -> {
            val halfSpan = normalised.pickDistance / 2.0
            val ratio = halfSpan / length
            if (ratio > 1.0) {
                throw SlingInputError(
                    "The 2-leg bridle cannot reach the hook with this sling length " +
                        "and pick-point distance."
                )
            }
            angle = Math.toDegrees(asin(ratio))
            hookToLoad = length * publishedAf(cos(Math.toRadians(angle)))
            if (hookToLoad <= 0) {
                throw SlingInputError(
                    "The 2-leg bridle needs a shorter pick-point distance or a " +
                        "longer sling to provide headroom."
                )
            }
            shapeNote = "Two legs share the load from the pick points."
        }

        normalised.hitch == "two_leg_round_choke" ||
            normalised.hitch == "two_leg_rect_choke" -> {
            val ext = external
            val int = internal
            if (ext == null || int == null) {
                throw SlingInputError("A 2-leg choke needs a choke angle.")
            }
            if (normalised.hitch == "two_leg_round_choke") {
                val radius = normalised.diameter / 2.0
                wrap = PI * normalised.diameter
                lead = length - wrap
                freeLeg = lead!!
                if (lead <= 0) {
                    throw SlingInputError(
                        "The sling is too short to complete the 2-leg round choke; " +
                            "at least ${fixed(wrap, 3)} m is required."
                    )
                }
                chokeToLoad = if (ext >= 90.0) {
                    maxOf(0.0, radius / sin(Math.toRadians(int)) - radius)
                } else {
                    0.0
                }
                shapeNote = "Two legs choke the round load on a full wrap."
            } else {
                cornerLength = (normalised.bottom / 2.0) / sin(Math.toRadians(int))
                wrap = normalised.bottom + 2.0 * normalised.side + 2.0 * cornerLength!!
                lead = length - wrap
                freeLeg = lead!!
                if (lead <= 0) {
                    throw SlingInputError(
                        "The sling is too short for the 2-leg rectangular choke " +
                            "and flare; at least ${fixed(wrap, 3)} m is required."
                    )
                }
                chokeToLoad = (normalised.bottom / 2.0) / tan(Math.toRadians(int))
                if (ext < 90.0) chokeToLoad = 0.0
                shapeNote = "Two legs choke the rectangular load at the corner strands."
            }
            val verticalSpan = freeLeg + (chokeToLoad ?: 0.0)
            val ratio = (normalised.pickDistance / 2.0) / verticalSpan
            if (ratio > 1.0) {
                throw SlingInputError(
                    "The 2-leg choke cannot reach the hook with this sling length " +
                        "and pick-point distance."
                )
            }
            angle = Math.toDegrees(asin(ratio))
            hookToLoad = freeLeg * cos(Math.toRadians(angle))
            if (hookToLoad <= 0) {
                throw SlingInputError(
                    "The 2-leg choke needs more free leg to provide headroom above " +
                        "the load."
                )
            }
        }

        normalised.hitch == "two_leg_round_basket" ||
            normalised.hitch == "two_leg_vertical_round_basket" ||
            normalised.hitch == "two_leg_rect_basket" ||
            normalised.hitch == "two_leg_vertical_rect_basket" -> {
            val vertical = normalised.hitch.startsWith("two_leg_vertical")
            var headroom: Double
            if (normalised.hitch.endsWith("round_basket")) {
                val radius = normalised.diameter / 2.0
                wrap = PI * radius
                val remaining = length - wrap
                if (remaining <= 0) {
                    throw SlingInputError(
                        "The sling is too short to wrap around the round load; " +
                            "at least ${fixed(wrap, 3)} m is required."
                    )
                }
                freeLeg = remaining / (spec.effectiveLegs.toDouble() / spec.legCount)
                angleLong = if (vertical) {
                    0.0
                } else {
                    Math.toDegrees(asin((normalised.pickDistance / 2.0) / freeLeg))
                }
                angleTrans = Math.toDegrees(asin(radius / freeLeg))
                headroom = freeLeg * publishedCombinedAf(
                    cos(Math.toRadians(angleLong!!)), cos(Math.toRadians(angleTrans)),
                ) - radius
                if (headroom <= 0) {
                    throw SlingInputError(
                        "The 2-leg round basket needs more free leg to provide " +
                            "headroom above the load."
                    )
                }
                shapeNote = "Two basket slings cradle the round load."
            } else {
                wrap = normalised.bottom + 2.0 * normalised.side
                val remaining = length - wrap
                if (remaining <= 0) {
                    throw SlingInputError(
                        "The sling is too short to wrap around the rectangular " +
                            "load; at least ${fixed(wrap, 3)} m is required."
                    )
                }
                freeLeg = remaining / (spec.effectiveLegs.toDouble() / spec.legCount)
                angleLong = if (vertical) {
                    0.0
                } else {
                    Math.toDegrees(asin((normalised.pickDistance / 2.0) / freeLeg))
                }
                angleTrans = Math.toDegrees(asin((normalised.bottom / 2.0) / freeLeg))
                headroom = freeLeg * publishedCombinedAf(
                    cos(Math.toRadians(angleLong!!)), cos(Math.toRadians(angleTrans)),
                )
                if (vertical) headroom -= normalised.side
                if (headroom <= 0) {
                    throw SlingInputError(
                        "The 2-leg rectangular basket needs more free leg to " +
                            "provide headroom above the load."
                    )
                }
                shapeNote = "Two basket slings cradle the rectangular load."
            }
            angle = angleLong!!
            hookToLoad = headroom
            afLong = publishedAf(cos(Math.toRadians(angleLong)))
            afTrans = publishedAf(cos(Math.toRadians(angleTrans)))
            precomputedAngleFactor = publishedCombinedAf(afLong, afTrans)
            if (precomputedAngleFactor <= 0) {
                throw SlingInputError("The sling angle is too close to horizontal.")
            }
        }

        normalised.hitch == "round_basket" ||
            normalised.hitch == "vertical_round_basket" -> {
            val radius = normalised.diameter / 2.0
            wrap = PI * radius
            val remaining = length - wrap
            if (remaining <= 0) {
                throw SlingInputError(
                    "The sling is too short to wrap around the round load; " +
                        "at least ${fixed(wrap, 3)} m is required."
                )
            }
            freeLeg = remaining / 2.0
            if (normalised.hitch == "round_basket") {
                val ratio = radius / freeLeg
                if (ratio > 1.0) {
                    throw SlingInputError(
                        "The round basket cannot reach the hook with this length."
                    )
                }
                angle = Math.toDegrees(asin(ratio))
                hookToLoad = freeLeg * publishedAf(cos(Math.toRadians(angle))) - radius
                if (hookToLoad <= 0) {
                    throw SlingInputError(
                        "The round basket needs more free leg to provide headroom above the load."
                    )
                }
                shapeNote = "The sling cradles half of the round load."
            } else {
                angle = 0.0
                hookToLoad = freeLeg - radius
                if (hookToLoad <= 0) {
                    throw SlingInputError(
                        "The vertical round basket needs more free leg to clear the load."
                    )
                }
                shapeNote = "The two legs rise vertically to a spreader bar."
            }
        }

        normalised.hitch == "rect_basket" ||
            normalised.hitch == "vertical_rect_basket" -> {
            wrap = normalised.bottom + 2.0 * normalised.side
            val remaining = length - wrap
            if (remaining <= 0) {
                throw SlingInputError(
                    "The sling is too short to wrap around the rectangular load; " +
                        "at least ${fixed(wrap, 3)} m is required."
                )
            }
            freeLeg = remaining / 2.0
            if (normalised.hitch == "rect_basket") {
                val ratio = (normalised.bottom / 2.0) / freeLeg
                if (ratio > 1.0) {
                    throw SlingInputError(
                        "The rectangular basket cannot reach the hook with this length."
                    )
                }
                angle = Math.toDegrees(asin(ratio))
                hookToLoad = freeLeg * publishedAf(cos(Math.toRadians(angle)))
                if (hookToLoad <= 0) {
                    throw SlingInputError(
                        "The rectangular basket needs more free leg to provide headroom " +
                            "above the load."
                    )
                }
                shapeNote = "The sling cradles the bottom and two sides of the load."
            } else {
                angle = 0.0
                hookToLoad = freeLeg - normalised.side
                if (hookToLoad <= 0) {
                    throw SlingInputError(
                        "The vertical rectangular basket needs more free leg to clear the load."
                    )
                }
                shapeNote = "The two legs rise vertically to a spreader bar."
            }
        }

        normalised.hitch == "round_choke" -> {
            // The round choke uses a complete circumference.  The main lifting line
            // remains vertical, even when the choke's contact angle changes.
            val radius = normalised.diameter / 2.0
            wrap = PI * normalised.diameter
            lead = length - wrap
            freeLeg = lead!!
            if (lead <= 0) {
                throw SlingInputError(
                    "The sling is too short to complete the round choke; " +
                        "at least ${fixed(wrap, 3)} m is required."
                )
            }
            // For an external angle below 90 degrees the field rule in the
            // reference document declares the choke flush against the load.
            chokeToLoad = if (external!! >= 90.0) {
                maxOf(0.0, radius / sin(Math.toRadians(internal!!)) - radius)
            } else {
                0.0
            }
            hookToLoad = lead + (chokeToLoad ?: 0.0)
            angle = 0.0
            shapeNote = "The noose completes one full circumference around the load."
        }

        normalised.hitch == "rect_choke" -> {
            val int = internal
            val ext = external
            if (int == null || ext == null) {
                throw SlingInputError("A rectangular choke needs a choke angle.")
            }
            cornerLength = (normalised.bottom / 2.0) / sin(Math.toRadians(int))
            wrap = normalised.bottom + 2.0 * normalised.side + 2.0 * cornerLength!!
            lead = length - wrap
            freeLeg = lead!!
            if (lead <= 0) {
                throw SlingInputError(
                    "The sling is too short for the rectangular choke and flare; " +
                        "at least ${fixed(wrap, 3)} m is required."
                )
            }
            chokeToLoad = (normalised.bottom / 2.0) / tan(Math.toRadians(int))
            if (ext < 90.0) {
                // Match the field rule used in the round-choke examples when the
                // outside angle is below a right angle.
                chokeToLoad = 0.0
            }
            hookToLoad = lead + (chokeToLoad ?: 0.0)
            angle = 0.0
            shapeNote = "The two corner strands flare from the load to the choke point."
        }
    }

    // The report prints the angle factor at three decimals, and the reference
    // documents derive every printed force from that published factor, so the same
    // factor drives the results returned here.
    val angleFactor = publishedAf(precomputedAngleFactor ?: cos(Math.toRadians(angle)))
    if (angleFactor <= 0) {
        throw SlingInputError("The sling angle is too close to horizontal.")
    }

    val effectiveLegs = spec.effectiveLegs
    // One-leg arrangements are rated as one continuous sling and a 2-leg
    // arrangement as two slings.  A 3- or 4-leg bridle is rated for the case where
    // one leg does not take its share, so its tag rating is the N-1 leg case.
    val slingLegs = if (spec.legCount > 1) spec.legCount else 1
    val ratingLegs = if (spec.bridle) ratedLegs(slingLegs) else slingLegs
    val tensionEach = tensionOverride ?: (weight / (effectiveLegs * angleFactor))
    val combinedFactor = spec.baseReeveFactor * reduction
    val requiredWll = weight / (ratingLegs * combinedFactor * angleFactor)

    // In the reference reports the displayed applied tension is the resolved force
    // path.  The required tag WLL is a rating requirement, not a second force path,
    // so the two values are kept separate and explicit.
    val maxTension = if (secondaryTension != null) {
        maxOf(tensionEach, secondaryTension)
    } else {
        tensionEach
    }

    val tagWll = number(normalised.tagWll, "Configured tag WLL", nonNegative = true)
    var tagAngle = number(normalised.tagWllAngle, "Tag WLL angle")
    // A single leg hangs vertical and takes the tag's full rating, so it is rated at
    // no angle.  Enforced here rather than only in the form, so a caller cannot
    // derate a one-leg sling by a leftover 2-leg angle -- the second sling of a
    // nested system is a multi-leg sling of its own and does take its own rated angle.
    if (!spec.needsTagAngle) tagAngle = 0.0

    val secondaryTagWll = number(
        normalised.secondaryTagWll, "Secondary tag WLL", nonNegative = true,
    )
    val secondaryTagAngle = number(
        normalised.secondaryTagWllAngle, "Secondary tag WLL angle",
    )
    val tagAf = publishedAf(cos(Math.toRadians(tagAngle)))
    val secondaryTagAf = publishedAf(cos(Math.toRadians(secondaryTagAngle)))
    val legCapacity = legCapacity(tagWll, ratingLegs, tagAf)

    // The secondary slings of a nested system have their own tag rating.  Each
    // secondary sling is itself a 2-, 3- or 4-leg sling closing onto a master leg,
    // so the note's rule applies to it exactly as it does to a bridle of the same
    // size: a 3-leg second sling is rated on 2 legs and a 4-leg on 3.  The physical
    // leg count is separate -- it is the number of secondary legs hanging under one
    // master leg and it divides the load.
    val secondaryLegs = if (spec.nested != 0) effectiveLegs / spec.legCount else 0
    val secondaryRatingLegs = if (spec.nested != 0) ratedLegs(spec.nested) else 0

    val secondaryCapacity = legCapacity(secondaryTagWll, secondaryRatingLegs, secondaryTagAf)
    val utilisation = tagUtilisation(requiredWll, legCapacity)
    val secondaryUtilisation = tagUtilisation(secondaryRequiredWll ?: 0.0, secondaryCapacity)

    val equipmentOk: Boolean? = if (legCapacity != null) {
        utilisation != null && utilisation <= 100.0
    } else {
        null
    }
    val equipmentStatus = if (legCapacity == null) {
        "No tag WLL entered"
    } else if (equipmentOk == true) {
        "PASS \u2014 configured WLL meets the requirement"
    } else {
        "FAIL \u2014 configured WLL is below the requirement"
    }

    val secondaryEquipmentOk: Boolean? = if (secondaryCapacity == null) {
        null
    } else {
        (secondaryUtilisation ?: 0.0) <= 100.0
    }
    val secondaryEquipmentStatus = if (secondaryCapacity == null) {
        "No secondary tag WLL entered"
    } else if ((secondaryUtilisation ?: 0.0) <= 100.0) {
        "PASS \u2014 secondary tag WLL meets the requirement"
    } else {
        "FAIL \u2014 secondary tag WLL is below the requirement"
    }

    return SlingResult(
        hitch = normalised.hitch,
        hitchLabel = spec.label,
        shape = spec.shape,
        diameter = normalised.diameter,
        side = normalised.side,
        bottom = normalised.bottom,
        pickDistance = normalised.pickDistance,
        pickLength = normalised.pickLength,
        pickWidth = normalised.pickWidth,
        secondaryLength = normalised.secondaryLength,
        secondarySpread = normalised.secondarySpread,
        nested = spec.nested,
        chokeAngle = normalised.chokeAngle,
        angleBasis = normalised.angleBasis,
        loadWeight = weight,
        slingLength = length,
        wrapLength = wrap,
        freeLegLength = freeLeg,
        angle = angle,
        angleLong = angleLong,
        angleTrans = angleTrans,
        afLong = afLong,
        afTrans = afTrans,
        angleFactor = angleFactor,
        baseReeveFactor = spec.baseReeveFactor,
        chokeReductionFactor = reduction,
        combinedReeveFactor = combinedFactor,
        externalAngle = external,
        internalAngle = internal,
        effectiveLegs = effectiveLegs,
        slingLegs = slingLegs,
        tensionEach = tensionEach,
        maxTension = maxTension,
        maxTensionBasis = "equal_to_tension_each",
        requiredWll = requiredWll,
        ratingLegs = ratingLegs,
        tagWllAngle = tagAngle,
        tagAngleFactor = tagAf,
        legCapacity = legCapacity,
        secondaryTagWll = secondaryTagWll,
        secondaryTagWllAngle = secondaryTagAngle,
        secondaryTagAngleFactor = secondaryTagAf,
        secondaryLegs = secondaryLegs.takeIf { it != 0 },
        secondaryRatingLegs = secondaryRatingLegs.takeIf { it != 0 },
        secondaryLegCapacity = secondaryCapacity,
        secondaryUtilisation = secondaryUtilisation,
        secondaryTension = secondaryTension,
        secondaryGroups = secondaryGroups,
        secondaryRequiredWll = secondaryRequiredWll,
        secondaryAngle = secondaryAngle,
        secondaryAf = secondaryAf,
        secondaryHeight = if (spec.nested != 0) heightSling2 else null,
        secondaryHeightAngle = if (spec.nested != 0) heightAngle else null,
        pileLevelDifference = if (spec.nested != 0 && spec.nested > 2) {
            pileLevelDifference
        } else {
            null
        },
        secondaryHeightGoverningOffset = if (spec.nested != 0 && spec.nested > 2) {
            offsets?.get(governingIndex)
        } else {
            null
        },
        totalCombinedLength = if (spec.nested != 0) totalCombined else null,
        secondaryCombinedAf = if (secondaryAf != null && afLong != null) {
            publishedCombinedAf(afLong, secondaryAf)
        } else {
            null
        },
        // Reserved distinction between a value printed by a reference document and
        // a value calculated by the model.  The current one-leg reports do not print
        // a separate maximum-tension row.
        documentMaxTension = null,
        documentRequiredWll = requiredWll,
        tagWll = tagWll,
        utilisation = utilisation,
        secondaryEquipmentStatus = secondaryEquipmentStatus,
        secondaryEquipmentOk = secondaryEquipmentOk,
        equipmentStatus = equipmentStatus,
        equipmentOk = equipmentOk,
        chokeToLoad = chokeToLoad,
        hookToLoad = hookToLoad,
        lead = lead,
        cornerLength = cornerLength,
        shapeNote = shapeNote,
        formula = SlingFormula(
            wrap = wrap,
            freeLeg = freeLeg,
            angle = angle,
            hookToLoad = hookToLoad,
            tensionEach = tensionEach,
            maxTension = maxTension,
            requiredWll = requiredWll,
        ),
    )
}

/** Convenience helper used by tests and the example loader. */
@Throws(SlingInputError::class)
fun solveDefaults(hitch: String, loadWeight: Double): SlingResult =
    solveOneLeg(exampleInputs(hitch), loadWeight)

/** A figure at a fixed number of decimals, with a dot decimal separator. */
private fun fixed(value: Double, decimals: Int): String =
    String.format(Locale.US, "%.${decimals}f", value)
