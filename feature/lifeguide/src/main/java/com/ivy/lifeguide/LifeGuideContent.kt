package com.ivy.lifeguide

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * fork 增补（2026-09-29）：人生指南模块内容层。
 * 内容来自 GitHub eternity4719/HowToLiveBetter（Unlicense 公有领域），
 * 打包于 assets/lifeguide/ 下：book/ 正篇 34 章、docs/ 附录长文、README.md 使用说明。
 */
sealed class GuideBlock {
    data class Heading(val level: Int, val text: String) : GuideBlock()
    data class Para(val text: String) : GuideBlock()
    data class Bullets(val items: List<String>) : GuideBlock()
    data class Quote(val text: String) : GuideBlock()
    data class Table(val rows: List<List<String>>) : GuideBlock()
    data object Hr : GuideBlock()
}

data class GuideSection(
    val index: Int,                 // 在文件内的序号（用于定位）
    val title: String,              // 建议标题（### 之后的部分）
    val tags: Map<String, String>,  // 成本标签：钱/时间/毅力/收益/口径
    val blocks: List<GuideBlock>,
    val evidence: String? = null,   // 证据等级 A/B/C
) {
    val searchText: String by lazy {
        buildString {
            append(title)
            append('\n')
            evidence?.let { append("证据等级:$it").append('\n') }
            tags.forEach { (k, v) -> append(k).append('=').append(v).append('\n') }
            blocks.forEach { b ->
                when (b) {
                    is GuideBlock.Heading -> append(b.text)
                    is GuideBlock.Para -> append(b.text)
                    is GuideBlock.Bullets -> b.items.forEach { append(it); append('\n') }
                    is GuideBlock.Quote -> append(b.text)
                    is GuideBlock.Table -> b.rows.forEach { r -> append(r.joinToString(" ")); append('\n') }
                    GuideBlock.Hr -> Unit
                }
                append('\n')
            }
        }
    }
}

data class GuideDoc(
    val id: String,                 // "book/01-不要早死"
    val group: String,              // "正篇" | "附录" | "使用说明"
    val fileName: String,
    val title: String,              // h1 标题（去掉 # 和序号点）
    val intro: String,              // 第一个条目之前的引导段落
    val sections: List<GuideSection>,
)

