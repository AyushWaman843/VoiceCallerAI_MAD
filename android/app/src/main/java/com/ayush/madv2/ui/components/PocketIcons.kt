package com.ayush.madv2.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ayush.madv2.R

enum class Glyph { Menu, New, Plus, Mic, Send, Forward, Close, Check, Back, Calendar, Contacts, Settings, Search, Refresh, Clock, Next, Home, Calls, Groups, Wallet, Bell, Templates, Device, Sun, Moon }

@Composable
fun PocketIcon(glyph: Glyph, label: String? = null, color: Color = MaterialTheme.colorScheme.onSurface) {
    val drawable = when (glyph) {
        Glyph.Home -> R.drawable.ic_home_home
        Glyph.Calls -> R.drawable.ic_home_phone
        Glyph.Groups -> R.drawable.ic_home_groups
        Glyph.Wallet -> R.drawable.ic_home_wallet
        Glyph.Bell -> R.drawable.ic_home_bell
        Glyph.Templates -> R.drawable.ic_home_templates
        Glyph.Mic -> R.drawable.baseline_mic_24
        Glyph.Contacts -> R.drawable.outline_account_circle_24
        Glyph.Settings -> R.drawable.ic_settings_24
        else -> null
    }
    if (drawable != null) {
        Icon(painterResource(drawable),
            contentDescription = label, tint = color, modifier = Modifier.size(23.dp))
        return
    }
    Canvas(Modifier.size(23.dp).semantics { if (label != null) contentDescription = label }) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            fun line(x: Float, y: Float, ex: Float, ey: Float) =
                drawLine(color, Offset(x, y), Offset(ex, ey), 1.65f, StrokeCap.Round)
            fun circle(radius: Float, x: Float, y: Float) =
                drawCircle(color, radius, Offset(x,y), style = Stroke(1.65f))
            when (glyph) {
                Glyph.Menu -> { line(4f,8f,20f,8f); line(4f,16f,15f,16f) }
                Glyph.Plus -> { line(5f,12f,19f,12f); line(12f,5f,12f,19f) }
                Glyph.Close -> { line(6f,6f,18f,18f); line(6f,18f,18f,6f) }
                Glyph.Send -> { line(12f,19f,12f,5f); line(6f,11f,12f,5f); line(12f,5f,18f,11f) }
                Glyph.Forward -> { line(5f,12f,19f,12f); line(13f,6f,19f,12f); line(19f,12f,13f,18f) }
                Glyph.Check -> { line(5f,12f,10f,17f); line(10f,17f,20f,6f) }
                Glyph.Back -> { line(19f,12f,5f,12f); line(5f,12f,11f,6f); line(5f,12f,11f,18f) }
                Glyph.Next -> { line(9f,6f,15f,12f); line(15f,12f,9f,18f) }
                Glyph.New -> {
                    val p = Path().apply { moveTo(13f,4f); lineTo(5f,4f); lineTo(5f,20f); lineTo(20f,20f); lineTo(20f,12f) }
                    drawPath(p,color,style=Stroke(1.65f,cap=StrokeCap.Round))
                    line(11f,13f,20f,4f); line(17f,4f,20f,4f); line(20f,4f,20f,7f)
                }
                Glyph.Calendar -> {
                    drawRoundRect(color,Offset(4f,5f),Size(16f,16f),androidx.compose.ui.geometry.CornerRadius(2f),style=Stroke(1.65f))
                    line(4f,10f,20f,10f); line(8f,3f,8f,7f); line(16f,3f,16f,7f)
                }
                Glyph.Search -> { circle(6f,10f,10f); line(15f,15f,21f,21f) }
                Glyph.Refresh -> {
                    drawArc(color,45f,290f,false,Offset(4f,4f),Size(16f,16f),style=Stroke(1.65f,cap=StrokeCap.Round))
                    line(19.3f,4f,19.3f,9f); line(14.3f,9f,19.3f,9f)
                }
                Glyph.Clock -> { circle(8f,12f,12f); line(12f,7f,12f,12f); line(12f,12f,16f,14f) }
                Glyph.Device -> {
                    drawRoundRect(color, Offset(5f,3f), Size(14f,18f), androidx.compose.ui.geometry.CornerRadius(2f),
                        style = Stroke(1.65f))
                    line(10f,18f,14f,18f)
                }
                Glyph.Sun -> {
                    circle(4f,12f,12f)
                    repeat(8) { index ->
                        val angle = index * kotlin.math.PI / 4
                        line((12 + 7 * kotlin.math.cos(angle)).toFloat(), (12 + 7 * kotlin.math.sin(angle)).toFloat(),
                            (12 + 10 * kotlin.math.cos(angle)).toFloat(), (12 + 10 * kotlin.math.sin(angle)).toFloat())
                    }
                }
                Glyph.Moon -> {
                    val crescent = Path().apply {
                        moveTo(17f, 3f)
                        cubicTo(9f, 5f, 8f, 15f, 17f, 18f)
                        cubicTo(13f, 22f, 5f, 19f, 4f, 12f)
                        cubicTo(3f, 6f, 9f, 2f, 17f, 3f)
                    }
                    drawPath(crescent, color, style = Stroke(1.65f, cap = StrokeCap.Round))
                }
                else -> Unit
            }
        }
    }
}

@Composable
fun PocketIconButton(glyph: Glyph, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    val interaction = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    IconButton(onClick = hapticClick(onClick), enabled = enabled, interactionSource = interaction,
        modifier = Modifier.size(48.dp).pressMotion(interaction)) {
        PocketIcon(glyph, label, MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else .35f))
    }
}
