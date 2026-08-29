package top.yunov.neteasy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest

/**
 * 统一的封面图加载组件：加载中/失败都显示一个带底色的音符占位，并带 crossfade 过渡。
 *
 * 之前播放页的大封面在图片还没加载出来时是整块空白（AsyncImage 无占位），
 * 弱网/首载会显得“没加载”。这里给一个 surfaceContainerHighest 底色 + 音符图标，
 * 让等待过程有明确的内容占位，加载完成后平滑淡入，感知上更快、更稳定。
 */
@Composable
fun CoverImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val model = url?.takeIf { it.isNotBlank() }
    if (model == null) {
        CoverPlaceholder(modifier = modifier)
        return
    }
    val req =
        ImageRequest
            .Builder(LocalContext.current)
            .data(model)
            .crossfade(true)
            .build()
    SubcomposeAsyncImage(
        model = req,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        loading = { CoverPlaceholder(modifier = Modifier.fillMaxSize()) },
        error = { CoverPlaceholder(modifier = Modifier.fillMaxSize()) }
    )
}

@Composable
private fun CoverPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier =
        modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Filled.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(28.dp)
        )
    }
}
