package com.zhafrisbs.liftingplancalculator.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.zhafrisbs.liftingplancalculator.ui.theme.AccentBlue
import com.zhafrisbs.liftingplancalculator.ui.theme.AddRow
import com.zhafrisbs.liftingplancalculator.ui.theme.BorderLine
import com.zhafrisbs.liftingplancalculator.ui.theme.BorderStrong
import com.zhafrisbs.liftingplancalculator.ui.theme.Danger
import com.zhafrisbs.liftingplancalculator.ui.theme.PanelSurface
import com.zhafrisbs.liftingplancalculator.ui.theme.SectionTitleBlue
import com.zhafrisbs.liftingplancalculator.ui.theme.StateCaution
import com.zhafrisbs.liftingplancalculator.ui.theme.StateOver
import com.zhafrisbs.liftingplancalculator.ui.theme.StateWithin
import com.zhafrisbs.liftingplancalculator.ui.theme.TextMuted
import com.zhafrisbs.liftingplancalculator.ui.theme.TextPrimary
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The form widgets every tab is built from.
 *
 * The reference keeps these in one place for a stated reason, and it is not
 * tidiness: five tabs each grew their own row builder and the result was rows of
 * three different heights and two different indents, which the operator reported
 * as "the spacing is nice on one tab and not the others".  One set of widgets is
 * what makes "the same size as that other tab's field" true by construction.
 */

// ---------------------------------------------------------------------------
// Structure
// ---------------------------------------------------------------------------

