package com.booapps.xcpeguard.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.booapps.xcpeguard.scan.Iocs

@Composable
fun SourceLink(label: String, noBrowser: String, modifier: Modifier = Modifier) {
    val c = LocalContext.current
    var failed by remember { mutableStateOf(false) }
    Tile(modifier, onClick = { failed = !openUrl(c, Iocs.SOURCE_URL) }) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(label, color = Palette.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text(Iocs.SOURCE_URL, color = Palette.accent, fontSize = 12.sp, textDecoration = TextDecoration.Underline)
            if (failed) Text(noBrowser, color = Palette.warn, fontSize = 12.sp)
        }
    }
}

@Composable
fun ShareDeviceButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Tile(modifier, onClick = onClick) {
        androidx.compose.foundation.layout.Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(com.booapps.xcpeguard.R.drawable.ic_shield), null,
                Modifier.size(22.dp), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Palette.ok))
            androidx.compose.foundation.layout.Spacer(Modifier.width(10.dp))
            Text(label, color = Palette.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

fun openUrl(c: Context, url: String): Boolean = try {
    c.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (_: ActivityNotFoundException) {
    false
}
