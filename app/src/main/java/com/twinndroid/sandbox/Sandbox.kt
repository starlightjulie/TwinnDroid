package com.twinndroid.sandbox

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import androidx.core.os.bundleOf
import com.twinndroid.engine.ApkSources
import com.twinndroid.install.InstallResult
import java.io.File

enum class SandboxStatus {
    /** This phone or its Android build doesn't offer work profiles. */
    UNSUPPORTED,

    /** Ready to be created. */
    AVAILABLE,

    /** Another app (Shelter, a company...) already holds the phone's one work profile. */
    OTHER_OWNER,

    /** Exists, but the user has paused work apps. */
    PAUSED,

    READY,
}

/** One app inside the sandbox, as reported from the far side of the bridge. */
data class SandboxApp(
    val packageName: String,
    val label: String,
    val versionName: String?,
    val versionCode: Long,
    val isSystem: Boolean,
    val frozen: Boolean,
    val launchable: Boolean,
    val iconPng: ByteArray?,
) {
    fun toBundle(): Bundle = bundleOf(
        "p" to packageName, "l" to label, "n" to versionName, "c" to versionCode,
        "s" to isSystem, "f" to frozen, "a" to launchable, "i" to iconPng,
    )

    companion object {
        fun from(b: Bundle) = SandboxApp(
            packageName = b.getString("p").orEmpty(),
            label = b.getString("l").orEmpty(),
            versionName = b.getString("n"),
            versionCode = b.getLong("c"),
            isSystem = b.getBoolean("s"),
            frozen = b.getBoolean("f"),
            launchable = b.getBoolean("a"),
            iconPng = b.getByteArray("i"),
        )
    }
}

/** What the sandbox lets one app do, for the per-app options sheet. */
data class SandboxAppOptions(
    /** Permission group -> blocked. Only groups the app actually asks for. */
    val permissions: Map<String, Boolean>,
    val widgets: Boolean,
    val interaction: Boolean,
    val autoFreeze: Boolean,
)

/** The main-profile side of the sandbox: what state it's in, and the commands you can send it. */
object Sandbox {
    const val CMD_PING = "ping"
    const val CMD_LIST = "list"
    const val CMD_INSTALL = "install"
    const val CMD_UNINSTALL = "uninstall"
    const val CMD_FREEZE = "freeze"
    const val CMD_FREEZE_ALL = "freeze_all"
    const val CMD_ENABLE_SYSTEM = "enable_system"
    const val CMD_CLEAR_DATA = "clear_data"
    const val CMD_LAUNCH = "launch"
    const val CMD_APP_OPTIONS = "app_options"
    const val CMD_SET_PERMISSION = "set_permission"
    const val CMD_SET_WIDGETS = "set_widgets"
    const val CMD_SET_INTERACTION = "set_interaction"
    const val CMD_GET_RULES = "get_rules"
    const val CMD_SET_RULES = "set_rules"
    const val CMD_COPY_OUT = "copy_out"
    const val CMD_DELETE_SANDBOX = "delete_sandbox"
    const val CMD_SET_AUTO_FREEZE = "set_auto_freeze"
    const val CMD_RECEIVE_FILES = "receive_files"
    const val CMD_BRING_OUT = "bring_out"

    const val ARG_PACKAGE = "package"
    const val ARG_ON = "on"
    const val ARG_OK = "ok"
    const val ARG_MESSAGE = "message"
    const val ARG_SIZES = "sizes"
    const val ARG_APPS = "apps"
    const val ARG_GROUP = "group"
    const val ARG_GROUPS = "groups"
    const val ARG_BLOCKED = "blocked"
    const val ARG_WIDGETS = "widgets"
    const val ARG_INTERACTION = "interaction"
    const val ARG_AUTO_FREEZE = "auto_freeze"

    /** True in the copy of TwinnDroid living inside the sandbox, which never shows its own UI. */
    fun isInside(context: Context): Boolean = SandboxPolicy.isOwner(context)

    fun status(context: Context): SandboxStatus {
        val pm = context.packageManager
        if (!pm.hasSystemFeature(PackageManager.FEATURE_MANAGED_USERS) ||
            !pm.hasSystemFeature(PackageManager.FEATURE_DEVICE_ADMIN)
        ) {
            return SandboxStatus.UNSUPPORTED
        }
        val um = context.getSystemService(UserManager::class.java)
        val profile = user(context)
        return when {
            profile != null && SandboxBridge.forwarder(context) != null ->
                if (um.isQuietModeEnabled(profile)) SandboxStatus.PAUSED else SandboxStatus.READY
            siblings(context).any { isWorkProfile(context, it) } -> SandboxStatus.OTHER_OWNER
            !context.getSystemService(DevicePolicyManager::class.java)
                .isProvisioningAllowed(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE) -> SandboxStatus.UNSUPPORTED
            else -> SandboxStatus.AVAILABLE
        }
    }

