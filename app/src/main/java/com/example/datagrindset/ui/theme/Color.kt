package com.example.datagrindset.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Classic Theme (Orange/Black/Brown)
val ClassicOrange = Color(0xFFE65100)
val ClassicLightOrange = Color(0xFFFF9800)
val ClassicBrown = Color(0xFF795548)
val ClassicDarkBrown = Color(0xFF5D4037)
val ClassicBlack = Color(0xFF212121)
val ClassicOffWhite = Color(0xFFF5F5F5)
val ClassicDarkSurface = Color(0xFF303030)

val ClassicLightColors = lightColorScheme(
    primary = ClassicOrange,
    onPrimary = Color.White,
    primaryContainer = ClassicLightOrange,
    onPrimaryContainer = ClassicBlack,
    secondary = ClassicBrown,
    onSecondary = Color.White,
    secondaryContainer = ClassicDarkBrown,
    onSecondaryContainer = Color.White,
    tertiary = ClassicLightOrange,
    onTertiary = ClassicBlack,
    tertiaryContainer = ClassicOrange,
    onTertiaryContainer = ClassicBlack,
    error = Color(0xFFB00020),
    onError = Color.White,
    background = ClassicOffWhite,
    onBackground = ClassicBlack,
    surface = Color.White,
    onSurface = ClassicBlack,
    surfaceVariant = Color(0xFFE0E0E0),
    onSurfaceVariant = ClassicBlack,
    outline = ClassicBrown
)

val ClassicDarkColors = darkColorScheme(
    primary = ClassicLightOrange,
    onPrimary = ClassicBlack,
    primaryContainer = ClassicOrange,
    onPrimaryContainer = ClassicOffWhite,
    secondary = ClassicDarkBrown,
    onSecondary = ClassicOffWhite,
    secondaryContainer = ClassicBrown,
    onSecondaryContainer = ClassicBlack,
    tertiary = ClassicOrange,
    onTertiary = ClassicBlack,
    tertiaryContainer = ClassicLightOrange,
    onTertiaryContainer = ClassicBlack,
    error = Color(0xFFCF6679),
    onError = Color.Black,
    background = ClassicBlack,
    onBackground = ClassicOffWhite,
    surface = ClassicDarkSurface,
    onSurface = ClassicOffWhite,
    surfaceVariant = Color(0xFF424242),
    onSurfaceVariant = ClassicOffWhite,
    outline = ClassicBrown
)

// --- Sky Theme (Blue/White/Black) ---
val SkyBlue = Color(0xFF03A9F4)
val SkyLightBlue = Color(0xFF81D4FA)
val SkyDarkBlue = Color(0xFF0277BD)
val SkyBackgroundDark = Color(0xFF121212)

val SkyLightColors = lightColorScheme(
    primary = SkyBlue,
    onPrimary = Color.Black,
    primaryContainer = SkyLightBlue,
    onPrimaryContainer = Color.Black,
    secondary = SkyLightBlue,
    onSecondary = Color.Black,
    secondaryContainer = SkyBlue,
    onSecondaryContainer = Color.Black,
    background = Color.White,
    onBackground = Color.Black,
    surface = Color(0xFFF0F0F0),
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFE0E0E0),
    onSurfaceVariant = Color.Black,
    error = Color(0xFFB00020),
    onError = Color.White,
    outline = SkyDarkBlue
)

val SkyDarkColors = darkColorScheme(
    primary = SkyLightBlue,
    onPrimary = Color.Black,
    primaryContainer = SkyBlue,
    onPrimaryContainer = Color.Black,
    secondary = SkyBlue,
    onSecondary = Color.Black,
    secondaryContainer = SkyDarkBlue,
    onSecondaryContainer = Color.White,
    background = SkyBackgroundDark,
    onBackground = Color.White,
    surface = Color(0xFF1E1E1E),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF303030),
    onSurfaceVariant = Color.White,
    error = Color(0xFFCF6679),
    onError = Color.Black,
    outline = SkyLightBlue
)

