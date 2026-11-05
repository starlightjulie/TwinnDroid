package com.twinndroid.sandbox

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.UserManager
import androidx.core.content.edit
import com.twinndroid.R

/** Switches the user can flip for the whole sandbox. */
data class SandboxRules(
    /** Copying text between the sandbox and the rest of the phone. */
    val blockClipboard: Boolean = false,
    /** Sharing files from personal apps into sandbox apps. */
    val blockShareIn: Boolean = false,
    /** Personal apps (dialer, contacts) looking up contacts stored in the sandbox. */
    val blockContacts: Boolean = true,
    /** Lets adb and Shizuku reach the sandbox, for testing and silent installs. */
    val allowDebugging: Boolean = false,
    /** How long after the screen locks auto-freeze apps get frozen. */
    val freezeDelaySeconds: Int = 0,
    /** Leaves auto-freeze apps alone while something is playing, so music keeps going on a locked phone. */
    val skipWhilePlaying: Boolean = true,
)

/**
 * Everything TwinnDroid does as owner of its work profile. Only works from the copy of TwinnDroid
 * running inside the sandbox, because that's the copy Android made the profile owner.
 */
object SandboxPolicy {
    private const val PREFS = "sandbox"

    /** Google's helper that sets up work profiles on some phones. It lives in the profile but isn't yours. */
    const val DEVICE_POLICY_APP = "com.google.android.apps.work.clouddpc"

    fun admin(context: Context) = ComponentName(context, SandboxAdminReceiver::class.java)

    private fun dpm(context: Context) = context.getSystemService(DevicePolicyManager::class.java)

    fun isOwner(context: Context): Boolean = dpm(context).isProfileOwnerApp(context.packageName)

