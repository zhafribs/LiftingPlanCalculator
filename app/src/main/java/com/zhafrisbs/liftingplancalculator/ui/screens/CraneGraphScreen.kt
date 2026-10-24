package com.zhafrisbs.liftingplancalculator.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhafrisbs.liftingplancalculator.core.ANGLE_MAX
import com.zhafrisbs.liftingplancalculator.core.ANGLE_MIN
import com.zhafrisbs.liftingplancalculator.core.CONFIG_BOOM
import com.zhafrisbs.liftingplancalculator.core.CONFIG_BOOM_EXT_JIB
import com.zhafrisbs.liftingplancalculator.core.CONFIG_BOOM_JIB
import com.zhafrisbs.liftingplancalculator.core.CONFIG_LABELS
import com.zhafrisbs.liftingplancalculator.core.CONFIGS
import com.zhafrisbs.liftingplancalculator.core.CraneGraphState
import com.zhafrisbs.liftingplancalculator.core.CraneReadouts
import com.zhafrisbs.liftingplancalculator.core.DEFAULT_STATE
import com.zhafrisbs.liftingplancalculator.core.Figures
import com.zhafrisbs.liftingplancalculator.core.GeometryException
import com.zhafrisbs.liftingplancalculator.core.GraphPoint
import com.zhafrisbs.liftingplancalculator.core.angleForTip
import com.zhafrisbs.liftingplancalculator.core.assemblyTip
import com.zhafrisbs.liftingplancalculator.core.boomTip
import com.zhafrisbs.liftingplancalculator.core.clamp
import com.zhafrisbs.liftingplancalculator.core.effectiveJibLength
import com.zhafrisbs.liftingplancalculator.core.envelopePoints
import com.zhafrisbs.liftingplancalculator.core.envelopeRadius
import com.zhafrisbs.liftingplancalculator.core.readouts
import com.zhafrisbs.liftingplancalculator.core.tipForState
import com.zhafrisbs.liftingplancalculator.core.tipHeight
import com.zhafrisbs.liftingplancalculator.core.workingRadius
import com.zhafrisbs.liftingplancalculator.ui.components.DropdownField
import com.zhafrisbs.liftingplancalculator.ui.components.NumberField
import com.zhafrisbs.liftingplancalculator.ui.components.ResultCard
import com.zhafrisbs.liftingplancalculator.ui.components.ResultRowData
import com.zhafrisbs.liftingplancalculator.ui.components.ResultTone
import com.zhafrisbs.liftingplancalculator.ui.components.SectionCard
import com.zhafrisbs.liftingplancalculator.ui.theme.TextMuted
import com.zhafrisbs.liftingplancalculator.ui.theme.TextPrimary
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin

/**
 * The Graph tab: the crane's working-range diagram, ported from `crane_graph/gui.py`.
 *
 * The desktop is a window with the controls down the left and a live diagram on the
 * right.  A phone has one column, so the **diagram comes first**: it is the thing the
 * tab is for, and a diagram below four cards would need scrolling to before it could
 * be read.  The desktop's left-hand column and its readout panel keep their contents
 * and their order behind it.
 *
 * **What the desktop did that a phone cannot keep, and what replaced it.**  The
 * desktop drove the diagram with a mouse: dragging the boom-tip handle set the angle,
 * dragging the crane marker moved the crane, dragging empty space *panned* the view,
 * the wheel *zoomed* about the cursor, and a right-click or a double-click fitted the
 * view.  There are no wheel and no right button here, so pan and zoom are dropped
 * outright rather than half-implemented — and because the view can no longer be put
 * somewhere and left there, it is refitted to the working range whenever the range
 * changes.  That is the desktop's own `Auto-fit view` checkbox (on by default there)
 * pinned on, and its `Fit view` button survives as the **Fit to view** control.  What
 * is *not* dropped is the desktop's own rule that the view must not move under the
 * finger: the camera is frozen for the duration of a drag and refitted when it ends,
 * exactly as `_apply`'s `not self.canvas.is_dragging()` guard does.
 */
