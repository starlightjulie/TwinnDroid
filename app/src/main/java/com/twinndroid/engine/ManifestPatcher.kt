package com.twinndroid.engine

import com.reandroid.arsc.chunk.xml.AndroidManifestBlock
import com.reandroid.arsc.chunk.xml.ResXmlAttribute
import com.reandroid.arsc.chunk.xml.ResXmlElement
import com.reandroid.arsc.value.ValueType
import com.twinndroid.data.CellOptions

/**
 * Rewrites a binary AndroidManifest so the app can be installed side by side with the original,
 * the same way other no-root cloning apps do it: new package name, unique provider authorities and custom
 * permissions, no shared user id. Attributes are matched by resource id so obfuscated
 * manifests (stripped attribute names) still work.
 */
object ManifestPatcher {

    private val COMPONENT_TAGS = setOf("application", "activity", "activity-alias", "service", "receiver", "provider")
    private val PERMISSION_DECL_TAGS = setOf("permission", "permission-group", "permission-tree")
    private val USES_PERMISSION_TAGS = setOf("uses-permission", "uses-permission-sdk-23", "uses-permission-sdk-m")
    private val EXPORTABLE_TAGS = setOf("activity", "activity-alias", "service", "receiver", "provider")
    private val REACHABLE_CATEGORIES = setOf(
        "android.intent.category.LAUNCHER",
        "android.intent.category.LEANBACK_LAUNCHER",
        "android.intent.category.BROWSABLE",
    )
    private val AD_PERMISSIONS = listOf(
        "com.google.android.gms.permission.AD_ID",
        "android.permission.ACCESS_ADSERVICES_AD_ID",
        "android.permission.ACCESS_ADSERVICES_ATTRIBUTION",
        "android.permission.ACCESS_ADSERVICES_TOPICS",
    )
    private val STORAGE_PERMISSIONS = listOf(
        "android.permission.READ_EXTERNAL_STORAGE",
        "android.permission.WRITE_EXTERNAL_STORAGE",
        "android.permission.MANAGE_EXTERNAL_STORAGE",
        "android.permission.READ_MEDIA_IMAGES",
        "android.permission.READ_MEDIA_VIDEO",
        "android.permission.READ_MEDIA_AUDIO",
        "android.permission.READ_MEDIA_VISUAL_USER_SELECTED",
        "android.permission.ACCESS_MEDIA_LOCATION",
    )
    private val BOOT_PERMISSIONS = listOf(
        "android.permission.RECEIVE_BOOT_COMPLETED",
        "android.intent.action.QUICKBOOT_POWERON",
    )

    /** Attributes holding class names that may be written relative to the package (".Foo"). */
    private val CLASS_NAME_ATTRS = intArrayOf(
        android.R.attr.name,
        android.R.attr.targetActivity,
        android.R.attr.manageSpaceActivity,
        android.R.attr.backupAgent,
        android.R.attr.parentActivityName,
        android.R.attr.appComponentFactory,
    )

    private fun ResXmlElement.attr(id: Int): ResXmlAttribute? = searchAttributeByResourceId(id)

    private fun ResXmlAttribute.str(): String? =
        if (valueType == ValueType.STRING) valueAsString else null

    private fun ResXmlAttribute.setReference(resId: Int) {
        valueType = ValueType.REFERENCE
        data = resId
    }

    private fun ResXmlAttribute.setFalse() {
        valueType = ValueType.BOOLEAN
        data = 0
    }

    private fun ResXmlElement.isInsideQueries(): Boolean =
        getParentElementsWithSelf().asSequence().any { it.name == "queries" }

    private fun AndroidManifestBlock.allElements(): List<ResXmlElement> {
        val root = manifestElement ?: return emptyList()
        return (sequenceOf(root) + root.recursiveElements().asSequence()).distinct().toList()
    }

