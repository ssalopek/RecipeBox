package com.example.recipebox.ui.theme

import androidx.compose.ui.graphics.Color

// Colors sampled from the target screen (khaki background, beige cards, near-black green ink).
// Contrast ratios of the pairings used in the app (all at least 4.5:1):
//   DarkGreen on Cream 7.7 | on CardFill 10.8 | on TagFill 4.7 | on LightSage 7.3
//   MutedGreyGreen on CardFill 6.8 | on Cream 4.8 | on FieldFill 6.3 | on Beige 7.1
//   White on MediumGreen 12.3 | Ink on ChipFill 8.0 | on FieldFill 8.3
//   ErrorRed on Cream 4.8 | on Beige 7.1 | White on ErrorRed 8.9

val Cream = Color(0xFFC5BEA7)          // app background (the example fades from #CDC7B2 at the top to #BFB89F at the bottom)
val CardFill = Color(0xFFE8E0CE)       // recipe cards
val FieldFill = Color(0xFFE2D8C2)      // search box and text fields
val ChipFill = Color(0xFFDFD5BF)       // unselected category chips
val DarkGreen = Color(0xFF143020)      // titles and body text
val MediumGreen = Color(0xFF243A29)    // primary: selected chip, buttons, Add recipe
val Ink = Color(0xFF313B2C)            // outlines of chips, search and text fields, chip labels
val LightSage = Color(0xFFB5BE9B)      // selected cards and chips
val TagFill = Color(0xFF919779)        // source tags
val Sage = Color(0xFF929F76)           // decorative only, too light for text
val Tan = Color(0xFFAAA184)            // decorative only
val Mustard = Color(0xFFDEAD47)        // not used in the UI right now
val NearBlackGreen = Color(0xFF19271F) // text on mustard, not used right now
val Beige = Color(0xFFECE5D2)          // dialogs
val BeigeBorder = Color(0xFFA5A088)    // card borders
val MutedGreyGreen = Color(0xFF444C40) // secondary text
val FieldBorder = Color(0xFF313B2C)    // same as Ink, kept for older references
val ErrorRed = Color(0xFF8F1D17)       // deeper than before, so red text stays readable on the khaki background