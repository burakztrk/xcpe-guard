package com.booapps.xcpeguard.overlay

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.booapps.xcpeguard.Lang
import com.booapps.xcpeguard.Prefs
import com.booapps.xcpeguard.adb.AdbClient
import com.booapps.xcpeguard.ui.MainActivity
import io.github.muntashirakon.adb.android.AdbMdns
import kotlin.concurrent.thread

class PairingOverlayService : LifecycleService(), SavedStateRegistryOwner {
    enum class Phase { WAITING, CODE, PAIRING, DONE, FAILED }

    private val saved = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry

    val phase = mutableStateOf(Phase.WAITING)
    val code = mutableStateOf("")
    val error = mutableStateOf("")
    val port = mutableIntStateOf(-1)

    private lateinit var wm: WindowManager
    private var view: ComposeView? = null
    private var params: WindowManager.LayoutParams? = null
    private var mdns: AdbMdns? = null
    private val main = Handler(Looper.getMainLooper())

    override fun attachBaseContext(base: Context) = super.attachBaseContext(Lang.wrap(base))

    override fun onCreate() {
        saved.performAttach()
        saved.performRestore(null)
        super.onCreate()
        running = true
        wm = getSystemService(WindowManager::class.java)
        val v = ComposeView(this)
        v.setViewTreeLifecycleOwner(this)
        v.setViewTreeSavedStateRegistryOwner(this)
        v.setContent { PairingPanel(this) }
        val density = resources.displayMetrics.density
        val lp = WindowManager.LayoutParams(
            (460 * density).toInt(), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (48 * density).toInt()
            y = (48 * density).toInt()
        }
        wm.addView(v, lp)
        view = v
        params = lp
        mdns = AdbMdns(this, AdbMdns.SERVICE_TYPE_TLS_PAIRING) { _, p -> main.post { onPairingPort(p) } }
            .also { it.start() }
    }

    private fun onPairingPort(p: Int) {
        port.intValue = p
        when {
            p > 0 && phase.value == Phase.WAITING -> {
                code.value = ""
                phase.value = Phase.CODE
                setFocusable(true)
            }
            p <= 0 && (phase.value == Phase.CODE || phase.value == Phase.FAILED) -> {
                code.value = ""
                phase.value = Phase.WAITING
                setFocusable(false)
            }
        }
    }

    private fun setFocusable(on: Boolean) {
        val lp = params ?: return
        lp.flags = if (on) lp.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        else lp.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        view?.let { wm.updateViewLayout(it, lp) }
    }

    fun digit(d: Char) {
        if (phase.value == Phase.FAILED) {
            error.value = ""
            code.value = ""
            phase.value = Phase.CODE
        }
        if (code.value.length >= 6 || phase.value != Phase.CODE) return
        code.value += d
        if (code.value.length == 6) submit()
    }

    fun backspace() {
        if (phase.value == Phase.FAILED) { error.value = ""; phase.value = Phase.CODE }
        code.value = code.value.dropLast(1)
    }

    fun submit() {
        val c = code.value
        val p = port.intValue
        if (c.length != 6 || p <= 0) return
        phase.value = Phase.PAIRING
        thread(name = "xcpe-pair") {
            val result = runCatching {
                val adb = AdbClient.get(this)
                check(adb.pair(AdbClient.HOST, p, c))
                check(adb.connectTls(this, 20_000))
                Prefs.setAdbMethod(this, AdbClient.METHOD_WIRELESS)
                AdbClient.grantSelf(this, adb)
            }
            main.post {
                if (result.isSuccess) {
                    phase.value = Phase.DONE
                    setFocusable(false)
                    main.postDelayed({ finish(paired = true) }, 1500)
                } else {
                    error.value = result.exceptionOrNull()?.message ?: ""
                    code.value = ""
                    phase.value = Phase.FAILED
                }
            }
        }
    }

    fun finish(paired: Boolean) {
        startActivity(Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MainActivity.EXTRA_PAIRED, paired))
        stopSelf()
    }

    override fun onDestroy() {
        running = false
        runCatching { mdns?.stop() }
        view?.let { runCatching { wm.removeView(it) } }
        view = null
        super.onDestroy()
    }

    companion object {
        @Volatile var running = false
            private set

        fun start(c: Context) { c.startService(Intent(c, PairingOverlayService::class.java)) }
    }
}
