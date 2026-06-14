package com.example

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import java.net.NetworkInterface
import java.util.Collections

object SecurityManager {

    /**
     * Checks if a VPN connection is active on the device.
     */
    fun isVpnActive(context: Context): Boolean {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (cm != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val activeNetwork = cm.activeNetwork
                    if (activeNetwork != null) {
                        val capabilities = cm.getNetworkCapabilities(activeNetwork)
                        if (capabilities != null) {
                            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                                return true
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // fallback
        }

        // Extra robust network interface name scan for custom VPNs, PPP routes, or tunnel adapters
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            if (interfaces != null) {
                for (iFace in Collections.list(interfaces)) {
                    if (iFace.isUp) {
                        val name = iFace.name.lowercase()
                        if (name.contains("tun") || name.contains("vpn") || name.contains("ppp") || 
                            name.contains("tap") || name.contains("p2p") || name.contains("wireguard")) {
                            return true
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // ignore
        }
        return false
    }

    /**
     * Checks if an ad blocker configuration (modified hosts or DNS blocker) is detected.
     */
    fun isAdBlockerActive(): Boolean {
        // 1. Hosts file checking of loopback ad domains
        try {
            val file = java.io.File("/system/etc/hosts")
            if (file.exists()) {
                val content = file.readText()
                if (content.contains("adaway") || content.contains("adblock") || 
                    content.contains("doubleclick") || content.contains("googleads") ||
                    content.contains("pagead2")) {
                    return true
                }
            }
        } catch (e: Exception) {
            // ignore
        }

        // 2. Common block-list DNS resolver checking
        try {
            val properties = System.getProperties()
            for (key in properties.keys()) {
                if (key is String && key.contains("dns")) {
                    val value = properties.getProperty(key)?.lowercase() ?: ""
                    if (value.contains("adguard") || value.contains("dns.adguard") || value.contains("1.1.1.2")) {
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            // ignore
        }

        return false
    }
}
