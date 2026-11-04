package com.booapps.xcpeguard.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.booapps.xcpeguard.R
import com.booapps.xcpeguard.adb.AdbClient
import com.booapps.xcpeguard.overlay.PairingOverlayService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SetupScreen(a: MainActivity) {
    val tick = a.resumes.intValue
    var devOn by remember { mutableStateOf(false) }
    var tcpOpen by remember { mutableStateOf(false) }
    var overlayOk by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(tick) {
        devOn = AdbClient.developerOptions(a)
        overlayOk = Settings.canDrawOverlays(a)
        tcpOpen = withContext(Dispatchers.IO) { AdbClient.tcpPortOpen() }
    }
    val step = if (!devOn) 2 else 3

    Row(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 32.dp)) {
        Column(Modifier.width(320.dp).fillMaxHeight()) {
            Image(painterResource(R.drawable.ic_shield), null, Modifier.size(48.dp), colorFilter = ColorFilter.tint(Palette.accent))
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.setup_title), color = Palette.text, fontSize = 30.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.setup_intro), color = Palette.muted, fontSize = 14.sp)
            Spacer(Modifier.height(24.dp))
            StepRow(1, stringResource(R.string.setup_s1), done = true, current = false)
            StepRow(2, stringResource(R.string.setup_s2), done = devOn, current = step == 2)
            StepRow(3, stringResource(R.string.setup_s3), done = false, current = step == 3)
            StepRow(4, stringResource(R.string.setup_s4), done = false, current = false)
            Spacer(Modifier.height(20.dp))
            Pill(stringResource(R.string.check_again)) { a.resumes.intValue++ }
        }
        Spacer(Modifier.width(40.dp))
        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
            if (step == 2) DevOptionsStep(a) else ConnectStep(a, tcpOpen, overlayOk, busy, message,
                onConnect = {
                    busy = true
                    message = a.getString(R.string.setup_tcp_prompt)
                    scope.launch {
                        val r = withContext(Dispatchers.IO) {
                            runCatching {
                                val s = AdbClient.open(a, allowPrompt = true)
                                AdbClient.grantSelf(a, s.adb)
                                AdbClient.close(a, s)
                            }
                        }
                        busy = false
                        if (r.isSuccess) a.finishSetup()
                        else message = a.getString(R.string.setup_failed, r.exceptionOrNull()?.message ?: "")
                    }
                })
        }
    }
}

@Composable
private fun StepRow(n: Int, label: String, done: Boolean, current: Boolean) {
    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        val bg = when { done -> Palette.ok; current -> Palette.accent; else -> Color(0x33FFFFFF) }
        Box(Modifier.size(30.dp).background(bg, RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) {
            Text(if (done) "✓" else n.toString(), color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Text(label, color = if (current) Palette.text else Palette.muted, fontSize = 17.sp,
            fontWeight = if (current) FontWeight.Medium else FontWeight.Normal)
    }
}

@Composable
private fun DevOptionsStep(a: MainActivity) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    Text(stringResource(R.string.dev_title), color = Palette.text, fontSize = 24.sp, fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(12.dp))
    Text(stringResource(R.string.dev_body), color = Palette.text, fontSize = 16.sp)
    Spacer(Modifier.height(20.dp))
    Row {
        Pill(stringResource(R.string.dev_open_about), Modifier.focusRequester(first)) {
            open(a, Intent(Settings.ACTION_DEVICE_INFO_SETTINGS))
        }
    }
}

@Composable
private fun ConnectStep(
    a: MainActivity, tcpOpen: Boolean, overlayOk: Boolean, busy: Boolean, message: String, onConnect: () -> Unit,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(tcpOpen, overlayOk) { runCatching { first.requestFocus() } }
    Text(stringResource(R.string.connect_title), color = Palette.text, fontSize = 24.sp, fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(8.dp))
    Text(stringResource(R.string.connect_body), color = Palette.muted, fontSize = 14.sp)
    Spacer(Modifier.height(14.dp))

    val wirelessFirst = !tcpOpen && Build.VERSION.SDK_INT >= 30
    val tcpCard: @Composable () -> Unit = {
        MethodCard(
            title = stringResource(R.string.m_tcp_title),
            badge = stringResource(if (tcpOpen) R.string.m_available else R.string.m_closed),
            badgeColor = if (tcpOpen) Palette.ok else Palette.muted,
            body = stringResource(if (tcpOpen) R.string.m_tcp_body_open else R.string.m_tcp_body_closed),
        ) {
            if (tcpOpen) Pill(stringResource(R.string.m_tcp_connect), if (!wirelessFirst) Modifier.focusRequester(first) else Modifier) {
                if (!busy) onConnect()
            } else Pill(stringResource(R.string.open_dev_settings)) {
                open(a, Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
            }
        }
    }
    val wirelessCard: @Composable () -> Unit = {
        if (Build.VERSION.SDK_INT >= 30) MethodCard(
            title = stringResource(R.string.m_wifi_title),
            badge = stringResource(R.string.m_wifi_badge),
            badgeColor = Palette.accent,
            body = stringResource(if (overlayOk) R.string.m_wifi_body else R.string.m_wifi_body_overlay),
        ) {
            if (!overlayOk) Pill(stringResource(R.string.m_wifi_overlay), if (wirelessFirst) Modifier.focusRequester(first) else Modifier) {
                requestOverlay(a)
            } else Pill(stringResource(R.string.m_wifi_start), if (wirelessFirst) Modifier.focusRequester(first) else Modifier) {
                PairingOverlayService.start(a)
                open(a, Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
            }
        }
    }
    if (wirelessFirst) { wirelessCard(); Spacer(Modifier.height(12.dp)); tcpCard() }
    else { tcpCard(); Spacer(Modifier.height(12.dp)); wirelessCard() }

    if (message.isNotEmpty()) {
        Spacer(Modifier.height(16.dp))
        Text(message, color = if (busy) Palette.warn else Palette.alarm, fontSize = 16.sp)
    }
}

@Composable
private fun MethodCard(title: String, badge: String, badgeColor: Color, body: String, action: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().background(Palette.card, RoundedCornerShape(16.dp)).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Palette.text, fontSize = 19.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(12.dp))
            Chip(badge, badgeColor)
        }
        Spacer(Modifier.height(8.dp))
        Text(body, color = Palette.muted, fontSize = 14.sp)
        Spacer(Modifier.height(10.dp))
        action()
    }
}

private fun open(a: MainActivity, i: Intent) {
    try { a.startActivity(i) } catch (e: ActivityNotFoundException) {
        runCatching { a.startActivity(Intent(Settings.ACTION_SETTINGS)) }
    }
}

private fun requestOverlay(a: MainActivity) {
    val withPkg = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + a.packageName))
    try { a.startActivity(withPkg) } catch (e: ActivityNotFoundException) {
        open(a, Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
    }
}
