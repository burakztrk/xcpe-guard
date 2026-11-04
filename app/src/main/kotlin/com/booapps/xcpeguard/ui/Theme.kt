package com.booapps.xcpeguard.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.tv.material3.darkColorScheme
import com.booapps.xcpeguard.scan.Level

object Palette {
    val bg = Color(0xFF0B0E13)
    val card = Color(0xFF151A22)
    val cardFocus = Color(0xFF243040)
    val text = Color(0xFFF1F3F4)
    val muted = Color(0xFF9AA0A6)
    val accent = Color(0xFF8AB4F8)
    val ok = Color(0xFF81C995)
    val info = Color(0xFF8AB4F8)
    val warn = Color(0xFFFDD663)
    val alarm = Color(0xFFFF6B6B)
    val fixed = Color(0xFFFFB74D)

    fun level(l: Int) = when (l) {
        Level.ALARM -> alarm
        Level.FIXED -> ok
        Level.WARN -> warn
        Level.INFO -> ok
        else -> ok
    }
}

@Composable
fun XcpeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(primary = Palette.accent, background = Palette.bg, surface = Palette.card)) {
        content()
    }
}

@Composable
fun Pill(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.colors(
            containerColor = Color(0x22FFFFFF), contentColor = Palette.text,
            focusedContainerColor = Color(0xFFE8EAED), focusedContentColor = Color(0xFF111111),
        ),
    ) { Text(text, fontSize = 15.sp, fontWeight = FontWeight.Medium) }
}

@Composable
fun Tile(modifier: Modifier = Modifier, onClick: () -> Unit = {}, content: @Composable BoxScope.() -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Palette.card, contentColor = Palette.text,
            focusedContainerColor = Palette.cardFocus, focusedContentColor = Palette.text,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.01f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, Color(0xFFE8EAED)), shape = RoundedCornerShape(16.dp)),
        ),
        content = content,
    )
}

@Composable
fun Chip(text: String, color: Color) {
    Box(Modifier.background(color, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 3.dp)) {
        Text(text, color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
