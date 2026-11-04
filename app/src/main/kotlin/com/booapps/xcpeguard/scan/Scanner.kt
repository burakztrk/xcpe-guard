package com.booapps.xcpeguard.scan

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import com.booapps.xcpeguard.Lang
import com.booapps.xcpeguard.Prefs
import com.booapps.xcpeguard.R
import com.booapps.xcpeguard.adb.AdbClient
import java.net.InetAddress

object Scanner {
    private val lock = Any()

    private const val BASE_PKGS = "base_pkgs"
    private const val BASE_SYS = "base_sys"
    private const val BASE_DISABLED = "base_disabled"
    private const val BASE_PRIV = "base_priv"
    private const val BASE_PERM = "base_perm"
    private const val BASE_OEM = "base_oem"
    private const val BASE_FP = "base_fp"
    private const val KEEP_ON = "keep_on"

    fun run(context: Context, allowPrompt: Boolean): Report = synchronized(lock) {
        val c = Lang.wrap(context.applicationContext)
        val out = mutableListOf<Finding>()
        var session: AdbClient.Companion.Session? = null
        var adbOk = false
        try {
            session = AdbClient.open(c, allowPrompt)
            adbOk = true
            if (Prefs.of(c).getString(BASE_FP, null) == null) AdbClient.grantSelf(c, session.adb)
            Checks(c, session.adb, out).runAll()
        } catch (e: Exception) {
            if (!adbOk) {
                out += Finding(Section.SETTINGS, Level.WARN, Txt.of(c, R.string.chk_noadb_title),
                    Txt.raw(e.message ?: e.toString()), Txt.of(c, R.string.info_noadb))
            } else {
                out += Finding(Section.SETTINGS, Level.WARN, Txt.of(c, R.string.chk_interrupted_title),
                    Txt.raw(e.message ?: e.toString()), Txt.of(c, R.string.info_interrupted))
            }
        } finally {
            AdbClient.close(c, session)
        }
        out += dns(c)
        out += adbState(c, session)
        val r = Report(System.currentTimeMillis(), adbOk, session?.method ?: "", out)
        Store.save(c, r)
        if (r.count(Level.ALARM) + r.count(Level.FIXED) > 0) Notifier.alert(c, r)
        r
    }

    fun act(context: Context, f: Finding): Boolean = synchronized(lock) {
        val c = Lang.wrap(context.applicationContext)
        val pkg = f.pkg ?: return false
        var s: AdbClient.Companion.Session? = null
        try {
            s = AdbClient.open(c, true)
            val p = Prefs.of(c)
            when (f.action) {
                Action.DISABLE -> {
                    putSet(p, KEEP_ON, getSet(p, KEEP_ON) - pkg)
                    s.adb.sh("pm disable-user --user 0 $pkg")
                    s.adb.sh("am force-stop $pkg")
                    putSet(p, BASE_DISABLED, getSet(p, BASE_DISABLED) + pkg)
                }
                Action.KEEP_ENABLED -> {
                    s.adb.sh("pm enable --user 0 $pkg")
                    putSet(p, BASE_DISABLED, getSet(p, BASE_DISABLED) - pkg)
                    putSet(p, KEEP_ON, getSet(p, KEEP_ON) + pkg)
                }
                null -> return false
            }
            true
        } catch (e: Exception) {
            false
        } finally {
            AdbClient.close(c, s)
        }
    }

    private class Checks(val c: Context, val adb: AdbClient, val out: MutableList<Finding>) {
        val p: SharedPreferences = Prefs.of(c)
        val fix = true
        fun s(id: Int, vararg a: Any) = Txt.of(c, id, *a)
        fun add(sec: Section, lvl: Int, t: Any, d: Any, info: Int, action: Action? = null, pkg: String? = null) {
            out += Finding(sec, lvl, Txt.from(t), Txt.from(d), s(info), action, pkg)
        }

