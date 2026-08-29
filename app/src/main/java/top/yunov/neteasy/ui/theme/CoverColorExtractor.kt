package top.yunov.neteasy.ui.theme

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yunov.neteasy.data.model.thumbnail

/**
 * 从歌曲封面 URL 提取一个「种子色」，喂给 material-kolor 生成整套 M3 动态配色——
 * 参考 SPlayer 的「封面取色」思路：不是简单取封面平均色（一张封面平均下来大概率
 * 是灰扑扑的一坨），而是找封面里那个「有代表性、够鲜艳」的色块。
 *
 * 用 AndroidX 官方 Palette 库（跟 Android 系统提取专辑封面主题色是同一套东西，
 * 稳定、免费、没有额外依赖风险），按 Vibrant → LightVibrant → DarkVibrant → Muted
 * → Dominant 的优先级挑，优先要「鲜艳」的而不是「面积最大」的（面积最大的往往是
 * 背景色，做主题色不好看）。挑出来的颜色还要过一道纯度/明度兜底：太灰/太黑/太白
 * 的直接放弃返回 null，让调用方回退到默认配色，避免生成出一坨看不出效果的灰主题。
 */
suspend fun extractCoverSeedColor(context: Context, coverUrl: String?): Color? {
    if (coverUrl.isNullOrBlank()) return null
    val bitmap = loadSmallBitmap(context, coverUrl) ?: return null
    // Palette 是同步阻塞的像素分析（哪怕图很小也是 CPU 计算），调用方 appScope 默认在
    // Main 线程，这里显式切到 Default 线程池跑，不占用主线程
    return withContext(Dispatchers.Default) {
        val palette =
            try {
                Palette.from(bitmap).maximumColorCount(24).generate()
            } catch (_: Exception) {
                return@withContext null
            }
        val swatch =
            palette.vibrantSwatch
                ?: palette.lightVibrantSwatch
                ?: palette.darkVibrantSwatch
                ?: palette.mutedSwatch
                ?: palette.dominantSwatch
                ?: return@withContext null
        val color = Color(swatch.rgb)
        if (color.isTooDull()) null else color
    }
}

private suspend fun loadSmallBitmap(context: Context, url: String): Bitmap? =
    try {
        val request =
            ImageRequest.Builder(context)
                .data(url.thumbnail(96))
                // Palette 要直接读像素，硬件位图（GPU 专用内存）读不了，取色场景必须关掉
                .allowHardware(false)
                .build()
        context.imageLoader.execute(request).drawable?.toBitmap()
    } catch (_: Exception) {
        null
    }

/** 太暗 / 太亮 / 太灰的颜色不适合当种子色——生成出来的主题要么看不清文字，要么跟没换一样 */
private fun Color.isTooDull(): Boolean {
    val max = maxOf(red, green, blue)
    val min = minOf(red, green, blue)
    val saturation = if (max == 0f) 0f else (max - min) / max
    return saturation < 0.12f || max < 0.08f || min > 0.94f
}