// --- Forest Lagoon Theme (Blue/Green/Brown) ---
val ForestGreen = Color(0xFF2E7D32)
val ForestLightGreen = Color(0xFF66BB6A)
val LagoonBlue = Color(0xFF0097A7)
val ForestBrown = Color(0xFF8D6E63)
val ForestBackgroundLight = Color(0xFFFFF8E1)
val ForestBackgroundDark = Color(0xFF3E2723)

val ForestLagoonLightColors = lightColorScheme(
    primary = ForestGreen,
    onPrimary = Color.White,
    primaryContainer = ForestLightGreen,
    onPrimaryContainer = Color.Black,
    secondary = LagoonBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF4DD0E1),
    onSecondaryContainer = Color.Black,
    tertiary = ForestBrown,
    onTertiary = Color.White,
    background = ForestBackgroundLight,
    onBackground = ForestBackgroundDark,
    surface = Color.White,
    onSurface = ForestBackgroundDark,
    surfaceVariant = Color(0xFFF0EAE2),
    onSurfaceVariant = ForestBackgroundDark,
    error = Color(0xFFD32F2F),
    onError = Color.White,
    outline = ForestBrown
)

val ForestLagoonDarkColors = darkColorScheme(
    primary = ForestLightGreen,
    onPrimary = Color.Black,
    primaryContainer = ForestGreen,
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF4DD0E1),
    onSecondary = Color.Black,
    secondaryContainer = LagoonBlue,
    onSecondaryContainer = Color.White,
    tertiary = Color(0xFFA1887F),
    onTertiary = Color.Black,
    background = ForestBackgroundDark,
    onBackground = ForestBackgroundLight,
    surface = Color(0xFF4E342E),
    onSurface = ForestBackgroundLight,
    surfaceVariant = Color(0xFF5D4037),
    onSurfaceVariant = ForestBackgroundLight,
    error = Color(0xFFE57373),
    onError = Color.Black,
    outline = Color(0xFFA1887F)
)

// --- Noir & Blanc Theme (Black/White) ---
val NoirPrimaryLight = Color(0xFF000000)
val NoirBackgroundLight = Color(0xFFFFFFFF)
val NoirSurfaceLight = Color(0xFFF5F5F5)
val NoirOnSurfaceLight = Color(0xFF000000)

val NoirPrimaryDark = Color(0xFFFFFFFF)
val NoirBackgroundDark = Color(0xFF000000)
val NoirSurfaceDark = Color(0xFF1E1E1E)
val NoirOnSurfaceDark = Color(0xFFFFFFFF)

val NoirBlancLightColors = lightColorScheme(
    primary = NoirPrimaryLight,
    onPrimary = NoirBackgroundLight,
    primaryContainer = Color(0xFF333333),
    onPrimaryContainer = NoirBackgroundLight,
    secondary = Color(0xFF555555),
    onSecondary = NoirBackgroundLight,
    background = NoirBackgroundLight,
    onBackground = NoirOnSurfaceLight,
    surface = NoirSurfaceLight,
    onSurface = NoirOnSurfaceLight,
    error = Color.Red,
    onError = Color.White,
    outline = Color(0xFF757575)
)

val NoirBlancDarkColors = darkColorScheme(
    primary = NoirPrimaryDark,
    onPrimary = NoirBackgroundDark,
    primaryContainer = Color(0xFFCCCCCC),
    onPrimaryContainer = NoirBackgroundDark,
    secondary = Color(0xFFAAAAAA),
    onSecondary = NoirBackgroundDark,
    background = NoirBackgroundDark,
    onBackground = NoirOnSurfaceDark,
    surface = NoirSurfaceDark,
    onSurface = NoirOnSurfaceDark,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    outline = Color(0xFF909090)
)

// --- Strawberry Milk Theme (Pink/White) ---
val StrawberryPink = Color(0xFFF48FB1)
val StrawberryLightPink = Color(0xFFF8BBD0)
val StrawberryDarkPink = Color(0xFFE91E63)
val StrawberryTextDark = Color(0xFF4E342E)

