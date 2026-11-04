package com.booapps.xcpeguard.adb

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import com.booapps.xcpeguard.Prefs
import com.booapps.xcpeguard.R
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class AdbClient private constructor(c: Context) : AbsAdbConnectionManager() {
    private val key: PrivateKey
    private val cert: Certificate

    init {
        api = Build.VERSION.SDK_INT
        hostAddress = HOST
        val kf = File(c.filesDir, "adb.key")
        val cf = File(c.filesDir, "adb.crt")
        if (kf.isFile && cf.isFile) {
            key = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(kf.readBytes()))
            cert = cf.inputStream().use { CertificateFactory.getInstance("X.509").generateCertificate(it) }
        } else {
            val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048, SecureRandom()) }.generateKeyPair()
            val der = SelfSignedCert.create(kp, "XCPE Guard")
            key = kp.private
            cert = CertificateFactory.getInstance("X.509").generateCertificate(der.inputStream())
            kf.writeBytes(key.encoded)
            cf.writeBytes(der)
        }
    }

    override fun getPrivateKey() = key
    override fun getCertificate() = cert
    override fun getDeviceName() = "xcpe-guard"

    fun sh(command: String, timeoutSec: Long = 30): String {
        val st = openStream("shell:$command; echo $END")
        val out = ByteArrayOutputStream()
        val job = pool.submit {
            val buf = ByteArray(8192)
            try {
                st.openInputStream().use { input ->
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        synchronized(out) { out.write(buf, 0, n) }
                        if (synchronized(out) { out.toString(Charsets.UTF_8.name()) }.contains(END)) break
                    }
                }
            } catch (_: IOException) {
            }
        }
        try {
            job.get(timeoutSec, TimeUnit.SECONDS)
        } catch (e: TimeoutException) {
            job.cancel(true)
            throw IOException("timeout: ${command.substringBefore(' ')}")
        } finally {
            runCatching { st.close() }
        }
        val text = synchronized(out) { out.toString(Charsets.UTF_8.name()) }
        return text.substringBefore(END)
    }

    companion object {
        private const val END = "__XG_END__"
        private val pool = Executors.newCachedThreadPool { r -> Thread(r, "xcpe-adb").apply { isDaemon = true } }
        const val HOST = "127.0.0.1"
        const val METHOD_TCP = "tcp"
        const val METHOD_WIRELESS = "wireless"
        private const val ADB_WIFI = "adb_wifi_enabled"

        @Volatile private var instance: AdbClient? = null
        fun get(c: Context): AdbClient = instance ?: synchronized(this) {
            instance ?: AdbClient(c.applicationContext).also { instance = it }
        }

        fun canToggle(c: Context) =
            c.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

        fun tcpPortOpen(): Boolean = try {
            Socket().use { it.connect(InetSocketAddress(HOST, 5555), 700); true }
        } catch (e: IOException) { false }

        fun usbDebugging(c: Context) = Settings.Global.getInt(c.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
        fun wirelessDebugging(c: Context) = Settings.Global.getInt(c.contentResolver, ADB_WIFI, 0) == 1
        fun developerOptions(c: Context) =
            Settings.Global.getInt(c.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1

        class Session(val adb: AdbClient) {
            var turnedOnUsb = false
            var turnedOnWifi = false
            var method = ""
        }

        fun open(c: Context, allowPrompt: Boolean): Session {
            val adb = get(c)
            val s = Session(adb)
            s.method = Prefs.adbMethod(c)
            if (adb.isConnected) return s
            adb.isThrowOnUnauthorised = !allowPrompt
            adb.setTimeout(if (allowPrompt) 60 else 12, TimeUnit.SECONDS)
            val cr = c.contentResolver
            var lastError: Exception? = null

            if (s.method != METHOD_WIRELESS) {
                if (!tcpPortOpen() && canToggle(c) && !usbDebugging(c)) {
                    Settings.Global.putInt(cr, Settings.Global.ADB_ENABLED, 1)
                    s.turnedOnUsb = true
                    repeat(20) { if (!tcpPortOpen()) Thread.sleep(500) }
                }
                if (tcpPortOpen()) {
                    try {
                        if (adb.connect(HOST, 5555)) {
                            s.method = METHOD_TCP
                            Prefs.setAdbMethod(c, METHOD_TCP)
                            return s
                        }
                    } catch (e: Exception) { lastError = e }
                }
            }
            if (Build.VERSION.SDK_INT >= 30) {
                if (canToggle(c) && !wirelessDebugging(c)) {
                    Settings.Global.putInt(cr, ADB_WIFI, 1)
                    s.turnedOnWifi = true
                    Thread.sleep(1500)
                }
                try {
                    if (adb.connectTls(c, 15_000)) {
                        s.method = METHOD_WIRELESS
                        Prefs.setAdbMethod(c, METHOD_WIRELESS)
                        return s
                    }
                } catch (e: Exception) { lastError = e }
            }
            close(c, s)
            throw IOException(c.getString(R.string.adb_unreachable) +
                (lastError?.let { " (" + (it.message ?: it.javaClass.simpleName) + ")" } ?: ""))
        }

        fun close(c: Context, s: Session?) {
            if (s == null) return
            runCatching { s.adb.disconnect() }
            if (!canToggle(c)) return
            val cr = c.contentResolver
            if (s.turnedOnWifi) Settings.Global.putInt(cr, ADB_WIFI, 0)
            if (s.turnedOnUsb) Settings.Global.putInt(cr, Settings.Global.ADB_ENABLED, 0)
        }

        fun switchOffNow(c: Context) {
            if (!canToggle(c)) return
            runCatching { get(c).disconnect() }
            Settings.Global.putInt(c.contentResolver, ADB_WIFI, 0)
            Settings.Global.putInt(c.contentResolver, Settings.Global.ADB_ENABLED, 0)
        }

        fun grantSelf(c: Context, adb: AdbClient) {
            val pkg = c.packageName
            runCatching { adb.sh("pm grant $pkg android.permission.WRITE_SECURE_SETTINGS") }
            runCatching { adb.sh("pm grant $pkg android.permission.POST_NOTIFICATIONS") }
            runCatching { adb.sh("appops set $pkg SYSTEM_ALERT_WINDOW allow") }
        }
    }
}