        lateinit var all: Set<String>
        lateinit var installed: Set<String>
        lateinit var disabled: Set<String>
        lateinit var system: Set<String>
        lateinit var uids: Map<String, Int>

        fun runAll() {
            all = pkgs(adb.sh("pm list packages -u"))
            installed = pkgs(adb.sh("pm list packages"))
            disabled = pkgs(adb.sh("pm list packages -d"))
            system = pkgs(adb.sh("pm list packages -s"))
            uids = uidMap(adb.sh("pm list packages -U"))
            if (!p.contains(BASE_DISABLED)) putSet(p, BASE_DISABLED, disabled)
            firmware()
            malware()
            suspicious()
            watched()
            newPackages()
            processes()
            accessibility()
            playProtect()
            props()
            oem()
            listingDiff(BASE_PRIV, "ls /system/priv-app /product/priv-app /system_ext/priv-app /vendor/priv-app",
                R.string.chk_privapp_title, R.string.info_privapp)
            listingDiff(BASE_PERM, "ls /system/etc/permissions /product/etc/permissions /system_ext/etc/permissions",
                R.string.chk_perm_title, R.string.info_perm)
            libs()
            network()
            privateDns()
        }

        fun firmware() {
            val fp = adb.sh("getprop ro.build.fingerprint").trim()
            val old = p.getString(BASE_FP, null)
            if (old != null && old != fp) {
                add(Section.PERSISTENCE, Level.INFO, s(R.string.chk_fw_title), s(R.string.chk_fw_changed, old, fp),
                    R.string.info_fw)
                p.edit().remove(BASE_SYS).remove(BASE_PRIV).remove(BASE_PERM).remove(BASE_OEM).apply()
            }
            p.edit().putString(BASE_FP, fp).apply()
        }

        fun switchOff(pkg: String): Boolean {
            adb.sh("pm disable-user --user 0 $pkg")
            adb.sh("am force-stop $pkg")
            return pkgs(adb.sh("pm list packages -d")).contains(pkg)
        }

        fun malware() {
            var seen = 0
            for (ioc in Iocs.MALWARE) {
                if (ioc.name !in all) continue
                seen++
                when {
                    ioc.name !in installed ->
                        add(Section.MALWARE, Level.OK, ioc.name, s(R.string.st_malware_removed), ioc.info)
                    ioc.name in disabled ->
                        add(Section.MALWARE, Level.OK, ioc.name, s(R.string.st_malware_off), ioc.info)
                    fix -> {
                        val ok = switchOff(ioc.name)
                        if (ok) putSet(p, BASE_DISABLED, getSet(p, BASE_DISABLED) + ioc.name)
                        add(Section.MALWARE, if (ok) Level.FIXED else Level.ALARM, ioc.name,
                            s(if (ok) R.string.st_fixed_malware else R.string.st_fix_failed), ioc.info,
                            if (ok) null else Action.DISABLE, ioc.name)
                    }
                    else -> add(Section.MALWARE, Level.ALARM, ioc.name, s(R.string.st_enabled_nofix), ioc.info,
                        Action.DISABLE, ioc.name)
                }
            }
            if (seen == 0) add(Section.MALWARE, Level.OK, s(R.string.chk_pkgs_title),
                s(R.string.chk_pkgs_none, Iocs.MALWARE.size), R.string.info_pkgs)
        }

        fun suspicious() {
            p.edit().remove(KEEP_ON).apply()
            for (sp in Iocs.SUSPICIOUS) {
                if (sp.name !in installed) continue
                when {
                    sp.name in disabled -> add(Section.SUSPICIOUS, Level.OK, sp.name, s(R.string.st_suspicious_off), sp.info)
                    switchOff(sp.name) -> {
                        putSet(p, BASE_DISABLED, getSet(p, BASE_DISABLED) + sp.name)
                        add(Section.SUSPICIOUS, Level.FIXED, sp.name, s(R.string.st_suspicious_fixed), sp.info)
                    }
                    else -> add(Section.SUSPICIOUS, Level.ALARM, sp.name, s(R.string.st_fix_failed), sp.info)
                }
            }
        }

