package com.example.recipebox.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Light theme only for now. Dynamic colour is off so the app keeps its own look.
private val LightColorScheme = lightColorScheme(
    primary = MediumGreen,
    onPrimary = Color.White,
    primaryContainer = LightSage,
    onPrimaryContainer = DarkGreen,
    secondary = DarkGreen,
    onSecondary = Color.White,
    secondaryContainer = LightSage,
    onSecondaryContainer = DarkGreen,
    tertiary = Mustard,
    onTertiary = NearBlackGreen,
    tertiaryContainer = Mustard,
    onTertiaryContainer = NearBlackGreen,
    background = Cream,
    onBackground = DarkGreen,
    surface = Cream,
    onSurface = DarkGreen,
    surfaceVariant = TagFill,
    onSurfaceVariant = MutedGreyGreen,
    surfaceTint = MediumGreen,
    inverseSurface = DarkGreen,
    inverseOnSurface = Cream,
    inversePrimary = Sage,
    outline = Ink,
    outlineVariant = BeigeBorder,
    error = ErrorRed,
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),   // the debug bar keeps this red
    onErrorContainer = Color(0xFF410E0B),
    scrim = Color.Black,
    surfaceBright = Cream,
    surfaceDim = Color(0xFFE6E0C8),
    surfaceContainerLowest = CardFill,
    surfaceContainerLow = Color(0xFFE2DAC6),
    surfaceContainer = ChipFill,
    surfaceContainerHigh = Beige,
    surfaceContainerHighest = Color(0xFFD9CFB8)
)

@Composable
fun RecipeBoxTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}

/** Text fields filled like the cards, with a dark outline. Used on every screen with an input. */
@Composable
fun recipeFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = FieldFill,
    unfocusedContainerColor = FieldFill,
    unfocusedBorderColor = Ink,
    focusedPlaceholderColor = MutedGreyGreen,
    unfocusedPlaceholderColor = MutedGreyGreen
)
