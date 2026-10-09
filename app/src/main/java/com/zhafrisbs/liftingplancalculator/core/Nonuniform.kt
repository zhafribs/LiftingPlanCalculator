package com.zhafrisbs.liftingplancalculator.core

import java.util.Locale
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Nonuniform lifts, ported from `lifting_calculator/nonuniform.py`.
 *
 * A *nonuniform* lift is one where the two sling legs do not share the load
 * equally.  The Uniform Load tab assumes each leg of a multi-leg hitch carries the
 * same share; that assumption fails as soon as the centre of gravity sits off the
 * midpoint of the pick points.
 *
 * Three scenarios are covered, and the third is a different model rather than a
 * variant of the first two:
 *
 *  * **2-Leg Asymmetric (Shortening Grab Hook)** — two legs sharing one headroom
 *    with the COG off the midpoint.
 *  * **Tandem, Aligned COG Pivot** and **Tandem, Repositioned Top Lifting Point** —
 *    two *independent cranes* sharing the load, with a declared tilt condition.
 *
 * `ratedLegs` and the two rounding constants are imported from [Sling] rather than
 * restated, so this tab and the Uniform Load tab cannot disagree about the rule the
 * reference documents state as *"If 4-Leg Sling, use Sling Legs = 3"*.
 */
class NonuniformInputError(message: String) : IllegalArgumentException(message)

/** A scenario's identity in the tab's list. */
data class ScenarioSpec(val key: String, val label: String, val description: String)

/**
 * Where crane 2's lifting lug sits, and what that changes.
 *
 * The two tandem methods take the same five numbers and differ in exactly one
 * place: how crane 2's projected distance is resolved once the cargo tilts.  That
 * follows from where lug 2 is welded on the load, and it is not a detail of
 * presentation — it changes the span, it changes which crane the load runs onto,
 * and it changes what has to be rated, because the two lug positions do not
 * tolerate the same rotation.
 */
data class TandemMethod(
    val key: String,
    val label: String,
    val fullLabel: String,
    /** The second method's projected distance, in words, for the report. */
    val rule: String,
    val lug2: String,
    val spanBehaviour: String,
    val maxRotation: String,
    val useFor: String,
    /**
     * Whether the method can be carried to the full 90 degrees of vertical
     * rotation.  It can only do so while the COG stays between the two hooks,
     * which is what lets crane 2 end up carrying the whole load.
     */
    val fullRotation: Boolean,
)

/** The two tandem methods, in the order the tab offers them. */
val TANDEM_METHODS: Map<String, TandemMethod> = listOf(
    TandemMethod(
        key = "tandem_aligned_pivot",
        label = "1. Aligned COG Pivot",
        fullLabel = "Tandem Lift (Aligned COG Pivot)",
        rule = "ND2 = D2 x cos(theta)",
        lug2 = "Welded on the side or inside of the frame, inline with the " +
            "horizontal COG axis.",
        spanBehaviour = "Stays wide and stable; the hooks do not have to " +
            "migrate inward as the load rotates.",
        maxRotation = "Full 90 degrees vertical rotation.",
        useFor = "Up-ending and load rotation manoeuvres: precast concrete " +
            "columns, vessels, storage tanks, wind tower sections.",
        fullRotation = true,
    ),
    TandemMethod(
        key = "tandem_top_lug",
        label = "2. Repositioned Top Lifting Point",
        fullLabel = "Tandem Lift (Repositioned Top Lifting Point)",
        // The document's header writes D2 - Y*tan(theta)*cos(theta), but its own
        // working is (D2 - Y*tan(theta))*cos(theta).  Only the second reproduces
        // its printed span, tensions and required WLL, so that is what is solved.
        rule = "ND2 = (D2 - Y x tan(theta)) x cos(theta)",
        lug2 = "Welded on the top surface face of the cargo, sitting above the " +
            "COG level.",
        spanBehaviour = "Compresses and narrows as the load tilts, which " +
            "shortens crane 2's balancing lever arm quickly.",
        maxRotation = "Limited; the span reaches zero at the angle where " +
            "tan(theta) = D2 / Y.",
        useFor = "Level-shifting and drift lifts where the load can never go " +
            "vertical: skids, bridge trusses, pipe rack modules.",
        fullRotation = false,
    ),
).associateBy { it.key }

/** The tandem methods, in the order the tab lists them. */
val TANDEM_METHOD_ORDER: List<String> = TANDEM_METHODS.keys.toList()

/**
 * Every scenario the model can solve, in tab order.
 *
 * The tandem case is a different model with two independent crane paths and a
 * declared tilt condition, not a variant of the asymmetric bridle, so it carries
 * its own solver and its own inputs rather than extra fields on the bridle.
 */
val SCENARIO_SPECS: Map<String, ScenarioSpec> = listOf(
    ScenarioSpec(
        "asym_2leg_shortening",
        "2-Leg Asymmetric (Shortening Grab Hook)",
        "Two legs sharing one headroom with the COG off the midpoint; the " +
            "shorter leg is shortened with a clevis grab hook.",
    ),
    ScenarioSpec(
        TANDEM_METHODS.getValue("tandem_aligned_pivot").key,
        TANDEM_METHODS.getValue("tandem_aligned_pivot").fullLabel,
        "Two independent cranes sharing the load. Lug 2 is inline with the COG, " +
            "so the span holds while the load rotates and can be carried to the " +
            "full 90 degrees of vertical rotation.",
    ),
    ScenarioSpec(
        TANDEM_METHODS.getValue("tandem_top_lug").key,
        TANDEM_METHODS.getValue("tandem_top_lug").fullLabel,
        "Two independent cranes sharing the load. Lug 2 is on the top surface " +
            "above the COG, so the span closes as the load tilts and the load runs " +
            "onto crane 2 far faster.",
    ),
).associateBy { it.key }

