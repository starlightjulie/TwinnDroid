package com.twinndroid.engine

import com.reandroid.apk.ApkModule
import com.twinndroid.data.CellOptions
import com.twinndroid.data.CloneConfig
import com.twinndroid.data.SourceKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Runs the real clone pipeline on the JVM. Point TWINN_TEST_APKS at an APK (or a
 * comma-separated base+splits set); outputs land in app/build/clone-test for inspection
 * with apksigner/aapt2.
 */
class CloneEngineTest {

    @Test
    fun clonesApkSet() = runBlocking {
        val inputs = System.getenv("TWINN_TEST_APKS")?.split(',')?.map { File(it.trim()) }
        assumeTrue("TWINN_TEST_APKS not set", inputs != null)
        val newPkg = System.getenv("TWINN_TEST_PKG") ?: "com.example.twinnclone"
        val out = File("build/clone-test").apply { deleteRecursively(); mkdirs() }

        val config = CloneConfig(
            id = "test",
            sourceKind = SourceKind.FILE,
            sourcePackage = "",
            sourceLabel = "",
            clonePackage = newPkg,
            label = "Twinn Test Clone",
            removedPermissions = listOf("android.permission.POST_NOTIFICATIONS"),
        )
        // 1x1 PNG; enough to exercise resource-table injection
        val png = java.util.Base64.getDecoder()
            .decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=")

        val key = SigningKeys.newKey()
        val signed = CloneEngine(out) { key }.build(config, inputs!!, png) { println(it) }

        assertEquals(inputs.size, signed.size)
        for (apk in signed) {
            val module = ApkModule.loadApkFile(apk)
            val manifest = module.androidManifest
            assertEquals(newPkg, manifest.packageName)
            if (module.hasTableBlock()) {
                module.tableBlock.packages.asSequence().filter { it.id == 0x7f }.forEach {
                    assertEquals("resource table package not renamed", newPkg, it.name)
                }
            }
            println("${apk.name}: package=${manifest.packageName} split=${manifest.split}")
            module.close()
        }
        val base = ApkModule.loadApkFile(signed.first())
        val text = base.androidManifest.toString()
        assertTrue("label not applied", base.androidManifest.applicationLabelString == "Twinn Test Clone")
        assertTrue("permission not removed", "POST_NOTIFICATIONS" !in base.androidManifest.usesPermissions.joinToString())
        val icon = base.androidManifest.iconResourceId
        val pkg = base.tableBlock.packages.asSequence().first { it.id == 0x7f }
        assertEquals("twinndroid_clone_icon", pkg.getEntry("", "drawable", "twinndroid_clone_icon").name)
        assertEquals(pkg.getEntry("", "drawable", "twinndroid_clone_icon").resourceId, icon)
        base.close()
        // A split's density-only image must not share the icon's id, or Android shows that instead
        for (apk in signed.drop(1)) {
            val split = ApkModule.loadApkFile(apk)
            if (split.hasTableBlock()) {
                split.tableBlock.packages.asSequence().mapNotNull { it.getSpecTypePair("drawable") }.forEach {
                    assertTrue("icon id clashes with ${apk.name}", it.highestEntryId < (icon and 0xFFFF))
                }
            }
            split.close()
        }
        println(text.take(200))
    }

    @Test
    fun hardensApkSet() = runBlocking {
        val inputs = System.getenv("TWINN_TEST_APKS")?.split(',')?.map { File(it.trim()) }
        assumeTrue("TWINN_TEST_APKS not set", inputs != null)
        val out = File("build/clone-test-cell").apply { deleteRecursively(); mkdirs() }
        val config = CloneConfig(
            id = "cell",
            sourceKind = SourceKind.FILE,
            sourcePackage = "",
            sourceLabel = "",
            clonePackage = "com.example.twinncell",
            label = "Twinn Cell",
            cell = CellOptions(noInternet = true),
        )
        val key = SigningKeys.newKey()
        val signed = CloneEngine(out) { key }.build(config, inputs!!, null) { println(it) }

        val base = ApkModule.loadApkFile(signed.first())
        val manifest = base.androidManifest
        val permissions = manifest.usesPermissions.joinToString()
        for (gone in listOf("android.permission.INTERNET", "android.permission.QUERY_ALL_PACKAGES", "android.permission.RECEIVE_BOOT_COMPLETED")) {
            assertTrue("$gone not removed", gone !in permissions)
        }
        val root = manifest.manifestElement
        assertTrue("<queries> not removed", root.recursiveElements().asSequence().none { it.name == "queries" })

        val app = manifest.applicationElement
        assertEquals("allowBackup not off", 0, app.searchAttributeByResourceId(android.R.attr.allowBackup).data)

        var launchers = 0
        for (el in root.recursiveElements().asSequence().filter { it.name in setOf("activity", "activity-alias", "service", "receiver", "provider") }) {
            val filters = el.recursiveElements().asSequence().toList()
            val reachable = filters.any { child ->
                val v = child.searchAttributeByResourceId(android.R.attr.name)?.valueAsString
                v == "android.intent.category.LAUNCHER" || v == "android.intent.category.BROWSABLE" || v == "android.intent.category.LEANBACK_LAUNCHER" ||
                    v == "android.appwidget.action.APPWIDGET_UPDATE"
            }
            val exported = el.searchAttributeByResourceId(android.R.attr.exported)
            if (reachable) {
                launchers++
            } else {
                assertEquals("${el.name} still exported", 0, exported?.data)
            }
        }
        assertTrue("launcher entry lost", launchers > 0)
        base.close()
    }
}
