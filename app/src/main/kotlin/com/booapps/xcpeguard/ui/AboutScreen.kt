package com.booapps.xcpeguard.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.booapps.xcpeguard.R
import com.booapps.xcpeguard.scan.Iocs
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

@Composable
fun AboutScreen(a: MainActivity) {
    val c = LocalContext.current
    val first = remember { FocusRequester() }
    var message by remember { mutableStateOf("") }
    val qr = remember { qrBitmap(Iocs.REPO_URL, 512) }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    Row(Modifier.fillMaxSize().padding(horizontal = 72.dp, vertical = 32.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
            Image(painterResource(R.drawable.ic_shield), null, Modifier.size(120.dp), colorFilter = ColorFilter.tint(Palette.ok))
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.app_name), color = Palette.text, fontSize = 40.sp, fontWeight = FontWeight.Light)
            Text(versionLabel(c), color = Palette.muted, fontSize = 16.sp)
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.about_body), color = Palette.muted, fontSize = 15.sp)
            Spacer(Modifier.height(24.dp))
            Row {
                Pill(stringResource(R.string.github), Modifier.focusRequester(first)) {
                    if (!openUrl(c, Iocs.REPO_URL)) message = c.getString(R.string.no_browser)
                }
                Spacer(Modifier.width(12.dp))
                Pill(stringResource(R.string.back)) { a.closeAbout() }
            }
            if (message.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(message, color = Palette.warn, fontSize = 14.sp)
            }
        }
        Spacer(Modifier.width(48.dp))
        Column(
            Modifier.width(400.dp).background(Palette.card, RoundedCornerShape(24.dp)).padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.share_title), color = Palette.text, fontSize = 19.sp, fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.share_body), color = Palette.muted, fontSize = 14.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Box(Modifier.background(Color.White, RoundedCornerShape(16.dp)).padding(10.dp)) {
                Image(qr.asImageBitmap(), stringResource(R.string.github), Modifier.size(170.dp), filterQuality = FilterQuality.None)
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.share_scan), color = Palette.muted, fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            Pill(stringResource(R.string.share_button)) { if (!share(c)) message = c.getString(R.string.share_none) }
        }
    }
}

fun versionLabel(c: Context): String = runCatching {
    val pi = c.packageManager.getPackageInfo(c.packageName, 0)
    c.getString(R.string.version_fmt, pi.versionName ?: "", pi.longVersionCode)
}.getOrDefault("")

private fun share(c: Context): Boolean = try {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, c.getString(R.string.share_text, Iocs.REPO_URL))
    c.startActivity(Intent.createChooser(send, c.getString(R.string.share_button)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (_: ActivityNotFoundException) {
    false
}

private fun qrBitmap(text: String, size: Int): Bitmap {
    val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 0))
    val px = IntArray(size * size) { i -> if (m.get(i % size, i / size)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
    return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
}
