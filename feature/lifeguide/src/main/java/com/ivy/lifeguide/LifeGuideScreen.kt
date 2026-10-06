package com.ivy.lifeguide

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.em
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.ui.R

private val LinkBlue = Color(0xFF2A6DB0)

// 排版常量（借鉴 Material 3 与开源阅读器 ReadYou / Anx Reader 的阅读页规范）
private val ReaderHorizontalPadding = 20.dp
private val ReaderMaxWidth = 640.dp
private val BodyLineHeight = 26.sp
private val BodyFontSize = 15.sp

@Composable
fun LifeGuideTab() {
    val viewModel: LifeGuideViewModel = viewModel()
    val state = viewModel.uiState()
    val view = state.view

    BackHandler(enabled = view !is GuideView.Catalog) {
        viewModel.showCatalog()
    }

    when (view) {
        GuideView.Catalog -> CatalogView(state = state, vm = viewModel)
        is GuideView.Reader -> ReaderView(state = state, view = view, vm = viewModel)
        GuideView.Search -> SearchView(state = state, vm = viewModel)
    }
}

// region 公共小组件

/** 有语义的成本标签按值着色（收益=大 绿 / 钱=大 红 / 毅力=是 红 …），其余中性。 */
@Composable
private fun chipColors(key: String, value: String): Pair<Color, Color> = when {
    key == "收益" && value == "大" -> com.ivy.wallet.ui.theme.Green.copy(alpha = 0.14f) to
            com.ivy.wallet.ui.theme.Green
    key == "收益" && value == "中" -> com.ivy.wallet.ui.theme.Orange.copy(alpha = 0.14f) to
            com.ivy.wallet.ui.theme.Orange
    key == "钱" && value == "大" -> com.ivy.wallet.ui.theme.Red.copy(alpha = 0.14f) to
            com.ivy.wallet.ui.theme.Red
    key == "毅力" && value == "是" -> com.ivy.wallet.ui.theme.Red.copy(alpha = 0.14f) to
            com.ivy.wallet.ui.theme.Red
    key == "证据等级" && (value == "A" || value == "B") -> LinkBlue.copy(alpha = 0.14f) to LinkBlue
    else -> UI.colors.medium to UI.colors.pureInverse
}

@Composable
private fun GuideChip(text: String, bg: Color, content: Color) {
    Text(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        text = text,
        style = UI.typo.c.style(color = content),
        maxLines = 1
    )
}

@Composable
private fun GuideTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                com.ivy.wallet.ui.theme.components.IvyIcon(
                    icon = R.drawable.ic_back,
                    tint = UI.colors.pureInverse
                )
            }
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = UI.typo.b1.style(fontWeight = FontWeight.ExtraBold)
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = UI.typo.c.style(color = UI.colors.gray),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        action?.invoke()
    }
}

@Composable
private fun TagChipsRow(section: GuideSection) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        section.evidence?.let { ev ->
            val (bg, fg) = chipColors("证据等级", ev)
            GuideChip(text = "证据 $ev", bg = bg, content = fg)
        }
        section.tags.forEach { (k, v) ->
            val (bg, fg) = chipColors(k, v)
            GuideChip(text = "$k=$v", bg = bg, content = fg)
        }
    }
}

/** 检索关键词高亮 */
private fun highlightAnnotated(text: String, terms: List<String>): AnnotatedString =
    buildAnnotatedString {
        append(text)
        val lower = text.lowercase()
        terms.mapNotNull { t ->
            val i = lower.indexOf(t.lowercase())
            i.takeIf { it >= 0 }?.let { it until (it + t.length) }
        }.forEach { range ->
            addStyle(
                SpanStyle(color = com.ivy.wallet.ui.theme.Orange, fontWeight = FontWeight.Bold),
                range.first,
                range.last + 1
            )
        }
    }

/** 阅读内容统一限宽居中（平板友好，手机不受影响） */
@Composable
private fun ReaderContentColumn(
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(Modifier.widthIn(max = ReaderMaxWidth)) { content() }
    }
}
// endregion

