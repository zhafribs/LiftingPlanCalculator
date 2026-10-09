package com.zhafrisbs.liftingplancalculator.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * The app's colour scheme.
 *
 * Deliberately a single dark scheme rather than a `isSystemInDarkTheme()`
 * branch: the reference app has one appearance, and a lifting plan that changed
 * colour with the phone's setting would be the same defect as a figure that
 * changed with its locale.
 */
private val LiftingPlanColors = darkColorScheme(
    primary = AccentBlue,
    onPrimary = WindowBackgroundDeep,
    primaryContainer = ButtonBlue,
    onPrimaryContainer = TextPrimary,
    secondary = SectionTitleBlue,
    onSecondary = WindowBackgroundDeep,
    secondaryContainer = SectionBody,
    onSecondaryContainer = TextPrimary,
    tertiary = AddRow,
    onTertiary = TextPrimary,
    background = WindowBackground,
    onBackground = TextPrimary,
    surface = PanelSurface,
    onSurface = TextPrimary,
    surfaceVariant = SectionBody,
    onSurfaceVariant = TextSecondary,
    surfaceContainer = PanelSurface,
    surfaceContainerHigh = SectionBody,
    outline = BorderStrong,
    outlineVariant = BorderLine,
    error = StateOver,
    onError = TextPrimary,
    errorContainer = Danger,
    onErrorContainer = TextPrimary,
    scrim = WindowBackgroundDeep,
)

@Composable
fun LiftingPlanCalculatorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LiftingPlanColors,
        content = content,
    )
}
