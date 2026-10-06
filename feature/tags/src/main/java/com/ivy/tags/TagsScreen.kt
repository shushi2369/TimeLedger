package com.ivy.tags

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.legacy.arkui.ArkBilingualTitle
import com.ivy.legacy.arkui.ArkStaggeredIn
import com.ivy.navigation.TagsScreen
import com.ivy.navigation.TransactionsScreen
import com.ivy.navigation.navigation
import com.ivy.wallet.ui.theme.components.IvyButton
import com.ivy.wallet.ui.theme.components.IvyIcon
import com.ivy.wallet.ui.theme.components.IvyToolbar
import com.ivy.wallet.ui.theme.components.BackButtonType
import com.ivy.legacy.ui.component.tags.AddOrEditTagModal
import java.util.UUID

/** 标签管理页：列表 + 交易数 + 新建/重命名/删除（AddOrEditTagModal）+ 点名称反查交易。 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun BoxWithConstraintsScope.TagsScreenImpl(screen: TagsScreen) {
    val viewModel: TagsViewModel = viewModel()
    val state by viewModel.state.collectAsState()
    val nav = navigation()

    LaunchedEffect(Unit) {
        viewModel.start()
    }

    var modalVisible by remember { mutableStateOf(false) }
    var editTag by remember { mutableStateOf<com.ivy.data.model.Tag?>(null) }
    var modalId by remember { mutableStateOf(UUID.randomUUID()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
    ) {
        IvyToolbar(
            backButtonType = BackButtonType.CLOSE,
            onBack = { nav.back() },
        ) {
            ArkBilingualTitle(
                cn = "标签",
                en = "TAGS",
                modifier = Modifier.padding(start = 16.dp)
            )
        }

        Spacer(Modifier.height(8.dp))

        LazyColumn(Modifier.weight(1f)) {
            items(state.tags, key = { it.tag.id.value }) { item ->
                ArkStaggeredIn(index = state.tags.indexOf(item).coerceAtMost(12)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                // 反查：跳交易列表按标签过滤
                                nav.navigateTo(
                                    TransactionsScreen(tagId = item.tag.id.value)
                                )
                            }
                            .padding(horizontal = 24.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val dotColor = item.tag.color.value
                            .takeIf { it != 0 }
                            ?.let(::Color)
                            ?: UI.colors.medium
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "#${item.tag.name.value}",
                            style = UI.typo.b2.style(fontWeight = FontWeight.Bold),
                            modifier = Modifier.weight(1f),
                            maxLines = 1
                        )
                        Text(
                            text = "${item.usageCount} 笔",
                            style = UI.typo.c.style(color = UI.colors.gray)
                        )
                        Spacer(Modifier.width(16.dp))
                        IvyIcon(
                            icon = com.ivy.ui.R.drawable.ic_edit,
                            tint = UI.colors.gray,
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .clickable {
                                    editTag = item.tag
                                    modalId = UUID.randomUUID()
                                    modalVisible = true
                                }
                        )
                    }
                }
            }

            if (state.tags.isEmpty() && !state.loading) {
                item {
                    Text(
                        text = "还没有标签，记账时可以给交易打标签",
                        style = UI.typo.b2.style(color = UI.colors.gray),
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp)
                    )
                }
            }

            item { Spacer(Modifier.height(120.dp)) }
        }

        IvyButton(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            iconStart = com.ivy.ui.R.drawable.ic_plus,
            text = "新建标签"
        ) {
            editTag = null
            modalId = UUID.randomUUID()
            modalVisible = true
        }
    }

    AddOrEditTagModal(
        id = com.ivy.data.model.TagId(modalId),
        visible = modalVisible,
        initialTag = editTag,
        onTagAdd = viewModel::addTag,
        onTagEdit = viewModel::updateTag,
        onTagDelete = viewModel::deleteTag,
        onDismiss = { modalVisible = false }
    )
}
