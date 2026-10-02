package com.ivy.reports

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ivy.base.legacy.Theme
import com.ivy.base.legacy.stringRes
import com.ivy.base.model.TransactionType
import com.ivy.data.model.Category
import com.ivy.data.model.CategoryId
import com.ivy.data.model.primitive.ColorInt
import com.ivy.data.model.primitive.IconAsset
import com.ivy.data.model.primitive.NotBlankTrimmedString
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.legacy.IvyWalletPreview
import com.ivy.legacy.data.AppBaseData
import com.ivy.legacy.data.LegacyDueSection
import com.ivy.legacy.datamodel.Account
import com.ivy.legacy.ui.component.IncomeExpensesCards
import com.ivy.legacy.ui.component.transaction.TransactionsDividerLine
import com.ivy.legacy.ui.component.transaction.transactions
import com.ivy.legacy.utils.clickableNoIndication
import com.ivy.legacy.utils.rememberInteractionSource
import com.ivy.navigation.PieChartStatisticScreen
import com.ivy.navigation.ReportScreen
import com.ivy.navigation.navigation
import com.ivy.ui.R
import com.ivy.ui.rememberScrollPositionListState
import com.ivy.wallet.domain.pure.data.IncomeExpensePair
import com.ivy.wallet.ui.theme.Gray
import com.ivy.wallet.ui.theme.Green
import com.ivy.wallet.ui.theme.GreenDark
import com.ivy.wallet.ui.theme.GreenLight
import com.ivy.wallet.ui.theme.IvyDark
import com.ivy.wallet.ui.theme.Orange
import com.ivy.wallet.ui.theme.Purple1Dark
import com.ivy.wallet.ui.theme.Red3Light
import com.ivy.wallet.ui.theme.components.BackButtonType
import com.ivy.wallet.ui.theme.components.BalanceRow
import com.ivy.wallet.ui.theme.components.CircleButtonFilled
import com.ivy.wallet.ui.theme.components.IvyButton
import com.ivy.wallet.ui.theme.components.IvyCheckboxWithText
import com.ivy.wallet.ui.theme.components.IvyIcon
import com.ivy.wallet.ui.theme.components.IvyOutlinedButton
import com.ivy.wallet.ui.theme.components.IvyToolbar
import com.ivy.wallet.ui.theme.pureBlur
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import java.util.UUID

@ExperimentalFoundationApi
@Composable
fun BoxWithConstraintsScope.ReportScreen(
    screen: ReportScreen,
    // fork 增补：作为底部 Tab 嵌入时不显示返回按钮
    showToolbarBackButton: Boolean = true,
) {
    val viewModel: ReportViewModel = viewModel()
    val state = viewModel.uiState()

    UI(
        state = state,
        onEventHandler = viewModel::onEvent,
        showToolbarBackButton = showToolbarBackButton,
    )
}

