package com.twinndroid.data

import kotlinx.serialization.Serializable

@Serializable
enum class SourceKind {
    /** Cloned from an app installed on this device; can auto-update when the original updates. */
    INSTALLED,

    /** Cloned from an imported .apk/.apks/.xapk/.apkm file kept in app storage. */
    FILE,
}

@Serializable
enum class IconMode { ORIGINAL, BADGE, CUSTOM }

/** Colour adjustments applied to the clone's icon before any badge. */
@Serializable
data class IconAdjust(
    /** Hue rotation in degrees, -180..180. */
    val hue: Float = 0f,
    /** 0 = greyscale, 1 = unchanged, 2 = double. */
    val saturation: Float = 1f,
    /** -1..1 */
    val brightness: Float = 0f,
    /** -0.5..0.5 */
    val contrast: Float = 0f,
    /** Colour laid over the icon's shape, or null for none. */
    val tint: Long? = null,
    val tintStrength: Float = 0.5f,
    val invert: Boolean = false,
    val flip: Boolean = false,
    /** 0, 90, 180 or 270. */
    val rotation: Int = 0,
) {
    val isIdentity: Boolean
        get() = this == IconAdjust()
}

/**
 * The walls around a hardened clone, or "cell". All of it is enforced by Android, not by code running
 * inside the app, so nothing in the app can talk its way past it.
 */
@Serializable
data class CellOptions(
    /** Own signing key: its Android ID won't match other clones and they can't share signature-level access. */
    val ownKey: Boolean = true,
    /** No QUERY_ALL_PACKAGES or <queries>, so Android hides other apps from it. */
    val hideApps: Boolean = true,
    /** Other apps can't start its screens, services or providers. */
    val privateComponents: Boolean = true,
    val noBackup: Boolean = true,
    val noAdId: Boolean = true,
    /** Only its own private folders, not shared photos and files. */
    val noStorage: Boolean = true,
    val noBoot: Boolean = true,
    /** Off by default because most apps are useless without it. */
    val noInternet: Boolean = false,
)

@Serializable
data class CloneConfig(
    val id: String,
    val sourceKind: SourceKind,
    val sourcePackage: String,
    val sourceLabel: String,
    val clonePackage: String,
    val label: String,
    val iconMode: IconMode = IconMode.BADGE,
    val badgeColor: Long = 0xFF7C4DFF,
    val badgeText: String = "2",
    /** Absolute path of the user's custom icon image (only for [IconMode.CUSTOM]). */
    val customIconPath: String? = null,
    val iconAdjust: IconAdjust = IconAdjust(),
    val removedPermissions: List<String> = emptyList(),
    val debuggable: Boolean = false,
    /** Null for a normal clone. */
    val cell: CellOptions? = null,
    /** Installed inside the sandbox profile instead of next to your other apps. */
    val inSandbox: Boolean = false,
    /** Version code of the source the currently installed clone was built from. 0 = never built. */
    val builtVersionCode: Long = 0,
    val builtVersionName: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
