package com.twinndroid.data

import android.content.Context
import androidx.core.content.edit
import com.twinndroid.engine.SigningKeys
import com.twinndroid.ui.theme.ThemeRepository
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Full TwinnDroid backup as a zip: clone list, the clone signing key, stored APK sources,
 * custom icons and preferences. Restoring it on a fresh install (even one signed with a
 * different key) brings every clone back and keeps them updatable.
 */
object BackupManager {
    private const val MARKER = "twinndroid-backup.txt"
    private val FILES = listOf("clones.json", "signing.p12")
    private val DIRS = listOf("sources", "icons", "keys")
    private val PREFS = listOf("settings", "theme")

    fun export(context: Context, out: OutputStream) {
        SigningKeys.get(context) // make sure a key exists before backing it up
        val root = context.filesDir
        ZipOutputStream(out.buffered()).use { zip ->
            zip.setLevel(Deflater.BEST_SPEED)
            zip.putNextEntry(ZipEntry(MARKER))
            zip.write("TwinnDroid backup v1\n".toByteArray())
            zip.closeEntry()
            for (name in FILES) File(root, name).takeIf { it.isFile }?.let { add(zip, it, name) }
            for (dir in DIRS) {
                val base = File(root, dir)
                base.walkTopDown().filter { it.isFile }.forEach { add(zip, it, "$dir/" + it.relativeTo(base).invariantSeparatorsPath) }
            }
            for (pref in PREFS) {
                val map = context.getSharedPreferences(pref, Context.MODE_PRIVATE).all
                val text = map.entries.joinToString("\n") { (k, v) ->
                    val type = when (v) {
                        is Boolean -> "b"
                        is Int -> "i"
                        is Long -> "l"
                        is Float -> "f"
                        else -> "s"
                    }
                    "$type\t$k\t${v.toString().replace("\\", "\\\\").replace("\n", "\\n")}"
                }
                zip.putNextEntry(ZipEntry("prefs/$pref.tsv"))
                zip.write(text.toByteArray())
                zip.closeEntry()
            }
        }
    }

    private fun add(zip: ZipOutputStream, file: File, name: String) {
        zip.putNextEntry(ZipEntry(name))
        file.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
    }

    /** Replaces the current data with the backup's. Throws if the file isn't a TwinnDroid backup. */
    fun restore(context: Context, input: InputStream) {
        val root = context.filesDir.canonicalFile
        val staging = File(context.cacheDir, "restore").apply { deleteRecursively(); mkdirs() }
        var valid = false
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                if (entry.name == MARKER) valid = true
                val target = File(staging, entry.name).canonicalFile
                require(target.path.startsWith(staging.canonicalPath + File.separator)) { "Invalid backup entry ${entry.name}" }
                target.parentFile?.mkdirs()
                target.outputStream().use { zip.copyTo(it) }
            }
        }
        require(valid) { "This is not a TwinnDroid backup file" }

        for (name in FILES) File(staging, name).takeIf { it.isFile }?.copyTo(File(root, name), overwrite = true)
        for (dir in DIRS) {
            val src = File(staging, dir)
            if (!src.isDirectory) continue
            File(root, dir).deleteRecursively()
            src.copyRecursively(File(root, dir), overwrite = true)
        }
        for (pref in PREFS) {
            val f = File(staging, "prefs/$pref.tsv").takeIf { it.isFile } ?: continue
            context.getSharedPreferences(pref, Context.MODE_PRIVATE).edit {
                clear()
                f.readLines().filter { it.isNotBlank() }.forEach { line ->
                    val parts = line.split('\t', limit = 3)
                    if (parts.size < 3) return@forEach
                    val v = parts[2].replace("\\n", "\n").replace("\\\\", "\\")
                    when (parts[0]) {
                        "b" -> putBoolean(parts[1], v.toBoolean())
                        "i" -> putInt(parts[1], v.toInt())
                        "l" -> putLong(parts[1], v.toLong())
                        "f" -> putFloat(parts[1], v.toFloat())
                        else -> putString(parts[1], v)
                    }
                }
            }
        }
        staging.deleteRecursively()

        CloneRepository.get(context).reload()
        SigningKeys.invalidate()
        ThemeRepository.get(context).reload()
        // A restored app-icon choice is applied by MainActivity.onStop (AppIcons.applyPending).
    }
}