/** Every scenario the model can solve, in tab order. */
val SCENARIO_ORDER: List<String> = listOf("asym_2leg_shortening") + TANDEM_METHOD_ORDER

/** The tandem method a scenario key names. */
fun tandemMethod(scenario: String): TandemMethod = TANDEM_METHODS[scenario]
    ?: throw NonuniformInputError(
        "'$scenario' is not a tandem method: it is ${TANDEM_METHOD_ORDER.joinToString(", ")}"
    )

/**
 * The leg counts a sling tag can be built with, and so the choices the form offers.
 *
 * **1 is a single sling leg, not a one-leg bridle**, and it is first because it is
 * the ordinary case — each hook path of this lift carries its own separately rated
 * sling.  A single leg hangs vertical, so it takes its tag's full rating and has no
 * tag angle to derate it: see [effectiveTagAngle].  2, 3 and 4 are the bridles
 * [ratedLegs] is stated for.
 */
val SLING_LEG_OPTIONS: List<Int> = listOf(1, 2, 3, 4)

/**
 * The tag angle that applies to a tag of [legs] legs.
 *
 * A single leg hangs vertical, so there is no angle at which to rate it: it takes
 * its tag's full rating.  Forcing zero here rather than only hiding the field in the
 * UI is load-bearing — a 45 deg left over from before the operator chose a single
 * leg would quietly derate the capacity by a third, with nothing on screen to
 * explain it.  The same rule the Uniform Load tab applies when an arrangement needs
 * no tag angle.
 */
fun effectiveTagAngle(legs: Int, angle: Double): Double =
    if (legs == SINGLE_LEG) 0.0 else angle

/** The scenario's identity, or a named failure. */
fun scenarioSpec(scenario: String): ScenarioSpec = SCENARIO_SPECS[scenario]
    ?: throw NonuniformInputError("Unknown nonuniform scenario: $scenario")

/**
 * User-entered values for a nonuniform lift.
 *
 * [cogFromPick1] is the horizontal distance from pick point 1 to the centre of
 * gravity.  It is not forced to be the larger of the two spans: the solver
 * identifies which leg is long and shortens the other, so the user may measure the
 * COG from either pick point.
 *
 * [pickPoint2Height] is the step between the two pick points, and it is signed:
 * **positive means pick point 2 sits higher than pick point 1**, negative means it
 * sits lower.  The sign is not cosmetic.  The long leg fixes the hook's height above
 * its own pick point, and the other leg's drop to the far pick point follows from
 * the step, so raising that pick point *shortens* the drop and therefore *lengthens*
 * the leg the operator has to shorten.  The document states the rule as
 * `Y2 = Y1 - Height Difference`; entering a negative value is how you say "pick
 * point 2 is lower" rather than "there is no step".
 *
 * **These defaults are the blank form, not the worked example.**  Every measurement
 * opens at zero; the tag angles stay at 45 degrees because that is the ordinary
 * figure.  The documents' inputs are in [EXAMPLE_INPUTS], stated in full so that
 * adjusting what a fresh form looks like cannot move a printed figure.
 */
data class NonuniformInputs(
    val scenario: String = "asym_2leg_shortening",
    val slingLength: Double = 0.0,
    val pickDistance: Double = 0.0,
    val cogFromPick1: Double = 0.0,
    val pickPoint2Height: Double = 0.0,
    /**
     * 1.0 rather than 0.0 like the rest, and it is the one exception that is not an
     * angle: the reeve factor **divides** the required rating, so zero is not a
     * value the solver accepts at all — a blank form with 0 here would open on a
     * refusal for a field the operator has no reason to touch.  1.0 is the factor's
     * own neutral ("no reeve loss to account for").
     */
    val baseReeveFactor: Double = 1.0,
    val tagWll1: Double = 0.0,
    val tagWll1Angle: Double = 45.0,
    val slingLegs1: Int = SINGLE_LEG,
    val tagWll2: Double = 0.0,
    val tagWll2Angle: Double = 45.0,
    val slingLegs2: Int = SINGLE_LEG,
    // Tandem lift (two independent cranes).  `distA` and `distB` are the level
    // horizontal distances from each hook to the centre of gravity, so the total
    // span between the hooks is their sum rather than a third measurement.
    // `cogHeight` is how far the COG sits above the baseline joining the two pick
    // points, and it is what lets the tilt shift the COG sideways instead of just
    // rotating the load about it.
    val distA: Double = 0.0,
    val distB: Double = 0.0,
    val cogHeight: Double = 0.0,
    val tiltAngle: Double = 0.0,
    // Per-crane installed ratings.  A tandem lift is carried by two separate
    // cranes, so each hook path is checked against its own sling tag and its own
    // crane rating; neither is shared with the other.
    val tagWllA: Double = 0.0,
    val tagWllAAngle: Double = 45.0,
    val slingLegsA: Int = SINGLE_LEG,
    val tagWllB: Double = 0.0,
    val tagWllBAngle: Double = 45.0,
    val slingLegsB: Int = SINGLE_LEG,
    val craneRatedA: Double = 0.0,
    val craneRatedB: Double = 0.0,
)

/**
 * The documents' worked cases.
 *
 * **Every field these examples care about is stated out loud, and that is
 * load-bearing.**  These are the *documents'* inputs, and the defaults above are
 * now the blank form the app opens on.  An example that inherited a default would
 * silently move the moment someone adjusted what a fresh form looks like — which is
 * exactly what happened when the defaults were zeroed.
 */