@Composable
internal fun CraneGraphScreen(modifier: Modifier = Modifier) {
    // Saveable rather than a ViewModel: this tab's shape is not part of the lift, and
    // the only thing it has to survive is a rotation.  A plan lost to a sideways phone
    // is the failure the rest of the app already designs against, and a diagram that
    // reset to 45 degrees would be the same fault in a smaller place.
    var state by rememberSaveable(stateSaver = CraneGraphStateSaver) { mutableStateOf(DEFAULT_STATE) }
    var showDimensions by rememberSaveable { mutableStateOf(true) }
    // Bumped by "Fit to view".  A counter rather than a boolean because the control
    // exists to *repeat* an action, and a flag would fire only the first press.
    var fitTick by rememberSaveable { mutableIntStateOf(0) }
    val readout = readouts(state)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = "Drag the boom tip to set the boom angle, and the crane marker to " +
                "move it on the XY axis. The view stays fitted to the working range.",
            color = TextMuted,
            style = MaterialTheme.typography.bodySmall,
        )

        SectionCard(title = "Working Range") {
            GraphDiagram(
                state = state,
                showDimensions = showDimensions,
                fitTick = fitTick,
                onAngle = { state = state.copy(angle = it) },
                onCrane = { x, y -> state = state.copy(craneX = x, craneY = y) },
                modifier = Modifier.fillMaxWidth().height(360.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = showDimensions,
                    onCheckedChange = { showDimensions = it },
                )
                Text(
                    text = "Show dimensions",
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { fitTick++ }) { Text("Fit to view") }
            }
        }

        SectionCard(
            title = "Boom configuration",
            // The desktop *greys out* the spin boxes a configuration does not use.  A
            // greyed box still occupies a row of a phone's form, and this project has
            // already recorded the cost of a field that is filled in and then ignored:
            // "on a phone, a field that is filled in and then ignored reads as a
            // fault".  Only the configuration's own lengths are shown.  The value is
            // *kept* rather than cleared, so switching back restores it exactly as the
            // desktop's disabled box does.
            note = "The desktop greys out the lengths a configuration does not use; " +
                "they are hidden here instead. Switching away keeps what was typed.",
        ) {
            DropdownField(
                label = "Configuration",
                options = CONFIGS,
                selected = state.config,
                optionLabel = { CONFIG_LABELS[it] ?: it },
                onSelected = { state = state.copy(config = it) },
            )
            // The limits are the desktop's own spin-box ranges, carried across
            // unchanged: 1-150 m boom, 0-60 m jib, 0-80 m extended jib, -30 to 60
            // degrees of offset.  They are mechanical limits, not preferences.
            NumberField(
                label = "Boom length",
                value = state.boomLength,
                onValueChange = { state = state.copy(boomLength = it) },
                unit = "m",
                decimals = 1,
                min = 1.0,
                max = 150.0,
            )
            if (state.config == CONFIG_BOOM_JIB) {
                NumberField(
                    label = "Jib length",
                    value = state.jibLength,
                    onValueChange = { state = state.copy(jibLength = it) },
                    unit = "m",
                    decimals = 1,
                    min = 0.0,
                    max = 60.0,
                )
            }
            if (state.config == CONFIG_BOOM_EXT_JIB) {
                NumberField(
                    label = "Extended jib length",
                    value = state.extJibLength,
                    onValueChange = { state = state.copy(extJibLength = it) },
                    unit = "m",
                    decimals = 1,
                    min = 0.0,
                    max = 80.0,
                )
            }
            if (state.config != CONFIG_BOOM) {
                NumberField(
                    label = "Jib offset angle",
                    value = state.jibOffset,
                    onValueChange = { state = state.copy(jibOffset = it) },
                    unit = "deg",
                    decimals = 1,
                    min = -30.0,
                    max = 60.0,
                )
            }
        }

        SectionCard(title = "Boom angle & envelope") {
            NumberField(
                label = "Boom angle",
                value = state.angle,
                onValueChange = { state = state.copy(angle = it) },
                unit = "deg",
                decimals = 1,
                min = ANGLE_MIN,
                max = ANGLE_MAX,
            )
            // The two envelope limits are kept ordered here rather than in the
            // diagram.  The desktop's `_env_changed` bumps the *other* box to match
            // whichever one the operator just moved, and does it with a re-entrancy
            // guard; a single write of both fields does the same thing without the
            // guard, because there is no second signal to suppress.
            NumberField(
                label = "Envelope from",
                value = state.envMin,
                onValueChange = { v ->
                    state = state.copy(envMin = v).let {
                        if (it.envMin > it.envMax) it.copy(envMax = it.envMin) else it
                    }
                },
                unit = "deg",
                decimals = 1,
                min = 0.0,
                max = 85.0,
            )
            NumberField(
                label = "Envelope to",
                value = state.envMax,
                onValueChange = { v ->
                    state = state.copy(envMax = v).let {
                        if (it.envMax < it.envMin) it.copy(envMin = it.envMax) else it
                    }
                },
                unit = "deg",
                decimals = 1,
                min = 0.0,
                max = 85.0,
            )
        }

        SectionCard(title = "Crane location on axis (X, Y)") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(Modifier.weight(1f)) {
                    NumberField(
                        label = "Position X",
                        value = state.craneX,
                        onValueChange = { state = state.copy(craneX = it) },
                        unit = "m",
                        decimals = 1,
                        min = -100.0,
                        max = 100.0,
                    )
                }
                Box(Modifier.weight(1f)) {
                    NumberField(
                        label = "Position Y",
                        value = state.craneY,
                        onValueChange = { state = state.copy(craneY = it) },
                        unit = "m",
                        decimals = 1,
                        min = -50.0,
                        max = 100.0,
                    )
                }
            }
            // The desktop's four quick positions.  Kept because dragging is the only
            // other way to move the crane now that the view cannot be panned, and a
            // finger is a poor instrument for landing on exactly (0, 0).
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Quick:",
                    color = TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
                CRANE_PRESETS.forEach { (x, y) ->
                    TextButton(onClick = { state = state.copy(craneX = x, craneY = y) }) {
                        Text("(${general(x)}, ${general(y)})")
                    }
                }
            }
        }

        ResultCard(title = "Readout", rows = readoutRows(state, readout))

        Box(Modifier.fillMaxWidth())
    }
}

// ---------------------------------------------------------------------------
// The readout panel
// ---------------------------------------------------------------------------

/**
 * The desktop's readout rows, in its own order and with its own captions.
 *
 * Built here rather than beside the geometry because they are the *desktop's*
 * presentation of it — `geometry.readouts()` returns the figures, and `gui.py`'s
 * `_update_readouts` is what turns them into these thirteen lines.  "not fitted"
 * and the em dash for an absent jib offset are its words, not inventions.
 */
private fun readoutRows(state: CraneGraphState, r: CraneReadouts): List<ResultRowData> {
    val jibLabel =
        if (state.config == CONFIG_BOOM_EXT_JIB) "Extended jib length" else "Jib length"
    return listOf(
        ResultRowData("Configuration", CONFIG_LABELS[state.config] ?: state.config),
        ResultRowData("Boom length", "${fixed(state.boomLength, 1)} m"),
        ResultRowData(jibLabel, if (r.jibActive) "${fixed(r.jibLength, 1)} m" else "not fitted"),
        ResultRowData(
            "Jib offset angle",
            if (r.jibActive) "${fixed(state.jibOffset, 1)} deg" else Figures.NOTHING,
        ),
        ResultRowData("Working radius", "${fixed(r.workingRadius, 2)} m"),
        ResultRowData("Tip height above ground", "${fixed(r.tipHeight, 2)} m"),
        ResultRowData("Tip height above crane", "${fixed(r.heightAboveCrane, 2)} m"),
        ResultRowData("Envelope radius", "${fixed(r.envelopeRadius, 2)} m"),
        ResultRowData("Envelope sweep", "${general(state.envMin)} - ${general(state.envMax)} deg"),
        ResultRowData("Boom angle", "${general(state.angle)} deg"),
        ResultRowData("Crane location (X, Y)", "(${general(state.craneX)}, ${general(state.craneY)})"),
        ResultRowData("Tip location (X, Y)", "(${fixed(r.tip.x, 2)}, ${fixed(r.tip.y, 2)})"),
        // The one row that is a judgement rather than a measurement, so it is the one
        // row that carries a tone.  Red for a tip below ground, green for a tip above
        // it — the desktop's own `#ff6b6b` / `#2e7d52`, in the app's semantic colours.
        ResultRowData(
            label = "Ground clearance",
            value = if (r.belowGround) "BELOW GROUND" else "OK",
            tone = if (r.belowGround) ResultTone.OVER else ResultTone.OK,
        ),
    )
}

