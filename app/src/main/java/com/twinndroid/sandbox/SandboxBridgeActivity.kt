package com.twinndroid.sandbox

import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.ResultReceiver
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.contentValuesOf
import androidx.core.content.IntentCompat
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import com.twinndroid.R
import com.twinndroid.engine.ApkSources
import com.twinndroid.install.InstallPart
import com.twinndroid.install.SessionInstaller
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import kotlin.coroutines.resume

/**
 * The far end of [SandboxBridge]. Invisible, and only startable by apps signed with TwinnDroid's key
 * (see the manifest), so nothing else on the phone can freeze, wipe or install through it. It stays
 * open for the length of a command because Android only lets a visible app show install prompts.
 */
class SandboxBridgeActivity : ComponentActivity() {
    private var picked: CompletableDeferred<List<Uri>>? = null
    private val picker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        picked?.complete(uris)
    }
    private var backFromSettings: CompletableDeferred<Unit>? = null
    private val settings = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        backFromSettings?.complete(Unit)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val reply = IntentCompat.getParcelableExtra(intent, SandboxBridge.EXTRA_REPLY, ResultReceiver::class.java)
        val command = intent.getStringExtra(SandboxBridge.EXTRA_COMMAND).orEmpty()
        lifecycleScope.launch {
            try {
                val out = withContext(Dispatchers.IO) { run(command, reply) }
                reply?.send(SandboxBridge.REPLY_DONE, out)
            } catch (t: Throwable) {
                reply?.send(SandboxBridge.REPLY_FAILED, bundleOf("error" to (t.message ?: t.javaClass.simpleName)))
            }
            finish()
        }
    }

    private suspend fun run(command: String, reply: ResultReceiver?): Bundle {
        val pkg = intent.getStringExtra(Sandbox.ARG_PACKAGE).orEmpty()
        val on = intent.getBooleanExtra(Sandbox.ARG_ON, false)
        when (command) {
            // These work on either side, so apps and files can also come back out to the main profile.
            Sandbox.CMD_INSTALL -> return install(pkg)
            Sandbox.CMD_RECEIVE_FILES -> return receiveFiles()
        }

        if (!SandboxPolicy.isOwner(this)) throw SandboxException("Not running inside the sandbox")
        when (command) {
            Sandbox.CMD_PING -> Unit
            Sandbox.CMD_LIST -> return list(reply)
            Sandbox.CMD_UNINSTALL -> return bundleOf(Sandbox.ARG_OK to SessionInstaller.uninstall(this, pkg).success)
            Sandbox.CMD_FREEZE -> SandboxPolicy.setFrozen(this, pkg, on)
            Sandbox.CMD_FREEZE_ALL -> SandboxPolicy.freezeEverything(this)
            Sandbox.CMD_SET_AUTO_FREEZE -> SandboxPolicy.setAutoFreeze(this, pkg, on)
            Sandbox.CMD_BRING_OUT -> return bringOut()
            Sandbox.CMD_ENABLE_SYSTEM -> SandboxPolicy.enableSystemApp(this, pkg)
            Sandbox.CMD_CLEAR_DATA -> return bundleOf(Sandbox.ARG_OK to clearData(pkg))
            Sandbox.CMD_LAUNCH -> launch(pkg)
            Sandbox.CMD_APP_OPTIONS -> return appOptions(pkg)
            Sandbox.CMD_SET_PERMISSION -> SandboxPolicy.setPermissionBlocked(this, pkg, intent.getStringExtra(Sandbox.ARG_GROUP).orEmpty(), on)
            Sandbox.CMD_SET_WIDGETS -> SandboxPolicy.allowWidgets(this, pkg, on)
            Sandbox.CMD_SET_INTERACTION -> SandboxPolicy.allowInteraction(this, pkg, on)
            Sandbox.CMD_GET_RULES -> return Sandbox.rulesToBundle(SandboxPolicy.rules(this))
            Sandbox.CMD_SET_RULES -> SandboxPolicy.setRules(this, Sandbox.rulesFromIntent(intent))
            Sandbox.CMD_COPY_OUT -> return copyOut(pkg)
            Sandbox.CMD_DELETE_SANDBOX -> getSystemService(DevicePolicyManager::class.java).wipeData(0)
            else -> throw SandboxException("Unknown command: $command")
        }
        return Bundle.EMPTY
    }

    /**
     * Android's own version of this prompt throws the install away when it sends you to Settings,
     * so ask first and the install can carry on once you come back.
     */
    private suspend fun ensureCanInstall() {
        if (packageManager.canRequestPackageInstalls()) return
        val back = CompletableDeferred<Unit>()
        backFromSettings = back
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))
        withContext(Dispatchers.Main) { settings.launch(intent) }
        back.await()
        if (!packageManager.canRequestPackageInstalls()) throw SandboxException(getString(R.string.sandbox_install_blocked))
    }

    private suspend fun install(pkg: String): Bundle {
        ensureCanInstall()
        val clip = intent.clipData ?: throw SandboxException("No APKs were sent")
        val sizes = intent.getLongArrayExtra(Sandbox.ARG_SIZES) ?: LongArray(clip.itemCount) { -1L }
        val parts = (0 until clip.itemCount).map { i ->
            val uri = clip.getItemAt(i).uri
            InstallPart("$i.apk", sizes[i]) { contentResolver.openInputStream(uri) ?: throw SandboxException("Can't read $uri") }
        }
        val result = SessionInstaller.install(this, pkg, parts)
        return bundleOf(Sandbox.ARG_OK to result.success, Sandbox.ARG_MESSAGE to result.message)
    }

    /** Sends a sandboxed app's APKs back across the bridge to be installed on the other side. */
    private suspend fun copyOut(pkg: String): Bundle {
        val files = ApkSources.installed(this, pkg).files
        val uris: List<Uri> = files.map { SandboxFileProvider.share(this, it) }
        try {
            val args = bundleOf(Sandbox.ARG_PACKAGE to pkg, Sandbox.ARG_SIZES to files.map { it.length() }.toLongArray())
            return SandboxBridge.call(this, Sandbox.CMD_INSTALL, args, uris, timeoutMs = null)
        } finally {
            SandboxFileProvider.forget(uris)
        }
    }

    private fun list(reply: ResultReceiver?): Bundle {
        val pm = packageManager
        val includeSystem = intent.getBooleanExtra(Sandbox.ARG_ON, false)
        val packages = pm.getInstalledPackages(PackageManager.MATCH_UNINSTALLED_PACKAGES or PackageManager.MATCH_DISABLED_COMPONENTS)
            .asSequence()
            .filter { it.packageName != packageName && it.packageName != SandboxPolicy.DEVICE_POLICY_APP }
            .filter { (it.applicationInfo?.flags ?: 0) and ApplicationInfo.FLAG_INSTALLED != 0 }
            .toList()
        val added = SandboxPolicy.addedSystemApps(this)
        val batch = ArrayList<Bundle>()
        for (info in packages) {
            val ai = info.applicationInfo ?: continue
            val frozen = SandboxPolicy.isFrozen(this, info.packageName)
            val system = ai.flags and ApplicationInfo.FLAG_SYSTEM != 0
            val launchable = frozen || pm.getLaunchIntentForPackage(info.packageName) != null
            if (system && info.packageName !in added && (!includeSystem || !launchable || !ai.enabled)) continue
            batch += SandboxApp(
                packageName = info.packageName,
                label = ai.loadLabel(pm).toString(),
                versionName = info.versionName,
                versionCode = PackageInfoCompat.getLongVersionCode(info),
                isSystem = system,
                frozen = frozen,
                launchable = launchable,
                iconPng = iconPng(ai),
            ).toBundle()
            if (batch.size == 4) {
                reply?.send(SandboxBridge.REPLY_PART, bundleOf(Sandbox.ARG_APPS to ArrayList(batch)))
                batch.clear()
            }
        }
        if (batch.isNotEmpty()) reply?.send(SandboxBridge.REPLY_PART, bundleOf(Sandbox.ARG_APPS to ArrayList(batch)))
        return Bundle.EMPTY
    }

    private fun iconPng(ai: ApplicationInfo): ByteArray? = runCatching {
        val out = ByteArrayOutputStream()
        ai.loadIcon(packageManager).toBitmap(96, 96).compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
        out.toByteArray()
    }.getOrNull()

    private suspend fun clearData(pkg: String): Boolean {
        if (Build.VERSION.SDK_INT < 28) throw SandboxException("Resetting an app needs Android 9 or newer")
        return suspendCancellableCoroutine { cont ->
            getSystemService(DevicePolicyManager::class.java).clearApplicationUserData(
                SandboxPolicy.admin(this), pkg, Executors.newSingleThreadExecutor(),
            ) { _, succeeded -> if (cont.isActive) cont.resume(succeeded) }
        }
    }

    private fun launch(pkg: String) {
        if (SandboxPolicy.isFrozen(this, pkg)) SandboxPolicy.setFrozen(this, pkg, false)
        val intent: Intent = packageManager.getLaunchIntentForPackage(pkg)
            ?: throw SandboxException("This app has nothing to open")
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        if (pkg in SandboxPolicy.autoFreezeApps(this)) SandboxFreezeService.start(this)
    }

    /** Saves whatever arrived to Download/TwinnDroid on this side of the boundary. */
    private fun receiveFiles(): Bundle {
        if (Build.VERSION.SDK_INT < 29) throw SandboxException("Moving files needs Android 10 or newer")
        val clip = intent.clipData ?: throw SandboxException("No files were sent")
        for (i in 0 until clip.itemCount) {
            val source = clip.getItemAt(i).uri
            val name = contentResolver.query(source, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            } ?: "file-$i"
            val target = contentResolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                contentValuesOf(
                    MediaStore.Downloads.DISPLAY_NAME to name,
                    MediaStore.Downloads.RELATIVE_PATH to Environment.DIRECTORY_DOWNLOADS + "/TwinnDroid",
                ),
            ) ?: throw SandboxException("Couldn't save $name")
            contentResolver.openInputStream(source)!!.use { input ->
                contentResolver.openOutputStream(target)!!.use { input.copyTo(it) }
            }
        }
        return Bundle.EMPTY
    }

    /** Shows the file picker inside the sandbox and forwards the picks to the other side. */
    private suspend fun bringOut(): Bundle {
        val deferred = CompletableDeferred<List<Uri>>()
        picked = deferred
        withContext(Dispatchers.Main) { picker.launch(arrayOf("*/*")) }
        val uris = deferred.await()
        if (uris.isEmpty()) return Bundle.EMPTY
        SandboxBridge.call(this, Sandbox.CMD_RECEIVE_FILES, uris = uris, timeoutMs = null)
        return bundleOf(Sandbox.ARG_OK to true)
    }

    private fun appOptions(pkg: String): Bundle {
        val blocked = SandboxPolicy.permissionStates(this, pkg)
        return bundleOf(
            Sandbox.ARG_GROUPS to blocked.keys.toTypedArray(),
            Sandbox.ARG_BLOCKED to blocked.values.toBooleanArray(),
            Sandbox.ARG_WIDGETS to SandboxPolicy.widgetsAllowed(this, pkg),
            Sandbox.ARG_INTERACTION to SandboxPolicy.interactionAllowed(this, pkg),
            Sandbox.ARG_AUTO_FREEZE to (pkg in SandboxPolicy.autoFreezeApps(this)),
        )
    }
}