val EXAMPLE_NONUNIFORM_INPUTS: Map<String, NonuniformInputs> = mapOf(
    // The document's worked case: 2 t over a 5.0 m span with the COG 3.5 m from
    // pick point 1, and pick point 2 sitting 0.5 m higher than pick point 1.
    "asym_2leg_shortening" to NonuniformInputs(
        scenario = "asym_2leg_shortening", slingLength = 7.0, pickDistance = 5.0,
        cogFromPick1 = 3.5, pickPoint2Height = 0.5, tiltAngle = 35.0,
    ),
    // Both tandem documents work the same case: a 60 t load, the COG 3 m from
    // crane 1 and 5 m from crane 2, 2 m below the lifting point baseline, at a
    // 35 degree tilt.  Only the lug position differs between them.
    "tandem_aligned_pivot" to NonuniformInputs(
        scenario = "tandem_aligned_pivot", slingLength = 7.0, pickDistance = 5.0,
        cogFromPick1 = 3.5, distA = 3.0, distB = 5.0, cogHeight = 2.0, tiltAngle = 35.0,
    ),
    "tandem_top_lug" to NonuniformInputs(
        scenario = "tandem_top_lug", slingLength = 7.0, pickDistance = 5.0,
        cogFromPick1 = 3.5, distA = 3.0, distB = 5.0, cogHeight = 2.0, tiltAngle = 35.0,
    ),
)

/** The documents' inputs for a scenario. */
fun exampleNonuniformInputs(scenario: String): NonuniformInputs =
    EXAMPLE_NONUNIFORM_INPUTS[scenario]
        ?: throw NonuniformInputError("No example for scenario: $scenario")

/** A number, or a named failure an operator can act on. */
private fun number(value: Double, name: String, positive: Boolean = false): Double {
    if (!value.isFinite()) throw NonuniformInputError("$name must be a finite number.")
    if (positive && value <= 0) {
        throw NonuniformInputError("$name must be greater than zero.")
    }
    return value
}

/**
 * Capacity of one tagged sling leg after its tag angle and leg count.
 *
 * The reference documents state the rule as `Tag WLL / (Sling Legs x
 * cos(Tag Angle))` — 5,300 kg / (2 x 0.707) = 3,748.2 kg — with the leg count
 * coming from [ratedLegs].
 *
 * This is **not** [tagUtilisation] from the sling module.  The two differ on
 * purpose: the uniform engine divides the *printed* figures, while this module
 * publishes the exact quotient.  Using one for the other would move the last digit
 * of every utilisation here.
 */
private fun tagUtilisationExact(requiredWll: Double, capacity: Double?): Double? {
    if (capacity == null || capacity == 0.0) return null
    return requiredWll / capacity * 100.0
}

// ---------------------------------------------------------------------------
// Results
// ---------------------------------------------------------------------------

/** What every scenario reports, so the shared rows have one home. */
sealed interface NonuniformResult {
    val scenario: String
    val scenarioLabel: String
    val loadWeight: Double
    val baseReeveFactor: Double
    val maxTension: Double
    val requiredWll: Double
    val equipmentOk: Boolean?
    /** The document's setup SWL: the whole load, not a per-leg share. */
    val setupSwl: Double
}

/** The 2-leg asymmetric bridle. */
data class Asym2LegResult(
    override val scenario: String,
    override val scenarioLabel: String,
    override val loadWeight: Double,
    override val baseReeveFactor: Double,
    val slingLength: Double,
    val pickDistance: Double,
    val cogFromPick1: Double,
    val pickPoint2Height: Double,
    val span1: Double,
    val span2: Double,
    val longLeg: Int,
    val shortLeg: Int,
    val headroom: Double,
    val drop1: Double,
    val drop2: Double,
    val shortLegDrop: Double,
    val shortLegLength: Double,
    val shortening: Double,
    val shorteningMm: Double,
    val needsLengthening: Boolean,
    val lengthening: Double,
    val length1: Double,
    val length2: Double,
    val angle1: Double,
    val angle2: Double,
    val angleFactor1: Double,
    val angleFactor2: Double,
    val share1: Double,
    val share2: Double,
    val tension1: Double,
    val tension2: Double,
    val requiredWll1: Double,
    val requiredWll2: Double,
    override val maxTension: Double,
    override val requiredWll: Double,
    val governingLeg: Int,
    val tagWll1: Double,
    val tagWll1Angle: Double,
    val tagAngleFactor1: Double,
    val slingLegs1: Int,
    val ratedLegs1: Int,
    val legCapacity1: Double?,
    val utilisation1: Double?,
    val tagWll2: Double,
    val tagWll2Angle: Double,
    val tagAngleFactor2: Double,
    val slingLegs2: Int,
    val ratedLegs2: Int,
    val legCapacity2: Double?,
    val utilisation2: Double?,
    override val equipmentOk: Boolean?,
    override val setupSwl: Double,
) : NonuniformResult