// ---------------------------------------------------------------------------
// The diagram
// ---------------------------------------------------------------------------

/** Which handle a gesture has hold of. */
private enum class DragTarget { NONE, TIP, CRANE }

/** The desktop's four quick crane positions. */
private val CRANE_PRESETS: List<Pair<Double, Double>> =
    listOf(-3.0 to 0.0, 0.0 to 0.0, 0.0 to 3.0, 3.0 to 0.0)

/**
 * The view transform: pixels per metre and the world point at the canvas centre.
 *
 * Width and height are carried along because the transform is *into* a canvas of a
 * particular size, and the gesture code needs the same transform as the drawing
 * code while sitting outside the draw scope.
 */
private data class Camera(
    val scale: Double,
    val centerX: Double,
    val centerY: Double,
    val width: Float,
    val height: Float,
)

private fun Camera.toScreen(p: GraphPoint): Offset = Offset(
    ((p.x - centerX) * scale + width / 2.0).toFloat(),
    (height / 2.0 - (p.y - centerY) * scale).toFloat(),
)

private fun Camera.toWorld(o: Offset): GraphPoint = GraphPoint(
    (o.x - width / 2.0) / scale + centerX,
    (height / 2.0 - o.y) / scale + centerY,
)

/** The world rectangle the canvas currently shows. */
private data class WorldBox(val x0: Double, val x1: Double, val y0: Double, val y1: Double)

private fun Camera.visibleWorld(): WorldBox {
    val x0 = toWorld(Offset(0f, 0f)).x
    val x1 = toWorld(Offset(width, 0f)).x
    val yTop = toWorld(Offset(0f, 0f)).y
    val yBottom = toWorld(Offset(0f, height)).y
    return WorldBox(
        minOf(x0, x1),
        maxOf(x0, x1),
        minOf(yTop, yBottom),
        maxOf(yTop, yBottom),
    )
}

/**
 * The camera that fits the working envelope, ported from `GraphCanvas.fit_view`.
 *
 * The desktop pads the bounding box by "10% of the larger span, plus 1.5 m" and
 * takes the smaller of the two axis ratios, which is what keeps the diagram from
 * being stretched.  The scale is clamped — the same `0.02` to `2000` — because a
 * degenerate envelope would otherwise divide by a span near zero.
 *
 * The points included are the desktop's: the arc, the pivot, the ground under the
 * crane, the farthest reach, and the current tip.  The ground under the crane is
 * there so the operator can see the machine standing on something; the farthest
 * reach is there because an envelope whose sweep stops short of 0 degrees would
 * otherwise be cropped.
 */
private fun fitCamera(state: CraneGraphState, width: Float, height: Float): Camera {
    val pivot = GraphPoint(state.craneX, state.craneY)
    val jib = effectiveJibLength(state.config, state.jibLength, state.extJibLength)
    val radius = envelopeRadius(state.boomLength, jib, state.jibOffset)
    val points = envelopePoints(pivot, radius, state.envMin, state.envMax) + listOf(
        pivot,
        GraphPoint(pivot.x, 0.0),
        GraphPoint(pivot.x + radius, pivot.y),
        tipForState(state),
    )
    var x0 = points.minOf { it.x }
    var x1 = points.maxOf { it.x }
    var y0 = points.minOf { it.y }
    var y1 = points.maxOf { it.y }
    val pad = 0.10 * maxOf(x1 - x0, y1 - y0, 1.0) + 1.5
    x0 -= pad
    x1 += pad
    y0 -= pad
    y1 += pad
    val scale = clamp(minOf(width / (x1 - x0), height / (y1 - y0)), 0.02, 2000.0)
    return Camera(scale, (x0 + x1) / 2.0, (y0 + y1) / 2.0, width, height)
}

/**
 * The interactive diagram.
 *
 * The three gestures the desktop had are reduced to the two that make sense without
 * a pointer: the tip handle sets the angle, the crane marker moves the crane, and
 * everything else does nothing.  The desktop panned on empty space, which has no
 * phone equivalent here — dropping the pan is what makes "nothing" the right answer
 * for a drag that starts nowhere near a handle, rather than a half-hearted pan.
 */
