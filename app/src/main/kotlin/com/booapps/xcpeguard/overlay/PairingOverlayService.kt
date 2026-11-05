package com.booapps.xcpeguard.overlay

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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
import kotlin.concurrent.thread

class PairingOverlayService : LifecycleService(), SavedStateRegistryOwner {
    enum class Phase { WAITING, PORT, CODE, PAIRING, DONE, FAILED }

    private val saved = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry

    val phase = mutableStateOf(Phase.WAITING)
    val code = mutableStateOf("")
    val error = mutableStateOf("")
    val port = mutableIntStateOf(-1)
    val manual = mutableStateOf(false)
    val typedPort = mutableStateOf("")
    val countdown = mutableIntStateOf(0)
    val note = mutableStateOf<PairingDiscovery.Note?>(null)
    val noteCode = mutableIntStateOf(0)

    private lateinit var wm: WindowManager
    private var view: ComposeView? = null
    private var params: WindowManager.LayoutParams? = null
    private var discovery: PairingDiscovery? = null
    private val main = Handler(Looper.getMainLooper())
    private var startedAt = 0L
    private var manualAt = 0L
    private val ticker = object : Runnable {
        override fun run() {
            checkManual()
            main.postDelayed(this, 1000)
        }
    }

    override fun attachBaseContext(base: Context) = super.attachBaseContext(Lang.wrap(base))

    override fun onCreate() {
        saved.performAttach()
        saved.performRestore(null)
        super.onCreate()
        running = true
        startedAt = SystemClock.elapsedRealtime()
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
        discovery = PairingDiscovery(this, ::onPairingPort) { n, c ->
            note.value = n
            noteCode.intValue = c
        }.also { it.start() }
        main.post(ticker)
    }

    private fun onPairingPort(p: Int) {
        port.intValue = p
        if (p > 0) {
            note.value = null
            when (phase.value) {
                Phase.WAITING, Phase.PORT -> showCode()
                Phase.CODE, Phase.FAILED -> manual.value = false
                else -> {}
            }
        } else if (!manual.value && (phase.value == Phase.CODE || phase.value == Phase.FAILED)) {
            code.value = ""
            phase.value = Phase.WAITING
            setFocusable(false)
            manualAt = 0L
        }
    }

    private fun showCode() {
        manual.value = false
        code.value = ""
        error.value = ""
        phase.value = Phase.CODE
        setFocusable(true)
    }

    private fun checkManual() {
        if (phase.value != Phase.WAITING) {
            countdown.intValue = 0
            return
        }
        val now = SystemClock.elapsedRealtime()
        val wireless = runCatching { AdbClient.wirelessDebugging(this) }.getOrNull()
        if (wireless == false) {
            manualAt = 0L
            countdown.intValue = 0
            return
        }
        if (manualAt == 0L) {
            val sinceStart = if (wireless == null) UNKNOWN_DELAY_MS else START_DELAY_MS
            manualAt = maxOf(startedAt + sinceStart, now + WIRELESS_DELAY_MS)
        }
        val left = manualAt - now
        if (left <= 0) openManual() else countdown.intValue = ((left + 999) / 1000).toInt()
    }

    private fun openManual() {
        countdown.intValue = 0
        if (port.intValue > 0) {
            showCode()
            return
        }
        manual.value = true
        typedPort.value = ""
        code.value = ""
        error.value = ""
        phase.value = Phase.PORT
        setFocusable(true)
    }

    private fun setFocusable(on: Boolean) {
        val lp = params ?: return
        lp.flags = if (on) lp.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        else lp.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        view?.let { wm.updateViewLayout(it, lp) }
    }

    fun digit(d: Char) {
        if (phase.value == Phase.PORT) {
            if (typedPort.value.length >= 5) return
            typedPort.value += d
            if (typedPort.value.length == 5) confirmPort()
            return
        }
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
        when {
            phase.value == Phase.PORT -> typedPort.value = typedPort.value.dropLast(1)
            phase.value == Phase.FAILED && manual.value -> {
                error.value = ""
                typedPort.value = ""
                phase.value = Phase.PORT
            }
            phase.value == Phase.FAILED -> {
                error.value = ""
                phase.value = Phase.CODE
                code.value = code.value.dropLast(1)
            }
            phase.value == Phase.CODE && manual.value && code.value.isEmpty() -> phase.value = Phase.PORT
            phase.value == Phase.CODE -> code.value = code.value.dropLast(1)
        }
    }

    fun confirm() {
        if (phase.value == Phase.PORT) confirmPort() else submit()
    }

    private fun confirmPort() {
        val p = typedPort.value.toIntOrNull()
        if (p == null || p !in 1..65535) {
            typedPort.value = ""
            return
        }
        code.value = ""
        error.value = ""
        phase.value = Phase.CODE
    }

    fun back() {
        when {
            phase.value == Phase.PAIRING || phase.value == Phase.DONE -> {}
            manual.value && phase.value != Phase.PORT -> {
                error.value = ""
                phase.value = Phase.PORT
            }
            manual.value -> hide()
            else -> finish(paired = false)
        }
    }

    fun hide() {
        manual.value = false
        code.value = ""
        error.value = ""
        phase.value = Phase.WAITING
        setFocusable(false)
        manualAt = SystemClock.elapsedRealtime() + REARM_SECONDS * 1000L
    }

    fun submit() {
        val c = code.value
        val p = if (manual.value) typedPort.value.toIntOrNull() ?: -1 else port.intValue
        if (phase.value != Phase.CODE || c.length != 6 || p <= 0) return
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
        main.removeCallbacks(ticker)
        discovery?.stop()
        discovery = null
        view?.let { runCatching { wm.removeView(it) } }
        view = null
        super.onDestroy()
    }

    companion object {
        const val REARM_SECONDS = 20
        private const val START_DELAY_MS = 30_000L
        private const val WIRELESS_DELAY_MS = 20_000L
        private const val UNKNOWN_DELAY_MS = 45_000L

        @Volatile var running = false
            private set

        fun start(c: Context) { c.startService(Intent(c, PairingOverlayService::class.java)) }
    }
}
