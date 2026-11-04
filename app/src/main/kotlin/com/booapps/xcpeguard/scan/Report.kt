package com.booapps.xcpeguard.scan

import android.content.Context
import com.booapps.xcpeguard.Prefs
import org.json.JSONArray
import org.json.JSONObject

enum class Section { MALWARE, SUSPICIOUS, PERSISTENCE, SETTINGS, NETWORK }

enum class Action { DISABLE, KEEP_ENABLED }

object Level {
    const val OK = 0
    const val INFO = 1
    const val WARN = 2
    const val ALARM = 3
    const val FIXED = 4
}

class Txt private constructor(val res: String?, val args: List<Any>, val raw: String?, val next: Txt?) {
    fun text(c: Context): String {
        val head = raw ?: run {
            val id = c.resources.getIdentifier(res, "string", c.packageName)
            if (id == 0) res ?: "" else c.getString(id, *args.toTypedArray())
        }
        return next?.let { head + "\n" + it.text(c) } ?: head
    }

    operator fun plus(o: Txt): Txt = Txt(res, args, raw, next?.plus(o) ?: o)

    val key: String get() = (raw ?: res ?: "") + args.joinToString(",") + (next?.key ?: "")

    fun json(): JSONObject = JSONObject().put("r", res ?: "").put("v", raw ?: "")
        .put("a", JSONArray().apply { args.forEach { put(it) } }).apply { next?.let { put("n", it.json()) } }

    companion object {
        fun of(c: Context, id: Int, vararg args: Any) = Txt(c.resources.getResourceEntryName(id), args.toList(), null, null)
        fun raw(s: String) = Txt(null, emptyList(), s, null)
        fun from(v: Any) = v as? Txt ?: raw(v.toString())

        fun parse(o: Any?): Txt = when (o) {
            is JSONObject -> {
                val a = o.optJSONArray("a") ?: JSONArray()
                Txt(o.optString("r").ifEmpty { null }, (0 until a.length()).map { a.get(it) },
                    if (o.optString("r").isEmpty()) o.optString("v") else null,
                    o.optJSONObject("n")?.let { parse(it) })
            }
            else -> raw(o?.toString() ?: "")
        }
    }
}

data class Finding(
    val section: Section,
    val level: Int,
    val title: Txt,
    val detail: Txt,
    val info: Txt,
    val action: Action? = null,
    val pkg: String? = null,
)

class Report(val time: Long, val adb: Boolean, val method: String, val findings: List<Finding>) {
    fun count(level: Int) = findings.count { it.level == level }
    val state: Int
        get() = when {
            count(Level.ALARM) > 0 -> Level.ALARM
            count(Level.FIXED) > 0 -> Level.FIXED
            count(Level.WARN) > 0 -> Level.WARN
            else -> Level.OK
        }
}

object Store {
    private const val REPORT = "report"
    private const val HISTORY = "history"
    private const val UNSEEN = "unseen"
    const val INTERVAL_MS = 24L * 60 * 60 * 1000

    class Entry(val time: Long, val state: Int, val alarms: Int, val fixed: Int, val warnings: Int, val adb: Boolean)

    fun save(c: Context, r: Report) {
        val p = Prefs.of(c)
        val items = JSONArray()
        r.findings.forEach { f ->
            items.put(JSONObject().put("s", f.section.name).put("l", f.level).put("t", f.title.json()).put("d", f.detail.json())
                .put("i", f.info.json()).put("a", f.action?.name ?: "").put("p", f.pkg ?: ""))
        }
        val rep = JSONObject().put("time", r.time).put("adb", r.adb).put("method", r.method).put("items", items)
        val hist = JSONArray(p.getString(HISTORY, "[]"))
        val list = JSONArray().put(JSONObject().put("time", r.time).put("state", r.state)
            .put("alarms", r.count(Level.ALARM)).put("fixed", r.count(Level.FIXED))
            .put("warnings", r.count(Level.WARN)).put("adb", r.adb))
        for (i in 0 until minOf(hist.length(), 29)) list.put(hist.get(i))
        val notify = r.count(Level.ALARM) + r.count(Level.FIXED) > 0
        addFixes(c, r)
        p.edit().putString(REPORT, rep.toString()).putString(HISTORY, list.toString())
            .putBoolean(UNSEEN, notify || p.getBoolean(UNSEEN, false)).apply()
    }