    /** Safe to call any number of times; runs on setup and again after every TwinnDroid update. */
    fun enforce(context: Context) {
        val dpm = dpm(context)
        val admin = admin(context)
        dpm.setProfileName(admin, context.getString(R.string.sandbox_profile_name))

        // Work profiles arrive locked down for company use; the sandbox needs to install what you ask it to.
        dpm.clearUserRestriction(admin, UserManager.DISALLOW_INSTALL_APPS)
        dpm.clearUserRestriction(admin, UserManager.DISALLOW_UNINSTALL_APPS)
        dpm.clearUserRestriction(admin, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES)

        // The only door between the two halves of TwinnDroid. Android forwards just these actions across
        // the profile boundary; the activity behind them only accepts callers signed with TwinnDroid's key.
        dpm.clearCrossProfileIntentFilters(admin)
        fun door(action: String, direction: Int) = dpm.addCrossProfileIntentFilter(
            admin,
            IntentFilter(action).apply { addCategory("android.intent.category.DEFAULT") },
            direction,
        )
        // These flags read backwards: "managed can access parent" is what lets the main profile's
        // intents reach the sandbox, and "parent can access managed" is the way back out.
        door(SandboxBridge.ACTION_INTO_SANDBOX, DevicePolicyManager.FLAG_MANAGED_CAN_ACCESS_PARENT)
        door(SandboxBridge.ACTION_OUT_OF_SANDBOX, DevicePolicyManager.FLAG_PARENT_CAN_ACCESS_MANAGED)

        // Sandboxed apps can still hand things out: open a link in your browser, share to a personal app.
        val share = IntentFilter().apply {
            addAction(Intent.ACTION_SEND)
            addAction(Intent.ACTION_SEND_MULTIPLE)
            addDataType("*/*")
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        dpm.addCrossProfileIntentFilter(admin, share, DevicePolicyManager.FLAG_PARENT_CAN_ACCESS_MANAGED)
        for (categories in listOf(listOf(Intent.CATEGORY_BROWSABLE), listOf(Intent.CATEGORY_BROWSABLE, Intent.CATEGORY_DEFAULT))) {
            val links = IntentFilter(Intent.ACTION_VIEW).apply {
                categories.forEach(::addCategory)
                addDataScheme("http")
                addDataScheme("https")
            }
            dpm.addCrossProfileIntentFilter(admin, links, DevicePolicyManager.FLAG_PARENT_CAN_ACCESS_MANAGED)
        }

        applyRules(context, rules(context))
        hideOwnLauncherIcons(context)
        dpm.setProfileEnabled(admin)
    }

    fun rules(context: Context): SandboxRules {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val d = SandboxRules()
        return SandboxRules(
            blockClipboard = p.getBoolean("block_clipboard", d.blockClipboard),
            blockShareIn = p.getBoolean("block_share_in", d.blockShareIn),
            blockContacts = p.getBoolean("block_contacts", d.blockContacts),
            allowDebugging = p.getBoolean("allow_debugging", d.allowDebugging),
            freezeDelaySeconds = p.getInt("freeze_delay", d.freezeDelaySeconds),
            skipWhilePlaying = p.getBoolean("skip_playing", d.skipWhilePlaying),
        )
    }

    fun setRules(context: Context, rules: SandboxRules) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putBoolean("block_clipboard", rules.blockClipboard)
            putBoolean("block_share_in", rules.blockShareIn)
            putBoolean("block_contacts", rules.blockContacts)
            putBoolean("allow_debugging", rules.allowDebugging)
            putInt("freeze_delay", rules.freezeDelaySeconds)
            putBoolean("skip_playing", rules.skipWhilePlaying)
        }
        applyRules(context, rules)
    }

    private fun applyRules(context: Context, rules: SandboxRules) {
        val dpm = dpm(context)
        val admin = admin(context)
        val current = dpm.getUserRestrictions(admin)
        // Re-applying runs on every update, so leave anything that's already right alone.
        fun restrict(key: String, on: Boolean) {
            if (current.getBoolean(key) == on) return
            if (on) dpm.addUserRestriction(admin, key) else dpm.clearUserRestriction(admin, key)
        }
        restrict(UserManager.DISALLOW_CROSS_PROFILE_COPY_PASTE, rules.blockClipboard)
        restrict(UserManager.DISALLOW_SHARE_INTO_MANAGED_PROFILE, rules.blockShareIn)
        restrict(UserManager.DISALLOW_DEBUGGING_FEATURES, !rules.allowDebugging)
        dpm.setCrossProfileContactsSearchDisabled(admin, rules.blockContacts)
        dpm.setCrossProfileCallerIdDisabled(admin, rules.blockContacts)
    }

    /** The app drawer's work tab should only show your sandboxed apps, not a second TwinnDroid. */
    private fun hideOwnLauncherIcons(context: Context) {
        val pm = context.packageManager
        val aliases = pm.getPackageInfo(context.packageName, PackageManager.GET_ACTIVITIES or PackageManager.MATCH_DISABLED_COMPONENTS)
            .activities.orEmpty().filter { it.name.contains(".Launcher") }
        for (alias in aliases) {
            pm.setComponentEnabledSetting(
                ComponentName(context, alias.name),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
    }

    fun setFrozen(context: Context, packageName: String, frozen: Boolean) {
        dpm(context).setApplicationHidden(admin(context), packageName, frozen)
    }

    fun isFrozen(context: Context, packageName: String): Boolean =
        dpm(context).isApplicationHidden(admin(context), packageName)

    fun freezeEverything(context: Context) {
        val pm = context.packageManager
        pm.getInstalledApplications(0)
            .filter { it.packageName != context.packageName && it.packageName != DEVICE_POLICY_APP }
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
            .forEach { setFrozen(context, it.packageName, true) }
    }

    fun autoFreezeApps(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet("auto_freeze", emptySet()).orEmpty()

    fun setAutoFreeze(context: Context, packageName: String, on: Boolean) {
        val next = autoFreezeApps(context).toMutableSet().apply { if (on) add(packageName) else remove(packageName) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putStringSet("auto_freeze", next) }
    }

    /** Brings a system app that came with the phone (Play Store, Chrome...) into the sandbox. */
    fun enableSystemApp(context: Context, packageName: String) {
        dpm(context).enableSystemApp(admin(context), packageName)
        setFrozen(context, packageName, false)
        val added = addedSystemApps(context) + packageName
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putStringSet("added_system", added) }
    }

    /** System apps you brought in yourself, which belong in the list even with system apps hidden. */
    fun addedSystemApps(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet("added_system", emptySet()).orEmpty()

    /** Runtime permissions that can be blocked per app, as Android's permission groups. */
    val PERMISSION_GROUPS: Map<String, List<String>> = linkedMapOf(
        "camera" to listOf("android.permission.CAMERA"),
        "microphone" to listOf("android.permission.RECORD_AUDIO"),
        "location" to listOf(
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.ACCESS_BACKGROUND_LOCATION",
        ),
        "contacts" to listOf(
            "android.permission.READ_CONTACTS",
            "android.permission.WRITE_CONTACTS",
            "android.permission.GET_ACCOUNTS",
        ),
        "calendar" to listOf("android.permission.READ_CALENDAR", "android.permission.WRITE_CALENDAR"),
        "phone" to listOf(
            "android.permission.READ_PHONE_STATE",
            "android.permission.READ_PHONE_NUMBERS",
            "android.permission.CALL_PHONE",
            "android.permission.READ_CALL_LOG",
            "android.permission.WRITE_CALL_LOG",
        ),
        "sms" to listOf(
            "android.permission.READ_SMS",
            "android.permission.SEND_SMS",
            "android.permission.RECEIVE_SMS",
        ),
        "files" to listOf(
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.READ_MEDIA_IMAGES",
            "android.permission.READ_MEDIA_VIDEO",
            "android.permission.READ_MEDIA_AUDIO",
        ),
        "nearby" to listOf(
            "android.permission.BLUETOOTH_SCAN",
            "android.permission.BLUETOOTH_CONNECT",
            "android.permission.NEARBY_WIFI_DEVICES",
        ),
        "sensors" to listOf("android.permission.BODY_SENSORS", "android.permission.ACTIVITY_RECOGNITION"),
    )

    /** Which groups [packageName] actually asks for, and whether each one is blocked right now. */
    fun permissionStates(context: Context, packageName: String): Map<String, Boolean> {
        val requested = context.packageManager
            .getPackageInfo(packageName, PackageManager.GET_PERMISSIONS or PackageManager.MATCH_UNINSTALLED_PACKAGES)
            .requestedPermissions.orEmpty().toSet()
        val dpm = dpm(context)
        val admin = admin(context)
        return PERMISSION_GROUPS.mapNotNull { (group, perms) ->
            val asked = perms.filter { it in requested }
            if (asked.isEmpty()) return@mapNotNull null
            group to asked.all { dpm.getPermissionGrantState(admin, packageName, it) == DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED }
        }.toMap()
    }

    fun setPermissionBlocked(context: Context, packageName: String, group: String, blocked: Boolean) {
        val state = if (blocked) DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED else DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT
        val dpm = dpm(context)
        val admin = admin(context)
        for (perm in PERMISSION_GROUPS[group].orEmpty()) {
            runCatching { dpm.setPermissionGrantState(admin, packageName, perm, state) }
        }
    }

    fun allowWidgets(context: Context, packageName: String, allow: Boolean) {
        val dpm = dpm(context)
        val admin = admin(context)
        if (allow) dpm.addCrossProfileWidgetProvider(admin, packageName) else dpm.removeCrossProfileWidgetProvider(admin, packageName)
    }

    fun widgetsAllowed(context: Context, packageName: String): Boolean =
        packageName in dpm(context).getCrossProfileWidgetProviders(admin(context))

    fun allowInteraction(context: Context, packageName: String, allow: Boolean) {
        if (Build.VERSION.SDK_INT < 30) return
        val dpm = dpm(context)
        val admin = admin(context)
        val set = dpm.getCrossProfilePackages(admin).toMutableSet()
        if (allow) set += packageName else set -= packageName
        dpm.setCrossProfilePackages(admin, set)
    }

    fun interactionAllowed(context: Context, packageName: String): Boolean =
        Build.VERSION.SDK_INT >= 30 && packageName in dpm(context).getCrossProfilePackages(admin(context))
}
