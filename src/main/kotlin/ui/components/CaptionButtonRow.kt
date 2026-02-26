package ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.FontLoadResult
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser
import io.github.composefluent.ExperimentalFluentApi
import io.github.composefluent.FluentTheme
import io.github.composefluent.background.BackgroundSizing
import io.github.composefluent.background.Layer
import io.github.composefluent.component.Icon
import io.github.composefluent.component.Text
import io.github.composefluent.component.TooltipBox
import io.github.composefluent.icons.Icons
import io.github.composefluent.icons.regular.Dismiss
import io.github.composefluent.icons.regular.Square
import io.github.composefluent.icons.regular.SquareMultiple
import io.github.composefluent.icons.regular.Subtract
import io.github.composefluent.scheme.PentaVisualScheme
import io.github.composefluent.scheme.VisualStateScheme
import io.github.composefluent.scheme.collectVisualState


@Composable
fun CaptionButtonRow(
    windowHandle: WinDef.HWND,
    isMaximize: Boolean,
    isActive: Boolean,
    accentColor: Color,
    frameColorEnabled: Boolean,
    onCloseRequest: () -> Unit,
    modifier: Modifier = Modifier,
    onMaximizeButtonRectUpdate: (Rect) -> Unit,
    onMinimizeButtonRectUpdate: (Rect) -> Unit = {},
    onCloseButtonRectUpdate: (Rect) -> Unit = {}
) {
    //Draw the caption button
    Row(
        modifier = modifier
            .zIndex(1f)
    ) {
        val colors = if (frameColorEnabled && accentColor != Color.Unspecified) {
            CaptionButtonDefaults.accentColors(accentColor)
        } else {
            CaptionButtonDefaults.defaultColors()
        }
        CaptionButton(
            onClick = {
                User32.INSTANCE.ShowWindow(windowHandle, WinUser.SW_MINIMIZE)
            },
            icon = CaptionButtonIcon.Minimize,
            isActive = isActive,
            colors = colors,
            modifier = Modifier.onGloballyPositioned {
                onMinimizeButtonRectUpdate(it.boundsInWindow())
            }
        )
        CaptionButton(
            onClick = {
                if (isMaximize) {
                    User32.INSTANCE.ShowWindow(
                        windowHandle,
                        WinUser.SW_RESTORE
                    )
                } else {
                    User32.INSTANCE.ShowWindow(
                        windowHandle,
                        WinUser.SW_MAXIMIZE
                    )
                }
            },
            icon = if (isMaximize) {
                CaptionButtonIcon.Restore
            } else {
                CaptionButtonIcon.Maximize
            },
            isActive = isActive,
            colors = colors,
            modifier = Modifier.onGloballyPositioned {
                onMaximizeButtonRectUpdate(it.boundsInWindow())
            }
        )
//        CaptionButton(
//            icon = CaptionButtonIcon.Close,
//            onClick = onCloseRequest,
//            isActive = isActive,
//            colors = CaptionButtonDefaults.closeColors(),
//            modifier = Modifier.onGloballyPositioned {
//                onCloseButtonRectUpdate(it.boundsInWindow())
//            }
//        )
//        CustomTextButton(
//            name = "我是卡奴",
//            onClick = onCloseRequest,
//            isActive = isActive,
//            colors = CaptionButtonDefaults.closeColors(),
//            modifier = Modifier.onGloballyPositioned {
//                onCloseButtonRectUpdate(it.boundsInWindow())
//            }
//        )
        CustomIconButton(
            name = "关闭",
            onClick = onCloseRequest,
            isActive = isActive,
            colors = CaptionButtonDefaults.closeColors(),
            modifier = Modifier.onGloballyPositioned {
                onCloseButtonRectUpdate(it.boundsInWindow())
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalFluentApi::class)
@Composable
fun CaptionButton(
    onClick: () -> Unit,
    icon: CaptionButtonIcon,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    colors: VisualStateScheme<CaptionButtonColor> = CaptionButtonDefaults.defaultColors(),
    interaction: MutableInteractionSource = remember { MutableInteractionSource() }
) {
    val color = colors.schemeFor(interaction.collectVisualState(false))
    TooltipBox(
        tooltip = { Text(icon.name) }
    ) {
        Layer(
            backgroundSizing = BackgroundSizing.OuterBorderEdge,
            border = null,
            color = if (isActive) {
                color.background
            } else {
                color.inactiveBackground
            },
            contentColor = if (isActive) {
                color.foreground
            } else {
                color.inactiveForeground
            },
            modifier = modifier.size(46.dp, 32.dp).clickable(
                onClick = onClick,
                interactionSource = interaction,
                indication = null
            ),
            shape = RectangleShape
        ) {
            val fontFamily by rememberFontIconFamily()
            if (fontFamily != null) {
                Text(
                    text = icon.glyph.toString(),
                    fontFamily = fontFamily,
                    textAlign = TextAlign.Center,
                    fontSize = 10.sp,
                    modifier = Modifier.fillMaxSize().wrapContentSize(Alignment.Center),
                )
            } else {
                Icon(
                    imageVector = icon.imageVector,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().wrapContentSize(Alignment.Center).size(13.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalTextApi::class)
@Composable
private fun rememberFontIconFamily(): MutableState<FontFamily?> { // 改为MutableState
    val fontIconFamily = remember { mutableStateOf<FontFamily?>(null) }
    val fontFamilyResolver = LocalFontFamilyResolver.current

    LaunchedEffect(fontFamilyResolver) {
        fontIconFamily.value = sequenceOf("Segoe Fluent Icons", "Segoe MDL2 Assets")
            .mapNotNull {
                val fontFamily = FontFamily(it)
                runCatching {
                    val result = fontFamilyResolver.resolve(fontFamily).value as FontLoadResult
                    if (result.typeface == null || result.typeface?.familyName != it) null else fontFamily
                }.getOrNull()
            }
            .firstOrNull()
    }

    return fontIconFamily // 返回MutableState（而非父类State）
}


object CaptionButtonDefaults {
    @Composable
    @Stable
    fun defaultColors(
        default: CaptionButtonColor = CaptionButtonColor(
            background = FluentTheme.colors.subtleFill.transparent,
            foreground = FluentTheme.colors.text.text.primary,
            inactiveBackground = FluentTheme.colors.subtleFill.transparent,
            inactiveForeground = FluentTheme.colors.text.text.disabled
        ),
        hovered: CaptionButtonColor = default.copy(
            background = FluentTheme.colors.subtleFill.secondary,
            inactiveBackground = FluentTheme.colors.subtleFill.secondary,
            inactiveForeground = FluentTheme.colors.text.text.primary
        ),
        pressed: CaptionButtonColor = default.copy(
            background = FluentTheme.colors.subtleFill.tertiary,
            foreground = FluentTheme.colors.text.text.secondary,
            inactiveBackground = FluentTheme.colors.subtleFill.tertiary,
            inactiveForeground = FluentTheme.colors.text.text.tertiary
        ),
        disabled: CaptionButtonColor = default.copy(
            foreground = FluentTheme.colors.text.text.disabled,
        ),
    ) = PentaVisualScheme(
        default = default,
        hovered = hovered,
        pressed = pressed,
        disabled = disabled
    )

    @Composable
    @Stable
    fun accentColors(
        accentColor: Color,
        default: CaptionButtonColor = CaptionButtonColor(
            background = FluentTheme.colors.subtleFill.transparent,
            foreground = FluentTheme.colors.text.text.primary,
            inactiveBackground = FluentTheme.colors.subtleFill.transparent,
            inactiveForeground = FluentTheme.colors.text.text.disabled
        ),
        hovered: CaptionButtonColor = default.copy(
            background = accentColor,
            foreground = Color.White,
            inactiveBackground = accentColor,
            inactiveForeground = Color.White
        ),
        pressed: CaptionButtonColor = default.copy(
            background = accentColor.copy(0.9f),
            foreground = Color.White.copy(0.7f),
            inactiveBackground = accentColor.copy(0.9f),
            inactiveForeground = Color.White.copy(0.7f)
        ),
        disabled: CaptionButtonColor = default.copy(
            foreground = FluentTheme.colors.text.text.disabled,
        ),
    ) = PentaVisualScheme(
        default = default,
        hovered = hovered,
        pressed = pressed,
        disabled = disabled
    )

    @Composable
    @Stable
    fun closeColors() = accentColors(Color(0xFFC42B1C))
}

@Stable
data class CaptionButtonColor(
    val background: Color,
    val foreground: Color,
    val inactiveBackground: Color,
    val inactiveForeground: Color
)

enum class CaptionButtonIcon(
    val glyph: Char,
    val imageVector: ImageVector
) {
    Minimize(
        glyph = '\uE921',
        imageVector = Icons.Default.Subtract
    ),
    Maximize(
        glyph = '\uE922',
        imageVector = Icons.Default.Square
    ),
    Restore(
        glyph = '\uE923',
        imageVector = Icons.Default.SquareMultiple
    ),
    Close(
        glyph = '\uE8BB',
        imageVector = Icons.Default.Dismiss
    )
}


@OptIn(ExperimentalFoundationApi::class, ExperimentalFluentApi::class)
@Composable
fun CustomTextButton(
    onClick: () -> Unit,
    name: String,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    colors: VisualStateScheme<CaptionButtonColor> = CaptionButtonDefaults.defaultColors(),
    interaction: MutableInteractionSource = remember { MutableInteractionSource() }
) {
    val color = colors.schemeFor(interaction.collectVisualState(false))
    TooltipBox(
        tooltip = { Text(name) }
    ) {
        Layer(
            backgroundSizing = BackgroundSizing.OuterBorderEdge,
            border = null,
            color = if (isActive) {
                color.background
            } else {
                color.inactiveBackground
            },
            contentColor = if (isActive) {
                color.foreground
            } else {
                color.inactiveForeground
            },
            modifier = modifier.size(46.dp, 32.dp).clickable(
                onClick = onClick,
                interactionSource = interaction,
                indication = null
            ),
            shape = RectangleShape
        ) {
            val fontFamily by rememberFontIconFamily()
            if (fontFamily != null) {
                Text(
                    text = name,
                    fontFamily = fontFamily,
                    textAlign = TextAlign.Center,
                    fontSize = 10.sp,
                    modifier = Modifier.fillMaxSize().wrapContentSize(Alignment.Center),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalFluentApi::class)
@Composable
fun CustomIconButton(
    onClick: () -> Unit,
    name: String,
    icon: Painter = painterResource("calabiyau.png"),
    isActive: Boolean,
    modifier: Modifier = Modifier,
    colors: VisualStateScheme<CaptionButtonColor> = CaptionButtonDefaults.defaultColors(),
    interaction: MutableInteractionSource = remember { MutableInteractionSource() }
) {
    val color = colors.schemeFor(interaction.collectVisualState(false))
    TooltipBox(
        tooltip = { Text(name) }
    ) {
        Layer(
            backgroundSizing = BackgroundSizing.OuterBorderEdge,
            border = null,
            color = if (isActive) {
                color.background
            } else {
                color.inactiveBackground
            },
            contentColor = if (isActive) {
                color.foreground
            } else {
                color.inactiveForeground
            },
            modifier = modifier.size(46.dp, 32.dp).clickable(
                onClick = onClick,
                interactionSource = interaction,
                indication = null
            ),
            shape = RectangleShape
        ) {
            Icon(
                painter = icon,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().wrapContentSize(Alignment.Center).size(13.dp),
            )
        }
    }
}

fun Rect.contains(x: Float, y: Float): Boolean {
    return x in left..<right && y >= top && y < bottom
}