package com.twinndroid.install

import android.app.AppOpsManager
import com.twinndroid.R
import androidx.annotation.StringRes
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Special-access permissions that Android grants from a Settings page rather than a dialog.
 *
 * Apps usually open that page with their own package name baked into the code at build time, so a
 * clone's "Open settings" button lands on the *original* app's page. TwinnDroid knows the clone's
 * real package name, so it can open the right page — or grant it directly through Shizuku.
 */
enum class SpecialAccess(
    @StringRes val label: Int,
    val permission: String,
    val op: String,
    val opStr: String,
    val settingsAction: String,
    val minSdk: Int,
) {
    ALL_FILES(
        R.string.access_all_files,
        "android.permission.MANAGE_EXTERNAL_STORAGE",
        "MANAGE_EXTERNAL_STORAGE",
        "android:manage_external_storage",
        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
        30,
    ),
    INSTALL_APPS(
        R.string.access_install_apps,
        "android.permission.REQUEST_INSTALL_PACKAGES",
        "REQUEST_INSTALL_PACKAGES",
        "android:request_install_packages",
        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        26,
    ),
    OVERLAY(
        R.string.access_overlay,
        "android.permission.SYSTEM_ALERT_WINDOW",
        "SYSTEM_ALERT_WINDOW",
        "android:system_alert_window",
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        23,
    ),
    ;

    companion object {
        /** The special accesses [packageName] asks for, with whether each is currently granted. */
        fun forPackage(context: Context, packageName: String): List<Pair<SpecialAccess, Boolean>> {
            val pm = context.packageManager
            val info = try {
                pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
            } catch (_: PackageManager.NameNotFoundException) {
                return emptyList()
            }
            val requested = info.requestedPermissions?.toSet().orEmpty()
            val uid = info.applicationInfo?.uid ?: return emptyList()
            val appOps = context.getSystemService(AppOpsManager::class.java)
            return entries
                .filter { Build.VERSION.SDK_INT >= it.minSdk && it.permission in requested }
                .map { access ->
                    val mode = runCatching { appOps.unsafeCheckOpNoThrow(access.opStr, uid, packageName) }
                        .getOrDefault(AppOpsManager.MODE_DEFAULT)
                    access to (mode == AppOpsManager.MODE_ALLOWED)
                }
        }

        /** Opens Android's page for this access, for [packageName] (not the app's original package). */
        fun openSettings(context: Context, packageName: String, access: SpecialAccess) {
            val intent = Intent(access.settingsAction, Uri.parse("package:$packageName"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }.onFailure {
                // Some skins don't take a package for this page; fall back to the app's info screen.
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }
}
