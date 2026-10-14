package io.github.fershibli.megagames.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import io.github.fershibli.megagames.core.ColorMode

/**
 * All colors used by the app and by the game art. There are six palettes: three color modes
 * (normal / high contrast / colorblind-friendly), each with its own light and dark variant.
 */
@Immutable
data class Palette(
    val isDark: Boolean,
    val mode: ColorMode,
    // App chrome
    val background: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val primary: Color,
    val onPrimary: Color,
    val text: Color,
    val textMuted: Color,
    val outline: Color,
    val success: Color,
    val danger: Color,
    val highlight: Color,
    val hint: Color,
    // Boards
    val felt: Color,
    val feltDark: Color,
    val wood: Color,
    val woodDark: Color,
    val boardLight: Color,
    val boardDark: Color,
    val boardLine: Color,
    // Playing cards
    val cardFace: Color,
    val cardEdge: Color,
    val cardBack: Color,
    val cardBackAccent: Color,
    val suitHearts: Color,
    val suitDiamonds: Color,
    val suitSpades: Color,
    val suitClubs: Color,
    // Mahjong tiles
    val tileFace: Color,
    val tileSide: Color,
    val tileSideDark: Color,
    // Backgammon
    val pointA: Color,
    val pointB: Color,
    val checkerPlayer: Color,
    val checkerAi: Color,
    // Pipes
    val pipe: Color,
    val water: Color,
    // Up to 9 distinct piece colors for Connect / Dots
    val pieces: List<Color>,
    /** Draw shapes/symbols on top of colors so color is never the only cue. */
    val symbols: Boolean,
    /** Draw thicker outlines everywhere. */
    val strongOutlines: Boolean,
) {
    companion object {
        fun of(mode: ColorMode, dark: Boolean): Palette = when (mode) {
            ColorMode.NORMAL -> if (dark) NormalDark else NormalLight
            ColorMode.HIGH_CONTRAST -> if (dark) HighContrastDark else HighContrastLight
            ColorMode.COLORBLIND -> if (dark) ColorblindDark else ColorblindLight
        }
    }
}

private val classicPieces = listOf(
    Color(0xFFD64545), Color(0xFF3B7DD8), Color(0xFF3FA34D), Color(0xFFF2C230), Color(0xFF8E5BD6),
    Color(0xFFF08A24), Color(0xFF2BB3C0), Color(0xFFE26BA8), Color(0xFF8B5A2B),
)

// Okabe & Ito (2008) palette, distinguishable with all common types of color vision deficiency.
private val okabeIto = listOf(
    Color(0xFFE69F00), Color(0xFF56B4E9), Color(0xFF009E73), Color(0xFFF0E442), Color(0xFF0072B2),
    Color(0xFFD55E00), Color(0xFFCC79A7), Color(0xFF999999), Color(0xFF332288),
)

val NormalLight = Palette(
    isDark = false, mode = ColorMode.NORMAL,
    background = Color(0xFFF3EEE4), surface = Color(0xFFFCFAF5), surfaceAlt = Color(0xFFE6DFD2),
    primary = Color(0xFF2E6B8A), onPrimary = Color.White, text = Color(0xFF2A2724),
    textMuted = Color(0xFF5F5953), outline = Color(0xFF9B9186), success = Color(0xFF2F8A4A),
    danger = Color(0xFFC0392B), highlight = Color(0xFFFFE08A), hint = Color(0xFF7FC8A9),
    felt = Color(0xFF2F6B4F), feltDark = Color(0xFF214D39), wood = Color(0xFFC9A06A), woodDark = Color(0xFF8A6239),
    boardLight = Color(0xFFFFFDF8), boardDark = Color(0xFFE9E1D3), boardLine = Color(0xFF4A443D),
    cardFace = Color(0xFFFFFEFA), cardEdge = Color(0xFFB9B0A3), cardBack = Color(0xFF2E5E8A), cardBackAccent = Color(0xFF9CC3E4),
    suitHearts = Color(0xFFC62828), suitDiamonds = Color(0xFFC62828), suitSpades = Color(0xFF1F1F1F), suitClubs = Color(0xFF1F1F1F),
    tileFace = Color(0xFFFBF5E6), tileSide = Color(0xFF3E8E6A), tileSideDark = Color(0xFF2B6A4D),
    pointA = Color(0xFF9C3B2E), pointB = Color(0xFFE8D7B5), checkerPlayer = Color(0xFFF7F1E3), checkerAi = Color(0xFF3A2A22),
    pipe = Color(0xFF8C939B), water = Color(0xFF2F8FE0),
    pieces = classicPieces, symbols = false, strongOutlines = false,
)

