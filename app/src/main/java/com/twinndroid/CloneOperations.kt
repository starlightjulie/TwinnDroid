package com.twinndroid

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.pm.PackageInfoCompat
import com.twinndroid.data.CloneConfig
import com.twinndroid.data.CloneRepository
import com.twinndroid.data.Settings
import com.twinndroid.data.SourceKind
import com.twinndroid.engine.ApkSources
import com.twinndroid.engine.CloneEngine
import com.twinndroid.engine.IconFactory
import com.twinndroid.engine.SourceApks
import com.twinndroid.install.InstallResult
import com.twinndroid.install.SessionInstaller
import com.twinndroid.install.ShizukuInstaller
import com.twinndroid.sandbox.Sandbox

/** Build + install pipeline shared by the UI and the background update worker. */
object CloneOperations {

    fun resolveSource(context: Context, config: CloneConfig): SourceApks = when (config.sourceKind) {
        SourceKind.INSTALLED -> ApkSources.installed(context, config.sourcePackage)
        SourceKind.FILE -> ApkSources.stored(context, ApkSources.sourceDir(context, config.id))
    }

    suspend fun buildAndInstall(
        context: Context,
        config: CloneConfig,
        allowPrompt: Boolean = true,
        log: (String) -> Unit,
    ): InstallResult {
        val source = resolveSource(context, config)
        log("Source: ${source.label} ${source.versionName ?: ""} (${source.files.size} APK file(s))")

        val iconPng = IconFactory.render(config, source.icon)?.let(IconFactory::toPng)
        val outputs = CloneEngine(context, config).build(config, source.files, iconPng, log)

        val settings = Settings(context)
        val result = if (config.inSandbox) {
            if (allowPrompt) {
                log("Installing into the sandbox…")
                Sandbox.install(context, config.clonePackage, outputs)
            } else {
                InstallResult(false, "Sandbox installs can't run in the background")
            }
        } else if (settings.useShizuku && ShizukuInstaller.hasPermission()) {
            log("Installing with Shizuku…")
            ShizukuInstaller.install(context, outputs)
        } else if (allowPrompt) {
            log("Waiting for Android installer confirmation…")
            SessionInstaller.install(context, config.clonePackage, outputs)
        } else {
            InstallResult(false, "Shizuku is not available for a background install")
        }

        outputs.firstOrNull()?.parentFile?.deleteRecursively()
        if (result.success) {
            CloneRepository.get(context).upsert(
                config.copy(builtVersionCode = source.versionCode, builtVersionName = source.versionName),
            )
        }
        log(if (result.success) "✓ ${result.message}" else "✗ ${result.message}")
        return result
    }

    /** Installed version code of a package, or null when it is not installed. */
    fun installedVersion(context: Context, packageName: String): Long? = try {
        PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(packageName, 0))
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    /** Like [installedVersion], but looks inside the sandbox for clones that live there. */
    fun installedVersion(context: Context, config: CloneConfig): Long? =
        if (config.inSandbox) config.builtVersionCode.takeIf { Sandbox.contains(context, config.clonePackage) }
        else installedVersion(context, config.clonePackage)

    /** Clones whose original app has a newer version than the one they were built from. */
    fun outdated(context: Context): List<CloneConfig> =
        CloneRepository.get(context).clones.value.filter { c ->
            c.sourceKind == SourceKind.INSTALLED && c.builtVersionCode > 0 &&
                installedVersion(context, c) != null &&
                (installedVersion(context, c.sourcePackage) ?: 0) > c.builtVersionCode
        }
}
