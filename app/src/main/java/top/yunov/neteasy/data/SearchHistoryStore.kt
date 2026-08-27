package top.yunov.neteasy.data

import android.content.Context

/**
 * 搜索历史：SharedPreferences 持久化，最近搜索的排最前，去重，最多保留 [MAX_SIZE] 条。
 * 搜索页在空态展示历史词引导用户，点某条再次发起搜索。
 */
class SearchHistoryStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): List<String> =
        prefs
            .getString(KEY_KEYWORDS, "")
            .orEmpty()
            .split(SEPARATOR)
            .filter { it.isNotBlank() }

    /** 记录一次搜索：去重后置顶，超出上限裁剪最旧的 */
    fun add(keyword: String) {
        if (keyword.isBlank()) return
        val current = load().filter { it != keyword }.toMutableList()
        current.add(0, keyword)
        while (current.size > MAX_SIZE) current.removeAt(current.lastIndex)
        prefs.edit().putString(KEY_KEYWORDS, current.joinToString(SEPARATOR)).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_KEYWORDS).apply()
    }

    private companion object {
        const val PREFS_NAME = "search_history"
        const val KEY_KEYWORDS = "keywords"
        const val SEPARATOR = "\u0001"
        const val MAX_SIZE = 20
    }
}
