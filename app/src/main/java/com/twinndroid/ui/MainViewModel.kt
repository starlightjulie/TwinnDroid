package com.twinndroid.ui

import android.app.Application
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.twinndroid.CloneOperations
import com.twinndroid.BuildConfig
import com.twinndroid.R
import com.twinndroid.data.AppUpdate
import com.twinndroid.data.CloneConfig
import com.twinndroid.data.UpdateChecker
import com.twinndroid.data.CloneRepository
import com.twinndroid.data.IconMode
import com.twinndroid.data.Settings
import com.twinndroid.data.SourceKind
import com.twinndroid.engine.ApkExporter
import com.twinndroid.engine.ApkSources
import com.twinndroid.engine.ExportFormat
import com.twinndroid.engine.IconFactory
import com.twinndroid.engine.SourceApks
import com.twinndroid.install.ShizukuInstaller
import com.twinndroid.install.SpecialAccess
import com.twinndroid.engine.TamperReport
import com.twinndroid.engine.TamperRisk
import com.twinndroid.install.InstallResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

enum class Screen { Home, Editor, IconEditor, Settings, Language, Appearance, Background, Header }

data class AppEntry(val packageName: String, val label: String, val icon: ImageBitmap?, val isSystem: Boolean)

data class CloneItem(
    val config: CloneConfig,
    val icon: ImageBitmap?,
    val installedVersion: Long?,
    val updateAvailable: Boolean,
    /** Special accesses the installed clone asks for, and whether each is granted. */
    val access: List<Pair<SpecialAccess, Boolean>> = emptyList(),
) {
    val missingAccess: List<SpecialAccess> get() = access.filterNot { it.second }.map { it.first }
}

data class EditorState(
    val config: CloneConfig,
    val isNew: Boolean,
    val sourceIcon: Drawable?,
    val sourceVersion: String?,
    val permissions: List<String>,
    val preview: ImageBitmap?,
    val tamper: TamperReport = TamperReport(TamperRisk.NONE, null, ""),
    val packageError: String? = null,
)

data class BuildState(
    val clonePackage: String,
    val label: String,
    val icon: ImageBitmap?,
    val running: Boolean,
    val logs: List<String>,
    /** 0 read source, 1 patch, 2 sign, 3 install, 4 finished. */
    val stage: Int = 0,
    val result: InstallResult? = null,
)