@Composable
private fun GraphDiagram(
    state: CraneGraphState,
    showDimensions: Boolean,
    fitTick: Int,
    onAngle: (Double) -> Unit,
    onCrane: (Double, Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()

    BoxWithConstraints(modifier) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        // A finger is not a mouse: the desktop's 14 px tip and 20 px crane radii are
        // far too small to hit reliably on glass, and are widened to finger-sized
        // targets.  The tip is the smaller of the two because it is the one that moves
        // the angle and a too-large radius would steal drags meant for the crane.
        val tipRadiusPx = with(density) { TIP_HIT_SIZE.toPx() }
        val craneRadiusPx = with(density) { CRANE_HIT_SIZE.toPx() }

        val fitted = remember(state, widthPx, heightPx, fitTick) {
            fitCamera(state, widthPx, heightPx)
        }
        // The camera the drag started with.  Held so the view can be *frozen* while a
        // gesture is in flight: refitting on every move would rescale the diagram
        // under the finger, and the desktop's `not self.canvas.is_dragging()` guard
        // exists for exactly that reason.
        var frozen by remember { mutableStateOf<Camera?>(null) }
        var dragging by remember { mutableStateOf(false) }
        var hot by remember { mutableStateOf(DragTarget.NONE) }
        val camera = if (dragging) frozen ?: fitted else fitted

        // The gesture runs in a coroutine that outlives any single recomposition, so
        // it reads the current state and camera through these rather than capturing
        // the values from the composition that started it.
        val stateNow by rememberUpdatedState(state)
        val cameraNow by rememberUpdatedState(camera)
        val angleNow by rememberUpdatedState(onAngle)
        val craneNow by rememberUpdatedState(onCrane)

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val target = hitTarget(
                                stateNow,
                                cameraNow,
                                down.position,
                                tipRadiusPx,
                                craneRadiusPx,
                            )
                            // Not consuming here is deliberate: a touch that starts
                            // away from both handles is the operator scrolling the
                            // form, and eating it would trap the page.
                            if (target == DragTarget.NONE) continue
                            frozen = cameraNow
                            hot = target
                            dragging = true
                            down.consume()
                            var pressed = true
                            while (pressed) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id }
                                if (change == null || !change.pressed) {
                                    pressed = false
                                } else {
                                    change.consume()
                                    applyDrag(
                                        target,
                                        stateNow,
                                        cameraNow,
                                        change.position,
                                        angleNow,
                                        craneNow,
                                    )
                                }
                            }
                            dragging = false
                            hot = DragTarget.NONE
                        }
                    }
                },
        ) {
            drawGraph(camera, state, measurer, showDimensions, hot)
        }
    }
}

/** The hit test, in the desktop's order: the tip first, then the crane. */
private fun hitTarget(
    state: CraneGraphState,
    camera: Camera,
    position: Offset,
    tipRadius: Float,
    craneRadius: Float,
): DragTarget {
    val tipScreen = camera.toScreen(tipForState(state))
    val tipDx = position.x - tipScreen.x
    val tipDy = position.y - tipScreen.y
    if (tipDx * tipDx + tipDy * tipDy <= tipRadius * tipRadius) return DragTarget.TIP

    val craneScreen = camera.toScreen(GraphPoint(state.craneX, state.craneY))
    val craneDx = position.x - craneScreen.x
    val craneDy = position.y - craneScreen.y
    if (craneDx * craneDx + craneDy * craneDy <= craneRadius * craneRadius) return DragTarget.CRANE
    return DragTarget.NONE
}

/**
 * One drag step, ported from `_emit_tip` and `_emit_crane`.
 *
 * The rounding to one decimal and the clamps are the desktop's: it feeds the same
 * spin boxes the operator types into, and a value the box would not hold would make
 * the field and the diagram disagree about the boom.
 */
private fun applyDrag(
    target: DragTarget,
    state: CraneGraphState,
    camera: Camera,
    position: Offset,
    onAngle: (Double) -> Unit,
    onCrane: (Double, Double) -> Unit,
) {
    val world = camera.toWorld(position)
    when (target) {
        DragTarget.TIP -> {
            val jib = effectiveJibLength(state.config, state.jibLength, state.extJibLength)
            val angle = try {
                angleForTip(
                    GraphPoint(state.craneX, state.craneY),
                    state.boomLength,
                    jib,
                    state.jibOffset,
                    world,
                )
            } catch (_: GeometryException) {
                // A zero-length boom has no angle to point at.  The desktop swallows
                // the same exception and simply stops following the finger.
                null
            }
            if (angle != null) {
                val snapped = roundTo1(clamp(angle, ANGLE_MIN, ANGLE_MAX))
                if (snapped != state.angle) onAngle(snapped)
            }
        }

        DragTarget.CRANE -> {
            val x = roundTo1(clamp(world.x, -100.0, 100.0))
            val y = roundTo1(clamp(world.y, -50.0, 100.0))
            if (x != state.craneX || y != state.craneY) onCrane(x, y)
        }

        DragTarget.NONE -> Unit
    }
}

// ---------------------------------------------------------------------------
// Painting
// ---------------------------------------------------------------------------

// The desktop's palette, transcribed from `gui.py`'s COL_* constants including the
// alphas: the envelope's fill is the same blue at 26/255, which is what makes it read
// as a range rather than as a shape.  Kept here rather than in `theme/Color.kt`
// because none of it is used anywhere else — these are the diagram's own colours, not
// the app's palette.
private val GraphBackground = Color(0xFF141922)
private val GraphGrid = Color(0xFF1F2735)
private val GraphAxis = Color(0xFF5F7191)
private val GraphAxisText = Color(0xFF7D8BA3)
private val EnvelopeFill = Color(92, 179, 255, 26)
private val EnvelopeStroke = Color(92, 179, 255, 175)
private val EnvelopeEdge = Color(92, 179, 255, 90)
private val BoomArcColour = Color(143, 183, 255, 85)
private val BoomColour = Color(0xFFFFB703)
private val JibColour = Color(0xFF5CB3FF)
private val JointColour = Color(0xFFE8EAF0)
private val DimRadiusColour = Color(255, 183, 3, 170)
private val DimHeightColour = Color(143, 183, 255, 170)
private val AngleArcColour = Color(223, 230, 240, 150)
private val HandleHighlight = Color(0xFFFFFFFF)
private val PillBackground = Color(16, 21, 30, 225)
private val GraphText = Color(0xFFDFE6F0)
private val OutriggerBase = Color(0xFF34405A)
private val OutriggerFoot = Color(0xFF5F7191)
private val CraneBody = Color(0xFF2F6FBF)
private val Counterweight = Color(0xFF233047)

/** The tip below ground is drawn in the desktop's red rather than in the jib blue. */
private val BelowGroundColour = Color(0xFFFF6B6B)

private val AxisDash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))

/** Finger-sized hit targets, as discussed at the call site. */
private val TIP_HIT_SIZE = 28.dp
private val CRANE_HIT_SIZE = 36.dp

