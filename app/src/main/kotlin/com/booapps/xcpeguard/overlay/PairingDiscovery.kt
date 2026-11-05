package com.booapps.xcpeguard.overlay

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import java.net.BindException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket

class PairingDiscovery(
    context: Context,
    private val onPort: (Int) -> Unit,
    private val onNote: (Note, Int) -> Unit,
) {
    enum class Note { START_FAILED, RESOLVE_FAILED, OTHER_DEVICE }

    private val nsd: NsdManager? = context.getSystemService(NsdManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val queue = ArrayDeque<NsdServiceInfo>()
    private val attempts = HashMap<String, Int>()
    private var resolving = false
    private var running = false
    private var current: String? = null

    private val discovery = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(serviceType: String) {}
        override fun onDiscoveryStopped(serviceType: String) {}
        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            main.post { if (running) onNote(Note.START_FAILED, errorCode) }
        }

        override fun onServiceFound(info: NsdServiceInfo) {
            main.post { enqueue(info) }
        }

        override fun onServiceLost(info: NsdServiceInfo) {
            main.post { lost(info.serviceName) }
        }
    }

    fun start() {
        if (running) return
        running = true
        val manager = nsd
        val started = manager != null && runCatching {
            manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discovery)
        }.isSuccess
        if (!started) onNote(Note.START_FAILED, -1)
    }

    fun stop() {
        if (!running) return
        running = false
        queue.clear()
        main.removeCallbacksAndMessages(null)
        runCatching { nsd?.stopServiceDiscovery(discovery) }
    }

    private fun enqueue(info: NsdServiceInfo) {
        if (!running || queue.any { it.serviceName == info.serviceName }) return
        queue.addLast(info)
        resolveNext()
    }

    private fun lost(name: String?) {
        queue.removeAll { it.serviceName == name }
        attempts.remove(name)
        if (name != null && name == current) {
            current = null
            if (running) onPort(-1)
        }
    }

    @Suppress("DEPRECATION")
    private fun resolveNext() {
        if (!running || resolving) return
        val manager = nsd ?: return
        val info = queue.removeFirstOrNull() ?: return
        resolving = true
        val asked = runCatching { manager.resolveService(info, Resolver()) }.isSuccess
        if (!asked) {
            resolving = false
            onNote(Note.RESOLVE_FAILED, -1)
            resolveNext()
        }
    }

    private inner class Resolver : NsdManager.ResolveListener {
        override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
            main.post {
                resolving = false
                val name = info.serviceName ?: ""
                val tries = (attempts[name] ?: 0) + 1
                attempts[name] = tries
                if (errorCode == NsdManager.FAILURE_ALREADY_ACTIVE && tries < MAX_ATTEMPTS) {
                    main.postDelayed({ enqueue(info) }, RETRY_MS)
                } else if (running) {
                    onNote(Note.RESOLVE_FAILED, errorCode)
                }
                resolveNext()
            }
        }

        override fun onServiceResolved(info: NsdServiceInfo) {
            val here = listensHere(info.port)
            main.post {
                resolving = false
                attempts.remove(info.serviceName)
                if (running) {
                    if (here) {
                        current = info.serviceName
                        onPort(info.port)
                    } else {
                        onNote(Note.OTHER_DEVICE, info.port)
                    }
                }
                resolveNext()
            }
        }
    }

    private fun listensHere(port: Int): Boolean {
        if (port !in 1..65535) return false
        return try {
            ServerSocket().use {
                it.reuseAddress = true
                it.bind(InetSocketAddress(InetAddress.getLoopbackAddress(), port))
            }
            false
        } catch (e: BindException) {
            true
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        private const val SERVICE_TYPE = "_adb-tls-pairing._tcp"
        private const val RETRY_MS = 500L
        private const val MAX_ATTEMPTS = 5
    }
}