val NormalDark = NormalLight.copy(
    isDark = true,
    background = Color(0xFF1C1F24), surface = Color(0xFF272B32), surfaceAlt = Color(0xFF333842),
    primary = Color(0xFF7DB8D6), onPrimary = Color(0xFF0E2633), text = Color(0xFFECE7DF),
    textMuted = Color(0xFFB4ADA3), outline = Color(0xFF6E6860), success = Color(0xFF6CC48A),
    danger = Color(0xFFFF7B6B), highlight = Color(0xFF8A6D1E), hint = Color(0xFF3F8C6C),
    felt = Color(0xFF1F4A37), feltDark = Color(0xFF153527), wood = Color(0xFF7A5A3A), woodDark = Color(0xFF4E3822),
    boardLight = Color(0xFF2F343C), boardDark = Color(0xFF262A31), boardLine = Color(0xFFCFC8BE),
    cardFace = Color(0xFFF1ECE2), cardEdge = Color(0xFF8D857A), cardBack = Color(0xFF2B4F73), cardBackAccent = Color(0xFF6E99C0),
    tileFace = Color(0xFFE8E0CC), tileSide = Color(0xFF2E6E52), tileSideDark = Color(0xFF1E4B37),
    pointA = Color(0xFF8A3A2E), pointB = Color(0xFFB9A684), checkerPlayer = Color(0xFFEDE5D3), checkerAi = Color(0xFF2A1D17),
    pipe = Color(0xFF6B737C), water = Color(0xFF4AA3F0),
)

val HighContrastLight = Palette(
    isDark = false, mode = ColorMode.HIGH_CONTRAST,
    background = Color(0xFFFFFFFF), surface = Color(0xFFFFFFFF), surfaceAlt = Color(0xFFE8E8E8),
    primary = Color(0xFF0033A0), onPrimary = Color.White, text = Color(0xFF000000),
    textMuted = Color(0xFF1A1A1A), outline = Color(0xFF000000), success = Color(0xFF006B21),
    danger = Color(0xFFB00000), highlight = Color(0xFFFFE600), hint = Color(0xFF00C36B),
    felt = Color(0xFF0B5D2A), feltDark = Color(0xFF053A19), wood = Color(0xFFB07A3B), woodDark = Color(0xFF5A3A12),
    boardLight = Color(0xFFFFFFFF), boardDark = Color(0xFFDADADA), boardLine = Color(0xFF000000),
    cardFace = Color(0xFFFFFFFF), cardEdge = Color(0xFF000000), cardBack = Color(0xFF0033A0), cardBackAccent = Color(0xFFFFFFFF),
    suitHearts = Color(0xFFC00000), suitDiamonds = Color(0xFFC00000), suitSpades = Color(0xFF000000), suitClubs = Color(0xFF000000),
    tileFace = Color(0xFFFFFFFF), tileSide = Color(0xFF00703A), tileSideDark = Color(0xFF003D1F),
    pointA = Color(0xFF8B0000), pointB = Color(0xFFF2E3C2), checkerPlayer = Color(0xFFFFFFFF), checkerAi = Color(0xFF000000),
    pipe = Color(0xFF404040), water = Color(0xFF0057E7),
    pieces = listOf(
        Color(0xFFD00000), Color(0xFF0040FF), Color(0xFF008A2E), Color(0xFFFFC400), Color(0xFF7A00C2),
        Color(0xFFFF6A00), Color(0xFF00A3B5), Color(0xFFFF2E9A), Color(0xFF6B3A00),
    ),
    symbols = true, strongOutlines = true,
)

