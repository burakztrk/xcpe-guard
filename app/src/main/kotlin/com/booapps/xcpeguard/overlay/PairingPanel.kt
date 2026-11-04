package com.booapps.xcpeguard.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import com.booapps.xcpeguard.R
import com.booapps.xcpeguard.ui.Palette
import com.booapps.xcpeguard.ui.XcpeTheme

@Composable
fun PairingPanel(svc: PairingOverlayService) = XcpeTheme {
    val phase = svc.phase.value
    Column(
        Modifier
            .background(Color(0xF2151A22), RoundedCornerShape(20.dp))
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(20.dp))
            .padding(20.dp)
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val d = digitOf(e.key)
                when {
                    d != null -> { svc.digit(d); true }
                    e.key == Key.Backspace || e.key == Key.Delete -> { svc.backspace(); true }
                    e.key == Key.Back && phase != PairingOverlayService.Phase.PAIRING -> { svc.finish(false); true }
                    else -> false
                }
            },
    ) {
        Text(stringResource(R.string.pair_title), color = Palette.text, fontSize = 20.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        when (phase) {
            PairingOverlayService.Phase.WAITING -> {
                Step("1", stringResource(R.string.pair_step1))
                Step("2", stringResource(R.string.pair_step2))
                Step("3", stringResource(R.string.pair_step3))
                Spacer(Modifier.height(8.dp))
                if (svc.error.value.isNotEmpty()) {
                    Text(stringResource(R.string.pair_failed_reopen, svc.error.value), color = Palette.alarm, fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                }
                Text(stringResource(R.string.pair_waiting), color = Palette.muted, fontSize = 14.sp)
            }
            PairingOverlayService.Phase.CODE, PairingOverlayService.Phase.FAILED -> CodeEntry(svc, phase)
            PairingOverlayService.Phase.PAIRING ->
                Text(stringResource(R.string.pair_pairing), color = Palette.text, fontSize = 16.sp)
            PairingOverlayService.Phase.DONE ->
                Text(stringResource(R.string.pair_done), color = Palette.ok, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun Step(n: String, text: String) = Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
    Box(Modifier.size(24.dp).background(Palette.accent, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
        Text(n, color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
    Spacer(Modifier.width(10.dp))
    Text(text, color = Palette.text, fontSize = 15.sp)
}

@Composable
private fun CodeEntry(svc: PairingOverlayService, phase: PairingOverlayService.Phase) {
    val first = remember { FocusRequester() }
    LaunchedEffect(phase) { runCatching { first.requestFocus() } }
    if (phase == PairingOverlayService.Phase.FAILED) {
        Text(stringResource(R.string.pair_failed_type, svc.error.value), color = Palette.alarm, fontSize = 14.sp)
        Spacer(Modifier.height(6.dp))
    }
    Text(stringResource(R.string.pair_enter), color = Palette.muted, fontSize = 14.sp)
    Spacer(Modifier.height(8.dp))
    val c = svc.code.value
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (i in 0 until 6) {
            Box(
                Modifier.weight(1f).height(48.dp).background(Color(0x22FFFFFF), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) { Text(c.getOrNull(i)?.toString() ?: "", color = Palette.text, fontSize = 24.sp, textAlign = TextAlign.Center) }
        }
    }
    Spacer(Modifier.height(12.dp))
    val rows = listOf("123", "456", "789", "<0>")
    for ((ri, row) in rows.withIndex()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((ci, k) in row.withIndex()) {
                val label = when (k) { '<' -> "⌫"; '>' -> "✓"; else -> k.toString() }
                val mod = Modifier.weight(1f).let { if (ri == 0 && ci == 0) it.focusRequester(first) else it }
                Button(
                    onClick = {
                        when (k) {
                            '<' -> svc.backspace()
                            '>' -> svc.submit()
                            else -> svc.digit(k)
                        }
                    },
                    modifier = mod.padding(vertical = 4.dp),
                    colors = ButtonDefaults.colors(containerColor = Color(0x22FFFFFF), contentColor = Palette.text),
                ) { Text(label, fontSize = 20.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
            }
        }
    }
}

private fun digitOf(k: Key): Char? = when (k) {
    Key.Zero, Key.NumPad0 -> '0'
    Key.One, Key.NumPad1 -> '1'
    Key.Two, Key.NumPad2 -> '2'
    Key.Three, Key.NumPad3 -> '3'
    Key.Four, Key.NumPad4 -> '4'
    Key.Five, Key.NumPad5 -> '5'
    Key.Six, Key.NumPad6 -> '6'
    Key.Seven, Key.NumPad7 -> '7'
    Key.Eight, Key.NumPad8 -> '8'
    Key.Nine, Key.NumPad9 -> '9'
    else -> null
}
