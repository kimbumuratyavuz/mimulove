package com.example.ui.theme

import androidx.compose.ui.graphics.Color

enum class HeartColor(
    val title: String,
    val primaryColor: Color,
    val gradientColors: List<Color>
) {
    RUBY_RED(
        title = "Yakut Kırmızısı",
        primaryColor = Color(0xFFE11D48),
        gradientColors = listOf(Color(0xFFFF4D6D), Color(0xFFE11D48), Color(0xFF9F1239))
    ),
    ROSE_PINK(
        title = "Gül Pembesi",
        primaryColor = Color(0xFFFB7185),
        gradientColors = listOf(Color(0xFFFF85A1), Color(0xFFFB7185), Color(0xFFBE123C))
    ),
    PURPLE_PASSION(
        title = "Büyülü Mor",
        primaryColor = Color(0xFF9333EA),
        gradientColors = listOf(Color(0xFFC084FC), Color(0xFF9333EA), Color(0xFF581C87))
    ),
    SUNSET_GOLD(
        title = "Gün Batımı",
        primaryColor = Color(0xFFF97316),
        gradientColors = listOf(Color(0xFFFBBF24), Color(0xFFF97316), Color(0xFFC2410C))
    ),
    OCEAN_BLUE(
        title = "Sonsuz Mavi",
        primaryColor = Color(0xFF0284C7),
        gradientColors = listOf(Color(0xFF38BDF8), Color(0xFF0284C7), Color(0xFF075985))
    ),
    EMERALD_MINT(
        title = "Zümrüt Bağı",
        primaryColor = Color(0xFF059669),
        gradientColors = listOf(Color(0xFF34D399), Color(0xFF059669), Color(0xFF064E3B))
    );

    companion object {
        fun fromName(name: String?): HeartColor {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: RUBY_RED
        }
    }
}
