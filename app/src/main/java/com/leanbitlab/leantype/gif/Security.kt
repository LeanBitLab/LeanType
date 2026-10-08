package com.leanbitlab.leantype.gif

import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import java.security.MessageDigest

fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
fun sha256Hex(s: String): String = sha256Hex(s.toByteArray())

object SigningCerts {
    /** SHA-256 digests (lowercase hex) of the package's signing certs. Empty if not installed. */
    @Suppress("DEPRECATION")
    fun sha256Digests(pm: PackageManager, pkg: String): Set<String> {
        return try {
            val sigs: Array<Signature>? = if (Build.VERSION.SDK_INT >= 28) {
                pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.let { si ->
                    if (si.hasMultipleSigners()) si.apkContentsSigners else si.signingCertificateHistory
                }
            } else {
                pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES).signatures
            }
            sigs?.map { sha256Hex(it.toByteArray()) }?.toSet() ?: emptySet()
        } catch (_: PackageManager.NameNotFoundException) { emptySet() }
    }
}
