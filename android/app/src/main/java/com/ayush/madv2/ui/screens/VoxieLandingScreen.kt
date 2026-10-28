package com.ayush.madv2.ui.screens

import com.ayush.madv2.ui.components.VoxieButton as Button
import com.ayush.madv2.ui.components.VoxieOutlinedButton as OutlinedButton
import com.ayush.madv2.ui.components.VoxieTextButton as TextButton

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayush.madv2.R
import com.ayush.madv2.ui.components.hapticClick
import com.ayush.madv2.ui.components.PocketIcon
import com.ayush.madv2.ui.components.Glyph

@Composable
fun VoxieLandingScreen(onGetStarted: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < .5f
    val ink = colors.onBackground
    val entranceScale = remember { Animatable(.72f) }
    LaunchedEffect(Unit) {
        entranceScale.animateTo(1f, spring(dampingRatio = .48f, stiffness = Spring.StiffnessLow))
    }
    val motion = rememberInfiniteTransition(label = "Voxie float")
    val floatOffset by motion.animateFloat(
        initialValue = 0f, targetValue = -8f,
        animationSpec = infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "Mascot position",
    )
    Box(Modifier.fillMaxSize().background(colors.background)) {
        if (!dark) Image(painterResource(R.drawable.landing_bg), contentDescription = null,
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            // Scroll on short or landscape windows instead of squeezing the copy and CTA.
            val contentHeight = maxHeight.coerceAtLeast(620.dp)
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Column(Modifier.widthIn(max = 480.dp).fillMaxWidth().height(contentHeight)
                    .padding(horizontal = 28.dp), horizontalAlignment = Alignment.Start) {
                    Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Image(painterResource(R.drawable.voxie_landing), contentDescription = null,
                            modifier = Modifier.size(32.dp))
                        Text("Voxie", color = ink, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height((contentHeight * .09f).coerceAtMost(76.dp)))
                    Text("Hey, I'm\nVoxie!", color = ink, fontSize = 46.sp, lineHeight = 49.sp,
                        letterSpacing = (-1.5).sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(12.dp))
                    Text("Your little AI companion\nthat makes calls for you,\neven when you're busy.",
                        color = colors.onSurfaceVariant, fontSize = 20.sp, lineHeight = 28.sp)
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Image(painterResource(R.drawable.voxie_landing), contentDescription = "Voxie, your AI calling companion",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().padding(vertical = 8.dp)
                                .graphicsLayer {
                                    translationY = floatOffset.dp.toPx()
                                    scaleX = entranceScale.value
                                    scaleY = entranceScale.value
                                })
                    }
                    Button(onClick = hapticClick(onGetStarted), shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp, pressedElevation = 2.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 62.dp)) {
                        Text("Get Started", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(24.dp))
                        PocketIcon(Glyph.Forward, color = LocalContentColor.current)
                    }
                    Spacer(Modifier.height(30.dp))
                }
            }
        }
    }
}