val StrawberryMilkLightColors = lightColorScheme(
    primary = StrawberryPink,
    onPrimary = StrawberryTextDark,
    primaryContainer = StrawberryLightPink,
    onPrimaryContainer = StrawberryTextDark,
    secondary = StrawberryLightPink,
    onSecondary = StrawberryTextDark,
    background = Color.White,
    onBackground = StrawberryTextDark,
    surface = Color(0xFFFFFBFA),
    onSurface = StrawberryTextDark,
    error = Color(0xFFE53935),
    onError = Color.White,
    outline = StrawberryPink
)

val StrawberryMilkDarkColors = darkColorScheme(
    primary = StrawberryDarkPink,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFAD1457),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFFF06292),
    onSecondary = Color.Black,
    background = Color(0xFF3C2F2F),
    onBackground = Color(0xFFFFEBEE),
    surface = Color(0xFF4E342E),
    onSurface = Color(0xFFFFEBEE),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    outline = StrawberryLightPink
)


// --- Strawberry & Kiwi Theme (Pink/Lite Green) ---
val StrawberryRedPink = Color(0xFFEC407A)
val KiwiLiteGreen = Color(0xFFAED581)
val KiwiDarkGreen = Color(0xFF689F38)

val StrawberryKiwiLightColors = lightColorScheme(
    primary = StrawberryRedPink,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF8BBD0),
    onPrimaryContainer = Color.Black,
    secondary = KiwiLiteGreen,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFFDCEDC8),
    onSecondaryContainer = Color.Black,
    background = Color(0xFFFFFDE7),
    onBackground = Color(0xFF424242),
    surface = Color.White,
    onSurface = Color(0xFF424242),
    error = Color(0xFFD32F2F),
    onError = Color.White,
    outline = KiwiDarkGreen
)

val StrawberryKiwiDarkColors = darkColorScheme(
    primary = Color(0xFFF48FB1),
    onPrimary = Color.Black,
    primaryContainer = StrawberryRedPink,
    onPrimaryContainer = Color.White,
    secondary = KiwiDarkGreen,
    onSecondary = Color.White,
    secondaryContainer = KiwiLiteGreen,
    onSecondaryContainer = Color.Black,
    background = Color(0xFF263238),
    onBackground = Color(0xFFECEFF1),
    surface = Color(0xFF37474F),
    onSurface = Color(0xFFECEFF1),
    error = Color(0xFFFF9E80),
    onError = Color.Black,
    outline = KiwiLiteGreen
)

// --- Solarized Theme ---
// Light Palette
val SolarizedBase03 = Color(0xFF002b36) // For dark text on light background
val SolarizedBase02 = Color(0xFF073642) // For darker dark text
val SolarizedBase3 = Color(0xFFfdf6e3)  // Light Background
val SolarizedBase2 = Color(0xFFeee8d5)  // Lighter Light Background (Surface)
val SolarizedYellow = Color(0xFFb58900)
val SolarizedOrange = Color(0xFFcb4b16)
val SolarizedBlue = Color(0xFF268bd2)
val SolarizedCyan = Color(0xFF2aa198)

// Dark Palette (some are same as above, just used differently)
val SolarizedBase0 = Color(0xFF839496)  // Light text on dark background
val SolarizedBase1 = Color(0xFF93a1a1)  // Lighter light text

val SolarizedLightColors = lightColorScheme(
    primary = SolarizedBlue,
    onPrimary = SolarizedBase3,
    primaryContainer = SolarizedCyan,
    onPrimaryContainer = SolarizedBase03,
    secondary = SolarizedOrange,
    onSecondary = SolarizedBase3,
    secondaryContainer = SolarizedYellow,
    onSecondaryContainer = SolarizedBase03,
    tertiary = SolarizedBlue, // Re-using for simplicity
    onTertiary = SolarizedBase3,
    background = SolarizedBase3,
    onBackground = SolarizedBase02, // Darker text for better contrast
    surface = SolarizedBase2,
    onSurface = SolarizedBase02,
    surfaceVariant = Color(0xFFE0D8C5), // Derived
    onSurfaceVariant = SolarizedBase02,
    error = Color(0xFFdc322f), // Solarized Red
    onError = SolarizedBase3,
    outline = SolarizedBase0
)

