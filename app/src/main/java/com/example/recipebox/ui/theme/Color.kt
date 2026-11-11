package com.example.recipebox.ui.theme

import androidx.compose.ui.graphics.Color

// Palette from the cream / green / mustard mood board, adjusted so text passes WCAG AA (4.5:1).
// Approximate contrast ratios of the pairings used in the app:
//   DarkGreen on Cream 8.3 | DarkGreen on White 8.9 | DarkGreen on LightSage 6.8
//   MediumGreen on Cream 5.7 | White on MediumGreen 5.8
//   NearBlackGreen on Mustard 7.0 | MutedGreyGreen on Cream 5.5 | MutedGreyGreen on Beige 4.7
// Mustard is a fill only. Never use it for text, and never put white text on it.

val Cream = Color(0xFFFFFDF1)
val DarkGreen = Color(0xFF335441)
val MediumGreen = Color(0xFF456F47)
val LightSage = Color(0xFFE4EACD)      // selected cards and chips
val Sage = Color(0xFFA9B782)           // decorative only, too light for text
val Tan = Color(0xFFCCBF9D)            // decorative borders only
val Mustard = Color(0xFFDEAD47)
val NearBlackGreen = Color(0xFF1F2D24) // text on mustard
val Beige = Color(0xFFF1EBD6)          // tags, dialogs
val BeigeBorder = Color(0xFFE3DAB9)    // card borders
val MutedGreyGreen = Color(0xFF5A6A5D) // secondary text
val FieldBorder = Color(0xFF7C8B6F)    // text field outline, 3.6:1 against white
val ErrorRed = Color(0xFFB3261E)