/** The two axis captions, named once because the Y one is also measured before the loop. */
private const val X_AXIS_CAPTION = "X / radius (m)"
private const val Y_AXIS_CAPTION = "Y / height (m)"

/** The desktop's paint order, which is back-to-front and is not arbitrary. */
private fun DrawScope.drawGraph(
    camera: Camera,
    state: CraneGraphState,
    measurer: TextMeasurer,
    showDimensions: Boolean,
    hot: DragTarget,
) {
    drawRect(GraphBackground)
    drawGrid(camera)
    drawAxesAndGround(camera, measurer)
    drawEnvelope(camera, state, measurer)
    if (showDimensions) drawDimensions(camera, state, measurer)
    drawAngleArc(camera, state, measurer)
    drawCraneMarker(camera, state, measurer, hot == DragTarget.CRANE)
    drawBoom(camera, state)
    drawHandles(camera, state, measurer, hot == DragTarget.TIP)
}

/**
 * The grid spacing: the first step from the desktop's ladder that is at least 45 px
 * apart on screen.
 *
 * A fixed world step would be a grid that is a solid block when zoomed out and empty
 * when zoomed in, so the step is chosen from the scale rather than the other way
 * around.
 */
private fun gridStep(scale: Double): Double {
    val steps = doubleArrayOf(0.1, 0.2, 0.5, 1.0, 2.0, 5.0, 10.0, 20.0, 50.0, 100.0, 200.0, 500.0, 1000.0)
    for (step in steps) if (step * scale >= 45.0) return step
    return 1000.0
}

/**
 * `int(x / step) * step` for the start of a grid pass.
 *
 * `toLong()` truncates toward zero where a floor would round down, and the desktop's
 * `int()` does the same.  The difference only shows on the negative side of an axis,
 * where it shifts the grid's phase by one step — visible, and worth keeping identical
 * because the two are meant to be the same drawing.
 */
private fun gridStart(from: Double, step: Double): Double = (from / step).toLong() * step

private fun DrawScope.drawGrid(camera: Camera) {
    val step = gridStep(camera.scale)
    val world = camera.visibleWorld()

    var x = gridStart(world.x0, step)
    while (x <= world.x1) {
        if (abs(x) > step * 1e-6) {
            val sx = camera.toScreen(GraphPoint(x, 0.0)).x
            drawLine(GraphGrid, Offset(sx, 0f), Offset(sx, camera.height), strokeWidth = 1f)
        }
        x += step
    }

    var y = gridStart(world.y0, step)
    while (y <= world.y1) {
        if (abs(y) > step * 1e-6) {
            val sy = camera.toScreen(GraphPoint(0.0, y)).y
            drawLine(GraphGrid, Offset(0f, sy), Offset(camera.width, sy), strokeWidth = 1f)
        }
        y += step
    }
}

private fun DrawScope.drawAxesAndGround(camera: Camera, measurer: TextMeasurer) {
    val step = gridStep(camera.scale)
    val world = camera.visibleWorld()

    // Ground (y = 0), with the engineering hatch below it and the radius labels along
    // it.  The hatch is what says "this line is the ground" rather than "this line is
    // another gridline", and it is only drawn when the ground is actually on screen.
    val groundY = camera.toScreen(GraphPoint(0.0, 0.0)).y
    if (groundY >= -2f && groundY <= camera.height + 2f) {
        drawLine(GraphAxis, Offset(0f, groundY), Offset(camera.width, groundY), strokeWidth = 2f)
        var hatchX = 0f
        while (hatchX <= camera.width) {
            drawLine(
                GraphAxis,
                Offset(hatchX, groundY + 1f),
                Offset(hatchX - 7f, groundY + 8f),
                strokeWidth = 1f,
            )
            hatchX += 11f
        }
        var tick = gridStart(world.x0, step)
        while (tick <= world.x1) {
            val sx = camera.toScreen(GraphPoint(tick, 0.0)).x
            // Suppressed at the very edges: a half-drawn number at the margin reads
            // worse than no number, and the desktop skips the same 14 px.
            if (sx >= 14f && sx <= camera.width - 14f) {
                drawTextAt(measurer, fmtTick(tick), sx + 3f, groundY + 20f, GraphAxisText, 9.sp)
            }
            tick += step
        }
    }

    // The Y axis itself, when it is on screen.  Its labels stay in the left margin when
    // it is not, so a crane dragged to the right does not take the scale with it.
    //
    // **A deliberate divergence in that off-screen case.**  The desktop pins the labels
    // at its left margin but still *right*-aligns them there, so the numbers end up to
    // the left of the canvas and cannot be read -- a crane moved far enough along X
    // takes the scale with it.  The desktop's intent (the scale stays at the left edge)
    // is kept; the anchor is moved inside the canvas so the numbers are legible.
    val axisX = camera.toScreen(GraphPoint(0.0, 0.0)).x
    val axisOnScreen = axisX >= -2f && axisX <= camera.width + 2f
    if (axisOnScreen) {
        drawLine(GraphAxis, Offset(axisX, 0f), Offset(axisX, camera.height), strokeWidth = 1f)
    }
    val labelRight = axisX - 10f

    // The Y caption's plate occupies the corner the topmost tick label would be drawn
    // in.  The desktop *clips* its caption box and draws it last, so it simply paints
    // over that label; here the two would interleave ("Y / height(34)(m)"), so the one
    // label under the plate is dropped instead.  Losing the top of the scale reads
    // better than two strings written through each other, and no other tick is touched.
    val captionBand = 8f +
        measurer.measure(Y_AXIS_CAPTION, textStyle(GraphAxisText, 9.sp, bold = false)).size.height +
        12f
    var label = gridStart(world.y0, step)
    while (label <= world.y1) {
        val sy = camera.toScreen(GraphPoint(0.0, label)).y
        if (sy >= captionBand && sy <= camera.height - 10f) {
            if (axisOnScreen) {
                drawTextRight(measurer, fmtTick(label), labelRight, sy + 5f, GraphAxisText, 9.sp)
            } else {
                drawTextAt(measurer, fmtTick(label), 8f, sy + 5f, GraphAxisText, 9.sp)
            }
        }
        label += step
    }

    drawCaption(measurer, camera, X_AXIS_CAPTION, bottomRight = true)
    drawCaption(measurer, camera, Y_AXIS_CAPTION, bottomRight = false)
}

