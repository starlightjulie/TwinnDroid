package com.twinndroid.engine

import android.content.Context
import com.android.apksig.ApkSigner
import com.android.apksig.KeyConfig
import com.reandroid.apk.ApkModule
import com.reandroid.archive.ByteInputSource
import com.twinndroid.data.CloneConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry

/**
 * Turns a set of source APKs into a signed, installable clone:
 * patch manifest(s) -> inject icon -> strip old signatures -> re-sign with the TwinnDroid key.
 */
class CloneEngine(
    private val workRoot: File,
    private val keyProvider: () -> SigningKeys.Key,
) {
    constructor(context: Context, config: CloneConfig? = null) : this(
        File(context.cacheDir, "build"),
        { if (config?.cell?.ownKey == true) SigningKeys.forClone(context, config.id) else SigningKeys.get(context) },
    )

    suspend fun build(
        config: CloneConfig,
        sources: List<File>,
        iconPng: ByteArray?,
        log: (String) -> Unit,
    ): List<File> = withContext(Dispatchers.IO) {
        val work = File(workRoot, config.id).apply { deleteRecursively(); mkdirs() }
        val key = keyProvider()
        var minSdk = 21
        val unsigned = mutableListOf<File>()
        val takenDrawables = if (iconPng != null) sources.maxOf(::highestDrawableEntry) else -1

        sources.forEachIndexed { index, src ->
            log("Patching ${src.name} (${index + 1}/${sources.size})")
            val module = ApkModule.loadApkFile(src)
            try {
                val manifest = module.androidManifest ?: error("${src.name} has no AndroidManifest.xml")
                val oldPkg = manifest.packageName ?: error("${src.name} has no package name")
                val isBase = !manifest.isSplit

                ManifestPatcher.renamePackage(manifest, oldPkg, config.clonePackage)
                // Resources.getIdentifier(name, type, getPackageName()) resolves against the
                // resource table's package name, so it must match the new package too.
                renameResourcePackage(module, oldPkg, config.clonePackage)
                config.cell?.let { ManifestPatcher.harden(manifest, it, isBase) }

                if (isBase) {
                    manifest.minSdkVersion?.let { minSdk = it }
                    ManifestPatcher.setLabel(manifest, config.label)
                    ManifestPatcher.removePermissions(manifest, config.removedPermissions)
                    if (config.debuggable) manifest.isDebuggable = true
                    if (iconPng != null) injectIcon(module, iconPng, takenDrawables)?.let {
                        ManifestPatcher.setIcon(manifest, it)
                    } ?: log("  ! No resource table, keeping original icon")
                }
                manifest.refresh()

                stripSignatures(module)
                // Android 11+ requires resources.arsc to be stored uncompressed.
                module.zipEntryMap.getInputSource("resources.arsc")?.method = ZipEntry.STORED

                val out = File(work, "u_${index}_${src.nameWithoutExtension}.apk")
                module.writeApk(out)
                unsigned += out
            } finally {
                module.close()
            }
        }

        log("Signing ${unsigned.size} APK(s)")
        val signed = unsigned.map { u ->
            val out = File(work, u.name.removePrefix("u_"))
            sign(u, out, key, minSdk)
            u.delete()
            out
        }
        log("Build finished")
        signed
    }

    private fun renameResourcePackage(module: ApkModule, oldPkg: String, newPkg: String) {
        if (!module.hasTableBlock()) return
        for (pkg in module.tableBlock.packages.asSequence()) {
            if (pkg.name == oldPkg) pkg.name = newPkg
        }
    }

    /** The highest drawable entry id in one APK, or -1 if it has none. */
    private fun highestDrawableEntry(apk: File): Int {
        val module = ApkModule.loadApkFile(apk)
        try {
            if (!module.hasTableBlock()) return -1
            return module.tableBlock.packages.asSequence().mapNotNull { it.getSpecTypePair("drawable") }.maxOfOrNull { it.highestEntryId } ?: -1
        } finally {
            module.close()
        }
    }

    /**
     * Adds the PNG as a new drawable resource in the base APK; returns its resource id.
     *
     * The id has to clear [takenDrawables], the highest one in any APK of the set. Images that only
     * exist at certain densities are only in the split's table, so the next free id in the base can
     * already belong to one, and Android would show that image as the icon instead.
     */
    private fun injectIcon(module: ApkModule, png: ByteArray, takenDrawables: Int): Int? {
        if (!module.hasTableBlock()) return null
        val table = module.tableBlock
        val pkg = table.packages.asSequence().firstOrNull { it.id == 0x7f }
            ?: table.packages.asSequence().firstOrNull() ?: return null
        val name = "twinndroid_clone_icon"
        val path = "res/$name.png"
        val entry = pkg.getEntry("", "drawable", name) ?: run {
            val drawables = pkg.getOrCreateSpecTypePair("drawable")
            drawables.getOrCreateEntry((maxOf(drawables.highestEntryId, takenDrawables) + 1).toShort(), "").apply { setName(name, true) }
        }
        entry.setValueAsString(path)
        module.add(ByteInputSource(png, path).apply { method = ZipEntry.STORED })
        return entry.resourceId
    }

    internal fun stripSignatures(module: ApkModule) {
        val sigFile = Regex("^META-INF/[^/]+\\.(SF|RSA|DSA|EC|MF)$", RegexOption.IGNORE_CASE)
        module.zipEntryMap.removeIf { src -> sigFile.matches(src.alias) || src.alias == "stamp-cert-sha256" }
        module.apkSignatureBlock = null
    }

    internal fun sign(input: File, output: File, key: SigningKeys.Key, minSdk: Int) {
        val signer = ApkSigner.SignerConfig.Builder("TWINN", KeyConfig.Jca(key.privateKey), listOf(key.certificate)).build()
        ApkSigner.Builder(listOf(signer))
            .setInputApk(input)
            .setOutputApk(output)
            .setMinSdkVersion(minSdk)
            .setV1SigningEnabled(minSdk < 24)
            .setV2SigningEnabled(true)
            .setV3SigningEnabled(true)
            .setDebuggableApkPermitted(true)
            .setLibraryPageAlignmentBytes(16384)
            .setCreatedBy("TwinnDroid")
            .build()
            .sign()
    }
}
