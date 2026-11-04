package com.booapps.xcpeguard.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.booapps.xcpeguard.Prefs
import com.booapps.xcpeguard.R
import com.booapps.xcpeguard.adb.AdbClient
import com.booapps.xcpeguard.scan.Finding
import com.booapps.xcpeguard.scan.Level
import com.booapps.xcpeguard.scan.Report
import com.booapps.xcpeguard.scan.Scanner
import com.booapps.xcpeguard.scan.Section
import com.booapps.xcpeguard.scan.ScanJob
import com.booapps.xcpeguard.scan.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@Composable
fun ReportScreen(a: MainActivity) {
    var report by remember { mutableStateOf(Store.last(a)) }
    var scanning by remember { mutableStateOf(false) }
    var scheduled by remember { mutableStateOf(Prefs.scheduled(a)) }
    var adbOn by remember { mutableStateOf(false) }
    val tick = a.resumes.intValue
    val scope = rememberCoroutineScope()
    val first = remember { FocusRequester() }

    fun scan() {
        if (scanning) return
        scanning = true
        scope.launch {
            report = withContext(Dispatchers.IO) { Scanner.run(a, allowPrompt = true) }
            scanning = false
            runCatching { first.requestFocus() }
            adbOn = AdbClient.usbDebugging(a) || AdbClient.wirelessDebugging(a)
            Store.markSeen(a)
        }
    }

    LaunchedEffect(tick) {
        adbOn = AdbClient.usbDebugging(a) || AdbClient.wirelessDebugging(a)
        Store.markSeen(a)
        if (a.scanOnOpen || (scheduled && Store.due(a))) { a.scanOnOpen = false; scan() }
        runCatching { first.requestFocus() }
    }

    Row(Modifier.fillMaxSize().padding(start = 48.dp, end = 32.dp, top = 22.dp, bottom = 18.dp)) {
        Column(Modifier.width(340.dp).fillMaxHeight()) {
            StatusPanel(report, scanning, scheduled)
            Spacer(Modifier.height(14.dp))
            Pill(stringResource(if (scanning) R.string.scanning else R.string.scan_now), Modifier.fillMaxWidth().focusRequester(first)) { scan() }
            Spacer(Modifier.height(10.dp))
            Pill(stringResource(if (scheduled) R.string.schedule_daily else R.string.schedule_off), Modifier.fillMaxWidth()) {
                scheduled = !scheduled
                Prefs.setScheduled(a, scheduled)
                ScanJob.schedule(a)
            }
            if (adbOn && AdbClient.canToggle(a)) {
                Spacer(Modifier.height(10.dp))
                Pill(stringResource(R.string.adb_off_now), Modifier.fillMaxWidth()) {
                    AdbClient.switchOffNow(a)
                    adbOn = false
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth()) {
                Pill(stringResource(R.string.connection_settings), Modifier.weight(1f)) { a.openSetup() }
                Spacer(Modifier.width(10.dp))
                Pill(stringResource(R.string.switch_language)) { a.switchLanguage() }
            }
            Spacer(Modifier.height(12.dp))
            SourceLink(stringResource(R.string.source_line), stringResource(R.string.no_browser), Modifier.fillMaxWidth())
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(16.dp))
            ShareDeviceButton(stringResource(R.string.fix_another)) { a.openAbout() }
        }
        Spacer(Modifier.width(32.dp))
        Findings(report, Modifier.weight(1f).fillMaxHeight())
    }
}

@Composable
private fun StatusPanel(r: Report?, scanning: Boolean, scheduled: Boolean) {
    val state = r?.state ?: Level.INFO
    val color = if (r == null) Palette.muted else Palette.level(state)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.ic_shield), null, Modifier.size(40.dp), colorFilter = ColorFilter.tint(color))
        Spacer(Modifier.width(10.dp))
        Text(stringResource(R.string.app_name), color = Palette.muted, fontSize = 16.sp)
    }
    Spacer(Modifier.height(8.dp))
    val headline = when {
        scanning -> R.string.st_scanning
        r == null -> R.string.st_never
        !r.adb -> R.string.st_noadb
        state == Level.ALARM -> R.string.st_alarm
        state == Level.FIXED -> R.string.st_fixed
        state == Level.WARN -> R.string.st_warn
        else -> R.string.st_clean
    }
    Text(stringResource(headline), color = color, fontSize = 28.sp, fontWeight = FontWeight.Light)
    Spacer(Modifier.height(8.dp))
    if (scanning) {
        Text(stringResource(R.string.scan_prompt_hint), color = Palette.warn, fontSize = 14.sp)
    } else if (r != null) {
        val df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        Text(stringResource(R.string.counts, r.count(Level.ALARM), r.count(Level.FIXED), r.count(Level.WARN)),
            color = Palette.text, fontSize = 15.sp)
        Text(stringResource(R.string.last_scan, df.format(Date(r.time))), color = Palette.muted, fontSize = 14.sp)
        Text(if (scheduled) stringResource(R.string.next_scan, df.format(Date(maxOf(r.time + Store.INTERVAL_MS, System.currentTimeMillis()))))
            else stringResource(R.string.next_scan_off), color = Palette.muted, fontSize = 14.sp)
        val m = when (r.method) {
            AdbClient.METHOD_TCP -> R.string.method_tcp
            AdbClient.METHOD_WIRELESS -> R.string.method_wireless
            else -> R.string.method_none
        }
        Text(stringResource(m), color = Palette.muted, fontSize = 14.sp)
    }
}

