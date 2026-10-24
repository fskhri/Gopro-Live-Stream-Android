package com.gopro.rebuild.camera

/**
 * GoPro camera model mapping reverse-engineered from GoPro Quik APK (CameraModel.java).
 * Maps hardware model IDs, model numbers, firmware prefixes, and BLE names to human-readable names.
 */
enum class GoProModel(val modelNumber: Int, val modelString: String, val displayName: String) {
    HERO2(1, "HD2.01", "HERO2"),
    HERO3_WHITE(2, "HD3.01", "HERO3 White"),
    HERO3_SILVER(3, "HD3.02", "HERO3 Silver"),
    HERO3_BLACK(4, "HD3.03", "HERO3 Black"),
    HERO3_PLUS_SILVER(10, "HD3.10", "HERO3+ Silver"),
    HERO3_PLUS_BLACK(11, "HD3.11", "HERO3+ Black"),
    HERO4_SILVER(12, "HD4.01", "HERO4 Silver"),
    HERO4_BLACK(13, "HD4.02", "HERO4 Black"),
    HERO(14, "HD3.20", "HERO"),
    HERO_PLUS_LCD(15, "HD3.21", "HERO+ LCD"),
    HERO4_SESSION(16, "HX1.01", "HERO4 Session"),
    HERO_PLUS(17, "HD3.22", "HERO+"),
    HERO5_BLACK(19, "HD5.02", "HERO5 Black"),
    HERO5_SESSION(21, "HD5.03", "HERO5 Session"),
    FUSION(22, "FS1.04", "Fusion"),
    HERO6_BLACK(24, "HD6.01", "HERO6 Black"),
    HERO7_BLACK(30, "HD7.01", "HERO7 Black"),
    HERO7_WHITE(32, "H18.02", "HERO7 White"),
    HERO7_SILVER(33, "H18.03", "HERO7 Silver"),
    HERO_2018(34, "H18.01", "HERO"),
    HERO8_BLACK(50, "HD8.01", "HERO8 Black"),
    MAX(51, "H19.03", "MAX"),
    HERO9_BLACK(55, "HD9.01", "HERO9 Black"),
    HERO10_BLACK(57, "H21.01", "HERO10 Black"),
    HERO11_BLACK(58, "H22.01", "HERO11 Black"),
    HERO11_BLACK_MINI(60, "H22.03", "HERO11 Black Mini"),
    HERO12_BLACK(62, "H23.01", "HERO12 Black"),
    HERO13_BLACK(65, "H24.01", "HERO13 Black"),
    HERO_2024(66, "H24.03", "HERO"),
    LIT_HERO(70, "H25.03", "LIT HERO"),
    PEBBLES(64, "H24.02", "MAX2"),
    SANDBAR(69, "H26.01", "MISSION 1 PRO"),
    SANDBAR_LITE(71, "H26.02", "MISSION 1");

    companion object {
        fun fromModelNumber(number: Int): GoProModel? =
            entries.firstOrNull { it.modelNumber == number }

        fun fromModelString(str: String): GoProModel? {
            val clean = str.trim()
            // 1. Exact modelString match (e.g. "HD6.01")
            entries.firstOrNull { it.modelString.equals(clean, ignoreCase = true) }?.let { return it }

            // 2. Exact displayName match (e.g. "HERO6 Black")
            entries.firstOrNull { it.displayName.equals(clean, ignoreCase = true) }?.let { return it }

            // 3. Hexadecimal model number (e.g. "00000018" -> 24 = HERO6 Black)
            clean.toIntOrNull(16)?.let { hexVal ->
                fromModelNumber(hexVal)?.let { return it }
            }

            // 4. Decimal model number (e.g. "24")
            clean.toIntOrNull(10)?.let { decVal ->
                fromModelNumber(decVal)?.let { return it }
            }

            return null
        }

        fun getDisplayName(raw: String?, fallback: String = "GoPro"): String {
            if (raw.isNullOrBlank()) return fallback
            val trimmed = raw.trim()

            // Try direct mapping
            val model = fromModelString(trimmed)
            if (model != null) return model.displayName

            // Try prefix matching against firmware strings (e.g. "HD6.01.02.10" -> "HERO6 Black")
            for (entry in entries) {
                if (trimmed.startsWith(entry.modelString, ignoreCase = true)) {
                    return entry.displayName
                }
            }

            // If raw looks like hex garbage (e.g. "00000018"), avoid showing it to user
            if (trimmed.matches(Regex("^[0-9a-fA-F]{6,}$"))) {
                return fallback
            }

            return trimmed
        }
    }
}
