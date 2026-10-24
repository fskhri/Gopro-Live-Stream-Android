package com.gopro.rebuild.camera

/**
 * Camera settings and presets
 * Based on GoPro Open API spec
 */

// === Video Resolutions ===
enum class VideoResolution(val id: Int, val label: String) {
    RES_5_3K(100, "5.3K"),
    RES_4K(1, "4K"),
    RES_4K_4_3(18, "4K 4:3"),
    RES_2_7K(4, "2.7K"),
    RES_2_7K_4_3(6, "2.7K 4:3"),
    RES_1080(9, "1080p"),
    RES_1080_4_3(28, "1080p 4:3"),
    RES_720(12, "720p");

    companion object {
        fun fromId(id: Int) = entries.find { it.id == id } ?: RES_4K
    }
}

// === Frame Rates ===
enum class FrameRate(val id: Int, val label: String) {
    FPS_240(0, "240"),
    FPS_200(13, "200"),
    FPS_120(1, "120"),
    FPS_100(2, "100"),
    FPS_60(5, "60"),
    FPS_50(6, "50"),
    FPS_30(8, "30"),
    FPS_25(9, "25"),
    FPS_24(10, "24");

    companion object {
        fun fromId(id: Int) = entries.find { it.id == id } ?: FPS_30
    }
}

// === Field of View / Lens ===
enum class FieldOfView(val id: Int, val label: String) {
    HYPERVIEW(9, "HyperView"),
    SUPERVIEW(3, "SuperView"),
    WIDE(0, "Wide"),
    LINEAR(4, "Linear"),
    LINEAR_HORIZON_LEVELING(8, "Linear+HL"),
    NARROW(2, "Narrow");

    companion object {
        fun fromId(id: Int) = entries.find { it.id == id } ?: WIDE
    }
}

// === HyperSmooth ===
enum class HyperSmooth(val id: Int, val label: String) {
    OFF(0, "Off"),
    ON(1, "On"),
    HIGH(2, "High"),
    BOOST(3, "Boost"),
    AUTO_BOOST(4, "Auto Boost");

    companion object {
        fun fromId(id: Int) = entries.find { it.id == id } ?: ON
    }
}

// === Photo Modes ===
enum class PhotoMode(val id: Int, val label: String) {
    SUPER_PHOTO(0, "SuperPhoto"),
    HDR(1, "HDR"),
    STANDARD(2, "Standard"),
    RAW(3, "RAW");

    companion object {
        fun fromId(id: Int) = entries.find { it.id == id } ?: STANDARD
    }
}

// === Timelapse Intervals ===
enum class TimelapseInterval(val id: Int, val label: String) {
    INTERVAL_0_5(0, "0.5s"),
    INTERVAL_1(1, "1s"),
    INTERVAL_2(2, "2s"),
    INTERVAL_5(3, "5s"),
    INTERVAL_10(4, "10s"),
    INTERVAL_30(5, "30s"),
    INTERVAL_60(6, "60s");

    companion object {
        fun fromId(id: Int) = entries.find { it.id == id } ?: INTERVAL_1
    }
}

// === Protune Settings ===
enum class ProtuneColor(val id: Int, val label: String) {
    VIBRANT(0, "Vibrant"),
    NATURAL(1, "Natural"),
    FLAT(2, "Flat");
}

enum class WhiteBalance(val id: Int, val label: String) {
    AUTO(0, "Auto"),
    WB_3000K(1, "3000K"),
    WB_4000K(5, "4000K"),
    WB_4800K(6, "4800K"),
    WB_5500K(2, "5500K"),
    WB_6000K(7, "6000K"),
    WB_6500K(3, "6500K"),
    NATIVE(4, "Native");
}

enum class ISO(val id: Int, val label: String) {
    ISO_100(0, "100"),
    ISO_200(1, "200"),
    ISO_400(2, "400"),
    ISO_800(3, "800"),
    ISO_1600(4, "1600"),
    ISO_3200(5, "3200"),
    ISO_6400(6, "6400");
}

enum class Sharpness(val id: Int, val label: String) {
    HIGH(0, "High"),
    MEDIUM(1, "Medium"),
    LOW(2, "Low");
}

enum class EVComp(val id: Int, val label: String) {
    MINUS_2(-4, "-2.0"),
    MINUS_1_5(-3, "-1.5"),
    MINUS_1(-2, "-1.0"),
    MINUS_0_5(-1, "-0.5"),
    ZERO(0, "0"),
    PLUS_0_5(1, "+0.5"),
    PLUS_1(2, "+1.0"),
    PLUS_1_5(3, "+1.5"),
    PLUS_2(4, "+2.0");
}