/**
 * One axis caption, on the dark plate the readout pills already use.
 *
 * The desktop draws its captions last, inside a rectangle it clips to, so anything
 * they land on is covered rather than blended with.  A phone's diagram is a fraction
 * of the desktop's size, so the plate is what makes the caption readable instead of
 * relying on the overprint happening to land somewhere harmless.
 */
private fun DrawScope.drawCaption(
    measurer: TextMeasurer,
    camera: Camera,
    text: String,
    bottomRight: Boolean,
) {
    val layout = measurer.measure(text, textStyle(GraphAxisText, 9.sp, bold = false))
    val width = layout.size.width.toFloat()
    val height = layout.size.height.toFloat()
    val left = if (bottomRight) camera.width - width - 18f else 8f
    val top = if (bottomRight) camera.height - height - 9f else 8f
    drawRoundRect(
        PillBackground,
        topLeft = Offset(left, top),
        size = Size(width + 12f, height + 6f),
        cornerRadius = CornerRadius(4f, 4f),
    )
    drawText(layout, topLeft = Offset(left + 6f, top + 3f))
}


private fun DrawScope.drawEnvelope(camera: Camera, state: CraneGraphState, measurer: TextMeasurer) {
    val pivot = GraphPoint(state.craneX, state.craneY)
    val jib = effectiveJibLength(state.config, state.jibLength, state.extJibLength)
    val radius = envelopeRadius(state.boomLength, jib, state.jibOffset)
    val arc = envelopePoints(pivot, radius, state.envMin, state.envMax).map { camera.toScreen(it) }
    val pivotScreen = camera.toScreen(pivot)

    if (arc.size >= 2) {
        // The sector: pivot out to the arc and back.  Filled rather than left open so
        // the *reachable* region reads as a region and not as a curve.
        val sector = Path().apply {
            moveTo(pivotScreen.x, pivotScreen.y)
            arc.forEach { lineTo(it.x, it.y) }
            close()
        }
        drawPath(sector, EnvelopeFill)
        drawPath(polyline(arc), EnvelopeStroke, style = Stroke(width = 2f))
        drawLine(EnvelopeEdge, pivotScreen, arc.first(), strokeWidth = 1f, pathEffect = AxisDash)
        drawLine(EnvelopeEdge, pivotScreen, arc.last(), strokeWidth = 1f, pathEffect = AxisDash)

    }

    // The arc the *boom tip* traces when a jib is fitted.  It is the reason the
    // envelope is not simply a bigger circle: the boom end is pinned to a shorter
    // radius, and drawing only the outer arc would hide that.
    if (jib > 0) {
        val boomArc = envelopePoints(pivot, state.boomLength, state.envMin, state.envMax, steps = 48)
            .map { camera.toScreen(it) }
        drawPath(
            polyline(boomArc),
            BoomArcColour,
            style = Stroke(width = 1f, pathEffect = AxisDash),
        )
    }

    // The envelope's own limits, written at the two ends of the arc and pushed 14 px
    // outwards so they clear the line they label.
    listOf(arc.first() to state.envMin, arc.last() to state.envMax).forEach { (point, angle) ->
        val vx = point.x - pivotScreen.x
        val vy = point.y - pivotScreen.y
        val norm = hypot(vx, vy).takeIf { it > 0f } ?: 1f
        val lx = point.x + vx / norm * 14f
        val ly = point.y + vy / norm * 14f
        drawTextAt(measurer, "${general(angle)}\u00b0", lx - 14f, ly + 4f, JibColour, 9.sp)
    }
}

private fun DrawScope.drawDimensions(camera: Camera, state: CraneGraphState, measurer: TextMeasurer) {
    val pivot = GraphPoint(state.craneX, state.craneY)
    val tip = tipForState(state)
    val pivotScreen = camera.toScreen(pivot)
    val tipScreen = camera.toScreen(tip)
    val groundY = camera.toScreen(GraphPoint(0.0, 0.0)).y

    // Working radius: a dashed horizontal from the pivot across to the tip's own X,
    // with a tick at the far end.  Horizontal because that is the quantity — the
    // radius is the horizontal distance, and a line drawn along the boom would be
    // measuring the boom.
    drawLine(
        DimRadiusColour,
        pivotScreen,
        Offset(tipScreen.x, pivotScreen.y),
        strokeWidth = 1f,
        pathEffect = AxisDash,
    )
    drawLine(
        DimRadiusColour,
        Offset(tipScreen.x, pivotScreen.y - 5f),
        Offset(tipScreen.x, pivotScreen.y + 5f),
        strokeWidth = 1.5f,
    )
    drawPill(
        measurer,
        camera,
        "R = ${fixed(workingRadius(tip), 2)} m",
        (pivotScreen.x + tipScreen.x) / 2f,
        pivotScreen.y - 12f,
        BoomColour,
        10.sp,
    )

    // Tip height: the dashed vertical from the tip down to the ground.
    if (groundY >= -50f && groundY <= camera.height + 50f) {
        drawLine(
            DimHeightColour,
            tipScreen,
            Offset(tipScreen.x, groundY),
            strokeWidth = 1f,
            pathEffect = AxisDash,
        )
        drawLine(
            DimHeightColour,
            Offset(tipScreen.x - 5f, groundY),
            Offset(tipScreen.x + 5f, groundY),
            strokeWidth = 1.5f,
        )
        val height = tipHeight(tip)
        drawPill(
            measurer,
            camera,
            "H = ${fixed(height, 2)} m",
            tipScreen.x + 8f,
            (tipScreen.y + groundY) / 2f,
            if (height < 0) BelowGroundColour else JibColour,
            10.sp,
        )
    }

    // **Where the tip is**, in the two quantities the axes are labelled in.
    //
    // The desktop measures the tip twice -- a radius along one axis and a height
    // along the other -- and never states the point those two describe. A reader who
    // wants the coordinate has to take one number off each axis and assemble them,
    // which is the step a diagram exists to remove.
    //
    // Placed above the handle, clear of the R pill below the axis and the H pill
    // down the drop; it travels with the handle when the tip is dragged, so the two
    // numbers move together rather than one of them going stale.
    drawPill(
        measurer,
        camera,
        "(${fixed(tip.x, 1)}, ${fixed(tip.y, 1)})",
        tipScreen.x + 8f,
        tipScreen.y - 16f,
        GraphText,
        10.sp,
    )
}

