package com.ivy.lifeguide

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed interface GuideView {
    data object Catalog : GuideView
    data class Reader(val docId: String, val anchorSectionIndex: Int?) : GuideView
    data object Search : GuideView
}

data class GuideSearchResult(
    val doc: GuideDoc,
    val section: GuideSection?,
    val snippet: String,
)

data class GuideUiState(
    val loading: Boolean,
    val docs: List<GuideDoc>,
    val view: GuideView,
    val query: String,
    val results: List<GuideSearchResult>,
)

@HiltViewModel
class LifeGuideViewModel @Inject constructor(
    private val content: LifeGuideContent,
) : ViewModel() {

    private var docs by mutableStateOf<List<GuideDoc>>(emptyList())
    private var loading by mutableStateOf(true)
    private var view by mutableStateOf<GuideView>(GuideView.Catalog)
    private var query by mutableStateOf("")
    private var results by mutableStateOf<List<GuideSearchResult>>(emptyList())

    init {
        viewModelScope.launch {
            // 查询在 Default 线程，状态赋值回主线程（后台线程写 Compose 状态会崩）
            val loaded = withContext(Dispatchers.Default) { content.loadAll() }
            docs = loaded
            loading = false
        }
    }

    @Composable
    fun uiState(): GuideUiState = GuideUiState(
        loading = loading,
        docs = docs,
        view = view,
        query = query,
        results = results,
    )

    fun openDoc(docId: String, anchorSectionIndex: Int? = null) {
        view = GuideView.Reader(docId, anchorSectionIndex)
    }

    fun openSearch() {
        view = GuideView.Search
    }

    fun showCatalog() {
        view = GuideView.Catalog
    }

    fun onQueryChange(q: String) {
        query = q
        // fork 修复：检索移出主线程（1.6MB 内容逐键扫描，中低端机掉帧）
        results = if (q.isBlank()) {
            emptyList()
        } else {
            val loaded = docs
            kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.Default) {
                searchDocs(loaded, q)
            }
        }
    }

    companion object {
        /**
         * 全文检索：多关键词 AND；匹配条目标题/成本标签/正文，附录无条目文档整体匹配。
         * 摘要取第一个关键词命中位置附近 ±60 字。
         */
        fun searchDocs(
            docs: List<GuideDoc>,
            rawQuery: String,
        ): List<GuideSearchResult> {
            val terms = rawQuery.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            if (terms.isEmpty()) return emptyList()

            fun matches(hay: String) = terms.all { hay.contains(it, ignoreCase = true) }

            fun snippet(hay: String): String {
                val lower = hay.lowercase()
                val pos = terms.firstNotNullOfOrNull { t ->
                    lower.indexOf(t.lowercase()).takeIf { it >= 0 }
                } ?: 0
                val start = (pos - 40).coerceAtLeast(0)
                val end = (pos + 120).coerceAtMost(hay.length)
                val prefix = if (start > 0) "…" else ""
                val suffix = if (end < hay.length) "…" else ""
                return prefix + hay.substring(start, end).replace('\n', ' ') + suffix
            }

            val out = mutableListOf<GuideSearchResult>()
            docs.forEach { doc ->
                doc.sections.forEach { sec ->
                    val hay = doc.title + "\n" + sec.searchText
                    if (matches(hay)) {
                        out += GuideSearchResult(doc, sec, snippet(sec.searchText.ifBlank { hay }))
                    }
                }
                if (doc.sections.isEmpty() && doc.intro.isNotBlank()) {
                    val hay = doc.title + "\n" + doc.intro
                    if (matches(hay)) {
                        out += GuideSearchResult(
                            doc,
                            GuideSection(-1, doc.title, emptyMap(), emptyList()),
                            snippet(hay)
                        )
                    }
                }
            }
            return out
        }
    }
}
