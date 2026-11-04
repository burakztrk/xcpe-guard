package com.booapps.xcpeguard.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.booapps.xcpeguard.R

private const val ABOUT_TR = "2026'da Google, bazı Android TV kutularının fabrika yazılımına üretici tarafından " +
    "gizli bir arka kapı (XCPE) konduğunu açıkladı. 1,3 milyondan fazla kutu bir botnete bağlandı: kutular sessizce " +
    "uygulama kurabiliyor, ekranı kaydedebiliyor ve internet bağlantınızı başkalarına kiralayabiliyordu. " +
    "XCPE Guard kutunuzu tarar, arka kapıyı kapatır ve geri gelmediğini düzenli olarak kontrol eder."

private const val ABOUT_EN = "In 2026 Google revealed that the maker of some Android TV boxes had hidden a backdoor " +
    "(XCPE) in their firmware. More than 1.3 million boxes joined a botnet: they could silently install apps, record " +
    "the screen and rent your internet connection out to strangers. XCPE Guard scans your box, switches the " +
    "backdoor off and keeps checking that it stays off."

@Composable
fun LanguageScreen(onPick: (String) -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 72.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.ic_shield), null, Modifier.size(44.dp), colorFilter = ColorFilter.tint(Palette.accent))
            Spacer(Modifier.width(14.dp))
            Text("XCPE Guard", color = Palette.text, fontSize = 32.sp, fontWeight = FontWeight.Light)
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Sorun ne?", color = Palette.text, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                Text(ABOUT_TR, color = Palette.muted, fontSize = 13.sp)
            }
            Column(Modifier.weight(1f)) {
                Text("What is the problem?", color = Palette.text, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                Text(ABOUT_EN, color = Palette.muted, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(16.dp))
        SourceLink("Google Threat Intelligence · rapor / report", "Tarayıcı bulunamadı · No browser found")
        Spacer(Modifier.height(18.dp))
        Text("Dil seçin  ·  Choose your language", color = Palette.muted, fontSize = 16.sp)
        Spacer(Modifier.height(12.dp))
        Row {
            Tile(Modifier.width(240.dp).focusRequester(first), onClick = { onPick("tr") }) {
                Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                    Text("Türkçe", color = Palette.text, fontSize = 24.sp, fontWeight = FontWeight.Medium)
                    Text("TR", color = Palette.muted, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.width(20.dp))
            Tile(Modifier.width(240.dp), onClick = { onPick("en") }) {
                Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                    Text("English", color = Palette.text, fontSize = 24.sp, fontWeight = FontWeight.Medium)
                    Text("EN", color = Palette.muted, fontSize = 13.sp)
                }
            }
        }
    }
}