    /**
     * Renames [oldPkg] to [newPkg]. Must run on every APK of a split set (base and splits),
     * since each split manifest carries the package name too.
     */
    fun renamePackage(manifest: AndroidManifestBlock, oldPkg: String, newPkg: String) {
        val root = manifest.manifestElement ?: error("APK has no <manifest> element")

        // 1. Make relative class names absolute while the old package is still known.
        for (el in manifest.allElements()) {
            if (el.name !in COMPONENT_TAGS || el.isInsideQueries()) continue
            for (id in CLASS_NAME_ATTRS) {
                val a = el.attr(id) ?: continue
                val v = a.str() ?: continue
                when {
                    v.startsWith(".") -> a.setValueAsString(oldPkg + v)
                    !v.contains('.') -> a.setValueAsString("$oldPkg.$v")
                }
            }
        }

        // 2. The package itself.
        manifest.packageName = newPkg

        // 3. A clone can never join the original's shared uid (different signature).
        root.removeAttributesWithId(android.R.attr.sharedUserId)
        root.removeAttributesWithId(android.R.attr.sharedUserLabel)
        root.removeAttributesWithName("sharedUserMaxSdkVersion")
        root.removeElementsIf { it.name == "instrumentation" }

        val elements = manifest.allElements().filterNot { it.isInsideQueries() }

        // 4. Declared permissions must be unique device-wide -> rename them, then every reference.
        val permMap = HashMap<String, String>()
        for (el in elements) {
            if (el.name !in PERMISSION_DECL_TAGS) continue
            val v = el.attr(android.R.attr.name)?.str() ?: continue
            permMap[v] = scoped(v, oldPkg, newPkg)
        }
        if (permMap.isNotEmpty()) {
            for (el in elements) {
                for (a in el.attributes.asSequence()) {
                    val v = a.str() ?: continue
                    permMap[v]?.let { a.setValueAsString(it) }
                }
            }
        }

        // 5. Provider authorities must be unique device-wide.
        for (el in elements) {
            if (el.name != "provider") continue
            val a = el.attr(android.R.attr.authorities) ?: continue
            val v = a.str() ?: continue
            a.setValueAsString(v.split(';').joinToString(";") { scoped(it.trim(), oldPkg, newPkg) })
        }

        // 6. Keep clone tasks separate from the original's tasks in Recents.
        for (el in elements) {
            val a = el.attr(android.R.attr.taskAffinity) ?: continue
            val v = a.str() ?: continue
            if (v.startsWith(oldPkg)) a.setValueAsString(newPkg + v.removePrefix(oldPkg))
        }
    }

    private fun scoped(value: String, oldPkg: String, newPkg: String): String =
        if (value.contains(oldPkg)) value.replace(oldPkg, newPkg) else "$newPkg.$value"

    /** Activities / aliases that show up in the launcher. */
    private fun launcherComponents(manifest: AndroidManifestBlock): List<ResXmlElement> =
        manifest.allElements().filter { el ->
            (el.name == "activity" || el.name == "activity-alias") &&
                el.recursiveElements().asSequence().any { child ->
                    child.name == "category" &&
                        child.attr(android.R.attr.name)?.str() == "android.intent.category.LAUNCHER"
                }
        }

    fun setLabel(manifest: AndroidManifestBlock, label: String) {
        manifest.setApplicationLabel(label)
        for (el in launcherComponents(manifest)) {
            el.getOrCreateAndroidAttribute("label", android.R.attr.label).setValueAsString(label)
        }
    }

    fun setIcon(manifest: AndroidManifestBlock, resId: Int) {
        manifest.iconResourceId = resId
        manifest.roundIconResourceId = resId
        for (el in launcherComponents(manifest)) {
            el.attr(android.R.attr.icon)?.setReference(resId)
            el.attr(android.R.attr.roundIcon)?.setReference(resId)
        }
    }

    /** Applies a cell's walls. Must run on every APK of a split set, since splits can carry components. */
    fun harden(manifest: AndroidManifestBlock, cell: CellOptions, isBase: Boolean) {
        val root = manifest.manifestElement ?: return
        if (cell.privateComponents) privatizeComponents(manifest)
        if (!isBase) return

        val removed = buildList {
            if (cell.hideApps) add("android.permission.QUERY_ALL_PACKAGES")
            if (cell.noAdId) addAll(AD_PERMISSIONS)
            if (cell.noStorage) addAll(STORAGE_PERMISSIONS)
            if (cell.noBoot) addAll(BOOT_PERMISSIONS)
            // ACCESS_NETWORK_STATE stays: apps crash on connectivity checks without it, and it can't reach anything.
            if (cell.noInternet) add("android.permission.INTERNET")
        }
        removePermissions(manifest, removed)
        if (cell.hideApps) root.removeElementsIf { it.name == "queries" }
        if (cell.noBackup) {
            val app = manifest.getOrCreateApplicationElement()
            app.getOrCreateAndroidAttribute("allowBackup", android.R.attr.allowBackup).setFalse()
            app.removeAttributesWithId(android.R.attr.fullBackupContent)
            app.removeAttributesWithId(android.R.attr.dataExtractionRules)
        }
    }

    private fun privatizeComponents(manifest: AndroidManifestBlock) {
        for (el in manifest.allElements()) {
            if (el.name !in EXPORTABLE_TAGS || el.isInsideQueries() || el.mustStayReachable()) continue
            el.getOrCreateAndroidAttribute("exported", android.R.attr.exported).setFalse()
        }
    }

    /** The launcher icon, web links coming back from a login, and home-screen widgets. */
    private fun ResXmlElement.mustStayReachable(): Boolean =
        recursiveElements().asSequence().any { child ->
            val value = child.attr(android.R.attr.name)?.str()
            (child.name == "category" && value in REACHABLE_CATEGORIES) ||
                (child.name == "action" && value == "android.appwidget.action.APPWIDGET_UPDATE")
        }

    fun removePermissions(manifest: AndroidManifestBlock, permissions: Collection<String>) {
        if (permissions.isEmpty()) return
        val set = permissions.toHashSet()
        manifest.manifestElement?.removeElementsIf { el ->
            el.name in USES_PERMISSION_TAGS && el.attr(android.R.attr.name)?.str() in set
        }
    }
}