        fun watched() {
            val base = getSet(p, BASE_DISABLED)
            disabled = pkgs(adb.sh("pm list packages -d"))
            val malware = Iocs.MALWARE.map { it.name }.toSet()
            var back = 0
            for (pkg in base.sorted()) {
                if (pkg in malware || pkg !in installed || pkg in disabled) continue
                back++
                if (fix && switchOff(pkg)) {
                    add(Section.PERSISTENCE, Level.FIXED, pkg, s(R.string.st_fixed_watch), R.string.info_watch)
                } else {
                    add(Section.PERSISTENCE, Level.WARN, pkg, s(R.string.st_watch_on), R.string.info_watch,
                        Action.DISABLE, pkg)
                }
            }
            if (back == 0) add(Section.PERSISTENCE, Level.OK, s(R.string.chk_watch_title),
                s(R.string.chk_watch_ok, base.size), R.string.info_watch)
            putSet(p, BASE_DISABLED, base + disabled)
        }

        fun newPackages() {
            val baseAll = getSet(p, BASE_PKGS)
            val baseSys = getSet(p, BASE_SYS)
            if (baseAll.isEmpty()) {
                putSet(p, BASE_PKGS, all)
                putSet(p, BASE_SYS, system)
                add(Section.PERSISTENCE, Level.INFO, s(R.string.chk_baseline_title), s(R.string.chk_baseline, all.size),
                    R.string.info_baseline)
                return
            }
            if (baseSys.isEmpty()) putSet(p, BASE_SYS, system)
            val newSys = if (baseSys.isEmpty()) emptySet() else system - baseSys
            for (pkg in newSys.sorted()) {
                if (fix && pkg in installed && pkg !in disabled && switchOff(pkg)) {
                    putSet(p, BASE_DISABLED, getSet(p, BASE_DISABLED) + pkg)
                    add(Section.PERSISTENCE, Level.FIXED, pkg, s(R.string.st_new_sys_fixed), R.string.info_new_sys)
                } else {
                    add(Section.PERSISTENCE, Level.ALARM, pkg, s(R.string.st_new_sys), R.string.info_new_sys,
                        Action.DISABLE, pkg)
                }
            }
            val newUser = all - baseAll - newSys
            for (pkg in newUser.sorted()) add(Section.PERSISTENCE, Level.INFO, pkg, s(R.string.st_new_user),
                R.string.info_new_user)
            if (newSys.isEmpty() && newUser.isEmpty()) add(Section.PERSISTENCE, Level.OK, s(R.string.chk_new_title),
                s(R.string.chk_new_none), R.string.info_new_sys)
            putSet(p, BASE_PKGS, baseAll + newUser)
            putSet(p, BASE_SYS, baseSys + newSys)
        }

        fun processes() {
            val hits = lines(adb.sh("ps -A -o USER,PID,NAME")).filter { l -> Iocs.PROCESSES.any { l.contains(it) } }
                .map { it.trim().replace(Regex("\\s+"), " ") }
            if (hits.isEmpty()) add(Section.SETTINGS, Level.OK, s(R.string.chk_proc_title), s(R.string.chk_proc_ok),
                R.string.info_proc)
            else add(Section.SETTINGS, Level.ALARM, s(R.string.chk_proc_title), join(hits), R.string.info_proc)
        }