data class ExportState(
    val source: SourceApks,
    val iconBitmap: ImageBitmap?,
    val splits: List<Pair<String, Long>>,
    val format: ExportFormat,
    val running: Boolean = false,
    val progress: Float = 0f,
    val done: Uri? = null,
    val error: String? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx get() = getApplication<Application>()
    private val repo = CloneRepository.get(app)
    val settings = Settings(app)

    var screen by mutableStateOf(Screen.Home)
    var clones by mutableStateOf<List<CloneItem>>(emptyList())
        private set
    var apps by mutableStateOf<List<AppEntry>?>(null)
        private set
    var editor by mutableStateOf<EditorState?>(null)
        private set
    var build by mutableStateOf<BuildState?>(null)
        private set
    var export by mutableStateOf<ExportState?>(null)
        private set
    /** Selected bottom tab; kept here so it survives visiting Settings/Editor. */
    var tab by mutableIntStateOf(0)
    /** Hides the UI so the background animation can be seen on its own. */
    var peek by mutableStateOf(false)
    /** Set when GitHub has a newer TwinnDroid release than this one. */
    var appUpdate by mutableStateOf<AppUpdate?>(null)
    var message by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false)
        private set

    init {
        if (settings.checkUpdates) checkForAppUpdate()
        viewModelScope.launch { repo.clones.collect { refreshClones() } }
    }

    fun refreshClones() = viewModelScope.launch {
        clones = withContext(Dispatchers.IO) {
            val outdated = CloneOperations.outdated(ctx).map { it.id }.toSet()
            repo.clones.value.sortedByDescending { it.createdAt }.map { c ->
                val installed = CloneOperations.installedVersion(ctx, c.clonePackage)
                val icon = runCatching {
                    if (installed != null) ctx.packageManager.getApplicationIcon(c.clonePackage)
                    else ctx.packageManager.getApplicationIcon(c.sourcePackage)
                }.getOrNull()
                val access = if (installed != null) SpecialAccess.forPackage(ctx, c.clonePackage) else emptyList()
                CloneItem(c, icon?.toBitmap(128, 128)?.asImageBitmap(), installed, c.id in outdated, access)
            }
        }
    }

    fun loadApps(force: Boolean = false) {
        if (apps != null && !force) return
        viewModelScope.launch {
            apps = withContext(Dispatchers.IO) {
                val pm = ctx.packageManager
                pm.getInstalledApplications(0)
                    .asSequence()
                    .filter { it.packageName != ctx.packageName }
                    .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
                    .map { ai ->
                        AppEntry(
                            packageName = ai.packageName,
                            label = ai.loadLabel(pm).toString(),
                            icon = runCatching { ai.loadIcon(pm).toBitmap(96, 96).asImageBitmap() }.getOrNull(),
                            isSystem = ai.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                        )
                    }
                    .sortedBy { it.label.lowercase() }
                    .toList()
            }
        }
    }

    // ---- editor -----------------------------------------------------------------------------

    fun newFromInstalled(packageName: String) = launchBusy {
        val source = withContext(Dispatchers.IO) { ApkSources.installed(ctx, packageName).also { it.tamper } }
        openEditor(newConfig(UUID.randomUUID().toString(), SourceKind.INSTALLED, source), source, isNew = true)
    }

    fun newFromFile(uri: Uri) = launchBusy {
        val id = UUID.randomUUID().toString()
        val source = withContext(Dispatchers.IO) {
            val dir = ApkSources.sourceDir(ctx, id)
            try {
                ApkSources.import(ctx, uri, dir)
                ApkSources.stored(ctx, dir).also { it.tamper }
            } catch (t: Throwable) {
                dir.deleteRecursively()
                throw t
            }
        }
        openEditor(newConfig(id, SourceKind.FILE, source), source, isNew = true)
    }

    fun edit(config: CloneConfig) = launchBusy {
        val source = withContext(Dispatchers.IO) { CloneOperations.resolveSource(ctx, config).also { it.tamper } }
        openEditor(config, source, isNew = false)
    }

    private fun newConfig(id: String, kind: SourceKind, source: SourceApks): CloneConfig {
        val existing = repo.clones.value.filter { it.sourcePackage == source.packageName }
        var n = existing.size + 2
        val usedPackages = repo.clones.value.map { it.clonePackage }.toSet()
        while ("${source.packageName}.twinn$n" in usedPackages ||
            CloneOperations.installedVersion(ctx, "${source.packageName}.twinn$n") != null
        ) n++
        return CloneConfig(
            id = id,
            sourceKind = kind,
            sourcePackage = source.packageName,
            sourceLabel = source.label,
            clonePackage = "${source.packageName}.twinn$n",
            label = "${source.label} $n",
            badgeText = n.toString(),
        )
    }

    private fun openEditor(config: CloneConfig, source: SourceApks, isNew: Boolean) {
        editor = EditorState(
            config = config,
            isNew = isNew,
            sourceIcon = source.icon,
            sourceVersion = source.versionName,
            permissions = source.requestedPermissions.distinct().sorted(),
            preview = preview(config, source.icon),
            tamper = source.tamper,
        )
        screen = Screen.Editor
    }

    private fun preview(config: CloneConfig, icon: Drawable?): ImageBitmap? =
        (IconFactory.render(config, icon) ?: icon?.toBitmap(IconFactory.SIZE, IconFactory.SIZE))?.asImageBitmap()

    fun updateConfig(transform: (CloneConfig) -> CloneConfig) {
        val e = editor ?: return
        val c = transform(e.config)
        val iconChanged = c.iconMode != e.config.iconMode || c.badgeColor != e.config.badgeColor ||
            c.badgeText != e.config.badgeText || c.customIconPath != e.config.customIconPath ||
            c.iconAdjust != e.config.iconAdjust
        editor = e.copy(
            config = c,
            preview = if (iconChanged) preview(c, e.sourceIcon) else e.preview,
            packageError = null,
        )
    }

    fun setCustomIcon(uri: Uri) = launchBusy {
        val e = editor ?: return@launchBusy
        val file = withContext(Dispatchers.IO) {
            File(ctx.filesDir, "icons").mkdirs()
            File(ctx.filesDir, "icons/${e.config.id}-${System.currentTimeMillis()}.img").also { f ->
                ctx.contentResolver.openInputStream(uri)!!.use { i -> f.outputStream().use { i.copyTo(it) } }
            }
        }
        updateConfig { it.copy(iconMode = IconMode.CUSTOM, customIconPath = file.path) }
    }

    fun closeEditor() {
        val e = editor
        if (e != null && e.isNew && e.config.sourceKind == SourceKind.FILE) {
            ApkSources.sourceDir(ctx, e.config.id).deleteRecursively()
        }
        editor = null
        screen = Screen.Home
    }

    fun saveAndBuild() {
        val e = editor ?: return
        val c = e.config.copy(label = e.config.label.trim(), clonePackage = e.config.clonePackage.trim())
        val err = when {
            !PACKAGE_REGEX.matches(c.clonePackage) -> ctx.getString(R.string.err_package_invalid)
            c.clonePackage == c.sourcePackage -> ctx.getString(R.string.err_package_same)
            repo.clones.value.any { it.id != c.id && it.clonePackage == c.clonePackage } -> ctx.getString(R.string.err_package_used)
            c.label.isEmpty() -> ctx.getString(R.string.err_name_empty)
            else -> null
        }
        if (err != null) {
            editor = e.copy(packageError = err)
            return
        }
        repo.upsert(c)
        lastPreview = c.id to e.preview
        editor = null
        tab = 0
        screen = Screen.Home
        runBuild(c)
    }

    fun rebuild(config: CloneConfig) = runBuild(config)

    private fun runBuild(config: CloneConfig) {
        if (build?.running == true) return
        val icon = (editorPreviewFor(config) ?: clones.firstOrNull { it.config.id == config.id }?.icon)
        build = BuildState(config.clonePackage, config.label, icon, running = true, logs = listOf("Building ${config.label}…"))
        viewModelScope.launch {
            val result = try {
                CloneOperations.buildAndInstall(ctx, config) { line ->
                    viewModelScope.launch(Dispatchers.Main) {
                        build?.let {
                            val stage = when {
                                line.startsWith("Patching") -> 1
                                line.startsWith("Signing") -> 2
                                line.startsWith("Installing") || line.startsWith("Waiting") -> 3
                                else -> it.stage
                            }
                            build = it.copy(logs = it.logs + line, stage = maxOf(stage, it.stage))
                        }
                    }
                }
            } catch (t: Throwable) {
                InstallResult(false, t.message ?: t.javaClass.simpleName).also {
                    build?.let { b -> build = b.copy(logs = b.logs + "✗ ${it.message}") }
                }
            }
            build?.let { build = it.copy(running = false, result = result, stage = if (result.success) 4 else it.stage) }
            refreshClones()
        }
    }

    private var lastPreview: Pair<String, ImageBitmap?>? = null
    private fun editorPreviewFor(config: CloneConfig): ImageBitmap? = lastPreview?.takeIf { it.first == config.id }?.second

    /** Asks GitHub for a newer release. [manual] reports "up to date" too, for the Settings button. */
    fun checkForAppUpdate(manual: Boolean = false) = viewModelScope.launch {
        val update = UpdateChecker.check(BuildConfig.VERSION_NAME)
        appUpdate = update
        if (manual && update == null) message = ctx.getString(R.string.update_none)
    }

    /** Grants a special access to a clone through Shizuku, then refreshes the list. */
    fun grantAccess(item: CloneItem, access: SpecialAccess) = launchBusy {
        val ok = ShizukuInstaller.allowAppOp(ctx, item.config.clonePackage, access.op)
        if (!ok) message = ctx.getString(R.string.err_grant, ctx.getString(access.label))
        refreshClones()
    }

    fun dismissBuild() {
        build = null
    }

    fun delete(config: CloneConfig) {
        repo.remove(config.id)
        ApkSources.sourceDir(ctx, config.id).deleteRecursively()
        config.customIconPath?.let { File(it).delete() }
    }

    fun launchIntent(packageName: String): Intent? = ctx.packageManager.getLaunchIntentForPackage(packageName)

    // ---- extract ------------------------------------------------------------------------------

    fun openExport(packageName: String) = launchBusy {
        val (source, splits) = withContext(Dispatchers.IO) {
            val src = ApkSources.installed(ctx, packageName)
            src to src.files.map { f -> (ApkSources.splitName(f) ?: "base") to f.length() }
        }
        export = ExportState(
            source = source,
            iconBitmap = source.icon?.toBitmap(128, 128)?.asImageBitmap(),
            splits = splits,
            format = if (source.files.size == 1) ExportFormat.APK else ExportFormat.APKS,
        )
    }

    fun setExportFormat(format: ExportFormat) {
        export?.let { if (!it.running) export = it.copy(format = format, done = null, error = null) }
    }

    private var exportJob: kotlinx.coroutines.Job? = null

    fun runExport(target: Uri) {
        val state = export ?: return
        export = state.copy(running = true, progress = 0f, done = null, error = null)
        exportJob = viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    ctx.contentResolver.openOutputStream(target, "w")!!.use { out ->
                        var last = -1f
                        ApkExporter.export(ctx, state.source, state.format, out) { p ->
                            if (p - last < 0.01f && p < 1f) return@export
                            last = p
                            viewModelScope.launch(Dispatchers.Main) { export?.let { if (it.running) export = it.copy(progress = p) } }
                        }
                    }
                }
                export = export?.copy(running = false, progress = 1f, done = target)
            } catch (t: Throwable) {
                runCatching { android.provider.DocumentsContract.deleteDocument(ctx.contentResolver, target) }
                export = export?.copy(running = false, error = if (t is kotlinx.coroutines.CancellationException) "Cancelled" else (t.message ?: t.javaClass.simpleName))
            }
        }
    }

    fun closeExport() {
        exportJob?.cancel()
        exportJob = null
        export = null
    }

    private fun launchBusy(block: suspend () -> Unit) = viewModelScope.launch {
        busy = true
        try {
            block()
        } catch (t: Throwable) {
            message = t.message ?: t.javaClass.simpleName
        } finally {
            busy = false
        }
    }

    companion object {
        val PACKAGE_REGEX = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$")
    }
}
