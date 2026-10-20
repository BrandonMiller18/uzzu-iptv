package com.bmiller.uzzu.player

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** S6 "Network" line: transport type + connected state, e.g. `Wi-Fi · Connected`. */
fun networkStateLine(context: Context): String = runCatching {
    val cm = context.getSystemService(ConnectivityManager::class.java) ?: return "Unknown"
    val caps = cm.activeNetwork?.let(cm::getNetworkCapabilities) ?: return "No network · Disconnected"
    val transport = when {
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
        else -> "Other"
    }
    val state = when {
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) -> "Connected"
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) -> "Connected (not validated)"
        else -> "No internet"
    }
    "$transport · $state"
}.getOrDefault("Unknown")