/** The two-crane tandem lift. */
data class TandemResult(
    override val scenario: String,
    override val scenarioLabel: String,
    override val loadWeight: Double,
    override val baseReeveFactor: Double,
    val methodKey: String,
    val methodLabel: String,
    val methodFullLabel: String,
    val methodRule: String,
    val methodLug2: String,
    val methodSpanBehaviour: String,
    val methodMaxRotation: String,
    val methodUseFor: String,
    val fullRotation: Boolean,
    val distA: Double,
    val distB: Double,
    val span: Double,
    val cogHeight: Double,
    val tiltAngle: Double,
    val angle: Double,
    val angleFactor: Double,
    val distANew: Double,
    val distBNew: Double,
    val spanNew: Double,
    val cogShift: Double,
    val lug2Travel: Double,
    val heightDifference: Double,
    val maxTiltAngle: Double,
    val shareALevel: Double,
    val shareBLevel: Double,
    val tensionALevel: Double,
    val tensionBLevel: Double,
    val requiredALevel: Double,
    val requiredBLevel: Double,
    val shareATilt: Double,
    val shareBTilt: Double,
    val tensionATilt: Double,
    val tensionBTilt: Double,
    val requiredATilt: Double,
    val requiredBTilt: Double,
    val shareAVertical: Double?,
    val shareBVertical: Double?,
    val tensionAVertical: Double?,
    val tensionBVertical: Double?,
    val requiredAVertical: Double?,
    val requiredBVertical: Double?,
    val requiredWllA: Double,
    val requiredWllB: Double,
    val governingCaseA: String,
    val governingCaseB: String,
    override val maxTension: Double,
    override val requiredWll: Double,
    val tagWllA: Double,
    val tagWllAAngle: Double,
    val tagAngleFactorA: Double,
    val slingLegsA: Int,
    val ratedLegsA: Int,
    val legCapacityA: Double?,
    val utilisationA: Double?,
    val tagWllB: Double,
    val tagWllBAngle: Double,
    val tagAngleFactorB: Double,
    val slingLegsB: Int,
    val ratedLegsB: Int,
    val legCapacityB: Double?,
    val utilisationB: Double?,
    val craneRatedA: Double,
    val craneRatedB: Double,
    val craneUsageA: Double?,
    val craneUsageB: Double?,
    val craneUsageStateA: UsageState?,
    val craneUsageStateB: UsageState?,
    override val equipmentOk: Boolean?,
    /**
     * True only when every entered crane sits outside the 75% window, so a single
     * figure cannot stand in for a check that covers both cranes.
     */
    val craneRiskFlag: Boolean?,
    override val setupSwl: Double,
) : NonuniformResult

// ---------------------------------------------------------------------------
// The scenarios
// ---------------------------------------------------------------------------

