package com.booapps.xcpeguard

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import java.util.Locale

object Prefs {
    private const val FILE = "xcpe_guard"
    const val LANG = "lang"
    const val SETUP_DONE = "setup_done"
    const val SCHEDULE = "schedule_daily"
    const val ADB_METHOD = "adb_method"

    fun of(c: Context): SharedPreferences = c.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun lang(c: Context): String = of(c).getString(LANG, "") ?: ""
    fun setLang(c: Context, l: String) = of(c).edit().putString(LANG, l).apply()

    fun setupDone(c: Context) = of(c).getBoolean(SETUP_DONE, false)
    fun setSetupDone(c: Context, v: Boolean) = of(c).edit().putBoolean(SETUP_DONE, v).apply()

    fun scheduled(c: Context) = of(c).getBoolean(SCHEDULE, true)
    fun setScheduled(c: Context, v: Boolean) = of(c).edit().putBoolean(SCHEDULE, v).apply()

    fun adbMethod(c: Context): String = of(c).getString(ADB_METHOD, "") ?: ""
    fun setAdbMethod(c: Context, m: String) = of(c).edit().putString(ADB_METHOD, m).apply()
}

object Lang {
    fun wrap(base: Context): Context {
        val l = Prefs.lang(base)
        if (l.isEmpty()) return base
        val locale = Locale(l)
        Locale.setDefault(locale)
        val cfg = Configuration(base.resources.configuration)
        cfg.setLocale(locale)
        return base.createConfigurationContext(cfg)
    }
}