private fun DrawScope.drawAngleArc(camera: Camera, state: CraneGraphState, measurer: TextMeasurer) {
    val pivotScreen = camera.toScreen(GraphPoint(state.craneX, state.craneY))
    val radius = 30f
    val angle = state.angle
    // Two degrees per segment, and at least two: a single-segment arc of a small angle
    // is a straight line, which would draw the angle as a chord.
    val steps = maxOf(2, (abs(angle) / 2.0).toInt() + 1)
    val points = (0..steps).map { i ->
        val a = Math.toRadians(angle * i / steps)
        Offset(
            pivotScreen.x + radius * cos(a).toFloat(),
            pivotScreen.y - radius * sin(a).toFloat(),
        )
    }
    drawPath(polyline(points), AngleArcColour, style = Stroke(width = 1f))

    val half = Math.toRadians(angle / 2.0)
    val lx = pivotScreen.x + (radius + 14f) * cos(half).toFloat() - 12f
    val ly = pivotScreen.y - (radius + 14f) * sin(half).toFloat() + 4f
    drawTextAt(measurer, "${general(angle)}\u00b0", lx, ly, GraphText, 9.sp, bold = true)
}

private fun DrawScope.drawCraneMarker(
    camera: Camera,
    state: CraneGraphState,
    measurer: TextMeasurer,
    hot: Boolean,
) {
    val p = camera.toScreen(GraphPoint(state.craneX, state.craneY))
    val border = if (hot) HandleHighlight else JibColour

    // Outrigger base and feet: the machine stands on the ground line rather than
    // floating at the pivot, which is what makes the marker read as a crane.
    drawRoundRect(
        OutriggerBase,
        topLeft = Offset(p.x - 22f, p.y + 4f),
        size = Size(44f, 8f),
        cornerRadius = CornerRadius(3f, 3f),
    )
    drawRoundRect(
        GraphAxis,
        topLeft = Offset(p.x - 22f, p.y + 4f),
        size = Size(44f, 8f),
        cornerRadius = CornerRadius(3f, 3f),
        style = Stroke(width = 1f),
    )
    drawRoundRect(
        OutriggerFoot,
        topLeft = Offset(p.x - 24f, p.y + 11f),
        size = Size(9f, 6f),
        cornerRadius = CornerRadius(2f, 2f),
    )
    drawRoundRect(
        OutriggerFoot,
        topLeft = Offset(p.x + 15f, p.y + 11f),
        size = Size(9f, 6f),
        cornerRadius = CornerRadius(2f, 2f),
    )

    // Superstructure on the -X side, which is behind the boom, plus the counterweight.
    // The highlight is on the superstructure's outline because that is the part the
    // finger is dragging.
    drawRoundRect(
        CraneBody,
        topLeft = Offset(p.x - 16f, p.y - 16f),
        size = Size(15f, 16f),
        cornerRadius = CornerRadius(3f, 3f),
    )
    drawRoundRect(
        border,
        topLeft = Offset(p.x - 16f, p.y - 16f),
        size = Size(15f, 16f),
        cornerRadius = CornerRadius(3f, 3f),
        style = Stroke(width = if (hot) 2f else 1f),
    )
    drawRoundRect(
        Counterweight,
        topLeft = Offset(p.x - 23f, p.y - 11f),
        size = Size(7f, 11f),
        cornerRadius = CornerRadius(2f, 2f),
    )

    // The slew point: the one mark that is exactly on the pivot, so the operator can
    // see where the angles are measured from.
    drawCircle(JointColour, radius = 4f, center = p)
    drawCircle(GraphBackground, radius = 4f, center = p, style = Stroke(width = 1f))

    drawPill(
        measurer,
        camera,
        "(${general(state.craneX)}, ${general(state.craneY)})",
        p.x,
        p.y + 30f,
        GraphText,
        9.sp,
    )
}

private fun DrawScope.drawBoom(camera: Camera, state: CraneGraphState) {
    val pivot = GraphPoint(state.craneX, state.craneY)
    val jib = effectiveJibLength(state.config, state.jibLength, state.extJibLength)
    val boomEnd = boomTip(pivot, state.boomLength, state.angle)
    val tip = assemblyTip(pivot, state.boomLength, jib, state.jibOffset, state.angle)
    val pivotScreen = camera.toScreen(pivot)
    val boomScreen = camera.toScreen(boomEnd)
    val tipScreen = camera.toScreen(tip)

    // Rounded caps so the boom reads as a member rather than as a hairline, and so the
    // joint where the jib meets it is covered rather than showing a notch.
    drawLine(BoomColour, pivotScreen, boomScreen, strokeWidth = 5f, cap = StrokeCap.Round)
    if (jib > 0) {
        drawLine(JibColour, boomScreen, tipScreen, strokeWidth = 3f, cap = StrokeCap.Round)
        drawCircle(JointColour, radius = 4f, center = boomScreen)
        drawCircle(GraphBackground, radius = 4f, center = boomScreen, style = Stroke(width = 1f))
    }
}