/** The 2-leg asymmetric bridle with a clevis shortening grab hook. */
@Throws(NonuniformInputError::class)
fun solveAsym2Leg(inputs: NonuniformInputs, loadWeight: Double): Asym2LegResult {
    val slingLength = number(inputs.slingLength, "Sling length", positive = true)
    val span = number(inputs.pickDistance, "Distance between pick points", positive = true)
    val cog = number(inputs.cogFromPick1, "COG position from pick point 1")
    val reeve = number(inputs.baseReeveFactor, "Base reeve factor", positive = true)
    val load = number(loadWeight, "Load weight")

    if (load < 0) throw NonuniformInputError("Load weight cannot be negative.")
    if (!(cog > 0.0 && cog < span)) {
        throw NonuniformInputError(
            "The COG must sit between the two pick points: enter a position " +
                "greater than 0 m and less than the ${thousands(span, 3)} m span."
        )
    }

    // Statics: the load share follows the moment about the opposite pick point, so
    // the leg on the short side of the COG carries the larger share.
    val x1 = cog
    val x2 = span - cog
    val share1 = load * x2 / span
    val share2 = load * x1 / span

    // Geometry: the longer horizontal run keeps the full standard length, and that
    // leg alone sets the hook's height.  The other pick point sits a signed step
    // away, so its drop follows from the step — which is the whole of the
    // document's Y2 = Y1 - Height Difference rule, applied to whichever pick point
    // happens to be the far one rather than assuming pick point 2 is.
    //
    // Each step is published before the next one uses it, which is the order the
    // document works in and the reason its last digits are reproducible.
    val longIs1 = x1 >= x2
    val xLong = max(x1, x2)
    val xShort = min(x1, x2)
    if (slingLength <= xLong) {
        throw NonuniformInputError(
            "The ${thousands(slingLength, 3)} m sling is too short to reach " +
                "${thousands(xLong, 3)} m across. Use a longer sling or a shorter span."
        )
    }

    val step = number(inputs.pickPoint2Height, "Height difference between pick points")
    val dropLong = publishedLength(sqrt(slingLength * slingLength - xLong * xLong))
    val drop1: Double
    val drop2: Double
    if (longIs1) {
        // Pick point 1 sets the drop; pick point 2 is `step` above it.
        drop1 = dropLong
        drop2 = publishedLength(dropLong - step)
    } else {
        // Pick point 2 sets the drop; pick point 1 is `step` below it.
        drop2 = dropLong
        drop1 = publishedLength(dropLong + step)
    }
    if (drop1 <= 0.0 || drop2 <= 0.0) {
        val raised = if (step > 0) "2" else "1"
        throw NonuniformInputError(
            "A ${thousands(abs(step), 3)} m step leaves pick point $raised at or " +
                "above hook level, so the sling would have to run uphill. Reduce " +
                "the step or use a longer sling."
        )
    }
    val dropShort = if (longIs1) drop2 else drop1
    val shortLength = publishedLength(sqrt(dropShort * dropShort + xShort * xShort))
    val shortening = publishedLength(slingLength - shortLength)

    val length1 = if (longIs1) slingLength else shortLength
    val length2 = if (longIs1) shortLength else slingLength

    val angle1 = Math.toDegrees(asin(x1 / length1))
    val angle2 = Math.toDegrees(asin(x2 / length2))
    val af1 = publishedAf(cos(Math.toRadians(angle1)))
    val af2 = publishedAf(cos(Math.toRadians(angle2)))

    // The document publishes the line tension from the angle factor alone and the
    // tag rating from the reeve factor as well.  They coincide at the published RF
    // of 1.00 and are kept separate so a reeved bridle stays honest about which
    // figure is the leg pull and which is the rating.
    val tension1 = share1 / af1
    val tension2 = share2 / af2
    val required1 = share1 / (reeve * af1)
    val required2 = share2 / (reeve * af2)

    val tagWll1 = number(inputs.tagWll1, "Sling 1 tag WLL")
    val tagWll2 = number(inputs.tagWll2, "Sling 2 tag WLL")
    // The operator states how many legs the tag is made of; the documents' rule
    // says what that rating is declared on, and it is the declared count that
    // divides.  Both are published so the report can name the choice and the rule
    // side by side — a form showing 4 beside a formula showing 3 reads as a fault
    // unless the report says why.
    val chosenLegs1 = number(inputs.slingLegs1.toDouble(), "Sling 1 sling legs").toInt()
    val chosenLegs2 = number(inputs.slingLegs2.toDouble(), "Sling 2 sling legs").toInt()
    val ratedLegs1 = ratedLegs(chosenLegs1)
    val ratedLegs2 = ratedLegs(chosenLegs2)
    // Resolved from the chosen count, not from the field the UI has just hidden.
    val tagAngle1 = effectiveTagAngle(
        chosenLegs1, number(inputs.tagWll1Angle, "Sling 1 tag WLL angle"),
    )
    val tagAngle2 = effectiveTagAngle(
        chosenLegs2, number(inputs.tagWll2Angle, "Sling 2 tag WLL angle"),
    )
    val tagAf1 = publishedAf(cos(Math.toRadians(tagAngle1)))
    val tagAf2 = publishedAf(cos(Math.toRadians(tagAngle2)))
    val capacity1 = legCapacity(tagWll1, ratedLegs1, tagAf1)
    val capacity2 = legCapacity(tagWll2, ratedLegs2, tagAf2)
    val utilisation1 = tagUtilisationExact(required1, capacity1)
    val utilisation2 = tagUtilisationExact(required2, capacity2)

    val governingLeg = if (required1 >= required2) 1 else 2
    // A clevis shortening grab hook can only take length *out* of a leg.  When the
    // step makes the far leg need more than the standard length there is nothing to
    // shorten, so the shortfall is reported as its own positive figure rather than
    // as a negative "shortening" that reads like an instruction to take out a
    // negative amount.
    val needsLengthening = shortening < 0.0

    val equipmentOk = if (utilisation1 == null || utilisation2 == null) {
        null
    } else {
        utilisation1 <= 100.0 && utilisation2 <= 100.0
    }

    return Asym2LegResult(
        scenario = inputs.scenario,
        scenarioLabel = scenarioSpec(inputs.scenario).label,
        loadWeight = load,
        slingLength = slingLength,
        pickDistance = span,
        cogFromPick1 = cog,
        pickPoint2Height = step,
        span1 = x1,
        span2 = x2,
        longLeg = if (longIs1) 1 else 2,
        shortLeg = if (longIs1) 2 else 1,
        headroom = dropLong,
        drop1 = drop1,
        drop2 = drop2,
        shortLegDrop = dropShort,
        shortLegLength = shortLength,
        shortening = shortening,
        shorteningMm = shortening * 1000.0,
        needsLengthening = needsLengthening,
        lengthening = if (needsLengthening) abs(shortening) else 0.0,
        length1 = length1,
        length2 = length2,
        angle1 = angle1,
        angle2 = angle2,
        angleFactor1 = af1,
        angleFactor2 = af2,
        baseReeveFactor = reeve,
        share1 = share1,
        share2 = share2,
        tension1 = tension1,
        tension2 = tension2,
        requiredWll1 = required1,
        requiredWll2 = required2,
        maxTension = max(tension1, tension2),
        requiredWll = max(required1, required2),
        governingLeg = governingLeg,
        tagWll1 = tagWll1,
        // The angle that applied, not the one that was typed: for a single leg the
        // field is hidden and 0 is what the capacity was computed from, so printing
        // the raw input would put an angle on the page that the form does not have.
        tagWll1Angle = tagAngle1,
        tagAngleFactor1 = tagAf1,
        slingLegs1 = chosenLegs1,
        ratedLegs1 = ratedLegs1,
        legCapacity1 = capacity1,
        utilisation1 = utilisation1,
        tagWll2 = tagWll2,
        tagWll2Angle = tagAngle2,
        tagAngleFactor2 = tagAf2,
        slingLegs2 = chosenLegs2,
        ratedLegs2 = ratedLegs2,
        legCapacity2 = capacity2,
        utilisation2 = utilisation2,
        equipmentOk = equipmentOk,
        // The document's setup SWL is the whole load, since the two legs are one
        // assembly rather than two separately rated slings.
        setupSwl = load,
    )
}

/**
 * Project each hook's distance to the COG after the tilt has been applied.
 *
 * Crane 1's lug is on the side of the load in both methods, so tilting it swings
 * the COG out by `Y*sin(theta)` and pulls its own reach in by `D1*cos(theta)`.
 * Crane 2 is where the two methods part company, and the difference is entirely
 * about where lug 2 is welded:
 *
 *  * Inline with the COG, lug 2 does not move relative to the centre of gravity, so
 *    only the projection shortens it: `D2*cos(theta)`.
 *  * On the top surface, above the COG, lug 2 first migrates inward by
 *    `Y*tan(theta)` as the load leans, and is then projected as well.
 *
 * Both documents publish these three decimals, and both take their shares from the
 * published figures rather than from the exact ones, so they are rounded here for
 * the same reason.
 */
