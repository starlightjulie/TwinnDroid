package com.twinndroid.install

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

data class InstallResult(val success: Boolean, val message: String)

/** One APK of an install, read from wherever it lives. A negative [size] means unknown. */
class InstallPart(val name: String, val size: Long, val open: () -> InputStream)

/** Standard, unprivileged install via PackageInstaller (shows Android's confirmation dialog). */
object SessionInstaller {

    internal val pending = ConcurrentHashMap<Int, CompletableDeferred<InstallResult>>()

    // Session ids are positive, so uninstalls can share the same result plumbing below zero.
    private val nextUninstall = AtomicInteger(-1)

    suspend fun install(context: Context, packageName: String, apks: List<File>): InstallResult =
        install(context, packageName, apks.mapIndexed { i, apk -> InstallPart("$i-${apk.name}", apk.length()) { apk.inputStream() } })

    @JvmName("installParts")
    suspend fun install(context: Context, packageName: String, parts: List<InstallPart>): InstallResult {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(packageName)
            setInstallReason(android.content.pm.PackageManager.INSTALL_REASON_USER)
            if (Build.VERSION.SDK_INT >= 31) {
                // Lets updates of clones we installed go through without a prompt where Android allows it.
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val deferred = CompletableDeferred<InstallResult>()
        val sessionId = withContext(Dispatchers.IO) {
            val id = installer.createSession(params)
            pending[id] = deferred
            try {
                installer.openSession(id).use { session ->
                    parts.forEach { part ->
                        session.openWrite(part.name, 0, part.size).use { out ->
                            part.open().use { it.copyTo(out) }
                            session.fsync(out)
                        }
                    }
                    session.commit(statusSender(context, id))
                }
            } catch (t: Throwable) {
                pending.remove(id)
                runCatching { installer.abandonSession(id) }
                throw t
            }
            id
        }
        return try { deferred.await() } finally { pending.remove(sessionId) }
    }

    /** Asks Android to uninstall [packageName], which shows its usual confirmation. */
    suspend fun uninstall(context: Context, packageName: String): InstallResult {
        val id = nextUninstall.getAndDecrement()
        val deferred = CompletableDeferred<InstallResult>()
        pending[id] = deferred
        return try {
            context.packageManager.packageInstaller.uninstall(packageName, statusSender(context, id))
            deferred.await()
        } finally {
            pending.remove(id)
        }
    }

    private fun statusSender(context: Context, id: Int): android.content.IntentSender {
        val intent = Intent(context, InstallReceiver::class.java).putExtra(InstallReceiver.EXTRA_SESSION, id)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
        return PendingIntent.getBroadcast(context, id, intent, flags).intentSender
    }
}
