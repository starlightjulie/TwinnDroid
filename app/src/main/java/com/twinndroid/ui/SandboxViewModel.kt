package com.twinndroid.ui

import android.app.Application
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.twinndroid.CloneOperations
import com.twinndroid.R
import com.twinndroid.data.SourceKind
import com.twinndroid.engine.ApkSources
import com.twinndroid.install.InstallResult
import com.twinndroid.sandbox.Sandbox
import com.twinndroid.sandbox.SandboxApp
import com.twinndroid.sandbox.SandboxAppOptions
import com.twinndroid.sandbox.SandboxPaymentStub
import com.twinndroid.sandbox.SandboxRules
import com.twinndroid.sandbox.SandboxShortcuts
import com.twinndroid.sandbox.SandboxStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class SandboxEntry(val app: SandboxApp, val icon: ImageBitmap?)

class SandboxViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx get() = getApplication<Application>()

    var status by mutableStateOf(Sandbox.status(app))
        private set
    /** Null until the first answer from the sandbox. */
    var apps by mutableStateOf<List<SandboxEntry>?>(null)
        private set
    var showSystem by mutableStateOf(false)
        private set
    var rules by mutableStateOf<SandboxRules?>(null)
        private set
    /** The app whose options sheet is open, with its current settings. */
    var options by mutableStateOf<Pair<SandboxEntry, SandboxAppOptions>?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
    /** Setup finished on Android's side and the profile is still starting. */
    var finishing by mutableStateOf(false)
        private set

    /** Cheap, no bridge call: safe to run on every resume. */
    fun refreshStatus() {
        val now = Sandbox.status(ctx)
        val wasReady = status == SandboxStatus.READY
        status = now
        if (now != SandboxStatus.READY) {
            apps = null
            rules = null
        } else if (!wasReady) {
            reload()
        }
    }

    /** True while the Sandbox tab is on screen, which is the only time Android lets the bridge open. */
    private var onScreen = false
    private var reloadWhenBack = false

    fun onScreenChanged(visible: Boolean) {
        onScreen = visible
        if (visible && reloadWhenBack) {
            reloadWhenBack = false
            reload()
        }
    }

    /** Asks the sandbox what's inside. Only call on a user action: each call briefly opens its bridge. */
    fun reload() = run {
        if (!onScreen) {
            reloadWhenBack = true
            return@run
        }
        val list = Sandbox.list(ctx, showSystem)
        apps = withContext(Dispatchers.Default) {
            list.map { a -> SandboxEntry(a, a.iconPng?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }) }
        }
        if (rules == null) rules = Sandbox.rules(ctx)
    }

    fun toggleSystem() {
        showSystem = !showSystem
        reload()
    }

    /** Android's setup has returned; wait for the new profile to come up and answer. */
    fun onSetupFinished() = viewModelScope.launch {
        finishing = true
        repeat(40) {
            status = Sandbox.status(ctx)
            if (status == SandboxStatus.READY) {
                finishing = false
                reload()
                return@launch
            }
            delay(500)
        }
        finishing = false
    }

    fun addOriginal(packageName: String) = runInstall { Sandbox.addOriginal(ctx, packageName) }

    /**
     * Puts the app behind a clone in the editor into the sandbox unchanged. If there's no sandbox yet
     * this does nothing, and the tab it landed on offers to create one.
     */
    fun addUnchanged(state: EditorState) = run {
        val c = state.config
        try {
            if (Sandbox.status(ctx) != SandboxStatus.READY || Sandbox.contains(ctx, c.sourcePackage)) return@run
            val result = when (c.sourceKind) {
                SourceKind.INSTALLED -> Sandbox.addOriginal(ctx, c.sourcePackage)
                SourceKind.FILE -> {
                    val files = withContext(Dispatchers.IO) { CloneOperations.resolveSource(ctx, c).files }
                    Sandbox.install(ctx, c.sourcePackage, files)
                }
            }
            if (!result.success) message = result.message
            reload()
        } finally {
            if (state.isNew && c.sourceKind == SourceKind.FILE) ApkSources.sourceDir(ctx, c.id).deleteRecursively()
        }
    }

    fun addFile(uri: Uri) = runInstall {
        val dir = ApkSources.sourceDir(ctx, "sandbox-" + UUID.randomUUID())
        try {
            withContext(Dispatchers.IO) { ApkSources.import(ctx, uri, dir) }
            val source = withContext(Dispatchers.IO) { ApkSources.stored(ctx, dir) }
            Sandbox.install(ctx, source.packageName, source.files)
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun runInstall(block: suspend () -> InstallResult) = run {
        val result = block()
        if (!result.success) message = result.message
        reload()
    }

    fun setFrozen(entry: SandboxEntry, frozen: Boolean) = run {
        Sandbox.setFrozen(ctx, entry.app.packageName, frozen)
        reload()
    }

    fun freezeAll() = run {
        Sandbox.freezeAll(ctx)
        reload()
    }

    fun addShortcut(entry: SandboxEntry) {
        if (!SandboxShortcuts.pinLaunch(ctx, entry.app)) message = ctx.getString(R.string.shortcut_unsupported)
    }

    fun addFreezeAllShortcut() {
        if (!SandboxShortcuts.pinFreezeAll(ctx)) message = ctx.getString(R.string.shortcut_unsupported)
    }

    fun sendFiles(uris: List<Uri>) = run {
        Sandbox.sendFiles(ctx, uris)
        message = ctx.getString(R.string.sandbox_files_saved)
    }

    fun bringFilesOut() = run {
        if (Sandbox.bringFilesOut(ctx)) message = ctx.getString(R.string.sandbox_files_out)
    }

    var paymentStub by mutableStateOf(SandboxPaymentStub.isEnabled(app))
        private set

    fun togglePaymentStub(on: Boolean) {
        SandboxPaymentStub.setEnabled(ctx, on)
        paymentStub = on
    }

    fun open(entry: SandboxEntry) = run {
        Sandbox.launch(ctx, entry.app.packageName)
        if (entry.app.frozen) reload()
    }

    fun clearData(entry: SandboxEntry) = run {
        val ok = Sandbox.clearData(ctx, entry.app.packageName)
        message = ctx.getString(if (ok) R.string.sandbox_reset_done else R.string.sandbox_reset_failed, entry.app.label)
    }

    fun uninstall(entry: SandboxEntry) = run {
        Sandbox.uninstall(ctx, entry.app.packageName)
        reload()
    }

    fun copyOut(entry: SandboxEntry) = run {
        val result = Sandbox.copyOut(ctx, entry.app.packageName)
        if (!result.success) message = result.message
    }

    fun openOptions(entry: SandboxEntry) = run {
        options = entry to Sandbox.appOptions(ctx, entry.app.packageName)
    }

    fun closeOptions() {
        options = null
    }

    fun setPermissionBlocked(group: String, blocked: Boolean) = withOptions { entry, current ->
        Sandbox.setPermissionBlocked(ctx, entry.app.packageName, group, blocked)
        current.copy(permissions = current.permissions + (group to blocked))
    }

    fun setWidgets(allow: Boolean) = withOptions { entry, current ->
        Sandbox.setWidgets(ctx, entry.app.packageName, allow)
        current.copy(widgets = allow)
    }

    fun setAutoFreeze(on: Boolean) = withOptions { entry, current ->
        Sandbox.setAutoFreeze(ctx, entry.app.packageName, on)
        current.copy(autoFreeze = on)
    }

    fun setInteraction(allow: Boolean) = withOptions { entry, current ->
        Sandbox.setInteraction(ctx, entry.app.packageName, allow)
        current.copy(interaction = allow)
    }

    private fun withOptions(block: suspend (SandboxEntry, SandboxAppOptions) -> SandboxAppOptions) = run {
        val (entry, current) = options ?: return@run
        options = entry to block(entry, current)
    }

    fun setRules(next: SandboxRules) = run {
        Sandbox.setRules(ctx, next)
        rules = next
    }

    fun deleteSandbox() = run {
        Sandbox.delete(ctx)
        delay(1500)
        refreshStatus()
    }

    private fun run(block: suspend () -> Unit) = viewModelScope.launch {
        busy = true
        try {
            block()
        } catch (t: Throwable) {
            message = t.message ?: t.javaClass.simpleName
        } finally {
            busy = false
        }
    }
}