    /** The sandbox's profile: a sibling profile that has TwinnDroid in it. */
    fun user(context: Context): UserHandle? {
        val apps = context.getSystemService(LauncherApps::class.java)
        return siblings(context).firstOrNull { user ->
            runCatching { apps.getApplicationInfo(context.packageName, 0, user) }.isSuccess
        }
    }

    /** Whether a package is installed in the sandbox, frozen or not. No bridge call, so it's cheap. */
    fun contains(context: Context, packageName: String): Boolean {
        val profile = user(context) ?: return false
        val apps = context.getSystemService(LauncherApps::class.java)
        return runCatching {
            val info = apps.getApplicationInfo(packageName, PackageManager.MATCH_UNINSTALLED_PACKAGES, profile)
            info.flags and android.content.pm.ApplicationInfo.FLAG_INSTALLED != 0
        }.getOrDefault(false)
    }

    fun icon(context: Context, packageName: String): android.graphics.drawable.Drawable? {
        val profile = user(context) ?: return null
        val apps = context.getSystemService(LauncherApps::class.java)
        return runCatching { apps.getActivityList(packageName, profile).firstOrNull()?.getIcon(0) }.getOrNull()
    }

    private fun siblings(context: Context): List<UserHandle> =
        context.getSystemService(UserManager::class.java).userProfiles.filter { it != Process.myUserHandle() }

    /**
     * Android only allows one work profile, and asking for a second offers to delete the first, so
     * this has to be caught before setup starts. Samsung's Secure Folder also shows up as a sibling
     * but doesn't use up the slot, so a sibling alone isn't enough to go on.
     */
    private fun isWorkProfile(context: Context, user: UserHandle): Boolean {
        if (Build.VERSION.SDK_INT >= 35) {
            val info = runCatching { context.getSystemService(LauncherApps::class.java).getLauncherUserInfo(user) }.getOrNull()
            if (info != null) return info.userType == "android.os.usertype.profile.MANAGED"
        }
        val system = Resources.getSystem()
        val badge = system.getIdentifier("managed_profile_label_badge", "string", "android")
        return badge != 0 && context.packageManager.getUserBadgedLabel("x", user).toString() == system.getString(badge, "x")
    }

