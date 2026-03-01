package ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.Button
import io.github.composefluent.component.ProgressRing
import io.github.composefluent.component.Text
import io.github.composefluent.icons.Icons
import io.github.composefluent.icons.regular.Play
import io.github.composefluent.icons.regular.Stop

/**
 * 空状态占位：大图标 + 提示文字，居中显示。
 *
 * @param icon 显示的图标
 * @param text 提示文字
 * @param modifier 外部修饰符
 */
@Composable
fun EmptyPlaceholder(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = rememberVectorPainter(icon),
                contentDescription = null,
                colorFilter = ColorFilter.tint(Color.Gray),
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(text, color = Color.Gray)
        }
    }
}

/**
 * 带边框和次级背景色的内容容器，常用于设置区域、列表区域等。
 *
 * @param modifier 外部修饰符
 * @param cornerRadius 圆角大小，默认 4.dp
 * @param padding 内边距，默认 0.dp（由调用方自行控制）
 * @param content 内容
 */
@Composable
fun SubtleBox(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 4.dp,
    padding: Dp = 0.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .clip(shape)
            .background(FluentTheme.colors.control.secondary)
            .border(1.dp, FluentTheme.colors.stroke.card.default, shape)
            .padding(padding),
        content = content
    )
}

/**
 * 音频播放/停止按钮。
 * - 加载中显示 [ProgressRing]
 * - 播放中显示停止图标（accent 色）
 * - 待播放显示播放图标
 *
 * @param isActive   当前是否处于活跃状态（播放中或加载中）
 * @param isLoading  是否正在加载（显示 loading ring）
 * @param onClick    点击回调
 */
@Composable
fun AudioPlayButton(
    isActive: Boolean,
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        iconOnly = true,
        onClick = onClick,
        modifier = modifier
    ) {
        if (isLoading) {
            ProgressRing(size = 16.dp)
        } else {
            val icon = if (isActive) Icons.Regular.Stop else Icons.Regular.Play
            Image(
                painter = rememberVectorPainter(icon),
                contentDescription = if (isActive) "停止" else "播放",
                colorFilter = ColorFilter.tint(
                    if (isActive) FluentTheme.colors.fillAccent.default
                    else FluentTheme.colors.text.text.primary
                ),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * 文件列表行：勾选框 + 可选图片缩略图 + 文件名 + 可选播放按钮。
 *
 * @param name          文件名
 * @param url           文件 URL
 * @param isSelected    是否已勾选
 * @param onToggle      切换勾选回调
 * @param playingUrl    当前正在播放的 URL（null 表示未播放）
 * @param loadingUrl    当前正在加载的 URL（null 表示未加载）
 * @param onPlayToggle  点击播放/停止回调，参数为 (url, isActive)
 * @param onImageClick  点击图片预览回调，参数为 (url, name)，null 表示不支持预览
 * @param thumbnailSize 缩略图大小，默认 36.dp
 * @param fontSize      文件名字号，默认 13.sp
 */
@Composable
fun FileListItem(
    name: String,
    url: String,
    isSelected: Boolean,
    onToggle: () -> Unit,
    playingUrl: String?,
    loadingUrl: String?,
    onPlayToggle: (url: String, isActive: Boolean) -> Unit,
    onImageClick: ((url: String, name: String) -> Unit)? = null,
    thumbnailSize: Dp = 36.dp,
    fontSize: androidx.compose.ui.unit.TextUnit = 13.sp
) {
    val canPreview = onImageClick != null && isImageFile(name, url)
    val canPlay = isAudioFile(name, url)
    val isPlaying = playingUrl == url && AudioPlayerManager.isPlaying(url)
    val isThisLoading = loadingUrl == url
    val isActive = isPlaying || isThisLoading

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onToggle)
            .padding(vertical = 4.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        io.github.composefluent.component.CheckBox(
            checked = isSelected,
            onCheckStateChange = { onToggle() }
        )
        Spacer(Modifier.width(8.dp))

        if (canPreview) {
            NetworkImage(
                url = url,
                modifier = Modifier
                    .size(thumbnailSize)
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onImageClick(url, name) },
                contentScale = ContentScale.Crop,
                placeholder = {
                    Box(
                        Modifier
                            .size(thumbnailSize)
                            .background(FluentTheme.colors.control.secondary)
                    )
                }
            )
            Spacer(Modifier.width(8.dp))
        }

        Text(name, fontSize = fontSize, modifier = Modifier.weight(1f))

        if (canPlay) {
            Spacer(Modifier.width(8.dp))
            AudioPlayButton(
                isActive = isActive,
                isLoading = isThisLoading,
                onClick = { onPlayToggle(url, isActive) }
            )
        }
    }
}