        fun accessibility() {
            val v = adb.sh("settings get secure enabled_accessibility_services").trim()
            val title = s(R.string.chk_a11y_title)
            if (v.isEmpty() || v == "null") {
                add(Section.SETTINGS, Level.OK, title, s(R.string.chk_a11y_none), R.string.info_a11y); return
            }
            val bad = (Iocs.MALWARE + Iocs.SUSPICIOUS).map { it.name }.toSet()
            val (drop, keep) = v.split(":").partition { it.substringBefore("/") in bad }
            when {
                drop.isEmpty() -> add(Section.SETTINGS, Level.INFO, title, s(R.string.chk_a11y_list, v), R.string.info_a11y)
                fix -> {
                    if (keep.isEmpty()) {
                        adb.sh("settings delete secure enabled_accessibility_services")
                        adb.sh("settings put secure accessibility_enabled 0")
                    } else adb.sh("settings put secure enabled_accessibility_services " + keep.joinToString(":"))
                    add(Section.SETTINGS, Level.FIXED, title, s(R.string.chk_a11y_fixed, drop.joinToString(", ")),
                        R.string.info_a11y)
                }
                else -> add(Section.SETTINGS, Level.ALARM, title, drop.joinToString(", "), R.string.info_a11y)
            }
        }

        fun playProtect() {
            val consent = adb.sh("settings get global package_verifier_user_consent").trim()
            val enable = adb.sh("settings get global package_verifier_enable").trim()
            val title = s(R.string.chk_pp_title)
            if (consent == "-1" || enable == "0") {
                if (fix) {
                    adb.sh("settings put global package_verifier_user_consent 1")
                    adb.sh("settings put global package_verifier_enable 1")
                    add(Section.SETTINGS, Level.FIXED, title, s(R.string.chk_pp_fixed), R.string.info_pp)
                } else add(Section.SETTINGS, Level.ALARM, title, s(R.string.chk_pp_off), R.string.info_pp)
            } else add(Section.SETTINGS, Level.OK, title, s(R.string.chk_pp_ok), R.string.info_pp)
        }

        fun props() {
            val found = Iocs.PROPS.mapNotNull { k -> adb.sh("getprop $k").trim().takeIf { it.isNotEmpty() }?.let { "$k=$it" } }
            if (found.isEmpty()) add(Section.PERSISTENCE, Level.OK, s(R.string.chk_props_title), s(R.string.chk_props_ok),
                R.string.info_props)
            else add(Section.PERSISTENCE, Level.ALARM, s(R.string.chk_props_title), join(found), R.string.info_props)
        }

        fun oem() {
            val base = getSet(p, BASE_OEM).associate { it.substringAfter(" ") to it.substringBefore(" ") }
            val now = mutableMapOf<String, String>()
            val oemGlobs = listOf("/oem/*.apk", "/oem/app/*/*.apk", "/oem/priv-app/*/*.apk").joinToString(" ")
            for (l in lines(adb.sh("sha256sum $oemGlobs 2>/dev/null"))) {
                val f = l.trim().split(Regex("\\s+"))
                if (f.size >= 2 && f[0].length == 64) now[f[1]] = f[0]
            }
            if (now.isEmpty()) {
                add(Section.PERSISTENCE, Level.OK, "/oem", s(R.string.chk_oem_none), R.string.info_oem); return
            }
            for ((path, hash) in now.toSortedMap()) {
                val info = when {
                    path.contains("xcpe", true) -> R.string.info_oem_xcpe
                    path.contains("FactoryTest", true) -> R.string.info_oem_factory
                    else -> R.string.info_oem
                }
                val old = base[path]
                when {
                    hash in Iocs.BAD_SHA256 -> add(Section.PERSISTENCE, Level.ALARM, path, s(R.string.chk_hash_bad), info)
                    old == null && path.contains("xcpe", true) ->
                        add(Section.PERSISTENCE, Level.INFO, path, s(R.string.chk_oem_xcpe_found), info)
                    old == null -> add(Section.PERSISTENCE, Level.INFO, path, s(R.string.chk_oem_recorded), info)
                    old != hash -> add(Section.PERSISTENCE, Level.ALARM, path, s(R.string.chk_oem_changed, hash), info)
                    path.contains("xcpe", true) ->
                        add(Section.PERSISTENCE, Level.INFO, path, s(R.string.chk_oem_xcpe_same), info)
                    else -> add(Section.PERSISTENCE, Level.OK, path, s(R.string.chk_oem_same), info)
                }
            }
            putSet(p, BASE_OEM, now.map { "${it.value} ${it.key}" }.toSet())
            val paths = (Iocs.MALWARE + Iocs.SUSPICIOUS).filter { it.name in all }.flatMap { pk ->
                lines(adb.sh("pm path ${pk.name}")).filter { it.startsWith("package:") }.map { it.substring(8).trim() }
            }
            if (paths.isNotEmpty()) for (l in lines(adb.sh("sha256sum " + paths.joinToString(" ")))) {
                val f = l.trim().split(Regex("\\s+"))
                if (f.size >= 2 && f[0] in Iocs.BAD_SHA256)
                    add(Section.MALWARE, Level.ALARM, f[1], s(R.string.chk_hash_bad), R.string.info_hash)
            }
        }

