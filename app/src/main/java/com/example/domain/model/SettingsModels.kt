package com.example.domain.model

enum class ThemeMode(val label: String) {
    SYSTEM("Follow System"),
    DARK("Dark Obsidian"),
    LIGHT("Light Alabaster")
}

enum class AccentPreset(val label: String, val hex: Long) {
    VIOLET("Violet", 0xFF8B5CF6),
    CYAN("Cyan", 0xFF06B6D4),
    ROSE("Rose", 0xFFF43F5E),
    AMBER("Amber", 0xFFF59E0B),
    EMERALD("Emerald", 0xFF10B981),
    DYNAMIC("Dynamic Color", 0xFF8B5CF6)
}

enum class ThumbnailQuality(val label: String, val maxPx: Int) {
    STANDARD("Balanced (Fast)", 360),
    HIGH("High Detail", 640)
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val accentPreset: AccentPreset = AccentPreset.VIOLET,
    val gridColumns: Int = 3,
    val viewMode: GalleryViewMode = GalleryViewMode.GRID,
    val thumbnailQuality: ThumbnailQuality = ThumbnailQuality.STANDARD,
    val animationsEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val defaultSort: MediaSortOrder = MediaSortOrder.DATE_DESC,
    val vaultAutoLockSeconds: Int = 0, // 0 = lock immediately on background, -1 = manual
    val biometricEnabled: Boolean = true,
    val trashRetentionDays: Int = 30
)
