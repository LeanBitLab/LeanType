// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.NetworkInterface

object NetworkUtils {
    private const val TAG = "NetworkUtils"

    fun isNetworkAvailable(context: Context): Boolean {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (cm != null) {
                val network = cm.activeNetwork
                if (network != null) {
                    val capabilities = cm.getNetworkCapabilities(network)
                    if (capabilities != null) {
                        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    }
                }
                return false
            }
        } catch (e: Throwable) {
            Log.w(TAG, "ConnectivityManager check failed", e)
        }

        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return true
            for (iface in interfaces) {
                if (!iface.isUp || iface.isLoopback) continue
                val name = iface.name.lowercase()
                if (name.startsWith("dummy") || name.startsWith("bond") || name.startsWith("sit") || name.startsWith("ip_vti")) continue
                val addrs = iface.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && !addr.isLinkLocalAddress && !addr.isAnyLocalAddress) {
                        return true
                    }
                }
            }
            return false
        } catch (e: Throwable) {
            Log.w(TAG, "NetworkInterface check failed", e)
        }

        return true
    }
}