// === Hindsight ===
enum class Hindsight(val id: Int, val label: String) {
    OFF(0, "Off"),
    SEC_15(1, "15s"),
    SEC_30(2, "30s");

    companion object {
        fun fromId(id: Int) = entries.find { it.id == id } ?: OFF
    }
}

// === Duration / Timer ===
enum class Duration(val id: Int, val label: String) {
    NO_LIMIT(0, "No Limit"),
    SEC_15(1, "15s"),
    SEC_30(2, "30s"),
    MIN_1(3, "1 min"),
    MIN_5(4, "5 min"),
    MIN_15(5, "15 min"),
    MIN_30(6, "30 min"),
    HOUR_1(7, "1 hour"),
    HOUR_2(8, "2 hours"),
    HOUR_3(9, "3 hours");
}

enum class Timer(val id: Int, val label: String) {
    OFF(0, "Off"),
    SEC_3(1, "3s"),
    SEC_10(2, "10s");
}

// === Setting IDs ===
object SettingIds {
    const val RESOLUTION = 2
    const val FPS = 3
    const val FOV = 121
    const val HYPERSMOOTH = 135
    const val HINDSIGHT = 167
    const val DURATION = 172
    const val TIMER = 173
    const val PHOTO_MODE = 191
    const val TIMELAPSE_INTERVAL = 30
    const val PROTUNE = 114
    const val WHITE_BALANCE = 115
    const val COLOR = 116
    const val ISO_MAX = 102
    const val ISO_MIN = 75
    const val SHARPNESS = 117
    const val EV_COMP = 118
    const val RAW_AUDIO = 139
    const val WIND_NOISE = 168
}

// === Status IDs ===
object StatusIds {
    const val INTERNAL_BATTERY_PRESENT = 1
    const val INTERNAL_BATTERY_LEVEL = 2
    const val EXTERNAL_BATTERY_PRESENT = 3
    const val EXTERNAL_BATTERY_LEVEL = 4
    const val SYSTEM_HOT = 6
    const val SYSTEM_BUSY = 8
    const val QUICK_CAPTURE = 9
    const val ENCODING_ACTIVE = 10
    const val LCD_LOCK_ACTIVE = 11
    const val CAMERA_LOCATE_ACTIVE = 45
    const val SD_CARD_STATUS = 33
    const val REMAINING_PHOTOS = 34
    const val REMAINING_VIDEO_TIME = 35
    const val NUM_TOTAL_PHOTOS = 38
    const val NUM_TOTAL_VIDEOS = 39
    const val CURRENT_TIME_MSEC = 57
    const val REMAINING_SPACE = 54
    const val NUM_GROUP_PHOTOS = 73
    const val NUM_GROUP_VIDEOS = 74
    const val BATTERY_LEVEL = 70
    const val CURRENT_MODE = 89
    const val CURRENT_SUBMODE = 90
    const val CURRENT_PRESET = 97
    const val PRESET_GROUP = 98
    const val MEDIA_MOD_STATUS = 102
    const val TURBO_TRANSFER = 113
    const val AP_STATE = 114
    const val SCHEDULED_CAPTURE = 123
}

// === Preset Groups ===
enum class PresetGroup(val id: Int, val label: String) {
    VIDEO(1000, "Video"),
    PHOTO(1001, "Photo"),
    TIMELAPSE(1002, "Timelapse");

    companion object {
        fun fromId(id: Int) = entries.find { it.id == id } ?: VIDEO
    }
}

// === Preset Icons ===
enum class PresetIcon(val id: Int, val label: String) {
    VIDEO(0, "Video"),
    TIMELAPSE(1, "Timelapse"),
    NIGHTLAPSE(2, "Night Lapse"),
    PHOTO(3, "Photo"),
    NIGHT_PHOTO(4, "Night Photo"),
    BURST(5, "Burst"),
    STANDARD(70, "Standard"),
    ACTIVITY(71, "Activity"),
    CINEMATIC(72, "Cinematic"),
    ULTRA_SLOMO(73, "Ultra Slo-Mo"),
    BASIC(74, "Basic"),
    PHOTO_2(75, "Photo"),
    LIVEBURST(76, "LiveBurst"),
    NIGHT_PHOTO_2(77, "Night Photo"),
    TIMEWARP(78, "TimeWarp"),
    TIMELAPSE_2(79, "Timelapse"),
    NIGHTLAPSE_2(80, "Night Lapse"),
    STAR_TRAILS(81, "Star Trails"),
    LIGHT_PAINTING(82, "Light Painting"),
    LIGHT_TRAILS(83, "Light Trails"),
    FULL_FRAME(84, "Full Frame");
}
