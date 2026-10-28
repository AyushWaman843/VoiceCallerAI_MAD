package com.ayush.madv2.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

@Composable
fun <T> VoxieTransition(target: T, modifier: Modifier = Modifier, content: @Composable (T) -> Unit) {
    AnimatedContent(targetState = target, modifier = modifier, transitionSpec = {
        (fadeIn(tween(220, delayMillis = 40)) + slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { it / 12 } +
            scaleIn(tween(300), initialScale = .98f)) togetherWith
            (fadeOut(tween(150)) + slideOutHorizontally(tween(230)) { -it / 18 })
    }, label = "Voxie screen transition") { screen -> content(screen) }
}

@Composable
fun Modifier.pressMotion(interaction: MutableInteractionSource): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .96f else 1f,
        spring(dampingRatio = .65f, stiffness = 650f), label = "Button press")
    return graphicsLayer {
        scaleX = scale; scaleY = scale
        translationY = (1f - scale) * 50f
    }
}

@Composable
fun VoxieButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape, colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(defaultElevation = 3.dp, pressedElevation = 0.dp),
    border: BorderStroke? = null, contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null, content: @Composable RowScope.() -> Unit) {
    val interaction = interactionSource ?: remember { MutableInteractionSource() }
    Button(hapticClick(onClick), modifier.pressMotion(interaction), enabled, shape, colors, elevation,
        border, contentPadding, interaction, content)
}

@Composable
fun VoxieOutlinedButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = ButtonDefaults.outlinedShape, colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    elevation: ButtonElevation? = null, border: BorderStroke? = ButtonDefaults.outlinedButtonBorder(enabled),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding, interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit) {
    val interaction = interactionSource ?: remember { MutableInteractionSource() }
    OutlinedButton(hapticClick(onClick), modifier.pressMotion(interaction), enabled, shape, colors, elevation,
        border, contentPadding, interaction, content)
}

@Composable
fun VoxieTextButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = ButtonDefaults.textShape, colors: ButtonColors = ButtonDefaults.textButtonColors(),
    elevation: ButtonElevation? = null, border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding, interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit) {
    val interaction = interactionSource ?: remember { MutableInteractionSource() }
    TextButton(hapticClick(onClick), modifier.pressMotion(interaction), enabled, shape, colors, elevation,
        border, contentPadding, interaction, content)
}

@Composable
fun VoxieFilledIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = IconButtonDefaults.filledShape,
    colors: IconButtonColors = IconButtonDefaults.filledIconButtonColors(),
    interactionSource: MutableInteractionSource? = null, content: @Composable () -> Unit) {
    val interaction = interactionSource ?: remember { MutableInteractionSource() }
    FilledIconButton(hapticClick(onClick), modifier.pressMotion(interaction), enabled, shape, colors, interaction, content)
}
