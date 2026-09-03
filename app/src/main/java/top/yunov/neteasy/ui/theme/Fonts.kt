package top.yunov.neteasy.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import top.yunov.neteasy.R

/**
 * 内置霞鹜文楷 Lite Medium（Lxgw WenKai Lite，v1.522，OFL 开源许可）：
 * 开源中文楷体，用作歌词字体（设置里可选，默认启用）。
 *
 * 选 Medium 而非 Regular：歌词渲染请求 Bold 字重、Regular 偏细且观感单薄，
 * Medium 笔画更实，配合伪粗体合成后更接近其他音乐 App 歌词排版的厚度。
 */
val LxgwWenKaiLiteFamily = FontFamily(
    Font(R.font.lxgw_wenkai_lite_medium)
)