fun tandemProjectedDistances(
    method: TandemMethod,
    distA: Double,
    distB: Double,
    cogHeight: Double,
    tilt: Double,
): Pair<Double, Double> {
    val radians = Math.toRadians(tilt)
    val cosT = cos(radians)
    val sinT = sin(radians)
    val distANew = publishedLength(distA * cosT + cogHeight * sinT)
    val distBNew = if (method.fullRotation) {
        publishedLength(distB * cosT)
    } else {
        publishedLength((distB - cogHeight * tan(radians)) * cosT)
    }
    return distANew to distBNew
}

/**
 * Solve the two-crane tandem lift at the level, tilted and vertical cases.
 *
 * The documents solve this in distinct steps and the order matters.  The level
 * baseline fixes where the load divides.  The tilt then moves the centre of gravity
 * sideways, because the COG sits below the baseline joining the two lifting points
 * rather than in it, and by how much depends on where lug 2 is welded.  Each crane
 * is then required to carry the highest of its own cases, which is not the same
 * crane in every case and is not always the same case: in the aligned pivot
 * reference lift the left crane is governed by the level case and the right crane
 * only once the load is stood right up on end.
 */
@Throws(NonuniformInputError::class)
fun solveTandem2Crane(inputs: NonuniformInputs, loadWeight: Double): TandemResult {
    val method = tandemMethod(inputs.scenario)
    val distA = number(inputs.distA, "Distance from crane 1 hook to COG", positive = true)
    val distB = number(inputs.distB, "Distance from crane 2 hook to COG", positive = true)
    val cogHeight = number(inputs.cogHeight, "COG depth below the baseline")
    val tilt = number(inputs.tiltAngle, "Cargo tilt angle")
    val reeve = number(inputs.baseReeveFactor, "Base reeve factor", positive = true)
    val load = number(loadWeight, "Load weight")

    if (load < 0) throw NonuniformInputError("Load weight cannot be negative.")
    if (cogHeight < 0) {
        throw NonuniformInputError(
            "The COG depth cannot be negative: measure it downward from the line " +
                "joining the two lifting points."
        )
    }
    if (!(tilt in 0.0..90.0)) {
        throw NonuniformInputError(
            "The cargo tilt angle must be between 0 and 90 degrees; " +
                "${thousands(tilt, 2)} was entered."
        )
    }

    // The span between the two hooks is the sum of the two COG distances: the COG
    // lies between the hooks on the level baseline, so it is not a third
    // independent measurement and asking for it separately would let the three
    // figures disagree.
    val span = publishedLength(distA + distB)

    // The angle beyond which the lift stops being a lift.  With lug 2 inline with
    // the COG the load can go all the way up on end, so the bound is the 90 degrees
    // it was already validated against.  With lug 2 on the top surface the span
    // closes at the angle where the lug's inward travel has eaten the whole of D2,
    // which is where tan(theta) = D2 / Y.
    val maxTilt = if (method.fullRotation || cogHeight == 0.0) {
        90.0
    } else {
        Math.toDegrees(atan(distB / cogHeight))
    }

    // Case A: the level baseline.  The document labels these 0 degrees and the
    // angle factors are exactly 1.000, because each sling stands vertical.
    val angle = 0.0
    val af = publishedAf(cos(Math.toRadians(angle)))
    val shareALevel = load * distB / span
    val shareBLevel = load * distA / span

    // Case B: the declared tilt.
    val (distANew, distBNew) = tandemProjectedDistances(method, distA, distB, cogHeight, tilt)
    // A tip-over is crane 2's hook having to pass to the far side of the COG, so the
    // projection closes past zero.  **What the boundary itself means depends on the
    // method, and that is the documents' distinction rather than a convenience:**
    //
    //  * Repositioned Top Lifting Point -- the span closing to nothing *is* the end
    //    of the method.  The document's own limit is "the span reaches zero at the
    //    angle where tan(theta) = D2 / Y", so zero is refused as well.
    //  * Aligned COG Pivot -- the projection closing to nothing at 90 degrees is
    //    Case C: the COG sits under crane 2's hook and the whole load hangs from it,
    //    which is the case that governs crane 2's sling.  This is the method that
    //    "can be carried to the full 90 degrees of vertical rotation", so zero is
    //    the answer there rather than a refusal.
    if (distBNew < 0.0 || (distBNew == 0.0 && !method.fullRotation)) {
        throw NonuniformInputError(
            "At a ${thousands(tilt, 2)} degree tilt with the COG " +
                "${thousands(cogHeight, 3)} m below the baseline, crane 2's " +
                "projected distance closes to nothing (the span runs out at " +
                "${fixed(maxTilt, 1)} degrees). The load would tip about crane 2 " +
                "instead of hanging from both hooks; reduce the tilt angle or the " +
                "COG depth."
        )
    }
    val spanNew = publishedLength(distANew + distBNew)
    val shareATilt: Double
    val shareBTilt: Double
    if (spanNew > 0.0) {
        shareATilt = load * distBNew / spanNew
        shareBTilt = load * distANew / spanNew
    } else {
        // Both projections are zero, which needs a zero COG depth **and** a 90
        // degree tilt: with the COG on the line joining the hooks the tilt cannot
        // move it at all, and at 90 degrees the ratio is 0/0.  Only the aligned
        // method can reach this (the top lifting point is refused at the boundary
        // just above).  The method's own vertical case answers it -- at vertical
        // the whole load hangs from crane 2, which is Case C below -- so that same
        // rule is applied rather than a second one invented for the boundary.
        shareATilt = 0.0
        shareBTilt = load
    }

    // Case C: the full 90 degrees of vertical rotation, which only the aligned
    // pivot method can be taken through.  Standing the load right up on end leaves
    // the whole of it hanging from crane 2, because crane 2's projected distance has
    // shrunk to nothing while the COG's own reach has not.  This is the case that
    // governs crane 2's sling, and it is the reason the two documents quote such
    // different required WLLs for the same 60 t load.  Both figures are null
    // together on the method that has no such case: publishing a 0.0 for crane 1
    // alone would be a number for a rotation that cannot happen, and a reader could
    // not tell it apart from a real one.
    val shareAVertical: Double?
    val shareBVertical: Double?
    val cases: List<Triple<String, Double, Double>>
    if (method.fullRotation) {
        shareAVertical = 0.0
        shareBVertical = load
        cases = listOf(
            Triple("level", shareALevel, shareBLevel),
            Triple("tilt", shareATilt, shareBTilt),
            Triple("vertical", 0.0, load),
        )
    } else {
        shareAVertical = null
        shareBVertical = null
        cases = listOf(
            Triple("level", shareALevel, shareBLevel),
            Triple("tilt", shareATilt, shareBTilt),
        )
    }

    // Each crane is governed by whichever of its own cases demands most.  For the
    // aligned pivot method this needs no sweep of the rotation range: the share on
    // crane 1 falls monotonically as the load goes up on end, so the two ends of the
    // range bound everything between them.
    fun governing(index: Int): Pair<String, Double> {
        val best = cases.maxByOrNull { if (index == 0) it.second else it.third }!!
        return best.first to (if (index == 0) best.second else best.third)
    }

    val (governingA, shareA) = governing(0)
    val (governingB, shareB) = governing(1)

    fun caseFigures(share: Double): Pair<Double, Double> =
        (share / af) to (share / (reeve * af))

    val (tensionALevel, requiredALevel) = caseFigures(shareALevel)
    val (tensionBLevel, requiredBLevel) = caseFigures(shareBLevel)
    val (tensionATilt, requiredATilt) = caseFigures(shareATilt)
    val (tensionBTilt, requiredBTilt) = caseFigures(shareBTilt)
    // The governing tension is not published as its own figure -- only the required
    // WLL and the name of the case that produced it -- so the maximum tension is the
    // highest of the *named* cases rather than a fourth number with no case attached.
    val requiredA = caseFigures(shareA).second
    val requiredB = caseFigures(shareB).second

    val tensionAVertical: Double?
    val requiredAVertical: Double?
    val tensionBVertical: Double?
    val requiredBVertical: Double?
    if (shareBVertical != null) {
        val a = caseFigures(shareAVertical!!)
        val b = caseFigures(shareBVertical)
        tensionAVertical = a.first
        requiredAVertical = a.second
        tensionBVertical = b.first
        requiredBVertical = b.second
    } else {
        tensionAVertical = null
        requiredAVertical = null
        tensionBVertical = null
        requiredBVertical = null
    }

    val tagWllA = number(inputs.tagWllA, "Crane 1 sling tag WLL")
    val tagWllB = number(inputs.tagWllB, "Crane 2 sling tag WLL")
    // Each crane's hook path takes the same leg-count treatment as the asymmetric
    // tab: the operator says how many legs the tag is made of, the documents' rule
    // says what the rating is declared on, and a single leg hangs vertical so its
    // angle is not applied.  Both the chosen and the declared count are published,
    // so the report can show a 4 beside a denominator of 3 and explain it.
    val chosenLegsA = number(inputs.slingLegsA.toDouble(), "Crane 1 sling legs").toInt()
    val chosenLegsB = number(inputs.slingLegsB.toDouble(), "Crane 2 sling legs").toInt()
    val ratedLegsA = ratedLegs(chosenLegsA)
    val ratedLegsB = ratedLegs(chosenLegsB)
    val tagAngleA = effectiveTagAngle(
        chosenLegsA, number(inputs.tagWllAAngle, "Crane 1 sling tag WLL angle"),
    )
    val tagAngleB = effectiveTagAngle(
        chosenLegsB, number(inputs.tagWllBAngle, "Crane 2 sling tag WLL angle"),
    )
    val tagAfA = publishedAf(cos(Math.toRadians(tagAngleA)))
    val tagAfB = publishedAf(cos(Math.toRadians(tagAngleB)))
    val capacityA = legCapacity(tagWllA, ratedLegsA, tagAfA)
    val capacityB = legCapacity(tagWllB, ratedLegsB, tagAfB)
    val utilisationA = tagUtilisationExact(requiredA, capacityA)
    val utilisationB = tagUtilisationExact(requiredB, capacityB)

    // Each crane is booked against its own rating, and the 75% allowable window is
    // a load-chart rule that applies to each crane independently.  Neither crane's
    // rating is shared with the other.
    //
    // Usage is what the Crane tab already calls usage: the demand over the rated
    // capacity read from the load chart at the working radius.  It used to be
    // (demand / 0.75) / rated capacity, so the same word meant a figure 4/3 larger
    // here than on the Crane tab for one and the same lift -- 100.00% against
    // 75.00% -- and a reader moving between the tabs had no way to tell that the two
    // were not describing the same thing.
    val craneRatedA = number(inputs.craneRatedA, "Crane 1 rated capacity")
    val craneRatedB = number(inputs.craneRatedB, "Crane 2 rated capacity")
    val craneUsageA = if (craneRatedA > 0) Calc.chartUsagePercent(requiredA, craneRatedA) else null
    val craneUsageB = if (craneRatedB > 0) Calc.chartUsagePercent(requiredB, craneRatedB) else null

    val radians = Math.toRadians(tilt)
    // How far the tilt carries the COG sideways under lug 1, which is what makes
    // this a shift rather than a pure rotation.
    val cogShift = publishedLength(cogHeight * sin(radians))
    // How far lug 2 migrates inward on its own account before it is projected.  It
    // is zero for the aligned pivot method, which is the whole difference between
    // the two spans.
    val lug2Travel = if (!method.fullRotation) publishedLength(cogHeight * tan(radians)) else 0.0

    val allTensions = mutableListOf(
        tensionALevel, tensionBLevel, tensionATilt, tensionBTilt,
    )
    if (tensionAVertical != null) {
        allTensions.add(tensionAVertical)
        allTensions.add(tensionBVertical!!)
    }

    val equipmentOk = if (utilisationA == null || utilisationB == null) {
        null
    } else {
        utilisationA <= 100.0 && utilisationB <= 100.0
    }

    // True only when every entered crane sits outside the 75% window, so a single
    // figure cannot stand in for a check that covers both cranes.
    val craneRiskFlag = if (craneUsageA == null || craneUsageB == null) {
        null
    } else {
        listOf(craneUsageA, craneUsageB).any { craneUsageBand(it) != UsageState.WITHIN }
    }

    return TandemResult(
        scenario = inputs.scenario,
        scenarioLabel = scenarioSpec(inputs.scenario).label,
        loadWeight = load,
        methodKey = method.key,
        methodLabel = method.label,
        methodFullLabel = method.fullLabel,
        methodRule = method.rule,
        methodLug2 = method.lug2,
        methodSpanBehaviour = method.spanBehaviour,
        methodMaxRotation = method.maxRotation,
        methodUseFor = method.useFor,
        fullRotation = method.fullRotation,
        distA = distA,
        distB = distB,
        span = span,
        cogHeight = cogHeight,
        tiltAngle = tilt,
        angle = angle,
        angleFactor = af,
        baseReeveFactor = reeve,
        distANew = distANew,
        distBNew = distBNew,
        spanNew = spanNew,
        cogShift = cogShift,
        lug2Travel = lug2Travel,
        heightDifference = publishedLength(span * sin(radians)),
        maxTiltAngle = maxTilt,
        shareALevel = shareALevel,
        shareBLevel = shareBLevel,
        tensionALevel = tensionALevel,
        tensionBLevel = tensionBLevel,
        requiredALevel = requiredALevel,
        requiredBLevel = requiredBLevel,
        shareATilt = shareATilt,
        shareBTilt = shareBTilt,
        tensionATilt = tensionATilt,
        tensionBTilt = tensionBTilt,
        requiredATilt = requiredATilt,
        requiredBTilt = requiredBTilt,
        shareAVertical = shareAVertical,
        shareBVertical = shareBVertical,
        tensionAVertical = tensionAVertical,
        tensionBVertical = tensionBVertical,
        requiredAVertical = requiredAVertical,
        requiredBVertical = requiredBVertical,
        requiredWllA = requiredA,
        requiredWllB = requiredB,
        governingCaseA = governingA,
        governingCaseB = governingB,
        maxTension = allTensions.max(),
        requiredWll = max(requiredA, requiredB),
        tagWllA = tagWllA,
        tagWllAAngle = tagAngleA,
        tagAngleFactorA = tagAfA,
        slingLegsA = chosenLegsA,
        ratedLegsA = ratedLegsA,
        legCapacityA = capacityA,
        utilisationA = utilisationA,
        tagWllB = tagWllB,
        tagWllBAngle = tagAngleB,
        tagAngleFactorB = tagAfB,
        slingLegsB = chosenLegsB,
        ratedLegsB = ratedLegsB,
        legCapacityB = capacityB,
        utilisationB = utilisationB,
        craneRatedA = craneRatedA,
        craneRatedB = craneRatedB,
        craneUsageA = craneUsageA,
        craneUsageB = craneUsageB,
        // A state rather than a string, because "within 75%", "between 75% and the
        // chart" and "over the chart" are three different things to tell an
        // operator and one bit cannot carry all three.
        craneUsageStateA = craneUsageA?.let { craneUsageBand(it) },
        craneUsageStateB = craneUsageB?.let { craneUsageBand(it) },
        // The sling tags alone decide the verdict.  The cranes raise a risk flag
        // beside the figure instead of silently flipping PASS to CHECK, because a
        // crane booked under its charted capacity is a rigging judgement about the
        // crane rather than about the slings, and the operator has to be the one to
        // make it.
        equipmentOk = equipmentOk,
        craneRiskFlag = craneRiskFlag,
        setupSwl = load,
    )
}