val HighContrastDark = HighContrastLight.copy(
    isDark = true,
    background = Color(0xFF000000), surface = Color(0xFF0D0D0D), surfaceAlt = Color(0xFF262626),
    primary = Color(0xFFFFD400), onPrimary = Color.Black, text = Color(0xFFFFFFFF),
    textMuted = Color(0xFFEDEDED), outline = Color(0xFFFFFFFF), success = Color(0xFF3DFF7A),
    danger = Color(0xFFFF5A5A), highlight = Color(0xFF0060FF), hint = Color(0xFF00E08A),
    felt = Color(0xFF003D1C), feltDark = Color(0xFF00210F), wood = Color(0xFF5A3A12), woodDark = Color(0xFF2E1D08),
    boardLight = Color(0xFF000000), boardDark = Color(0xFF1F1F1F), boardLine = Color(0xFFFFFFFF),
    cardFace = Color(0xFFFFFFFF), cardEdge = Color(0xFFFFFFFF), cardBack = Color(0xFFFFD400), cardBackAccent = Color(0xFF000000),
    tileFace = Color(0xFFFFFFFF), tileSide = Color(0xFF00A651), tileSideDark = Color(0xFF00703A),
    pointA = Color(0xFFFF8C00), pointB = Color(0xFF4D4D4D), checkerPlayer = Color(0xFFFFFFFF), checkerAi = Color(0xFFFF2E2E),
    pipe = Color(0xFFBFBFBF), water = Color(0xFF00C8FF),
    pieces = listOf(
        Color(0xFFFF3B3B), Color(0xFF4D8DFF), Color(0xFF2EE65C), Color(0xFFFFE100), Color(0xFFC77DFF),
        Color(0xFFFF8C1A), Color(0xFF00E5FF), Color(0xFFFF66C4), Color(0xFFD2A26B),
    ),
)

val ColorblindLight = Palette(
    isDark = false, mode = ColorMode.COLORBLIND,
    background = Color(0xFFF5F3EE), surface = Color(0xFFFFFFFF), surfaceAlt = Color(0xFFE4E1DA),
    primary = Color(0xFF0072B2), onPrimary = Color.White, text = Color(0xFF1E1E1E),
    textMuted = Color(0xFF4F4F4F), outline = Color(0xFF7A7A7A), success = Color(0xFF0072B2),
    danger = Color(0xFFD55E00), highlight = Color(0xFFF0E442), hint = Color(0xFF56B4E9),
    felt = Color(0xFF2C5D7C), feltDark = Color(0xFF1D4057), wood = Color(0xFFC9A06A), woodDark = Color(0xFF7D5A35),
    boardLight = Color(0xFFFFFFFF), boardDark = Color(0xFFE6E3DC), boardLine = Color(0xFF333333),
    cardFace = Color(0xFFFFFFFF), cardEdge = Color(0xFF9A9A9A), cardBack = Color(0xFF6A4C93), cardBackAccent = Color(0xFFD7C6EE),
    // Four-color deck: every suit has its own color and shape.
    suitHearts = Color(0xFFD55E00), suitDiamonds = Color(0xFF0072B2), suitSpades = Color(0xFF1E1E1E), suitClubs = Color(0xFF009E73),
    tileFace = Color(0xFFFFFBF0), tileSide = Color(0xFF0072B2), tileSideDark = Color(0xFF004E7A),
    pointA = Color(0xFFD55E00), pointB = Color(0xFF56B4E9), checkerPlayer = Color(0xFFFFFFFF), checkerAi = Color(0xFF1E1E1E),
    pipe = Color(0xFF8A8A8A), water = Color(0xFF0072B2),
    pieces = okabeIto, symbols = true, strongOutlines = false,
)

val ColorblindDark = ColorblindLight.copy(
    isDark = true,
    background = Color(0xFF17191E), surface = Color(0xFF23262D), surfaceAlt = Color(0xFF30343C),
    primary = Color(0xFF56B4E9), onPrimary = Color(0xFF0B1E2A), text = Color(0xFFF0F0F0),
    textMuted = Color(0xFFB8B8B8), outline = Color(0xFF6F6F6F), success = Color(0xFF56B4E9),
    danger = Color(0xFFE69F00), highlight = Color(0xFF7A6F00), hint = Color(0xFF0072B2),
    felt = Color(0xFF1D3B50), feltDark = Color(0xFF122838), wood = Color(0xFF6E5233), woodDark = Color(0xFF45321D),
    boardLight = Color(0xFF2A2E35), boardDark = Color(0xFF22252B), boardLine = Color(0xFFDADADA),
    cardFace = Color(0xFFF3F3F3), cardEdge = Color(0xFF7C7C7C), cardBack = Color(0xFF4E3870), cardBackAccent = Color(0xFFAE97CF),
    suitHearts = Color(0xFFD55E00), suitDiamonds = Color(0xFF0072B2), suitSpades = Color(0xFF1E1E1E), suitClubs = Color(0xFF00805C),
    tileFace = Color(0xFFEDE7D6), tileSide = Color(0xFF0072B2), tileSideDark = Color(0xFF004E7A),
    pointA = Color(0xFFB04E00), pointB = Color(0xFF3D7FA6), checkerPlayer = Color(0xFFF0F0F0), checkerAi = Color(0xFF111111),
    pipe = Color(0xFF6F6F6F), water = Color(0xFF56B4E9),
)
