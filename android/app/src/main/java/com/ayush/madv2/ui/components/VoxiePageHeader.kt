package com.ayush.madv2.ui.components

import androidx.compose.animation.core.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayush.madv2.R

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoxiePageHeader(title: String, subtitle: String, modifier: Modifier = Modifier) {
    val keyboardClosingOrClosed = WindowInsets.imeAnimationTarget.getBottom(LocalDensity.current) == 0
    val motion = rememberInfiniteTransition(label = "Voxie breathing")
    val float by motion.animateFloat(0f, -5f, infiniteRepeatable(tween(2400), RepeatMode.Reverse), label = "Float")
    AnimatedVisibility(visible = keyboardClosingOrClosed, modifier = modifier.fillMaxWidth(),
        enter = expandVertically(tween(200)) + fadeIn(tween(160)),
        exit = shrinkVertically(tween(120)) + fadeOut(tween(100))) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(title, fontSize = 25.sp, lineHeight = 29.sp, letterSpacing = (-.6).sp, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Image(painterResource(R.drawable.voxie_landing), null,
            Modifier.size(92.dp).graphicsLayer { translationY = float.dp.toPx() })
    }
    }
}
