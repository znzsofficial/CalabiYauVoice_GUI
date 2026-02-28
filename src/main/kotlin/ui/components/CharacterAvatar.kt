package ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import data.WikiEngine
import io.github.composefluent.FluentTheme
import io.github.composefluent.icons.Icons
import io.github.composefluent.icons.regular.Person

@Composable
fun NetworkImage(
    url: String,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    placeholder: @Composable (() -> Unit)? = null
) {
    var imageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    // 当 URL 变化时，启动加载
    LaunchedEffect(url) {
        // 如果 URL 为空，直接重置
        if (url.isBlank()) {
            imageBitmap = null
            return@LaunchedEffect
        }

        // 尝试从 WikiEngine 加载 (带缓存)
        val bitmap = WikiEngine.loadNetworkImage(url)
        imageBitmap = bitmap
    }

    if (imageBitmap != null) {
        Image(
            bitmap = imageBitmap!!,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale
        )
    } else {
        // 如果有占位符且当前没图，显示占位符
        placeholder?.invoke()
    }
}

@Composable
fun CharacterAvatar(
    characterName: String,
    modifier: Modifier = Modifier.size(32.dp)
) {
    // 1. 获取头像的真实 URL (MediaWiki 解析)
    var avatarUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(characterName) {
        // 缓存表中不存在该角色名时，不发请求，直接用默认图标
        if (!WikiEngine.isCharacterNameValid(characterName)) return@LaunchedEffect
        val url = WikiEngine.getCharacterAvatarUrl(characterName)
        if (url != null) {
            avatarUrl = url
        }
    }

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(FluentTheme.colors.control.secondary)
            .border(1.dp, FluentTheme.colors.stroke.card.default, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        // 2. 如果解析到了 URL，使用自定义的网络图片加载器
        if (avatarUrl != null) {
            NetworkImage(
                url = avatarUrl!!,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                placeholder = {
                    // 加载过程中的占位符
                    PlaceholderIcon()
                }
            )
        } else {
            // 解析 URL 还没完成，或者解析失败，显示默认图标
            PlaceholderIcon()
        }
    }
}

// 抽离出来的占位图标，方便复用
@Composable
fun PlaceholderIcon() {
    Image(
        painter = rememberVectorPainter(Icons.Regular.Person),
        contentDescription = null,
        colorFilter = ColorFilter.tint(Color.Gray),
        modifier = Modifier.size(20.dp)
    )
}

/** 根据文件名或 URL 后缀判断是否为可预览的图片 */
fun isImageFile(name: String, url: String): Boolean {
    val IMAGE_EXTS = setOf("png", "jpg", "jpeg", "gif", "webp")
    fun String.ext() = substringAfterLast('.', "").lowercase().substringBefore('?')
    return name.ext() in IMAGE_EXTS || url.ext() in IMAGE_EXTS
}
