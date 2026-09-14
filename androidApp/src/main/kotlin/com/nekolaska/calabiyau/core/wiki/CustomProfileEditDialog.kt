package com.nekolaska.calabiyau.core.wiki

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import data.ApiResult
import data.CustomUserApi
import data.CustomUserProfile
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

/**
 * 自定义资料编辑对话框（Android）。
 * 支持：选择本地图片上传头像 + 编辑昵称 / 徽章 / 签名。
 */
@Composable
fun CustomProfileEditDialog(
    currentProfile: CustomUserProfile?,
    bid: String,
    wikiUserId: Long,
    onDismiss: () -> Unit,
    onSaved: (CustomUserProfile) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var customName by remember(currentProfile) { mutableStateOf(currentProfile?.customName.orEmpty()) }
    var badge by remember(currentProfile) { mutableStateOf(currentProfile?.badge.orEmpty()) }
    var bio by remember(currentProfile) { mutableStateOf(currentProfile?.bio.orEmpty()) }

    // 选图后进入裁切：cropSource 为待裁切原图，croppedAvatar 为确认后的方形结果
    var cropSource by remember { mutableStateOf<Bitmap?>(null) }
    var croppedAvatar by remember { mutableStateOf<Bitmap?>(null) }
    var isBusy by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val decoded = runCatching {
                    decodeSampledAvatar(context, uri, AVATAR_CROP_SOURCE_SIZE_PX)
                }.getOrNull()
                if (decoded == null) {
                    errorMessage = "无法读取所选图片"
                } else {
                    errorMessage = null
                    cropSource = decoded
                }
            }
        }
    }

    fun submit() {
        val cookies = WikiAuthHelper.getWikiCookies()
        if (cookies.isNullOrBlank()) {
            errorMessage = "未检测到 Wiki 登录 Cookie，请先登录"
            return
        }
        val finalName = customName.trim().ifBlank { null }
        val finalBio = bio.trim().ifBlank { null }
        val finalBadge = badge.trim().ifBlank { null }
        val cropped = croppedAvatar
        isBusy = true
        errorMessage = null
        scope.launch {
            var avatarUrl: String? = currentProfile?.avatarUrl
            if (cropped != null) {
                // 裁切结果已是方形：限制 512px → WEBP 压缩
                val encoded = runCatching { encodeSquareAvatar(cropped, AVATAR_TARGET_SIZE_PX) }.getOrNull()
                if (encoded == null) {
                    errorMessage = "图片处理失败"
                    isBusy = false
                    return@launch
                }
                if (encoded.size > 2 * 1024 * 1024) {
                    errorMessage = "图片处理结果过大，请更换图片"
                    isBusy = false
                    return@launch
                }
                when (val upload = CustomUserApi.uploadAvatar(encoded, "image/webp", cookies)) {
                    is ApiResult.Success -> avatarUrl = upload.value
                    is ApiResult.Error -> {
                        errorMessage = upload.message
                        isBusy = false
                        return@launch
                    }
                }
            }
            when (val save = CustomUserApi.updateProfile(
                customName = finalName,
                avatarUrl = avatarUrl,
                bio = finalBio,
                badge = finalBadge,
                wikiCookie = cookies
            )) {
                is ApiResult.Success -> {
                    onSaved(save.value)
                }
                is ApiResult.Error -> errorMessage = save.message
            }
            isBusy = false
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        title = { Text("编辑自定义资料") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "绑定账号：$bid（WikiID $wikiUserId）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 头像选择区
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        contentAlignment = Alignment.Center
                    ) {
                        val croppedPreview = croppedAvatar
                        val currentAvatar = currentProfile?.avatarUrl
                        when {
                            croppedPreview != null -> Image(
                                bitmap = croppedPreview.asImageBitmap(),
                                contentDescription = "头像预览",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            !currentAvatar.isNullOrBlank() -> AsyncImage(
                                model = currentAvatar,
                                contentDescription = "当前头像",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            else -> Text(
                                text = bid.take(1).uppercase(),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        FilledTonalButton(
                            onClick = { avatarPicker.launch("image/*") },
                            enabled = !isBusy
                        ) {
                            Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (croppedAvatar != null) "重新选择" else "选择头像")
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "选图后可拖动裁切为方形",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                OutlinedTextField(
                    value = customName,
                    onValueChange = { if (it.length <= 30) customName = it },
                    label = { Text("自定义昵称（留空显示 BID）") },
                    supportingText = { Text("${customName.length}/30") },
                    singleLine = true,
                    enabled = !isBusy,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = badge,
                    onValueChange = { if (it.length <= 20) badge = it },
                    label = { Text("徽章 / 头衔") },
                    supportingText = { Text("${badge.length}/20") },
                    singleLine = true,
                    enabled = !isBusy,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = bio,
                    onValueChange = { if (it.length <= 200) bio = it },
                    label = { Text("个人签名") },
                    supportingText = { Text("${bio.length}/200") },
                    enabled = !isBusy,
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                val displayError = errorMessage
                if (!displayError.isNullOrBlank()) {
                    Text(
                        text = displayError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            if (isBusy) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                TextButton(onClick = { submit() }) { Text("保存") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isBusy) { Text("取消") }
        }
    )

    // 裁切弹窗（选图后触发）
    val pendingCrop = cropSource
    if (pendingCrop != null) {
        AvatarCropDialog(
            source = pendingCrop,
            onConfirm = { cropped ->
                croppedAvatar = cropped
                cropSource = null
                errorMessage = null
            },
            onDismiss = { cropSource = null }
        )
    }
}

/** 简化的自定义档案头像组件：有头像用头像，否则显示首字母。 */
@Composable
fun CustomProfileAvatar(
    profile: CustomUserProfile?,
    fallbackText: String,
    modifier: Modifier = Modifier,
    textStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleSmall
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
    ) {
        val avatarUrl = profile?.avatarUrl
        if (!avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = "头像",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = (profile?.customName?.takeIf { it.isNotBlank() } ?: fallbackText)
                        .firstOrNull()?.uppercase() ?: "?",
                    style = textStyle,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

// ───── 头像压缩管线 ─────

/** 头像目标边长：展示最大 64dp，按 3x 密度留足余量 */
private const val AVATAR_TARGET_SIZE_PX = 512

/** 裁切源图的最大边长（保证裁切精度，同时避免超大位图） */
private const val AVATAR_CROP_SOURCE_SIZE_PX = 2048

/** 采样解码：按目标尺寸计算 inSampleSize，避免整图加载进内存 */
private fun decodeSampledAvatar(context: android.content.Context, uri: Uri, targetSizePx: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    // bounds 模式下 decodeStream 返回值恒为 null，只填充 bounds；流打开失败才算读不到
    val streamOpened = runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
            true
        } ?: false
    }.getOrDefault(false)
    if (!streamOpened) return null
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    // 防御极端大图
    if (bounds.outWidth > 20000 || bounds.outHeight > 20000) return null

    var sample = 1
    while (bounds.outWidth / (sample * 2) >= targetSizePx && bounds.outHeight / (sample * 2) >= targetSizePx) {
        sample *= 2
    }
    val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
    return runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOpts)
        }
    }.getOrNull()
}

/** 中心裁方 → 限制边长 → WEBP 压缩（API 30+ 用 WEBP_LOSSY） */
private fun encodeSquareAvatar(source: Bitmap, targetSizePx: Int, quality: Int = 85): ByteArray? {
    val side = minOf(source.width, source.height)
    if (side <= 0) return null
    val x = (source.width - side) / 2
    val y = (source.height - side) / 2
    val square = Bitmap.createBitmap(source, x, y, side, side)
    val scaled = if (side > targetSizePx) {
        Bitmap.createScaledBitmap(square, targetSizePx, targetSizePx, true)
    } else {
        square
    }
    val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Bitmap.CompressFormat.WEBP_LOSSY
    } else {
        Bitmap.CompressFormat.WEBP
    }
    return ByteArrayOutputStream().use { out ->
        if (!scaled.compress(format, quality, out)) null else out.toByteArray()
    }
}