val SolarizedDarkColors = darkColorScheme(
    primary = SolarizedBlue,
    onPrimary = SolarizedBase03,
    primaryContainer = SolarizedCyan,
    onPrimaryContainer = SolarizedBase3,
    secondary = SolarizedOrange,
    onSecondary = SolarizedBase03,
    secondaryContainer = SolarizedYellow,
    onSecondaryContainer = SolarizedBase3,
    tertiary = SolarizedBlue,
    onTertiary = SolarizedBase03,
    background = SolarizedBase03,
    onBackground = SolarizedBase0, // Lighter text
    surface = SolarizedBase02,
    onSurface = SolarizedBase0,
    surfaceVariant = Color(0xFF0A404D), // Derived
    onSurfaceVariant = SolarizedBase0,
    error = Color(0xFFdc322f), // Solarized Red
    onError = SolarizedBase03, // Dark text on red for dark theme
    outline = SolarizedBase0
)

// --- Nordic Night Theme ---
// Polar Night (Dark Backgrounds)
val Nord0 = Color(0xFF2E3440) // Darkest
val Nord1 = Color(0xFF3B4252)
val Nord2 = Color(0xFF434C5E)
val Nord3 = Color(0xFF4C566A) // Lighter Dark

// Snow Storm (Light Backgrounds/Text)
val Nord4 = Color(0xFFD8DEE9) // Lightest Gray/Off-white
val Nord5 = Color(0xFFE5E9F0)
val Nord6 = Color(0xFFECEFF4) // Purest White/Lightest

// Frost (Blues)
val Nord7 = Color(0xFF8FBCBB) // Muted Cyan/Greenish Blue
val Nord8 = Color(0xFF88C0D0) // Light Blue
val Nord9 = Color(0xFF81A1C1) // Blue
val Nord10 = Color(0xFF5E81AC) // Darker Blue (Primary for light)

// Aurora (Accents)
val Nord11 = Color(0xFFBF616A) // Red
val Nord12 = Color(0xFFD08770) // Orange
val Nord13 = Color(0xFFEBCB8B) // Yellow
val Nord14 = Color(0xFFA3BE8C) // Green
val Nord15 = Color(0xFFB48EAD) // Purple

val NordicNightLightColors = lightColorScheme(
    primary = Nord10, // Darker Blue
    onPrimary = Nord6, // White text
    primaryContainer = Nord8, // Light Blue
    onPrimaryContainer = Nord0, // Dark text
    secondary = Nord14, // Aurora Green
    onSecondary = Nord0,
    secondaryContainer = Color(0xFFC8E6C9), // Lighter green
    onSecondaryContainer = Nord0,
    tertiary = Nord7, // Muted Cyan
    onTertiary = Nord0,
    background = Nord6, // Purest White/Lightest
    onBackground = Nord0,
    surface = Nord5, // Slightly off-white
    onSurface = Nord0,
    surfaceVariant = Nord4,
    onSurfaceVariant = Nord0,
    error = Nord11, // Red
    onError = Nord6,
    outline = Nord3
)

val NordicNightDarkColors = darkColorScheme(
    primary = Nord8, // Light Blue
    onPrimary = Nord0, // Dark text
    primaryContainer = Nord10, // Darker Blue
    onPrimaryContainer = Nord6, // White text
    secondary = Nord14, // Aurora Green
    onSecondary = Nord0,
    secondaryContainer = Color(0xFF38703A), // Darker green
    onSecondaryContainer = Nord6,
    tertiary = Nord7, // Muted Cyan
    onTertiary = Nord0,
    background = Nord0, // Darkest Polar Night
    onBackground = Nord6,
    surface = Nord1, // Slightly lighter Polar Night
    onSurface = Nord6,
    surfaceVariant = Nord2,
    onSurfaceVariant = Nord6,
    error = Nord11, // Red
    onError = Nord6,
    outline = Nord4
)


// Default theme (you can set this to any of your themes, e.g., Classic)
val DefaultLightColors = ClassicLightColors
val DefaultDarkColors = ClassicDarkColors

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)