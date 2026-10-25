package com.twinndroid.engine

import com.twinndroid.R
import androidx.annotation.StringRes
import android.content.Context
import androidx.core.graphics.drawable.toBitmap
import com.reandroid.apk.ApkBundle
import com.reandroid.apk.ApkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.io.OutputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.coroutines.coroutineContext

enum class ExportFormat(val ext: String, @StringRes val label: Int, @StringRes val description: Int) {
    APK("apk", R.string.fmt_apk, R.string.fmt_apk_desc),
    MERGED("apk", R.string.fmt_merged, R.string.fmt_merged_desc),
    APKS("apks", R.string.fmt_apks, R.string.fmt_apks_desc),
    XAPK("xapk", R.string.fmt_xapk, R.string.fmt_xapk_desc),
    APKM("apkm", R.string.fmt_apkm, R.string.fmt_apkm_desc),
}

/** Saves an app's APK set in an installable format. Original signatures are kept except for [ExportFormat.MERGED]. */
object ApkExporter {

    fun fileName(source: SourceApks, format: ExportFormat): String {
        val name = "${source.label}_${source.versionName ?: source.versionCode}".replace(Regex("[^A-Za-z0-9._-]+"), "_")
        return "$name.${format.ext}"
    }

    suspend fun export(
        context: Context,
        source: SourceApks,
        format: ExportFormat,
        out: OutputStream,
        progress: (Float) -> Unit,
    ) = withContext(Dispatchers.IO) {
        when (format) {
            ExportFormat.APK -> {
                require(source.files.size == 1) { "This app uses split APKs; choose Merged APK or a bundle format" }
                copy(source.files.first(), out, progress)
            }
            ExportFormat.MERGED -> merge(context, source, out, progress)
            ExportFormat.APKS -> bundle(out, progress, entries(source) { _, split -> if (split == null) "base.apk" else "split_$split.apk" })
            ExportFormat.XAPK -> {
                val entries = entries(source) { _, split -> if (split == null) "${source.packageName}.apk" else "$split.apk" }
                bundle(out, progress, entries) { zip ->
                    val manifest = buildJsonObject {
                        put("xapk_version", 2)
                        put("package_name", source.packageName)
                        put("name", source.label)
                        put("version_code", source.versionCode.toString())
                        put("version_name", source.versionName ?: "")
                        put("min_sdk_version", source.minSdk.toString())
                        put("target_sdk_version", source.targetSdk.toString())
                        put("permissions", buildJsonArray { source.requestedPermissions.forEach { add(JsonPrimitive(it)) } })
                        put("split_configs", buildJsonArray { entries.mapNotNull { it.split }.forEach { add(JsonPrimitive(it)) } })
                        put("total_size", source.files.sumOf { it.length() })
                        put("icon", "icon.png")
                        put(
                            "split_apks",
                            buildJsonArray {
                                entries.forEach { e ->
                                    add(buildJsonObject { put("file", e.name); put("id", e.split ?: "base") })
                                }
                            },
                        )
                    }
                    putText(zip, "manifest.json", manifest.toString())
                    putIcon(zip, source)
                }
            }
            ExportFormat.APKM -> {
                val entries = entries(source) { _, split -> if (split == null) "base.apk" else "split_$split.apk" }
                bundle(out, progress, entries) { zip ->
                    val info = buildJsonObject {
                        put("apkm_version", 5)
                        put("app_name", source.label)
                        put("pname", source.packageName)
                        put("release_version", source.versionName ?: "")
                        put("versioncode", source.versionCode.toString())
                        put("min_api", source.minSdk.toString())
                        put("splits", buildJsonArray { entries.mapNotNull { it.split }.forEach { add(JsonPrimitive(it)) } })
                    }
                    putText(zip, "info.json", info.toString())
                    putIcon(zip, source)
                }
            }
        }
        progress(1f)
    }

    private class Entry(val file: File, val name: String, val split: String?)

    private fun entries(source: SourceApks, name: (File, String?) -> String): List<Entry> =
        source.files.map { f ->
            val split = ApkSources.splitName(f)
            Entry(f, name(f, split), split)
        }