@Singleton
class LifeGuideContent @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val groupTitles = mapOf(
        "book" to "正篇 · 34 章",
        "docs" to "附录 · 长文与工具",
        "root" to "使用说明",
    )

    fun loadAll(): List<GuideDoc> = buildList {
        addAll(loadGroup("root", "lifeguide", rootLevel = true))
        addAll(loadGroup("book", "lifeguide/book"))
        addAll(loadGroup("docs", "lifeguide/docs"))
    }

    private fun loadGroup(group: String, assetPath: String, rootLevel: Boolean = false): List<GuideDoc> {
        val names = context.assets.list(assetPath).orEmpty()
            .filter { it.endsWith(".md") }
            .sorted()
        if (rootLevel) {
            // assets 根目录列表会混入目录，过滤
            return names.mapNotNull { name ->
                parseDoc(
                    id = "$group/${name.removeSuffix(".md")}",
                    group = groupTitles[group] ?: group,
                    fileName = name,
                    text = runCatching {
                        context.assets.open("$assetPath/$name").bufferedReader().readText()
                    }.getOrNull() ?: return@mapNotNull null
                )
            }
        }
        return names.mapNotNull { name ->
            parseDoc(
                id = "$group/${name.removeSuffix(".md")}",
                group = groupTitles[group] ?: group,
                fileName = name,
                text = runCatching {
                    context.assets.open("$assetPath/$name").bufferedReader().readText()
                }.getOrNull() ?: return@mapNotNull null
            )
        }.sortedBy { it.fileName }
    }

    companion object {
        /** 解析单个 md 文件为 GuideDoc。 */
        fun parseDoc(id: String, group: String, fileName: String, text: String): GuideDoc {
            var docTitle = fileName.removeSuffix(".md").replaceFirst('-', '·')
            val sections = mutableListOf<GuideSection>()
            val introLines = mutableListOf<String>()

            var curTitle: String? = null
            var curTags = mutableMapOf<String, String>()
            var curLines = mutableListOf<String>()
            var sectionIndex = 0

            fun closeSection() {
                val t = curTitle ?: return
                // 从要点行提取证据等级（- 证据等级：A）
                val evidence = curLines.firstNotNullOfOrNull { l ->
                    Regex("^[-*]?\\s*证据等级[:：]\\s*([ABC])\\b")
                        .find(l.trim())?.groupValues?.get(1)
                }
                // 证据等级已作为标签展示，正文列表中去重
                val bodyLines = curLines.filterNot {
                    it.trim().removePrefix("-").trim().startsWith("证据等级")
                }
                val blocks = parseBlocks(bodyLines)
                if (blocks.isEmpty() && t.isBlank()) return
                sections += GuideSection(
                    index = sectionIndex++,
                    title = t,
                    tags = curTags.toMap(),
                    blocks = blocks,
                    evidence = evidence
                )
                curTitle = null
                curTags = mutableMapOf()
                curLines = mutableListOf()
            }

            text.lineSequence().forEach { raw ->
                val line = raw.trimEnd()
                when {
                    line.startsWith("# ") -> {
                        docTitle = line.removePrefix("# ").trim()
                    }

                    line.startsWith("### ") || line.startsWith("## ") -> {
                        closeSection()
                        curTitle = line.trimStart('#').trim()
                    }

                    line.startsWith("<!--") && line.contains("成本标签") -> {
                        val inner = line.removePrefix("<!--").removeSuffix("-->").trim()
                        val payload = inner.substringAfter("成本标签:", "").trim()
                        payload.split(Regex("\\s+")).forEach { pair ->
                            val kv = pair.split('=', limit = 2)
                            if (kv.size == 2) curTags[kv[0]] = kv[1]
                        }
                    }

                    line.startsWith("[←") || line.startsWith("[返回") -> {
                        // 目录导航链接，跳过
                    }

                    line.startsWith("---") && line.length <= 6 -> {
                        if (curTitle == null) introLines.add("\u2500\u2500\u2500")
                        else curLines.add("---")
                    }

                    else -> {
                        if (curTitle == null) introLines.add(line) else curLines.add(line)
                    }
                }
            }
            closeSection()

            return GuideDoc(
                id = id,
                group = group,
                fileName = fileName,
                title = docTitle,
                intro = introLines.joinToString("\n").trim(),
                sections = sections
            )
        }

        /** 把段落文本解析为可渲染的块。 */
        fun parseBlocks(lines: List<String>): List<GuideBlock> {
            val blocks = mutableListOf<GuideBlock>()
            val bullets = mutableListOf<String>()
            val table = mutableListOf<List<String>>()
            val quote = mutableListOf<String>()
            var para = mutableListOf<String>()

            fun flushPara() {
                if (para.isNotEmpty()) {
                    blocks += GuideBlock.Para(para.joinToString(" ").trim())
                    para = mutableListOf()
                }
            }

            fun flushBullets() {
                if (bullets.isNotEmpty()) {
                    blocks += GuideBlock.Bullets(bullets.toList())
                    bullets.clear()
                }
            }

            fun flushTable() {
                if (table.isNotEmpty()) {
                    blocks += GuideBlock.Table(table.toList())
                    table.clear()
                }
            }

            fun flushQuote() {
                if (quote.isNotEmpty()) {
                    blocks += GuideBlock.Quote(quote.joinToString("\n").trim())
                    quote.clear()
                }
            }

            fun flushAll() {
                flushPara(); flushBullets(); flushTable(); flushQuote()
            }

            lines.forEach { rawLine ->
                val line = rawLine.trim()
                when {
                    line.isEmpty() -> flushAll()

                    line.startsWith("|") -> {
                        flushPara(); flushBullets(); flushQuote()
                        val cells = line.trim('|').split('|').map { it.trim() }
                        // 跳过表格分隔行 |---|---|
                        if (cells.any { it.isNotBlank() && !Regex("^[\\-: ]+$").matches(it) }) {
                            table += cells
                        }
                    }

                    line.startsWith(">") -> {
                        flushPara(); flushBullets(); flushTable()
                        quote += line.removePrefix(">").trim()
                    }

                    line.startsWith("- ") || line.startsWith("* ") -> {
                        flushPara(); flushTable(); flushQuote()
                        bullets += line.substring(2).trim()
                    }

                    Regex("^\\d+[.、)] ").containsMatchIn(line) -> {
                        flushPara(); flushTable(); flushQuote()
                        bullets += line
                    }

                    line.startsWith("#") -> {
                        flushAll()
                        val level = line.takeWhile { it == '#' }.length
                        blocks += GuideBlock.Heading(level, line.trimStart('#').trim())
                    }

                    else -> {
                        flushBullets(); flushTable(); flushQuote()
                        para += line
                    }
                }
            }
            flushAll()
            return blocks
        }
    }
}