// region 目录
@Composable
private fun CatalogView(state: GuideUiState, vm: LifeGuideViewModel) {
    val docs = state.docs
    val total = docs.sumOf { it.sections.size }
    val aCount = docs.sumOf { d -> d.sections.count { it.evidence == "A" } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(UI.colors.pure)
            .statusBarsPadding()
    ) {
        // Hero 区
        ReaderContentColumn {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .width(4.dp)
                            .height(24.dp)
                            .background(UI.colors.primary)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "人生指南",
                        style = UI.typo.b1.style(fontWeight = FontWeight.ExtraBold)
                            .copy(fontSize = 30.sp, lineHeight = 36.sp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "FIELD MANUAL",
                        style = UI.typo.c.style(
                            fontWeight = FontWeight.Bold,
                            color = UI.colors.gray
                        ).copy(fontSize = 12.sp, letterSpacing = 0.08.em)
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "按性价比排序的循证生活建议 · 来自 HowToLiveBetter（公有领域）",
                    style = UI.typo.c.style(color = UI.colors.gray)
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GuideChip(text = "$total 条建议", bg = UI.colors.medium, content = UI.colors.pureInverse)
                    GuideChip(text = "正篇 34 章", bg = UI.colors.medium, content = UI.colors.pureInverse)
                    GuideChip(
                        text = "证据 A 级 $aCount 条",
                        bg = LinkBlue.copy(alpha = 0.14f),
                        content = LinkBlue
                    )
                }

                Spacer(Modifier.height(12.dp))

                // 检索入口（改版时曾丢失，加回）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(UI.colors.medium)
                        .clickable { vm.openSearch() }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    com.ivy.wallet.ui.theme.components.IvyIcon(
                        icon = R.drawable.ic_search,
                        tint = UI.colors.gray
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "检索 $total 条建议…",
                        style = UI.typo.b2.style(color = UI.colors.gray)
                    )
                }
            }
        }

        GuideTopBarDivider()

        if (state.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return
        }

        LazyColumn(Modifier.fillMaxSize()) {
            val groups = state.docs.groupBy { it.group }
            listOf("使用说明", "正篇 · 34 章", "附录 · 长文与工具").forEach { group ->
                val docsInGroup = groups[group].orEmpty()
                if (docsInGroup.isEmpty()) return@forEach

                item(key = "hdr-$group") {
                    Text(
                        modifier = Modifier.padding(start = 24.dp, top = 22.dp, bottom = 8.dp),
                        text = group,
                        style = UI.typo.c.style(
                            color = UI.colors.gray,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
                itemsIndexed(docsInGroup, key = { _, d -> d.id }) { _, doc ->
                    // fork 修复：附录文件名不含分隔符，整名被塞进 46dp 序号列换行撑爆行高；仅纯数字才显示
                    val rawNumber = doc.fileName.removeSuffix(".md").substringBefore('-')
                    val number = rawNumber.takeIf { it.all { c -> c.isDigit() } && it.isNotEmpty() } ?: "·"
                    val aInDoc = doc.sections.count { it.evidence == "A" }
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 20.dp, vertical = 5.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(UI.colors.medium)
                            .clickable { vm.openDoc(doc.id) }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = number,
                            style = UI.typo.b1.style(
                                color = UI.colors.gray,
                                fontWeight = FontWeight.ExtraBold
                            ).copy(fontSize = 20.sp),
                            modifier = Modifier.width(46.dp)
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = doc.title,
                                style = UI.typo.b2.style(fontWeight = FontWeight.Bold),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = if (doc.sections.isEmpty()) {
                                    "参考文档"
                                } else {
                                    "${doc.sections.size} 条 · 证据 A 级 $aInDoc 条"
                                },
                                style = UI.typo.c.style(color = UI.colors.gray)
                            )
                        }
                        Text("›", style = UI.typo.b1.style(color = UI.colors.gray))
                    }
                }
            }
            item { Spacer(Modifier.height(48.dp)) }
        }
    }
}