        fun listingDiff(key: String, cmd: String, title: Int, info: Int) {
            val now = lines(adb.sh("$cmd 2>/dev/null")).map { it.trim() }.filter { it.isNotEmpty() && !it.endsWith(":") }.toSet()
            val base = getSet(p, key)
            putSet(p, key, base + now)
            if (base.isEmpty()) {
                add(Section.PERSISTENCE, Level.OK, s(title), s(R.string.chk_list_recorded, now.size), info); return
            }
            val added = now - base
            if (added.isEmpty()) add(Section.PERSISTENCE, Level.OK, s(title), s(R.string.chk_list_ok, now.size), info)
            else add(Section.PERSISTENCE, Level.ALARM, s(title), s(R.string.chk_list_new, join(added.sorted())), info)
        }

        fun libs() {
            val find = "find /system /vendor /product /system_ext /oem /data/local/tmp \\( " +
                Iocs.LIBS.joinToString(" -o ") { "-name $it" } + " \\) 2>/dev/null"
            val hits = lines(adb.sh(find, timeoutSec = 90))
            if (hits.isEmpty()) add(Section.PERSISTENCE, Level.OK, s(R.string.chk_libs_title), s(R.string.chk_libs_ok),
                R.string.info_libs)
            else add(Section.PERSISTENCE, Level.ALARM, s(R.string.chk_libs_title), join(hits), R.string.info_libs)
        }

        fun network() {
            val named = (Iocs.MALWARE + Iocs.SUSPICIOUS).mapNotNull { pk -> uids[pk.name]?.let { it to pk.name } }.toMap()
            val ownUids = named.keys.filter { it >= 10000 }.toSet()
            var total = 0
            val hits = mutableListOf<String>()
            val odd = mutableListOf<String>()
            for (l in lines(adb.sh("cat /proc/net/tcp /proc/net/tcp6 2>/dev/null"))) {
                val f = l.trim().split(Regex("\\s+"))
                if (f.size < 8 || f[0] == "sl" || !f[2].contains(":") || f[3] == "0A") continue
                val ip = hexIp(f[2].substringBefore(":")) ?: continue
                if (ip == "0.0.0.0") continue
                val port = f[2].substringAfter(":").toInt(16)
                val uid = f[7].toIntOrNull() ?: continue
                total++
                val who = named[uid] ?: "uid $uid"
                when {
                    ip in Iocs.IPS || port in Iocs.MQTT_PORTS || uid in ownUids -> hits += "$ip:$port ($who)"
                    uid == 1000 && port == 8000 && !private(ip) -> odd += "$ip:$port (system)"
                }
            }
            val t = s(R.string.chk_net_title)
            if (hits.isNotEmpty()) add(Section.NETWORK, Level.ALARM, t, join(hits), R.string.info_net)
            if (odd.isNotEmpty()) add(Section.NETWORK, Level.WARN, t, s(R.string.chk_net_odd, join(odd)), R.string.info_net)
            if (hits.isEmpty() && odd.isEmpty()) add(Section.NETWORK, Level.OK, t, s(R.string.chk_net_ok, total), R.string.info_net)
        }

