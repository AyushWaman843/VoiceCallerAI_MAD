package com.ayush.madv2.ui.components
import com.ayush.madv2.ui.components.VoxieFilledIconButton as FilledIconButton

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayush.madv2.ui.theme.PocketMotion

@Composable
fun PocketComposer(
    text: String, onTextChange: (String) -> Unit,
    recording: Boolean, transcribing: Boolean, busy: Boolean,
    samples: List<Float>, focusRequester: FocusRequester,
    onPlus: () -> Unit, onMic: () -> Unit, onCancel: () -> Unit,
    onFinish: () -> Unit, onSend: () -> Unit,
    homeStyle: Boolean = false,
    onFocus: () -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    val longHomePrompt = homeStyle && (text.length > 36 || '\n' in text)
    Surface(
        color = if (homeStyle) colors.surface else colors.surfaceContainer, shape = RoundedCornerShape(28.dp),
        shadowElevation = if (homeStyle) 2.dp else 0.dp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = if (homeStyle) 0.dp else 12.dp, vertical = 8.dp)
            .animateContentSize(tween(PocketMotion.Standard)),
    ) {
        AnimatedContent(
            targetState = if (recording) "recording" else if (transcribing) "transcribing" else if (busy) "processing" else "text",
            label = "Composer mode",
        ) { mode ->
            if (mode == "text" && longHomePrompt) {
                Column(Modifier.fillMaxWidth().padding(start = 18.dp, top = 16.dp, end = 10.dp, bottom = 9.dp)) {
                    BasicTextField(value = text, onValueChange = onTextChange, enabled = !busy,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface, lineHeight = 23.sp),
                        cursorBrush = SolidColor(colors.onSurface), maxLines = 6,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 66.dp, max = 150.dp)
                            .focusRequester(focusRequester).onFocusChanged { if (it.isFocused) onFocus() }
                            .semantics { contentDescription = "Message Voxie" })
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically) {
                        PocketIconButton(Glyph.Plus, "Add group or template", enabled = !busy, onClick = onPlus)
                        Spacer(Modifier.weight(1f))
                        PocketIconButton(Glyph.Mic, "Record voice message", enabled = !busy, onClick = onMic)
                        Spacer(Modifier.width(4.dp))
                        FilledIconButton(onClick = hapticClick(onSend), enabled = !busy && text.isNotBlank(),
                            shape = CircleShape, modifier = Modifier.size(44.dp)) {
                            PocketIcon(Glyph.Send, "Send message", colors.onPrimary)
                        }
                    }
                }
            } else {
            Row(Modifier.fillMaxWidth().heightIn(min = 58.dp).padding(5.dp),
                verticalAlignment = Alignment.CenterVertically) {
                when (mode) {
                    "recording" -> {
                        PocketIconButton(Glyph.Close, "Cancel recording", onClick = onCancel)
                        VoiceWaveform(samples, Modifier.weight(1f).height(42.dp))
                        FilledIconButton(onClick = hapticClick(onFinish), shape = CircleShape,
                            modifier = Modifier.size(48.dp)) { PocketIcon(Glyph.Check, "Finish recording", colors.onPrimary) }
                    }
                    "transcribing" -> {
                        Box(Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 12.dp)) {
                            ThinkingIndicator("Transcribing")
                        }
                    }
                    "processing" -> {
                        Box(Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 12.dp)) {
                            ThinkingIndicator("Working on your request")
                        }
                    }
                    else -> {
                        PocketIconButton(Glyph.Plus, if (homeStyle) "Add group or template" else "Add to call", enabled = !busy, onClick = onPlus)
                        BasicTextField(
                            value = text, onValueChange = onTextChange, enabled = !busy,
                            textStyle = (if (homeStyle) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge).copy(color = colors.onSurface),
                            cursorBrush = SolidColor(colors.onSurface), maxLines = 6,
                            modifier = Modifier.weight(1f).heightIn(min = 24.dp, max = 156.dp)
                                .focusRequester(focusRequester).onFocusChanged { if (it.isFocused) onFocus() }
                                .semantics { contentDescription = "Message Voxie" },
                            decorationBox = { inner ->
                                Box(contentAlignment = Alignment.CenterStart) {
                                    if (text.isEmpty()) Text(if (homeStyle) "Ask Voxie to make a call…" else "Message Voxie", color = colors.onSurfaceVariant,
                                        style = if (homeStyle) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                                        maxLines = if (homeStyle) 1 else Int.MAX_VALUE, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                    inner()
                                }
                            },
                        )
                        if (homeStyle) {
                            PocketIconButton(Glyph.Mic, "Record voice message", enabled = !busy, onClick = onMic)
                            FilledIconButton(onClick = hapticClick(onSend), enabled = !busy && text.isNotBlank(),
                                shape = CircleShape, modifier = Modifier.size(44.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(disabledContainerColor = colors.primary, disabledContentColor = colors.onPrimary)) {
                                PocketIcon(Glyph.Send, "Send message", colors.onPrimary)
                            }
                        } else AnimatedContent(text.isNotBlank(), label = "Voice or send") { hasText ->
                            if (hasText) {
                                FilledIconButton(onClick = hapticClick(onSend), enabled = !busy,
                                    modifier = Modifier.size(48.dp), shape = CircleShape) {
                                    PocketIcon(Glyph.Send, "Send message", colors.onPrimary)
                                }
                            } else PocketIconButton(Glyph.Mic, "Record voice message", enabled = !busy, onClick = onMic)
                        }
                    }
                }
            }
            }
        }
    }
}

@Composable
fun VoiceWaveform(samples: List<Float>, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.secondary
    // A single animation moves the timeline; amplitudes come only from the recorder.
    val phase = remember { Animatable(0f) }
    LaunchedEffect(samples) {
        phase.snapTo(0f)
        phase.animateTo(1f, tween(80, easing = LinearEasing))
    }
    Canvas(modifier.semantics { contentDescription = "Microphone volume waveform" }) {
        val spacing = size.width / 36f
        samples.takeLast(37).forEachIndexed { index, amplitude ->
            val x = (index - phase.value) * spacing
            if (x >= 0 && x <= size.width) {
                val height = (amplitude.coerceIn(0f, 1f) * (size.height - 6.dp.toPx()) + 3.dp.toPx())
                drawLine(color, Offset(x, (size.height-height)/2),
                    Offset(x, (size.height+height)/2), 2.5.dp.toPx(), StrokeCap.Round)
            }
        }
    }
}

@Composable
fun ThinkingIndicator(label: String) {
    val transition = rememberInfiniteTransition(label = "Thinking")
    val phase by transition.animateFloat(0f, 6.283185f,
        infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "Thinking dots")
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Canvas(Modifier.size(width = 30.dp, height = 22.dp)) {
            repeat(3) { index ->
                val lift = ((kotlin.math.sin(phase - index * 1.05f) + 1f) / 2f)
                drawCircle(color.copy(alpha = .35f + .65f * lift), 2.8.dp.toPx(),
                    Offset((5 + index * 10).dp.toPx(), center.y - 4.dp.toPx() * lift))
            }
        }
        Text(label, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}
