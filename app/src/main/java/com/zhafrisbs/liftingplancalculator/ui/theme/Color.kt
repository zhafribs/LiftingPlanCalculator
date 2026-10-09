package com.zhafrisbs.liftingplancalculator.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The palette, taken from the reference project's `theme.py`.
 *
 * The reference is a dark engineering surface, and so is this: an operator
 * reading a lift plan should see the same colours in a plant room as on the
 * desktop, whichever way the phone's own light/dark setting is configured.
 */

// -- surfaces ---------------------------------------------------------------
val WindowBackground = Color(0xFF1B212C)
val WindowBackgroundDeep = Color(0xFF161B24)
val PanelSurface = Color(0xFF232B3A)
val SectionBody = Color(0xFF2A3347)

// -- lines and text ---------------------------------------------------------
val BorderLine = Color(0xFF2C3547)
val BorderStrong = Color(0xFF34405A)
val TextPrimary = Color(0xFFDFE6F0)
val TextMuted = Color(0xFF8FA3C0)
val TextSecondary = Color(0xFFAAB4C4)

// -- accents ----------------------------------------------------------------
val AccentBlue = Color(0xFF5CB3FF)
val SectionTitleBlue = Color(0xFF8FB7FF)
val ButtonBlue = Color(0xFF2A5C9E)
val ButtonBlueHover = Color(0xFF306BC0)

// -- semantic ---------------------------------------------------------------
/** Inside the 75% crane window. */
val StateWithin = Color(0xFF2E7D52)

/** Above 75% but still within the charted capacity. */
val StateCaution = Color(0xFFB06A1E)

/** Beyond the charted capacity. */
val StateOver = Color(0xFFB23B3B)

/** The red cross that removes a line. */
val Danger = Color(0xFF8A3A3A)

/** The `+` that adds a line. */
val AddRow = Color(0xFF2E6B4E)