@Composable
private fun Findings(r: Report?, modifier: Modifier) {
    if (r == null) {
        Column(modifier) { Text(stringResource(R.string.no_report), color = Palette.muted, fontSize = 16.sp) }
        return
    }
    val order = listOf(Section.MALWARE, Section.PERSISTENCE, Section.SUSPICIOUS, Section.SETTINGS, Section.NETWORK)
    val rank = mapOf(Level.ALARM to 0, Level.FIXED to 1, Level.WARN to 2, Level.INFO to 3, Level.OK to 4)
    val c = androidx.compose.ui.platform.LocalContext.current
    val fixes = remember(r.time) { Store.recentFixes(c) }
    val df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(r.time) { listState.scrollToItem(0) }
    LazyColumn(modifier, state = listState, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (fixes.isNotEmpty()) {
            item(key = "fixed_banner") { FixedBanner(fixes.size) }
            items(fixes, key = { "fx_${it.time}_${it.finding.title.key}" }) {
                FindingCard(it.finding, stringResource(R.string.fixed_at, df.format(Date(it.time))))
            }
        }
        for (sec in order) {
            val list = r.findings.filter { it.section == sec && it.level != Level.FIXED }.sortedBy { rank[it.level] }
            if (list.isEmpty()) continue
            item(key = "h_$sec") { SectionHeader(sec) }
            items(list, key = { "${sec}_${it.title.key}_${it.detail.key.hashCode()}" }) { FindingCard(it) }
        }
        item(key = "history") { History() }
    }
}

@Composable
private fun SectionHeader(s: Section) {
    val (t, d) = when (s) {
        Section.MALWARE -> R.string.sec_malware to R.string.sec_malware_d
        Section.PERSISTENCE -> R.string.sec_persistence to R.string.sec_persistence_d
        Section.SUSPICIOUS -> R.string.sec_suspicious to R.string.sec_suspicious_d
        Section.SETTINGS -> R.string.sec_settings to R.string.sec_settings_d
        Section.NETWORK -> R.string.sec_network to R.string.sec_network_d
    }
    Column(Modifier.padding(top = 14.dp, bottom = 2.dp)) {
        Text(stringResource(t), color = Palette.text, fontSize = 20.sp, fontWeight = FontWeight.Medium)
        Text(stringResource(d), color = Palette.muted, fontSize = 13.sp)
    }
}

@Composable
private fun FindingCard(f: Finding, note: String? = null) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val lvl = when (f.level) {
        Level.ALARM -> R.string.lvl_alarm
        Level.FIXED -> R.string.lvl_fixed
        Level.WARN -> R.string.lvl_warn
        Level.INFO -> R.string.lvl_info
        else -> R.string.lvl_ok
    }
    val info = f.info.text(ctx)
    var open by androidx.compose.runtime.saveable.rememberSaveable(f.title.key + f.detail.key) { mutableStateOf(false) }
    Tile(Modifier.fillMaxWidth(), onClick = { if (info.isNotEmpty()) open = !open }) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Badge(f.level, stringResource(lvl))
                Spacer(Modifier.width(12.dp))
                Text(f.title.text(ctx), color = Palette.text, fontSize = 17.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f))
                if (info.isNotEmpty()) {
                    Spacer(Modifier.width(12.dp))
                    Text(if (open) "▴" else "▾", color = Palette.muted, fontSize = 22.sp)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(f.detail.text(ctx), color = Palette.text, fontSize = 14.sp)
            if (note != null) Text(note, color = Palette.ok, fontSize = 13.sp)
            androidx.compose.animation.AnimatedVisibility(open) {
                Column {
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.background(androidx.compose.ui.graphics.Color(0x14FFFFFF), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text("ⓘ", color = Palette.info, fontSize = 14.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(info, color = Palette.muted, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun History() {
    val c = androidx.compose.ui.platform.LocalContext.current
    val h = remember { Store.history(c) }
    if (h.isEmpty()) return
    val df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    Column(Modifier.padding(top = 18.dp)) {
        Text(stringResource(R.string.history), color = Palette.text, fontSize = 20.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        h.take(12).forEach { e ->
            Text(stringResource(R.string.history_row, df.format(Date(e.time)), e.alarms, e.fixed, e.warnings) +
                if (e.adb) "" else "  ·  " + stringResource(R.string.history_noadb),
                color = when {
                    e.alarms > 0 || e.fixed > 0 -> Palette.alarm
                    e.warnings > 0 -> Palette.warn
                    else -> Palette.ok
                }, fontSize = 14.sp)
        }
    }
}

@Composable
private fun Badge(level: Int, label: String) {
    when (level) {
        Level.OK, Level.INFO ->
            Image(painterResource(R.drawable.ic_shield), label, Modifier.size(26.dp), colorFilter = ColorFilter.tint(Palette.ok))
        Level.FIXED -> Row(verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.ic_shield), null, Modifier.size(26.dp), colorFilter = ColorFilter.tint(Palette.ok))
            Spacer(Modifier.width(8.dp))
            Chip(label, Palette.ok)
        }
        else -> Chip(label, Palette.level(level))
    }
}

@Composable
private fun FixedBanner(n: Int) {
    Row(
        Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Color(0x2281C995), RoundedCornerShape(16.dp))
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painterResource(R.drawable.ic_shield), null, Modifier.size(44.dp), colorFilter = ColorFilter.tint(Palette.ok))
        Spacer(Modifier.width(16.dp))
        Column {
            Text(stringResource(R.string.fixed_banner_title, n), color = Palette.ok, fontSize = 22.sp, fontWeight = FontWeight.Medium)
            Text(stringResource(R.string.fixed_banner_body), color = Palette.text, fontSize = 14.sp)
        }
    }
}