@Composable
private fun GuideTopBarDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(1.dp)
            .background(UI.colors.medium)
    )
}
// endregion

// region 阅读
private sealed interface RenderItem {
    val key: String

    data class Intro(val text: String) : RenderItem {
        override val key get() = "intro"
    }

    data class Head(val section: GuideSection) : RenderItem {
        override val key get() = "s${section.index}-h"
    }

    data class Tags(val section: GuideSection) : RenderItem {
        override val key get() = "s${section.index}-t"
    }

    data class Block(val sectionIndex: Int, val idx: Int, val block: GuideBlock) : RenderItem {
        override val key get() = "s$sectionIndex-b$idx"
    }
}

@Composable
private fun ReaderView(
    state: GuideUiState,
    view: GuideView.Reader,
    vm: LifeGuideViewModel,
) {
    val doc = state.docs.firstOrNull { it.id == view.docId } ?: return
    val listState = rememberLazyListState()
    var showOutline by remember { mutableStateOf(false) }

    val renderItems = remember(doc) {
        buildList {
            if (doc.intro.isNotBlank()) add(RenderItem.Intro(doc.intro))
            doc.sections.forEach { sec ->
                add(RenderItem.Head(sec))
                if (sec.evidence != null || sec.tags.isNotEmpty()) add(RenderItem.Tags(sec))
                sec.blocks.forEachIndexed { bi, block ->
                    add(RenderItem.Block(sec.index, bi, block))
                }
            }
        }
    }

    // 滚动位置反查当前条目（本章目录里高亮）
    val currentSectionIndex by remember(renderItems) {
        derivedStateOf {
            val fi = listState.firstVisibleItemIndex
            var cur: Int? = null
            for (i in 0..fi.coerceAtMost(renderItems.lastIndex)) {
                val item = renderItems[i]
                if (item is RenderItem.Head) cur = item.section.index
            }
            cur
        }
    }

    LaunchedEffect(view.anchorSectionIndex, doc.id) {
        val anchor = view.anchorSectionIndex ?: return@LaunchedEffect
        val idx = renderItems.indexOfFirst {
            it is RenderItem.Head && it.section.index == anchor
        }
        if (idx >= 0) listState.scrollToItem(idx)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(UI.colors.pure)
            .statusBarsPadding()
    ) {
        GuideTopBar(
            title = doc.title,
            subtitle = "${doc.sections.size} 条建议",
            onBack = { vm.showCatalog() },
            action = {
                Text(
                    modifier = Modifier.clickable { showOutline = !showOutline },
                    text = if (showOutline) "收起目录" else "本章目录",
                    style = UI.typo.c.style(
                        color = UI.colors.primary,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        )

        GuideTopBarDivider()

        if (showOutline) {
            ReaderContentColumn {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(UI.colors.medium)
                        .heightIn(max = 300.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    doc.sections.forEach { sec ->
                        val isCurrent = currentSectionIndex == sec.index
                        Text(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isCurrent) {
                                        com.ivy.wallet.ui.theme.Orange.copy(alpha = 0.12f)
                                    } else {
                                        Color.Transparent
                                    }
                                )
                                .clickable {
                                    showOutline = false
                                    vm.openDoc(doc.id, anchorSectionIndex = sec.index)
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            text = sec.title,
                            style = UI.typo.b2.style(
                                color = if (isCurrent) {
                                    com.ivy.wallet.ui.theme.Orange
                                } else {
                                    UI.colors.pureInverse
                                },
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(renderItems, key = { _, it -> it.key }) { _, item ->
                ReaderContentColumn {
                    when (item) {
                        is RenderItem.Intro -> {
                            if (item.text.isNotBlank()) {
                                Text(
                                    modifier = Modifier.padding(
                                        horizontal = ReaderHorizontalPadding,
                                        vertical = 10.dp
                                    ),
                                    text = guideAnnotated(item.text),
                                    style = UI.typo.b2.style(color = UI.colors.pureInverse)
                                        .copy(fontSize = BodyFontSize, lineHeight = BodyLineHeight)
                                )
                            }
                        }

                        is RenderItem.Head -> {
                            Row(
                                modifier = Modifier.padding(
                                    start = ReaderHorizontalPadding,
                                    end = ReaderHorizontalPadding,
                                    top = 26.dp,
                                    bottom = 10.dp
                                ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier
                                        .width(4.dp)
                                        .height(20.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(com.ivy.wallet.ui.theme.Orange)
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = item.section.title,
                                    style = UI.typo.b2.style(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = UI.colors.pureInverse
                                    ).copy(fontSize = 18.sp, lineHeight = 26.sp)
                                )
                            }
                        }

                        is RenderItem.Tags -> {
                            Row(
                                modifier = Modifier.padding(
                                    start = ReaderHorizontalPadding,
                                    end = ReaderHorizontalPadding,
                                    bottom = 10.dp
                                )
                            ) {
                                TagChipsRow(section = item.section)
                            }
                        }

                        is RenderItem.Block -> {
                            Box(
                                Modifier.padding(
                                    horizontal = ReaderHorizontalPadding,
                                    vertical = 5.dp
                                )
                            ) {
                                GuideBlockView(block = item.block)
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(72.dp)) }
        }
    }
}
// endregion

// region 检索
@Composable
private fun SearchView(state: GuideUiState, vm: LifeGuideViewModel) {
    val terms = state.query.trim().split(Regex("\\s+")).filter { it.isNotBlank() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(UI.colors.pure)
            .statusBarsPadding()
    ) {
        GuideTopBar(title = "检索人生指南", onBack = { vm.showCatalog() })

        ReaderContentColumn {
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::onQueryChange,
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = UI.colors.medium,
                    focusedContainerColor = UI.colors.medium,
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = com.ivy.wallet.ui.theme.Ivy
                ),
                placeholder = {
                    Text("关键词，如：头盔、体检、应急、社保", style = UI.typo.b2)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            )
        }

        Spacer(Modifier.height(10.dp))

        when {
            state.query.isBlank() -> {
                ReaderContentColumn {
                    Text(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                        text = "在全部建议里检索：标题、成本标签和正文都会参与匹配，多关键词用空格隔开。",
                        style = UI.typo.b2.style(color = UI.colors.gray)
                            .copy(lineHeight = 22.sp)
                    )
                }
            }

            state.results.isEmpty() -> {
                ReaderContentColumn {
                    Text(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                        text = "没有匹配的建议，换个词试试。",
                        style = UI.typo.b2.style(color = UI.colors.gray)
                    )
                }
            }

            else -> {
                ReaderContentColumn {
                    Text(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                        text = "${state.results.size} 条结果",
                        style = UI.typo.c.style(color = UI.colors.gray)
                    )
                }
            }
        }

        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(
                state.results,
                key = { _, r -> "${r.doc.id}#${r.section?.index}" }
            ) { _, result ->
                ReaderContentColumn {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 5.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(UI.colors.medium)
                            .clickable {
                                vm.openDoc(
                                    docId = result.doc.id,
                                    anchorSectionIndex = result.section?.index?.takeIf { it >= 0 }
                                )
                            }
                            .padding(14.dp)
                    ) {
                        Text(
                            text = highlightAnnotated(result.doc.title, terms),
                            style = UI.typo.c.style(color = UI.colors.gray)
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = highlightAnnotated(
                                result.section?.title ?: result.doc.title,
                                terms
                            ),
                            style = UI.typo.b2.style(fontWeight = FontWeight.Bold)
                        )
                        if (result.section?.evidence != null || result.section?.tags?.isNotEmpty() == true) {
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                result.section.evidence?.let { ev ->
                                    val (bg, fg) = chipColors("证据等级", ev)
                                    GuideChip(text = "证据 $ev", bg = bg, content = fg)
                                }
                                result.section.tags.forEach { (k, v) ->
                                    val (bg, fg) = chipColors(k, v)
                                    GuideChip(text = "$k=$v", bg = bg, content = fg)
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = highlightAnnotated(result.snippet, terms),
                            style = UI.typo.c.style(color = UI.colors.pureInverse)
                                .copy(lineHeight = 20.sp)
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(48.dp)) }
        }
    }
}
// endregion

// region 块渲染
@Composable
private fun GuideBlockView(block: GuideBlock) {
    when (block) {
        is GuideBlock.Heading -> Text(
            text = guideAnnotated(block.text),
            style = UI.typo.b2.style(
                fontWeight = FontWeight.Bold,
                color = UI.colors.pureInverse
            ).copy(fontSize = 15.sp)
        )

        is GuideBlock.Para -> Text(
            text = guideAnnotated(block.text),
            style = UI.typo.b2.style(color = UI.colors.pureInverse)
                .copy(fontSize = BodyFontSize, lineHeight = BodyLineHeight)
        )

        is GuideBlock.Bullets -> Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            block.items.forEach { item ->
                val isSource = item.startsWith("来源：") || item.startsWith("来源:")
                Row {
                    if (!isSource) {
                        Text(
                            text = "•",
                            style = UI.typo.b2.style(color = UI.colors.gray),
                            modifier = Modifier.width(14.dp)
                        )
                    } else {
                        Spacer(Modifier.width(14.dp))
                    }
                    Text(
                        text = guideAnnotated(item),
                        style = UI.typo.b2.style(
                            color = if (isSource) UI.colors.gray else UI.colors.pureInverse
                        ).copy(
                            fontSize = if (isSource) 13.sp else BodyFontSize,
                            lineHeight = if (isSource) 19.sp else BodyLineHeight
                        )
                    )
                }
            }
        }

        is GuideBlock.Quote -> Row {
            Box(
                Modifier
                    .width(3.dp)
                    .height((block.text.split('\n').size * 24).coerceAtLeast(22).dp)
                    .background(UI.colors.gray)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = guideAnnotated(block.text),
                style = UI.typo.b2.style(color = UI.colors.gray)
                    .copy(lineHeight = BodyLineHeight)
            )
        }

        is GuideBlock.Table -> Column(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .clip(RoundedCornerShape(10.dp))
                .background(UI.colors.medium)
        ) {
            block.rows.forEachIndexed { rowIndex, row ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(if (rowIndex % 2 == 1) UI.colors.pure.copy(alpha = 0.45f) else Color.Transparent)
                ) {
                    row.forEach { cell ->
                        Text(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp, vertical = 7.dp),
                            text = cell,
                            style = UI.typo.c.style(
                                color = UI.colors.pureInverse,
                                fontWeight = if (rowIndex == 0) FontWeight.Bold else FontWeight.Normal
                            )
                        )
                    }
                }
            }
        }

        GuideBlock.Hr -> Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(UI.colors.medium)
        )
    }
}

/** 行内 markdown：**加粗**、[文字](链接)、<链接>。 */
private fun guideAnnotated(text: String): AnnotatedString = buildAnnotatedString {
    val pattern = Regex(
        "\\*\\*(.+?)\\*\\*|\\[([^\\]]+)]\\(([^)]+)\\)|<((?:https?:)?//[^>\\s]+)>"
    )
    var last = 0
    pattern.findAll(text).forEach { m ->
        append(text.substring(last, m.range.first))
        when {
            m.groupValues[1].isNotEmpty() -> withStyle(
                SpanStyle(fontWeight = FontWeight.Bold)
            ) { append(m.groupValues[1]) }

            m.groupValues[2].isNotEmpty() -> withStyle(
                SpanStyle(color = LinkBlue)
            ) { append(m.groupValues[2]) }

            else -> withStyle(
                SpanStyle(color = LinkBlue, fontSize = 12.sp)
            ) { append(m.groupValues[4]) }
        }
        last = m.range.last + 1
    }
    append(text.substring(last))
}
// endregion
