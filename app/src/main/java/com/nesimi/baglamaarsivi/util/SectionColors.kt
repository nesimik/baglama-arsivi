package com.nesimi.baglamaarsivi.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

data class SectionStyle(
    val section: Int?,
    val container: Color,
    val border: Color,
    val badgeBg: Color,
    val badgeText: Color,
    val name: String
)

/**
 * Bölüm renkleri (1. bölüm mavi, 2. yeşil ...). Kart zeminleri bilerek AÇIK tutuldu:
 * aydınlık modda pastel, karanlık modda bile orta tonlu (simsiyah değil).
 */
object SectionColors {
    private val hues = listOf(
        Color(0xFF3B82F6), // mavi
        Color(0xFF22C55E), // yeşil
        Color(0xFFF59E0B), // amber
        Color(0xFFA855F7), // mor
        Color(0xFF14B8A6), // turkuaz
        Color(0xFFF43F5E), // gül
        Color(0xFF6366F1), // çivit
        Color(0xFF84CC16)  // fıstık
    )

    private val darkBase = Color(0xFF3A3430)

    fun forTag(tag: String, isDark: Boolean): SectionStyle = forSection(VideoOrder.section(tag), isDark)

    fun forSection(section: Int?, isDark: Boolean): SectionStyle {
        if (section == null || section <= 0) {
            return if (isDark) SectionStyle(null, Color(0xFF3A3430), Color(0xFF574D46), Color(0xFF4A413B), Color(0xFFE8DDD4), "Numarasız")
            else SectionStyle(null, Color(0xFFFFFFFF), Color(0xFFE8DED5), Color(0xFFF3ECE4), Color(0xFF6B5E55), "Numarasız")
        }
        val hue = hues[(section - 1) % hues.size]
        return if (isDark) {
            SectionStyle(
                section = section,
                container = lerp(darkBase, hue, 0.22f),
                border = lerp(darkBase, hue, 0.55f),
                badgeBg = lerp(darkBase, hue, 0.70f),
                badgeText = Color.White,
                name = "$section. Bölüm"
            )
        } else {
            SectionStyle(
                section = section,
                container = lerp(Color.White, hue, 0.07f),
                border = lerp(Color.White, hue, 0.35f),
                badgeBg = lerp(Color.White, hue, 0.18f),
                badgeText = lerp(Color.Black, hue, 0.75f),
                name = "$section. Bölüm"
            )
        }
    }
}
