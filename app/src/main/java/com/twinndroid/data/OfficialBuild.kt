package com.twinndroid.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

/**
 * Tells official TwinnDroid builds (signed with the creator's release key) apart from rebuilds
 * published elsewhere. Anyone can build the open-source code, but only official builds carry
 * this certificate.
 */
object OfficialBuild {
    /** SHA-256 of StarlightJulie's release signing certificate. */
    const val CERT_SHA256 = "1CDAAA9B7FDBAA1ABEFDD61B59D8F72AA6E63F656C2988B8F47B9EE5AB95EEFC"

    fun isOfficial(context: Context): Boolean = runCatching {
        val pm = context.packageManager
        val certs = if (Build.VERSION.SDK_INT >= 28) {
            val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            info.signingInfo?.apkContentsSigners.orEmpty()
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures.orEmpty()
        }
        certs.any { sig ->
            MessageDigest.getInstance("SHA-256").digest(sig.toByteArray())
                .joinToString("") { "%02X".format(it) } == CERT_SHA256
        }
    }.getOrDefault(false)
}
