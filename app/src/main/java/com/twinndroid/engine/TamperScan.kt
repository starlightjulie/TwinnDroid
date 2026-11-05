package com.twinndroid.engine

import java.io.File
import java.util.zip.ZipFile

/**
 * What a clone is likely to run into because of how the original app protects itself.
 * TwinnDroid re-signs every clone, which app-shielding products treat as tampering, so a
 * clone of a shielded app usually crashes on launch. We cannot defeat this without root, so
 * the best we can do is warn before building.
 */
enum class TamperRisk { NONE, INTEGRITY, SHIELD }

class TamperReport(val risk: TamperRisk, val product: String?, val detail: String)

/**
 * Looks for the fingerprints of commercial app-shielding / RASP products and strong integrity
 * checks by scanning APK entry names only (no extraction), so it is fast and allocation-light.
 */
object TamperScan {

    // Marker substring (lower-case, matched against zip entry names) -> product name.
    private val SHIELD_MARKERS = linkedMapOf(
        ".ecc" to "Promon SHIELD",
        "libshield.so" to "Promon SHIELD",
        // Supercell ships SHIELD in every game, but not all of them carry the .ecc files.
        "libsupercell_" to "Promon SHIELD",
        "libjiagu" to "360 Jiagu",
        "libdexhelper" to "Bangcle / SecShell",
        "libsecexe" to "Bangcle / SecShell",
        "libsecmain" to "Bangcle / SecShell",
        "libsecshell" to "Bangcle / SecShell",
        "libnesec" to "Alibaba Mobile Guard",
        "libmobisec" to "Alibaba Mobile Guard",
        "libtup.so" to "Tencent Legu",
        "libtprt" to "Tencent Legu",
        "libtersafe" to "Tencent Legu",
        "libnqshield" to "NQ Shield",
        "libapp-guard" to "AppGuard",
        "libappguard" to "AppGuard",
        "libarxan" to "Arxan / Digital.ai",
        "libdexprotector" to "DexProtector",
        "libdexpro" to "DexProtector",
        "libappdome" to "Appdome",
        "assets/appdome" to "Appdome",
        "libkonyjsvm" to "Kony",
        "libw.so" to "Whitecryption",
    )

    // Softer signal: these indicate Play Integrity / SafetyNet style attestation, which fails
    // for a re-signed clone (different certificate) but does not always hard-crash the app.
    private val INTEGRITY_MARKERS = listOf(
        "com/google/android/play/core/integrity",
        "safetynet",
        "libscudo", // sometimes bundled with integrity SDKs
    )

    fun scan(files: List<File>): TamperReport {
        val entries = HashSet<String>()
        for (f in files) {
            runCatching {
                ZipFile(f).use { zip ->
                    val e = zip.entries()
                    while (e.hasMoreElements()) entries += e.nextElement().name.lowercase()
                }
            }
        }
        for ((marker, product) in SHIELD_MARKERS) {
            if (entries.any { it.contains(marker) }) {
                return TamperReport(
                    TamperRisk.SHIELD,
                    product,
                    "This app is protected by $product, which blocks re-signed copies. " +
                        "A clone will very likely crash on launch, and no no-root cloner can get past this.",
                )
            }
        }
        for (marker in INTEGRITY_MARKERS) {
            if (entries.any { it.contains(marker) }) {
                return TamperReport(
                    TamperRisk.INTEGRITY,
                    null,
                    "This app uses Google Play Integrity or SafetyNet. The clone is signed with a " +
                        "different key, so sign-in or anti-cheat may refuse to work even if the app opens.",
                )
            }
        }
        return TamperReport(TamperRisk.NONE, null, "")
    }
}