@ExperimentalFoundationApi
@Composable
private fun BoxWithConstraintsScope.UI(
    state: ReportScreenState = ReportScreenState(),
    onEventHandler: (ReportScreenEvent) -> Unit = {},
    showToolbarBackButton: Boolean = true,
) {
    val legacyTransactions = state.transactions
    val nav = navigation()
    val context = LocalContext.current

    val listState = rememberScrollPositionListState(key = "reports")

    if (state.loading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(1000f)
                .background(pureBlur())
                .clickableNoIndication(rememberInteractionSource()) {
                    // consume clicks
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.generating_report),
                style = UI.typo.b1.style(
                    fontWeight = FontWeight.ExtraBold,
                    color = Orange
                )
            )
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding(),
        state = listState
    ) {
        stickyHeader {
            Toolbar(
                showBackButton = showToolbarBackButton,
                onExport = {
                    onEventHandler.invoke(ReportScreenEvent.OnExport(context = context))
                },
                onFilter = {
                    onEventHandler.invoke(
                        ReportScreenEvent.OnFilterOverlayVisible(
                            filterOverlayVisible = true
                        )
                    )
                }
            )
        }

        item {
            var periodPickerVisible by remember { mutableStateOf(false) }

            com.ivy.legacy.arkui.ArkBilingualTitle(
                cn = stringResource(R.string.reports),
                en = "REPORTS",
                modifier = Modifier.padding(start = 32.dp)
            )

            Spacer(Modifier.height(8.dp))

            // 微信式期间切换条：◀ 2026年10月 ▶（点文字弹月/年选择）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "◀",
                    style = UI.typo.b2.style(
                        fontWeight = FontWeight.Bold,
                        color = UI.colors.pureInverse.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier
                        .clip(UI.shapes.rFull)
                        .clickable { onEventHandler.invoke(ReportScreenEvent.OnPeriodPrevious(state.yearMode)) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )

                Text(
                    text = if (state.yearMode) "${state.selectedYear}年"
                    else "${state.selectedYear}年${state.selectedMonth}月",
                    style = UI.typo.b2.style(
                        fontWeight = FontWeight.ExtraBold,
                        color = UI.colors.primary
                    ),
                    modifier = Modifier
                        .clip(UI.shapes.rFull)
                        .clickable { periodPickerVisible = true }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )

                Text(
                    text = "▶",
                    style = UI.typo.b2.style(
                        fontWeight = FontWeight.Bold,
                        color = UI.colors.pureInverse.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier
                        .clip(UI.shapes.rFull)
                        .clickable { onEventHandler.invoke(ReportScreenEvent.OnPeriodNext(state.yearMode)) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )

                Spacer(Modifier.weight(1f))
            }

            // 月/年选择对话框
            if (periodPickerVisible) {
                val currentYear = state.selectedYear
                AlertDialog(
                    onDismissRequest = { periodPickerVisible = false },
                    title = { Text("查看期间") },
                    text = {
                        Column {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                androidx.compose.material3.FilterChip(
                                    selected = !state.yearMode,
                                    onClick = {
                                        onEventHandler.invoke(
                                            ReportScreenEvent.OnPeriodMonthPicked(
                                                currentYear, state.selectedMonth
                                            )
                                        )
                                    },
                                    label = { Text("按月") }
                                )
                                androidx.compose.material3.FilterChip(
                                    selected = state.yearMode,
                                    onClick = {
                                        onEventHandler.invoke(ReportScreenEvent.OnPeriodYearPicked(currentYear))
                                    },
                                    label = { Text("按年") }
                                )
                            }

                            Spacer(Modifier.height(12.dp))

                            // 年份切换
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "◀",
                                    modifier = Modifier
                                        .clickable {
                                            onEventHandler.invoke(ReportScreenEvent.OnPeriodYearPicked(currentYear - 1))
                                        }
                                        .padding(8.dp)
                                )
                                Text(
                                    text = "$currentYear 年",
                                    style = UI.typo.b2.style(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                                Text(
                                    text = "▶",
                                    modifier = Modifier
                                        .clickable {
                                            onEventHandler.invoke(ReportScreenEvent.OnPeriodYearPicked(currentYear + 1))
                                        }
                                        .padding(8.dp)
                                )
                            }

                            // 按月时：12 个月格子
                            if (!state.yearMode) {
                                Spacer(Modifier.height(8.dp))
                                for (row in 0..2) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceEvenly
                                    ) {
                                        for (col in 0..3) {
                                            val m = row * 4 + col + 1
                                            if (m <= 12) {
                                                val selected = state.selectedMonth == m &&
                                                        state.selectedYear == currentYear
                                                Text(
                                                    text = "${m}月",
                                                    style = UI.typo.b2.style(
                                                        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Normal,
                                                        color = if (selected) Color.White else UI.colors.pureInverse
                                                    ),
                                                    modifier = Modifier
                                                        .clip(UI.shapes.rFull)
                                                        .background(
                                                            if (selected) UI.colors.primary else UI.colors.medium
                                                        )
                                                        .clickable {
                                                            onEventHandler.invoke(
                                                                ReportScreenEvent.OnPeriodMonthPicked(currentYear, m)
                                                            )
                                                            periodPickerVisible = false
                                                        }
                                                        .padding(horizontal = 14.dp, vertical = 8.dp)
                                                )
                                            } else {
                                                Spacer(Modifier.width(48.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        androidx.compose.material3.TextButton(
                            onClick = { periodPickerVisible = false }
                        ) { Text("完成") }
                    }
                )
            }

            Spacer(Modifier.height(8.dp))

            BalanceRow(
                modifier = Modifier
                    .padding(start = 32.dp),
                textColor = UI.colors.pureInverse,
                currency = state.baseCurrency,
                balance = state.balance,
                balanceAmountPrefix = when {
                    state.balance > 0 -> "+"
                    else -> null
                }
            )

            Spacer(Modifier.height(20.dp))

            IncomeExpensesCards(
                history = state.history,
                currency = state.baseCurrency,
                income = state.income,
                expenses = state.expenses,
                hasAddButtons = false,
                itemColor = UI.colors.pure,
                incomeHeaderCardClicked = {
                    if (state.transactions.isNotEmpty()) {
                        nav.navigateTo(
                            PieChartStatisticScreen(
                                type = TransactionType.INCOME,
                                transactions = legacyTransactions.toImmutableList(),
                                accountList = state.accountIdFilters,
                                treatTransfersAsIncomeExpense = state.treatTransfersAsIncExp
                            )
                        )
                    }
                },
                expenseHeaderCardClicked = {
                    if (state.transactions.isNotEmpty()) {
                        nav.navigateTo(
                            PieChartStatisticScreen(
                                type = TransactionType.EXPENSE,
                                transactions = legacyTransactions.toImmutableList(),
                                accountList = state.accountIdFilters,
                                treatTransfersAsIncomeExpense = state.treatTransfersAsIncExp
                            )
                        )
                    }
                }
            )

            if (state.showTransfersAsIncExpCheckbox) {
                IvyCheckboxWithText(
                    modifier = Modifier
                        .padding(16.dp),
                    text = stringResource(R.string.transfers_as_income_expense),
                    checked = state.treatTransfersAsIncExp
                ) {
                    onEventHandler.invoke(
                        ReportScreenEvent.OnTreatTransfersAsIncomeExpense(
                            transfersAsIncomeExpense = it
                        )
                    )
                }
            } else {
                Spacer(Modifier.height(32.dp))
            }

            TransactionsDividerLine(
                paddingHorizontal = 0.dp
            )

            Spacer(Modifier.height(4.dp))
        }

        item {
            ReportsBarChart(history = state.history, monthlyGranularity = state.yearMode)
        }

        if (state.filter != null) {
            transactions(
                baseData = AppBaseData(
                    baseCurrency = state.baseCurrency,
                    categories = state.categories,
                    accounts = state.accounts,
                ),

                upcoming = LegacyDueSection(
                    trns = state.upcomingTransactions,
                    stats = IncomeExpensePair(
                        income = state.upcomingIncome.toBigDecimal(),
                        expense = state.upcomingExpenses.toBigDecimal()
                    ),
                    expanded = state.upcomingExpanded
                ),

                setUpcomingExpanded = {
                    onEventHandler.invoke(ReportScreenEvent.OnUpcomingExpanded(upcomingExpanded = it))
                },

                overdue = LegacyDueSection(
                    trns = state.overdueTransactions,
                    stats = IncomeExpensePair(
                        income = state.overdueIncome.toBigDecimal(),
                        expense = state.overdueExpenses.toBigDecimal()
                    ),
                    expanded = state.overdueExpanded
                ),
                setOverdueExpanded = {
                    onEventHandler.invoke(ReportScreenEvent.OnOverdueExpanded(overdueExpanded = it))
                },

                history = state.history,
                lastItemSpacer = 48.dp,

                onPayOrGet = {
                    onEventHandler.invoke(ReportScreenEvent.OnPayOrGetLegacy(transaction = it))
                },
                emptyStateTitle = stringRes(R.string.no_transactions),
                emptyStateText = stringRes(R.string.no_transactions_for_your_filter),
                shouldShowAccountSpecificColorInTransactions = state.showAccountColorsInTransactions,
                onSkipTransaction = {
                    onEventHandler.invoke(ReportScreenEvent.SkipTransactionLegacy(transaction = it))
                },
                onSkipAllTransactions = {
                    onEventHandler.invoke(ReportScreenEvent.SkipTransactionsLegacy(transactions = it))
                }
            )
        } else {
            item {
                NoFilterEmptyState(
                    setFilterOverlayVisible = {
                        onEventHandler.invoke(
                            ReportScreenEvent.OnFilterOverlayVisible(
                                filterOverlayVisible = it
                            )
                        )
                    }
                )
            }
        }
    }

    FilterOverlay(
        visible = state.filterOverlayVisible,
        baseCurrency = state.baseCurrency,
        accounts = state.accounts,
        categories = state.categories,
        filter = state.filter,
        allTags = state.allTags,
        onClose = {
            onEventHandler.invoke(
                ReportScreenEvent.OnFilterOverlayVisible(
                    filterOverlayVisible = false
                )
            )
        },
        onSetFilter = {
            onEventHandler.invoke(ReportScreenEvent.OnFilter(filter = it))
        },
        onTagSearch = {
            onEventHandler.invoke(ReportScreenEvent.OnTagSearch(data = it))
        }
    )
}

@Composable
private fun NoFilterEmptyState(
    setFilterOverlayVisible: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))

        IvyIcon(
            icon = R.drawable.ic_filter_l,
            tint = Gray
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.no_filter),
            style = UI.typo.b1.style(
                color = Gray,
                fontWeight = FontWeight.ExtraBold
            )
        )

        Spacer(Modifier.height(8.dp))

        Text(
            modifier = Modifier.padding(horizontal = 32.dp),
            text = stringResource(R.string.invalid_filter_warning),
            style = UI.typo.b2.style(
                color = Gray,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        )

        Spacer(Modifier.height(32.dp))

        IvyButton(
            iconStart = R.drawable.ic_filter_xs,
            text = stringResource(R.string.set_filter)
        ) {
            setFilterOverlayVisible(true)
        }

        Spacer(Modifier.height(96.dp))
    }
}

@Composable
private fun Toolbar(
    onExport: () -> Unit,
    onFilter: () -> Unit,
    showBackButton: Boolean = true,
) {
    val nav = navigation()
    IvyToolbar(
        backButtonType = if (showBackButton) BackButtonType.CLOSE else BackButtonType.NONE,
        onBack = {
            nav.back()
        }
    ) {
        Spacer(Modifier.weight(1f))

        // Export CSV
        IvyOutlinedButton(
            text = stringResource(R.string.export),
            iconTint = Green,
            textColor = Green,
            solidBackground = true,
            padding = 8.dp,
            iconStart = R.drawable.ic_export_csv
        ) {
            onExport()
        }

        Spacer(Modifier.width(16.dp))

        // Filter
        CircleButtonFilled(
            icon = R.drawable.ic_filter_xs
        ) {
            onFilter()
        }

        Spacer(Modifier.width(24.dp))
    }
}

@ExperimentalFoundationApi
@Preview
@Composable
private fun Preview(theme: Theme = Theme.LIGHT) {
    IvyWalletPreview(theme) {
        val acc1 = Account("Cash", color = Green.toArgb())
        val acc2 = Account("DSK", color = GreenDark.toArgb())
        val cat1 = Category(
            name = NotBlankTrimmedString.unsafe("Science"),
            color = ColorInt(Purple1Dark.toArgb()),
            icon = IconAsset.unsafe("atom"),
            id = CategoryId(UUID.randomUUID()),
            orderNum = 0.0,
        )
        val state = ReportScreenState(
            baseCurrency = "BGN",
            balance = -6405.66,
            income = 2000.0,
            expenses = 8405.66,
            upcomingIncome = 4800.23,
            upcomingExpenses = 0.0,
            overdueIncome = 2335.12,
            overdueExpenses = 0.0,
            history =
            persistentListOf(),
            upcomingTransactions = persistentListOf(),
            overdueTransactions = persistentListOf(),

            upcomingExpanded = true,
            overdueExpanded = true,
            filter = ReportFilter.emptyFilter("BGN"),
            loading = false,
            accounts = persistentListOf(
                acc1,
                acc2,
                Account("phyre", color = GreenLight.toArgb(), icon = "cash"),
                Account("Revolut", color = IvyDark.toArgb()),
            ),
            categories = persistentListOf(
                cat1,
                Category(
                    name = NotBlankTrimmedString.unsafe("Pet"),
                    color = ColorInt(Red3Light.toArgb()),
                    icon = IconAsset.unsafe("pet"),
                    id = CategoryId(UUID.randomUUID()),
                    orderNum = 0.0,
                ),
                Category(
                    name = NotBlankTrimmedString.unsafe("Home"),
                    color = ColorInt(Green.toArgb()),
                    icon = null,
                    id = CategoryId(UUID.randomUUID()),
                    orderNum = 0.0,
                ),
            ),
        )

        UI(state = state)
    }
}

@ExperimentalFoundationApi
@Preview
@Composable
private fun Preview_NO_FILTER(theme: Theme = Theme.LIGHT) {
    IvyWalletPreview(theme) {
        val acc1 = Account("Cash", color = Green.toArgb())
        val acc2 = Account("DSK", color = GreenDark.toArgb())
        val cat1 = Category(
            name = NotBlankTrimmedString.unsafe("Science"),
            color = ColorInt(Purple1Dark.toArgb()),
            icon = IconAsset.unsafe("atom"),
            id = CategoryId(UUID.randomUUID()),
            orderNum = 0.0,
        )
        val state = ReportScreenState(
            baseCurrency = "BGN",
            balance = 0.0,
            income = 0.0,
            expenses = 0.0,
            upcomingIncome = 0.0,
            upcomingExpenses = 0.0,
            overdueIncome = 0.0,
            overdueExpenses = 0.0,

            history = persistentListOf(),
            upcomingTransactions = persistentListOf(),
            overdueTransactions = persistentListOf(),

            upcomingExpanded = true,
            overdueExpanded = true,

            filter = null,
            loading = false,

            accounts = persistentListOf(
                acc1,
                acc2,
                Account("phyre", color = GreenLight.toArgb(), icon = "cash"),
                Account("Revolut", color = IvyDark.toArgb()),
            ),
            categories = persistentListOf(
                cat1,
                Category(
                    name = NotBlankTrimmedString.unsafe("Pet"),
                    color = ColorInt(Red3Light.toArgb()),
                    icon = IconAsset.unsafe("pet"),
                    id = CategoryId(UUID.randomUUID()),
                    orderNum = 0.0,
                ),
                Category(
                    name = NotBlankTrimmedString.unsafe("Home"),
                    color = ColorInt(Green.toArgb()),
                    icon = null,
                    id = CategoryId(UUID.randomUUID()),
                    orderNum = 0.0,
                ),
            ),
        )

        UI(state = state)
    }
}

/** For screenshot testing */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReportUiTest(isDark: Boolean) {
    val theme = if (isDark) Theme.DARK else Theme.LIGHT
    Preview(theme)
}

/** For screenshot testing */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReportNoFilterUiTest(isDark: Boolean) {
    val theme = if (isDark) Theme.DARK else Theme.LIGHT
    Preview_NO_FILTER(theme)
}