/**
 * The case that governs one crane, in words rather than as a key.
 *
 * Lives in the model so both surfaces read it: the words name *which case* sized the
 * crane, which is a fact about the solve, and two copies of it is one more thing
 * that can disagree — the phone's copy once said "tilted case" where the desktop
 * says "declared tilt".
 */
fun governingCaseWords(caseKey: String?): String = when (caseKey) {
    "level" -> "level baseline"
    "tilt" -> "declared tilt"
    "vertical" -> "full 90\u00b0 vertical rotation"
    else -> "\u2014"
}

/** A figure with a thousands separator and a fixed number of decimals. */
private fun thousands(value: Double, decimals: Int): String =
    String.format(Locale.US, "%,.${decimals}f", value)

private fun fixed(value: Double, decimals: Int): String =
    String.format(Locale.US, "%.${decimals}f", value)

/** Solve one nonuniform scenario. */
@Throws(NonuniformInputError::class)
fun solveNonuniform(inputs: NonuniformInputs, loadWeight: Double): NonuniformResult =
    when (inputs.scenario) {
        "asym_2leg_shortening" -> solveAsym2Leg(inputs, loadWeight)
        "tandem_aligned_pivot", "tandem_top_lug" -> solveTandem2Crane(inputs, loadWeight)
        else -> throw NonuniformInputError(
            "Unknown nonuniform scenario: ${inputs.scenario}"
        )
    }