    private suspend fun bundle(
        out: OutputStream,
        progress: (Float) -> Unit,
        entries: List<Entry>,
        extra: (ZipOutputStream) -> Unit = {},
    ) {
        val total = entries.sumOf { it.file.length() }.coerceAtLeast(1)
        var done = 0L
        ZipOutputStream(out.buffered(1 shl 16)).use { zip ->
            // APKs are already compressed; storing-level deflate keeps exports fast.
            zip.setLevel(Deflater.NO_COMPRESSION)
            for (e in entries) {
                zip.putNextEntry(ZipEntry(e.name))
                e.file.inputStream().use { input ->
                    val buf = ByteArray(1 shl 16)
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        zip.write(buf, 0, n)
                        done += n
                        progress(done.toFloat() / total * 0.97f)
                    }
                }
                zip.closeEntry()
            }
            zip.setLevel(Deflater.DEFAULT_COMPRESSION)
            extra(zip)
        }
    }

    private suspend fun copy(file: File, out: OutputStream, progress: (Float) -> Unit) {
        val total = file.length().coerceAtLeast(1)
        var done = 0L
        file.inputStream().use { input ->
            val buf = ByteArray(1 shl 16)
            while (true) {
                coroutineContext.ensureActive()
                val n = input.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                done += n
                progress(done.toFloat() / total)
            }
        }
        out.flush()
    }

    private fun putText(zip: ZipOutputStream, name: String, text: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(text.toByteArray())
        zip.closeEntry()
    }

    private fun putIcon(zip: ZipOutputStream, source: SourceApks) {
        val icon = source.icon ?: return
        val bmp = runCatching { icon.toBitmap(192, 192) }.getOrNull() ?: return
        zip.putNextEntry(ZipEntry("icon.png"))
        zip.write(IconFactory.toPng(bmp))
        zip.closeEntry()
    }

    private val SPLIT_META = setOf(
        "com.android.vending.splits",
        "com.android.vending.splits.required",
        "com.android.vending.derived.apk.id",
        "com.android.stamp.source",
        "com.android.stamp.type",
    )

    /** Merges base + splits into one APK with ARSCLib, strips split metadata and re-signs it. */
    private suspend fun merge(context: Context, source: SourceApks, out: OutputStream, progress: (Float) -> Unit) {
        val work = File(context.cacheDir, "merge").apply { deleteRecursively(); mkdirs() }
        try {
            val signed = mergeTo(source.files, work, CloneEngine(context), SigningKeys.get(context), source.minSdk, progress)
            signed.inputStream().use { it.copyTo(out) }
            out.flush()
        } finally {
            work.deleteRecursively()
        }
    }

    /** Context-free merge used by [merge] and the JVM tests. Returns the signed APK inside [work]. */
    internal suspend fun mergeTo(
        files: List<File>,
        work: File,
        engine: CloneEngine,
        key: SigningKeys.Key,
        fallbackMinSdk: Int,
        progress: (Float) -> Unit,
    ): File {
        progress(0.05f)
        val bundle = ApkBundle()
        try {
            files.forEach { f -> bundle.addModule(ApkModule.loadApkFile(f, ApkSources.splitName(f) ?: "base")) }
            progress(0.2f)
            coroutineContext.ensureActive()
            val merged = bundle.mergeModules()
            progress(0.55f)
            val manifest = merged.androidManifest
            manifest.manifestElement?.let { root ->
                root.removeAttributesWithName("requiredSplitTypes")
                root.removeAttributesWithName("splitTypes")
                root.removeAttributesWithId(android.R.attr.isSplitRequired)
            }
            manifest.applicationElement?.let { app ->
                app.removeAttributesWithId(android.R.attr.isSplitRequired)
                app.removeElementsIf { el ->
                    el.name == "meta-data" &&
                        el.searchAttributeByResourceId(android.R.attr.name)?.valueAsString in SPLIT_META
                }
            }
            manifest.refresh()
            engine.stripSignatures(merged)
            merged.zipEntryMap.getInputSource("resources.arsc")?.method = ZipEntry.STORED
            val unsigned = File(work, "merged-unsigned.apk")
            merged.writeApk(unsigned)
            progress(0.8f)
            coroutineContext.ensureActive()
            val signed = File(work, "merged.apk")
            engine.sign(unsigned, signed, key, manifest.minSdkVersion ?: fallbackMinSdk)
            unsigned.delete()
            progress(0.9f)
            return signed
        } finally {
            bundle.close()
        }
    }
}