private fun DrawScope.drawHandles(
    camera: Camera,
    state: CraneGraphState,
    measurer: TextMeasurer,
    hot: Boolean,
) {
    val tip = tipForState(state)
    val tipScreen = camera.toScreen(tip)
    val radius = if (hot) 9f else 7f
    drawCircle(BoomColour, radius = radius, center = tipScreen)
    drawCircle(
        if (hot) HandleHighlight else GraphBackground,
        radius = radius,
        center = tipScreen,
        style = Stroke(width = 2f),
    )
    // The coordinates only appear while the handle is held: they are the feedback for
    // a drag in progress, and a label permanently beside the tip is one more thing
    // over the diagram.
    if (hot) {
        drawTextAt(
            measurer,
            "(${fixed(tip.x, 1)}, ${fixed(tip.y, 1)})",
            tipScreen.x + 14f,
            tipScreen.y + 4f,
            GraphText,
            9.sp,
            bold = true,
        )
    }
}

// ---------------------------------------------------------------------------
// Small drawing helpers
// ---------------------------------------------------------------------------

private fun polyline(points: List<Offset>): Path = Path().apply {
    if (points.isEmpty()) return@apply
    moveTo(points[0].x, points[0].y)
    for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
}

private fun textStyle(colour: Color, size: TextUnit, bold: Boolean): TextStyle = TextStyle(
    color = colour,
    fontSize = size,
    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
)

/**
 * Draw a label with [x] as the *baseline's* left edge.
 *
 * The desktop positions text by baseline (`drawText(QPointF(...), ...)`), and the
 * measurement that matters for a scale label is where its baseline sits relative to
 * the tick, not where its box starts.  Compose anchors at the box, so the baseline is
 * recovered from the layout rather than guessed at.
 */
private fun DrawScope.drawTextAt(
    measurer: TextMeasurer,
    text: String,
    x: Float,
    baselineY: Float,
    colour: Color,
    size: TextUnit,
    bold: Boolean = false,
) {
    val layout = measurer.measure(text, textStyle(colour, size, bold))
    drawText(layout, topLeft = Offset(x, baselineY - layout.getLineBaseline(0)))
}

/** As [drawTextAt], but anchored on the baseline's *right* edge: the desktop's right. */
private fun DrawScope.drawTextRight(
    measurer: TextMeasurer,
    text: String,
    rightX: Float,
    baselineY: Float,
    colour: Color,
    size: TextUnit,
) {
    val layout: TextLayoutResult = measurer.measure(text, textStyle(colour, size, bold = false))
    drawText(layout, topLeft = Offset(rightX - layout.size.width, baselineY - layout.getLineBaseline(0)))
}

/**
 * A label on a rounded dark background, ported from `_pill`.
 *
 * The pill is why the readouts can sit *on* the diagram: a dark plate under the text
 * is what keeps "R = 21.21 m" legible over the envelope's fill, and the desktop
 * nudges the plate inside the canvas rather than letting it hang off the edge.
 */
private fun DrawScope.drawPill(
    measurer: TextMeasurer,
    camera: Camera,
    text: String,
    cx: Float,
    cy: Float,
    colour: Color,
    size: TextUnit,
) {
    val layout = measurer.measure(text, textStyle(colour, size, bold = true))
    val textWidth = layout.size.width.toFloat()
    val textHeight = layout.size.height.toFloat()
    val left = (cx - textWidth / 2f - 6f).coerceIn(2f, maxOf(2f, camera.width - textWidth - 18f))
    val top = (cy - textHeight / 2f - 3f).coerceIn(2f, maxOf(2f, camera.height - textHeight - 9f))
    drawRoundRect(
        PillBackground,
        topLeft = Offset(left, top),
        size = Size(textWidth + 12f, textHeight + 6f),
        cornerRadius = CornerRadius(4f, 4f),
    )
    drawText(layout, topLeft = Offset(left + 6f, top + 3f))
}

// ---------------------------------------------------------------------------
// Number formatting
// ---------------------------------------------------------------------------

/** A fixed number of decimals, in [Locale.US] as everywhere else in the app. */
private fun fixed(value: Double, decimals: Int): String =
    String.format(Locale.US, "%.${decimals}f", value)

/**
 * Python's `:g`, for the readout rows that use it.
 *
 * The desktop prints the angle, the envelope sweep and the crane's position with
 * `f"{value:g}"`, which drops a trailing `.0` — so 45 degrees reads `45 deg` and not
 * `45.0 deg`.  Only ever called with the values the operator can reach, which are
 * whole numbers or one-decimal ones, so the shortest round-tripping form is the same
 * string `%g` would produce.
 */
private fun general(value: Double): String =
    if (value == floor(value)) value.toLong().toString() else value.toString()

/**
 * A tick label: whole numbers without a decimal point, everything else as `:g`.
 *
 * The desktop's `_fmt_tick`, and the reason the grid reads "20" rather than "20.0"
 * while still labelling a 0.5 m grid as "0.5".
 */
private fun fmtTick(value: Double): String =
    if (value == floor(value)) value.toLong().toString() else general(value)

/**
 * Round half to even, as Python's `round()` does.
 *
 * The desktop rounds the dragged angle and crane position to one decimal through
 * Python's `round`, which breaks a tie to even; a half-up round would disagree with
 * it on exactly the boundary values the operator's finger lands on.
 */
private fun roundTo1(value: Double): Double = Math.rint(value * 10.0) / 10.0

/**
 * The state as a saveable list.
 *
 * A hand-written `Saver` rather than `@Parcelize`: this is one small record, and an
 * extra plugin for ten doubles would be a build-time cost for a rotation.
 */
private val CraneGraphStateSaver: Saver<CraneGraphState, Any> = listSaver(
    save = {
        listOf<Any>(
            it.config,
            it.boomLength,
            it.jibLength,
            it.extJibLength,
            it.jibOffset,
            it.angle,
            it.envMin,
            it.envMax,
            it.craneX,
            it.craneY,
        )
    },
    restore = {
        CraneGraphState(
            config = it[0] as String,
            boomLength = it[1] as Double,
            jibLength = it[2] as Double,
            extJibLength = it[3] as Double,
            jibOffset = it[4] as Double,
            angle = it[5] as Double,
            envMin = it[6] as Double,
            envMax = it[7] as Double,
            craneX = it[8] as Double,
            craneY = it[9] as Double,
        )
    },
)
