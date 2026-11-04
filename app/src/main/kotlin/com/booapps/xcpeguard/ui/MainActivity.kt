package com.booapps.xcpeguard.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.booapps.xcpeguard.Lang
import com.booapps.xcpeguard.Prefs
import com.booapps.xcpeguard.scan.ScanJob

class MainActivity : ComponentActivity() {
    enum class Screen { LANGUAGE, SETUP, REPORT, ABOUT }

    val resumes = mutableIntStateOf(0)
    var screen by mutableStateOf(Screen.LANGUAGE)
    var scanOnOpen by mutableStateOf(false)
    private var aboutReturn = Screen.REPORT

    override fun attachBaseContext(base: Context) = super.attachBaseContext(Lang.wrap(base))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle(intent)
        setContent {
            XcpeTheme {
                androidx.activity.compose.BackHandler(enabled = screen == Screen.ABOUT ||
                    (screen == Screen.SETUP && Prefs.setupDone(this@MainActivity))) {
                    screen = if (screen == Screen.ABOUT) aboutReturn else Screen.REPORT
                }
                Box(Modifier.fillMaxSize().background(Palette.bg)) {
                    when (screen) {
                        Screen.LANGUAGE -> LanguageScreen { lang ->
                            Prefs.setLang(this@MainActivity, lang)
                            recreate()
                        }
                        Screen.SETUP -> SetupScreen(this@MainActivity)
                        Screen.REPORT -> ReportScreen(this@MainActivity)
                        Screen.ABOUT -> AboutScreen(this@MainActivity)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onResume() {
        super.onResume()
        resumes.intValue++
    }

    private fun handle(i: Intent?) {
        if (i?.getBooleanExtra(EXTRA_PAIRED, false) == true) finishSetup()
        screen = when {
            Prefs.lang(this).isEmpty() -> Screen.LANGUAGE
            !Prefs.setupDone(this) -> Screen.SETUP
            else -> Screen.REPORT
        }
    }

    fun finishSetup() {
        Prefs.setSetupDone(this, true)
        ScanJob.schedule(this)
        scanOnOpen = true
        screen = Screen.REPORT
    }

    fun openSetup() { screen = Screen.SETUP }
    fun openAbout() {
        aboutReturn = screen
        screen = Screen.ABOUT
    }

    fun closeAbout() { screen = aboutReturn }

    fun switchLanguage() {
        Prefs.setLang(this, if (Prefs.lang(this) == "tr") "en" else "tr")
        recreate()
    }

    companion object {
        const val EXTRA_PAIRED = "paired"
    }
}