        fun privateDns() {
            val mode = adb.sh("settings get global private_dns_mode").trim()
            val auto = mode.isEmpty() || mode == "null" || mode == "opportunistic"
            add(Section.NETWORK, if (auto) Level.INFO else Level.OK, s(R.string.chk_pdns_title),
                s(if (auto) R.string.chk_pdns_auto else R.string.chk_pdns_mode, mode), R.string.info_pdns)
        }
    }

    private fun dns(c: Context): Finding {
        val open = Iocs.DOMAINS.mapNotNull { d ->
            runCatching { InetAddress.getAllByName(d) }.getOrNull()
                ?.takeIf { a -> a.any { !it.isAnyLocalAddress && !it.isLoopbackAddress } }
                ?.let { "$d → ${it[0].hostAddress}" }
        }
        return if (open.isEmpty()) Finding(Section.NETWORK, Level.OK, Txt.of(c, R.string.chk_dns_title),
            Txt.of(c, R.string.chk_dns_ok, Iocs.DOMAINS.size), Txt.of(c, R.string.info_dns))
        else Finding(Section.NETWORK, Level.WARN, Txt.of(c, R.string.chk_dns_title),
            Txt.of(c, R.string.chk_dns_open, join(open)), Txt.of(c, R.string.info_dns))
    }

    private fun adbState(c: Context, s: AdbClient.Companion.Session?): Finding {
        val on = AdbClient.usbDebugging(c) || AdbClient.wirelessDebugging(c)
        var detail = Txt.of(c, if (on) R.string.chk_adb_on else R.string.chk_adb_off)
        if (!AdbClient.canToggle(c)) detail += Txt.of(c, R.string.chk_adb_no_toggle)
        return Finding(Section.SETTINGS, if (on) Level.INFO else Level.OK, Txt.of(c, R.string.chk_adb_title),
            detail, Txt.of(c, R.string.info_adb))
    }

    fun lines(s: String?): List<String> = s?.replace("\r", "")?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()

    private fun pkgs(out: String) = lines(out).map { it.trim() }.filter { it.startsWith("package:") }
        .map { it.substring(8).substringBefore(" ") }.toSet()

    private fun uidMap(out: String): Map<String, Int> = lines(out).mapNotNull { l ->
        val t = l.trim()
        val u = t.indexOf(" uid:")
        if (!t.startsWith("package:") || u < 0) null
        else t.substring(u + 5).split(',', ' ')[0].toIntOrNull()?.let { t.substring(8, u) to it }
    }.toMap()

    fun hexIp(hex: String): String? {
        val h = if (hex.length == 32) {
            if (!hex.startsWith("0000000000000000FFFF0000")) return null
            hex.substring(24)
        } else hex
        if (h.length != 8) return null
        val v = h.toLong(16)
        return "${v and 0xff}.${(v shr 8) and 0xff}.${(v shr 16) and 0xff}.${(v shr 24) and 0xff}"
    }

    private fun private(ip: String) = ip.startsWith("10.") || ip.startsWith("192.168.") || ip.startsWith("127.") ||
        Regex("172\\.(1[6-9]|2\\d|3[01])\\..*").matches(ip)

    private fun join(l: Collection<String>) = l.take(12).joinToString("\n") + if (l.size > 12) "\n… +${l.size - 12}" else ""

    private fun getSet(p: SharedPreferences, k: String): Set<String> =
        p.getString(k, "")!!.split("\n").filter { it.isNotEmpty() }.toSet()

    private fun putSet(p: SharedPreferences, k: String, v: Set<String>) =
        p.edit().putString(k, v.sorted().joinToString("\n")).apply()
}
