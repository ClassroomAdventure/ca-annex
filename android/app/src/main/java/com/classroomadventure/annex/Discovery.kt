package com.classroomadventure.annex

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.net.Inet4Address

/** 同じ Wi-Fi にいる Mac（Annex）を Bonjour で探す。 */
class Discovery(context: Context, private val onChange: (List<Found>) -> Unit) {
    data class Found(val name: String, val host: String)

    private val nsd = context.getSystemService(NsdManager::class.java)
    private val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
    private var lock: WifiManager.MulticastLock? = null
    private val main = Handler(Looper.getMainLooper())
    private val found = LinkedHashMap<String, Found>()
    private var listener: NsdManager.DiscoveryListener? = null

    fun start() {
        if (listener != null) return
        lock = wifi.createMulticastLock("annex").apply { setReferenceCounted(false); acquire() }
        val l = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { Log.w(TAG, "discovery failed $errorCode") }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onServiceFound(info: NsdServiceInfo) = resolve(info)
            override fun onServiceLost(info: NsdServiceInfo) {
                main.post { if (found.remove(info.serviceName) != null) onChange(found.values.toList()) }
            }
        }
        listener = l
        nsd.discoverServices(Proto.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, l)
    }

    @Suppress("DEPRECATION")
    private fun resolve(info: NsdServiceInfo) {
        nsd.resolveService(info, object : NsdManager.ResolveListener {
            override fun onResolveFailed(si: NsdServiceInfo, errorCode: Int) {
                // 同時解決は1件ずつしかできないので少し待って再試行
                if (errorCode == NsdManager.FAILURE_ALREADY_ACTIVE) main.postDelayed({ resolve(info) }, 300)
            }
            override fun onServiceResolved(si: NsdServiceInfo) {
                val addr = si.host ?: return
                val host = if (addr is Inet4Address) addr.hostAddress else addr.hostAddress?.substringBefore('%')
                host ?: return
                main.post {
                    found[si.serviceName] = Found(si.serviceName, host)
                    onChange(found.values.toList())
                }
            }
        })
    }

    fun stop() {
        listener?.let { try { nsd.stopServiceDiscovery(it) } catch (_: Exception) {} }
        listener = null
        lock?.release()
        lock = null
    }

    companion object { private const val TAG = "Discovery" }
}