/** A section heading with its body, and an optional action beside the heading. */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
    note: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                color = SectionTitleBlue,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            if (action != null) action()
        }
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = PanelSurface,
            border = BorderStroke(1.dp, BorderLine),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content,
            )
        }
        if (!note.isNullOrEmpty()) {
            Text(
                text = note,
                color = TextMuted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/**
 * The `+` that adds a line to a section.
 *
 * Drawn as a text glyph rather than an icon pack: Material3 does not bundle the
 * icon set, and taking a dependency on it for two characters is not worth the
 * size.  Green, so it never reads as the delete control beside it.  The label
 * lives in `semantics` because a bare `+` announces nothing to a screen reader.
 */
@Composable
fun AddLineButton(onClick: () -> Unit, contentDescription: String = "Add line") {
    FilledIconButton(
        onClick = onClick,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = AddRow,
            contentColor = TextPrimary,
        ),
        modifier = Modifier.semantics { this.contentDescription = contentDescription },
    ) {
        Text(
            text = "+",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * The red cross that removes a line.
 *
 * Filled rather than left as a bare glyph.  As a plain `×` on a dark card it was
 * genuinely hard to find -- the worst thing a delete control can be -- and a
 * filled red button with a light glyph reads as a control at a glance, in the
 * colour the app already uses for destructive actions.
 *
 * `\u00D7` (multiplication sign) rather than a dingbat: the sign is Latin-1 and
 * certain to be in every bundled font, where a missing glyph renders as a blank
 * box — a worse delete button than a plain cross, and one that looks correct in
 * a diff.
 */
@Composable
fun RemoveLineButton(onClick: () -> Unit, contentDescription: String = "Remove line") {
    FilledIconButton(
        onClick = onClick,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = Danger,
            contentColor = TextPrimary,
        ),
        modifier = Modifier.semantics { this.contentDescription = contentDescription },
    ) {
        Text(
            text = "\u00D7",
            style = MaterialTheme.typography.titleLarge,
        )
    }
}

/**
 * The way out of a full-screen page.
 *
 * A text glyph rather than an icon-pack asset, for the reason [AddLineButton] gives:
 * Material3 does not bundle the icon set, and this app does not take a dependency on
 * it for one character.  `\u2190` is U+2190, in the Unicode *Arrows* block, which
 * every Android font stack covers — unlike a dingbat, which renders as a blank box.
 *
 * It replaced a `\u00D7`.  A cross means *close*, and these pages are not overlays:
 * they sit exactly where the tabs were, so leaving one is **going back**, and the
 * symbol should say that rather than implying the page was floating on top of
 * something.
 */
@Composable
fun BackButton(onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(
            text = "\u2190",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

// ---------------------------------------------------------------------------
// Results
// ---------------------------------------------------------------------------

/** How a result row is coloured: a judgement, or merely a figure. */
enum class ResultTone { NEUTRAL, OK, CAUTION, OVER }

data class ResultRowData(
    val label: String,
    val value: String,
    val tone: ResultTone = ResultTone.NEUTRAL,
)

@Composable
private fun toneColor(tone: ResultTone) = when (tone) {
    ResultTone.NEUTRAL -> TextPrimary
    ResultTone.OK -> StateWithin
    ResultTone.CAUTION -> StateCaution
    ResultTone.OVER -> StateOver
}

/** The panel a tab's figures are shown in: a label and a value, one per row. */
@Composable
fun ResultCard(title: String, rows: List<ResultRowData>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            color = TextPrimary,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = PanelSurface,
            border = BorderStroke(1.dp, BorderStrong),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(12.dp)) {
                rows.forEachIndexed { index, row ->
                    if (index > 0) HorizontalDivider(color = BorderLine, modifier = Modifier.padding(vertical = 6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = row.label,
                            color = TextMuted,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = row.value,
                            color = toneColor(row.tone),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Text entry
// ---------------------------------------------------------------------------

@Composable
private fun entryColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AccentBlue,
    unfocusedBorderColor = BorderStrong,
    focusedLabelColor = AccentBlue,
    unfocusedLabelColor = TextMuted,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    cursorColor = AccentBlue,
)

/** A free-text line: a description, a material, a name. */
@Composable
fun LabeledTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = hint?.let { { Text(it, color = TextMuted) } },
        singleLine = true,
        colors = entryColors(),
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * A password line.
 *
 * Its own widget rather than a `password = true` flag on [LabeledTextField], because
 * a password is not a text box with the characters hidden: it must not be
 * capitalised and must not be autocorrected, and both of those are settings on the
 * **keyboard** rather than on the box.  A field that hides the text but lets the
 * keyboard capitalise the first letter turns a password the user typed correctly
 * into a failure they cannot see.
 */
@Composable
fun PasswordField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            capitalization = KeyboardCapitalization.None,
        ),
        colors = entryColors(),
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * A number box.
 *
 * The text the operator is typing is held here rather than derived from [value]
 * on every recomposition, because a half-typed figure is a normal state of the
 * form: parsing `12.` back to `12.00` mid-keystroke would move the caret and
 * fight the operator.  [LaunchedEffect] re-syncs the box only when [value]
 * changes to something this box did not itself produce — which is what makes
 * Clear and Reset actually empty the field instead of leaving the last answer
 * sitting in it.
 */
@Composable
fun NumberField(
    label: String,
    value: Double,
    onValueChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
    unit: String = "",
    decimals: Int = 2,
    min: Double = 0.0,
    max: Double = Double.MAX_VALUE,
) {
    var text by remember(label) { mutableStateOf(editable(value, decimals)) }

    LaunchedEffect(label, value) {
        val parsed = text.toDoubleOrNull()
        if (parsed == null || abs(parsed - value) > 1e-6) {
            text = editable(value, decimals)
        }
    }

    OutlinedTextField(
        value = text,
        onValueChange = { new ->
            text = new
            new.toDoubleOrNull()?.let { onValueChange(it.coerceIn(min, max)) }
        },
        label = { Text(label) },
        suffix = if (unit.isEmpty()) null else { { Text(unit, color = TextMuted) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        colors = entryColors(),
        modifier = modifier.fillMaxWidth(),
    )
}

private fun editable(value: Double, decimals: Int): String =
    if (decimals <= 0) value.roundToInt().toString()
    else String.format(Locale.US, "%.${decimals}f", value)

// ---------------------------------------------------------------------------
// Choices
// ---------------------------------------------------------------------------

/** A dropdown for a small, fixed set of options. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> DropdownField(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = optionLabel(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = entryColors(),
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}