    fun setupIntent(context: Context): Intent =
        Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE)
            .putExtra(DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, SandboxPolicy.admin(context))
            .putExtra(DevicePolicyManager.EXTRA_PROVISIONING_SKIP_ENCRYPTION, true)

    suspend fun list(context: Context, includeSystem: Boolean): List<SandboxApp> {
        val apps = mutableListOf<SandboxApp>()
        SandboxBridge.call(context, CMD_LIST, bundleOf(ARG_ON to includeSystem), timeoutMs = 60_000) { part ->
            @Suppress("DEPRECATION")
            part.getParcelableArrayList<Bundle>(ARG_APPS)?.forEach { apps += SandboxApp.from(it) }
        }
        return apps.sortedBy { it.label.lowercase() }
    }

    /** Copies APKs into the sandbox and installs them there. Waits for Android's install prompt. */
    suspend fun install(context: Context, packageName: String, files: List<File>): InstallResult {
        val uris = files.map { SandboxFileProvider.share(context, it) }
        try {
            val args = bundleOf(ARG_PACKAGE to packageName, ARG_SIZES to files.map { it.length() }.toLongArray())
            val out = SandboxBridge.call(context, CMD_INSTALL, args, uris, timeoutMs = null)
            return InstallResult(out.getBoolean(ARG_OK), out.getString(ARG_MESSAGE).orEmpty())
        } finally {
            SandboxFileProvider.forget(uris)
        }
    }

    /** Puts an unmodified copy of an app from this phone into the sandbox. */
    suspend fun addOriginal(context: Context, packageName: String): InstallResult {
        val info = context.packageManager.getApplicationInfo(packageName, 0)
        if (info.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0) {
            SandboxBridge.call(context, CMD_ENABLE_SYSTEM, bundleOf(ARG_PACKAGE to packageName))
            return InstallResult(true, "")
        }
        return install(context, packageName, ApkSources.installed(context, packageName).files)
    }

    suspend fun uninstall(context: Context, packageName: String) {
        SandboxBridge.call(context, CMD_UNINSTALL, bundleOf(ARG_PACKAGE to packageName), timeoutMs = null)
    }

    suspend fun setFrozen(context: Context, packageName: String, frozen: Boolean) {
        SandboxBridge.call(context, CMD_FREEZE, bundleOf(ARG_PACKAGE to packageName, ARG_ON to frozen))
    }

    suspend fun freezeAll(context: Context) {
        SandboxBridge.call(context, CMD_FREEZE_ALL, timeoutMs = 60_000)
    }

    suspend fun setAutoFreeze(context: Context, packageName: String, on: Boolean) {
        SandboxBridge.call(context, CMD_SET_AUTO_FREEZE, bundleOf(ARG_PACKAGE to packageName, ARG_ON to on))
    }

    /** Copies the picked files into the sandbox's Download/TwinnDroid folder. */
    suspend fun sendFiles(context: Context, uris: List<android.net.Uri>) {
        SandboxBridge.call(context, CMD_RECEIVE_FILES, uris = uris, timeoutMs = null)
    }

    /** Opens the file picker inside the sandbox and copies what you pick to this side's downloads. */
    suspend fun bringFilesOut(context: Context): Boolean =
        SandboxBridge.call(context, CMD_BRING_OUT, timeoutMs = null).getBoolean(ARG_OK)

    suspend fun clearData(context: Context, packageName: String): Boolean =
        SandboxBridge.call(context, CMD_CLEAR_DATA, bundleOf(ARG_PACKAGE to packageName), timeoutMs = 60_000).getBoolean(ARG_OK)

    suspend fun launch(context: Context, packageName: String) {
        SandboxBridge.call(context, CMD_LAUNCH, bundleOf(ARG_PACKAGE to packageName))
    }

    suspend fun copyOut(context: Context, packageName: String): InstallResult {
        val out = SandboxBridge.call(context, CMD_COPY_OUT, bundleOf(ARG_PACKAGE to packageName), timeoutMs = null)
        return InstallResult(out.getBoolean(ARG_OK), out.getString(ARG_MESSAGE).orEmpty())
    }

    suspend fun appOptions(context: Context, packageName: String): SandboxAppOptions {
        val out = SandboxBridge.call(context, CMD_APP_OPTIONS, bundleOf(ARG_PACKAGE to packageName))
        val groups = out.getStringArray(ARG_GROUPS).orEmpty()
        val blocked = out.getBooleanArray(ARG_BLOCKED) ?: BooleanArray(groups.size)
        return SandboxAppOptions(
            permissions = groups.zip(blocked.toList()).toMap(),
            widgets = out.getBoolean(ARG_WIDGETS),
            interaction = out.getBoolean(ARG_INTERACTION),
            autoFreeze = out.getBoolean(ARG_AUTO_FREEZE),
        )
    }

    suspend fun setPermissionBlocked(context: Context, packageName: String, group: String, blocked: Boolean) {
        SandboxBridge.call(context, CMD_SET_PERMISSION, bundleOf(ARG_PACKAGE to packageName, ARG_GROUP to group, ARG_ON to blocked))
    }

    suspend fun setWidgets(context: Context, packageName: String, allow: Boolean) {
        SandboxBridge.call(context, CMD_SET_WIDGETS, bundleOf(ARG_PACKAGE to packageName, ARG_ON to allow))
    }

    suspend fun setInteraction(context: Context, packageName: String, allow: Boolean) {
        SandboxBridge.call(context, CMD_SET_INTERACTION, bundleOf(ARG_PACKAGE to packageName, ARG_ON to allow))
    }

    suspend fun rules(context: Context): SandboxRules =
        rulesFrom(SandboxBridge.call(context, CMD_GET_RULES))

    suspend fun setRules(context: Context, rules: SandboxRules) {
        SandboxBridge.call(context, CMD_SET_RULES, rulesToBundle(rules))
    }

    suspend fun delete(context: Context) {
        SandboxBridge.call(context, CMD_DELETE_SANDBOX)
    }

    fun rulesToBundle(r: SandboxRules): Bundle = bundleOf(
        "block_clipboard" to r.blockClipboard,
        "block_share_in" to r.blockShareIn,
        "block_contacts" to r.blockContacts,
        "allow_debugging" to r.allowDebugging,
        "freeze_delay" to r.freezeDelaySeconds,
        "skip_playing" to r.skipWhilePlaying,
    )

    private fun rulesFrom(b: Bundle) = SandboxRules(
        blockClipboard = b.getBoolean("block_clipboard"),
        blockShareIn = b.getBoolean("block_share_in"),
        blockContacts = b.getBoolean("block_contacts"),
        allowDebugging = b.getBoolean("allow_debugging"),
        freezeDelaySeconds = b.getInt("freeze_delay"),
        skipWhilePlaying = b.getBoolean("skip_playing"),
    )

    fun rulesFromIntent(intent: Intent): SandboxRules = rulesFrom(intent.extras ?: Bundle.EMPTY)

    /** Moving files needs MediaStore's Downloads collection, which arrived in Android 10. */
    val supportsFileTransfer: Boolean get() = Build.VERSION.SDK_INT >= 29
}
