package com.hlju.funlinkbluetooth.ui

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.isRenderEffectSupported
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun rememberPageBackdrop(): LayerBackdrop {
    val backgroundColor = MiuixTheme.colorScheme.background
    return rememberLayerBackdrop {
        drawRect(backgroundColor)
        drawContent()
    }
}

@Composable
fun Modifier.defaultBlurSurface(
    backdrop: Backdrop,
    shape: Shape = RectangleShape,
    blurRadius: Float = 72f,
    surfaceColor: Color = MiuixTheme.colorScheme.surface.copy(alpha = 0.76f),
): Modifier {
    return if (isRenderEffectSupported()) {
        textureBlur(
            backdrop = backdrop,
            shape = shape,
            blurRadius = blurRadius,
            colors = BlurColors(
                blendColors = listOf(
                    BlendColorEntry(surfaceColor, BlurBlendMode.SrcOver),
                ),
            ),
        )
    } else {
        background(surfaceColor, shape)
    }
}