    fun last(c: Context): Report? {
        val s = Prefs.of(c).getString(REPORT, null) ?: return null
        return runCatching {
            val o = JSONObject(s)
            val a = o.getJSONArray("items")
            val f = (0 until a.length()).map { i ->
                val x = a.getJSONObject(i)
                Finding(Section.valueOf(x.getString("s")), x.getInt("l"), Txt.parse(x.opt("t")), Txt.parse(x.opt("d")),
                    Txt.parse(x.opt("i")), x.getString("a").takeIf { it.isNotEmpty() }?.let { Action.valueOf(it) },
                    x.getString("p").takeIf { it.isNotEmpty() })
            }
            if ((0 until a.length()).any { a.getJSONObject(it).opt("i") !is JSONObject }) return null
            Report(o.getLong("time"), o.getBoolean("adb"), o.optString("method"), f)
        }.getOrNull()
    }

    fun history(c: Context): List<Entry> = runCatching {
        val a = JSONArray(Prefs.of(c).getString(HISTORY, "[]"))
        (0 until a.length()).map { i ->
            val x = a.getJSONObject(i)
            Entry(x.getLong("time"), x.getInt("state"), x.getInt("alarms"), x.getInt("fixed"), x.getInt("warnings"),
                x.getBoolean("adb"))
        }
    }.getOrDefault(emptyList())

    class Fix(val time: Long, val finding: Finding)

    private const val FIXES = "fixes"
    private const val FIX_WINDOW_MS = 7L * 24 * 60 * 60 * 1000

    private fun addFixes(c: Context, r: Report) {
        val fixed = r.findings.filter { it.level == Level.FIXED }
        if (fixed.isEmpty()) return
        val p = Prefs.of(c)
        val old = JSONArray(p.getString(FIXES, "[]"))
        val out = JSONArray()
        fixed.forEach { f ->
            out.put(JSONObject().put("time", r.time).put("s", f.section.name).put("t", f.title.json()).put("d", f.detail.json())
                .put("i", f.info.json()).put("a", f.action?.name ?: "").put("p", f.pkg ?: ""))
        }
        for (i in 0 until minOf(old.length(), 50 - out.length())) {
            val x = old.getJSONObject(i)
            if (r.time - x.getLong("time") < 30L * 24 * 60 * 60 * 1000) out.put(x)
        }
        p.edit().putString(FIXES, out.toString()).apply()
    }

    fun recentFixes(c: Context): List<Fix> = runCatching {
        val a = JSONArray(Prefs.of(c).getString(FIXES, "[]"))
        val now = System.currentTimeMillis()
        (0 until a.length()).map { a.getJSONObject(it) }
            .filter { now - it.getLong("time") < FIX_WINDOW_MS && it.opt("t") is JSONObject && it.opt("i") is JSONObject }
            .map { x ->
            Fix(x.getLong("time"), Finding(Section.valueOf(x.getString("s")), Level.FIXED, Txt.parse(x.opt("t")),
                Txt.parse(x.opt("d")), Txt.parse(x.opt("i")),
                x.getString("a").takeIf { it.isNotEmpty() }?.let { Action.valueOf(it) },
                x.getString("p").takeIf { it.isNotEmpty() }))
        }
    }.getOrDefault(emptyList())

    fun lastTime(c: Context) = last(c)?.time ?: 0L
    fun due(c: Context) = System.currentTimeMillis() - lastTime(c) > INTERVAL_MS
    fun unseen(c: Context) = Prefs.of(c).getBoolean(UNSEEN, false)
    fun markSeen(c: Context) = Prefs.of(c).edit().putBoolean(UNSEEN, false).apply()
}
