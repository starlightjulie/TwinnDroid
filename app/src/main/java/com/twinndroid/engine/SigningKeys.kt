package com.twinndroid.engine

import android.content.Context
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * The single key TwinnDroid signs every clone with. Keeping the same key is what lets a rebuilt
 * clone install as an *update* (data and logins kept). Back it up via [export] — if it is lost,
 * existing clones must be uninstalled before they can be rebuilt.
 */
object SigningKeys {
    private const val ALIAS = "twinndroid"
    // Fixed container password for the keystore file, not a secret: the file lives in app-private
    // storage and every install generates its own key. Changing it would break existing backups.
    private val PASSWORD = "twinndroid".toCharArray()

    class Key(val privateKey: PrivateKey, val certificate: X509Certificate)

    @Volatile private var cached: Key? = null

    private fun file(context: Context) = File(context.filesDir, "signing.p12")

    @Synchronized
    fun get(context: Context): Key {
        cached?.let { return it }
        val f = file(context)
        if (!f.exists()) generate(f)
        return f.inputStream().use { read(it) }.also { cached = it }
    }

    /** A key belonging to one clone alone. Kept in filesDir/keys so backups carry it. */
    @Synchronized
    fun forClone(context: Context, cloneId: String): Key {
        val f = cloneKeyFile(context, cloneId)
        if (!f.exists()) {
            f.parentFile?.mkdirs()
            generate(f)
        }
        return f.inputStream().use { read(it) }
    }

    fun deleteCloneKey(context: Context, cloneId: String) {
        cloneKeyFile(context, cloneId).delete()
    }

    private fun cloneKeyFile(context: Context, cloneId: String) = File(File(context.filesDir, "keys"), "$cloneId.p12")

    private fun read(input: InputStream): Key {
        val ks = KeyStore.getInstance("PKCS12")
        ks.load(input, PASSWORD)
        val alias = if (ks.containsAlias(ALIAS)) ALIAS else ks.aliases().toList().first()
        val key = ks.getKey(alias, PASSWORD) as PrivateKey
        val cert = ks.getCertificate(alias) as X509Certificate
        return Key(key, cert)
    }

    /** Creates a fresh self-signed RSA key (kept in memory). */
    fun newKey(): Key {
        val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048, SecureRandom()) }.generateKeyPair()
        val name = X500Name("CN=TwinnDroid Clone, O=TwinnDroid")
        val now = System.currentTimeMillis()
        val holder = JcaX509v3CertificateBuilder(
            name,
            BigInteger(64, SecureRandom()),
            Date(now - TimeUnit.DAYS.toMillis(1)),
            Date(now + TimeUnit.DAYS.toMillis(365L * 40)),
            name,
            kp.public,
        ).build(JcaContentSignerBuilder("SHA256withRSA").build(kp.private))
        return Key(kp.private, JcaX509CertificateConverter().getCertificate(holder))
    }

    private fun generate(target: File) {
        val key = newKey()
        val ks = KeyStore.getInstance("PKCS12")
        ks.load(null, null)
        ks.setKeyEntry(ALIAS, key.privateKey, PASSWORD, arrayOf(key.certificate))
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.outputStream().use { ks.store(it, PASSWORD) }
        tmp.renameTo(target)
    }

    fun export(context: Context, out: OutputStream) {
        get(context)
        file(context).inputStream().use { it.copyTo(out) }
    }

    /** Replaces the signing key with a previously exported TwinnDroid key. */
    @Synchronized
    fun import(context: Context, input: InputStream) {
        val bytes = input.readBytes()
        read(bytes.inputStream()) // validate first
        file(context).writeBytes(bytes)
        cached = null
    }

    /** Drops the cached key so the next [get] re-reads signing.p12 (after a backup restore). */
    fun invalidate() {
        cached = null
    }

    fun fingerprint(context: Context): String =
        MessageDigest.getInstance("SHA-256").digest(get(context).certificate.encoded)
            .joinToString(":") { "%02X".format(it) }
}
