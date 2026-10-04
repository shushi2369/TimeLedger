package com.ivy.reports

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.viewModelScope
import com.ivy.base.legacy.LegacyTransaction
import com.ivy.base.legacy.TransactionHistoryItem
import com.ivy.base.legacy.stringRes
import com.ivy.base.model.TransactionType
import com.ivy.base.time.TimeConverter
import com.ivy.base.time.TimeProvider
import com.ivy.data.model.Category
import com.ivy.data.model.CategoryId
import com.ivy.data.model.Expense
import com.ivy.data.model.Income
import com.ivy.data.model.Tag
import com.ivy.data.model.Transaction
import com.ivy.data.model.TransactionId
import com.ivy.data.model.Transfer
import com.ivy.data.model.primitive.ColorInt
import com.ivy.data.model.primitive.NotBlankTrimmedString
import com.ivy.data.repository.CategoryRepository
import com.ivy.data.repository.TagRepository
import com.ivy.data.repository.TransactionRepository
import com.ivy.data.repository.mapper.TransactionMapper
import com.ivy.data.temp.migration.getTransactionType
import com.ivy.data.temp.migration.getValue
import com.ivy.domain.RootScreen
import com.ivy.domain.features.Features
import com.ivy.domain.usecase.csv.ExportCsvUseCase
import com.ivy.frp.filterSuspend
import com.ivy.legacy.IvyWalletCtx
import com.ivy.legacy.data.model.TimePeriod
import com.ivy.legacy.datamodel.Account
import com.ivy.legacy.datamodel.temp.toLegacy
import com.ivy.legacy.utils.getISOFormattedDateTime
import com.ivy.legacy.utils.scopedIOThread
import com.ivy.legacy.utils.timeNowUTC
import com.ivy.legacy.utils.toLowerCaseLocal
import com.ivy.legacy.utils.uiThread
import com.ivy.ui.ComposeViewModel
import com.ivy.ui.R
import com.ivy.wallet.domain.action.account.AccountsAct
import com.ivy.wallet.domain.action.exchange.ExchangeAct
import com.ivy.wallet.domain.action.settings.BaseCurrencyAct
import com.ivy.wallet.domain.action.transaction.CalcTrnsIncomeExpenseAct
import com.ivy.wallet.domain.action.transaction.TrnsWithDateDivsAct
import com.ivy.wallet.domain.deprecated.logic.PlannedPaymentsLogic
import com.ivy.wallet.domain.pure.data.IncomeExpenseTransferPair
import com.ivy.wallet.domain.pure.exchange.ExchangeData
import com.ivy.wallet.domain.pure.transaction.trnCurrency
import com.ivy.wallet.domain.pure.util.orZero
import com.ivy.wallet.ui.theme.Gray
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

@Stable
@HiltViewModel
class ReportViewModel @Inject constructor(
    private val plannedPaymentsLogic: PlannedPaymentsLogic,
    private val transactionRepository: TransactionRepository,
    private val ivyContext: IvyWalletCtx,
    private val exchangeAct: ExchangeAct,
    private val accountsAct: AccountsAct,
    private val categoryRepository: CategoryRepository,
    private val trnsWithDateDivsAct: TrnsWithDateDivsAct,
    private val calcTrnsIncomeExpenseAct: CalcTrnsIncomeExpenseAct,
    private val baseCurrencyAct: BaseCurrencyAct,
    private val transactionMapper: TransactionMapper,
    private val tagRepository: TagRepository,
    private val exportCsvUseCase: ExportCsvUseCase,
    private val timeProvider: TimeProvider,
    private val timeConverter: TimeConverter,
    private val features: Features
) : ComposeViewModel<ReportScreenState, ReportScreenEvent>() {
    private val unSpecifiedCategory =
        Category(
            name = NotBlankTrimmedString.unsafe(stringRes(R.string.unspecified)),
            color = ColorInt(Gray.toArgb()),
            icon = null,
            id = CategoryId(UUID.randomUUID()),
            orderNum = 0.0,
        )
    private var baseCurrency by mutableStateOf("")
    private var categories by mutableStateOf<ImmutableList<Category>>(persistentListOf())
    private var historyIncomeExpense by mutableStateOf(IncomeExpenseTransferPair.zero())
    private var filter by mutableStateOf<ReportFilter?>(null)
    var yearMode by mutableStateOf(false)
        private set
    var selectedYear by mutableStateOf(java.time.LocalDate.now().year)
        private set
    var selectedMonth by mutableStateOf(java.time.LocalDate.now().monthValue)
        private set
    private var balance by mutableDoubleStateOf(0.0)
    private var income by mutableDoubleStateOf(0.0)
    private var expenses by mutableDoubleStateOf(0.0)
    private var upcomingIncome by mutableDoubleStateOf(0.0)
    private var upcomingExpenses by mutableDoubleStateOf(0.0)
    private var overdueIncome by mutableDoubleStateOf(0.0)
    private var overdueExpenses by mutableDoubleStateOf(0.0)
    private var history by mutableStateOf<ImmutableList<TransactionHistoryItem>>(persistentListOf())
    private var upcomingTransactions by
    mutableStateOf<ImmutableList<LegacyTransaction>>(persistentListOf())
    private var overdueTransactions by
    mutableStateOf<ImmutableList<LegacyTransaction>>(persistentListOf())
    private var accounts by mutableStateOf<ImmutableList<Account>>(persistentListOf())
    private var upcomingExpanded by mutableStateOf(false)
    private var overdueExpanded by mutableStateOf(false)
    private var loading by mutableStateOf(false)
    private var accountIdFilters by mutableStateOf<ImmutableList<UUID>>(persistentListOf())
    private var transactions by mutableStateOf<ImmutableList<LegacyTransaction>>(persistentListOf())
    private var filterOverlayVisible by mutableStateOf(false)
    private var showTransfersAsIncExpCheckbox by mutableStateOf(false)
    private var treatTransfersAsIncExp by mutableStateOf(false)
    private var allTags by mutableStateOf<ImmutableList<Tag>>(persistentListOf())

    private var tagSearchJob: Job? = null
    private val tagSearchDebounceTimeInMills: Long = 500

    @Composable
    fun getShouldShowAccountSpecificColorInTransactions(): Boolean {
        return features.showAccountColorsInTransactions.asEnabledState()
    }

    @Composable
    override fun uiState(): ReportScreenState {
        LaunchedEffect(Unit) {
            start()
        }

        return ReportScreenState(
            yearMode = yearMode,
            selectedYear = selectedYear,
            selectedMonth = selectedMonth,
            categories = categories,
            accounts = accounts,
            accountIdFilters = accountIdFilters,
            balance = balance,
            baseCurrency = baseCurrency,
            expenses = expenses,
            filter = filter,
            filterOverlayVisible = filterOverlayVisible,
            history = history,
            income = income,
            loading = loading,
            overdueExpanded = overdueExpanded,
            overdueExpenses = overdueExpenses,
            overdueIncome = overdueIncome,
            overdueTransactions = overdueTransactions,
            showTransfersAsIncExpCheckbox = showTransfersAsIncExpCheckbox,
            transactions = transactions,
            treatTransfersAsIncExp = treatTransfersAsIncExp,
            upcomingExpanded = upcomingExpanded,
            upcomingExpenses = upcomingExpenses,
            upcomingIncome = upcomingIncome,
            upcomingTransactions = upcomingTransactions,
            allTags = allTags,
            showAccountColorsInTransactions = getShouldShowAccountSpecificColorInTransactions()
        )
    }

    override fun onEvent(event: ReportScreenEvent) {
        viewModelScope.launch(Dispatchers.Default) {
            when (event) {
                is ReportScreenEvent.OnFilter -> {
                    // 筛选浮层可改期间：反同步期间条显示状态，避免 ◀▶ 基于过期年月跳转
                    syncPeriodDisplay(event.filter)
                    setFilter(event.filter)
                }
                is ReportScreenEvent.OnPeriodPrevious -> shiftPeriod(-1)
                is ReportScreenEvent.OnPeriodNext -> shiftPeriod(1)
                is ReportScreenEvent.OnPeriodMonthPicked -> pickMonth(event.year, event.month)
                is ReportScreenEvent.OnPeriodYearPicked -> pickYear(event.year)
                is ReportScreenEvent.OnExport -> export(event.context)
                is ReportScreenEvent.OnPayOrGet -> payOrGet(event.transaction)
                is ReportScreenEvent.SkipTransaction -> skipTransaction(event.transaction)
                is ReportScreenEvent.SkipTransactions -> skipTransactions(event.transactions)
                is ReportScreenEvent.OnPayOrGetLegacy -> payOrGetLegacy(event.transaction)
                is ReportScreenEvent.SkipTransactionLegacy -> skipTransactionLegacy(event.transaction)
                is ReportScreenEvent.SkipTransactionsLegacy -> skipTransactionsLegacy(event.transactions)
                is ReportScreenEvent.OnOverdueExpanded -> setOverdueExpandedValue(event.overdueExpanded)
                is ReportScreenEvent.OnUpcomingExpanded -> setUpcomingExpandedValue(event.upcomingExpanded)
                is ReportScreenEvent.OnFilterOverlayVisible -> setFilterOverlayVisibleValue(event.filterOverlayVisible)
                is ReportScreenEvent.OnTreatTransfersAsIncomeExpense -> onTreatTransfersAsIncomeExpense(
                    event.transfersAsIncomeExpense
                )

                is ReportScreenEvent.OnTagSearch -> onTagSearch(event.data)
            }
        }
    }

    private suspend fun onTagSearch(query: String) {
        withContext(Dispatchers.IO) {
            tagSearchJob?.cancelAndJoin()
            delay(tagSearchDebounceTimeInMills) // Debounce effect
            tagSearchJob = launch(Dispatchers.IO) {
                NotBlankTrimmedString.from(query.toLowerCaseLocal())
                    .fold(
                        ifRight = {
                            allTags =
                                tagRepository.findByText(text = it.value).toImmutableList()
                        },
                        ifLeft = {
                            allTags = tagRepository.findAll().toImmutableList()
                        }
                    )
            }
        }
    }

    private fun start() {
        viewModelScope.launch(Dispatchers.IO) {
            baseCurrency = baseCurrencyAct(Unit)
            accounts = accountsAct(Unit)
            categories =
                (listOf(unSpecifiedCategory) + categoryRepository.findAll()).toImmutableList()
            allTags = tagRepository.findAll().toImmutableList()

            // fork 增强：无筛选器时默认"本月 + 全部类型/账户/类别"，报表打开即出图表（2026-09-27）
            if (filter == null) {
                val defaultFilter = ReportFilter(
                    trnTypes = listOf(TransactionType.INCOME, TransactionType.EXPENSE),
                    period = TimePeriod(
                        month = com.ivy.legacy.data.model.Month.fromMonthValue(selectedMonth),
                        year = selectedYear
                    ),
                    accounts = accounts.toList(),
                    categories = categories.toList(),
                    currency = baseCurrency,
                    minAmount = null,
                    maxAmount = null,
                    includeKeywords = emptyList(),
                    excludeKeywords = emptyList(),
                    includedTags = emptyList(),
                    excludedTags = emptyList()
                )
                filter = defaultFilter
                setFilter(defaultFilter)
            }
        }
    }

    /** 微信式期间切换：月模式 ±1 月（跨年自动滚动），年模式 ±1 年。 */
    private fun shiftPeriod(direction: Int) {
        if (yearMode) {
            pickYear(selectedYear + direction)
        } else {
            var m = selectedMonth + direction
            var y = selectedYear
            if (m > 12) { m = 1; y += 1 }
            if (m < 1) { m = 12; y -= 1 }
            pickMonth(y, m)
        }
    }

    /**
     * 从筛选器的期间反推期间条显示状态：月期间→月模式；全年 fromToRange→年模式；
     * 自定义区间/近N天→退出年模式（界面显示"自定义区间"占位，◀▶ 在此期间不映射到具体年月）。
     */
    private fun syncPeriodDisplay(filterValue: ReportFilter?) {
        val period = filterValue?.period ?: return
        val monthPeriod = period.month
        val range = period.fromToRange
        when {
            monthPeriod != null -> {
                yearMode = false
                selectedMonth = monthPeriod.monthValue
                period.year?.let { selectedYear = it }
            }
            range != null -> {
                val fromUtc = range.from?.atZone(ZoneId.of("UTC"))
                val toUtc = range.to?.atZone(ZoneId.of("UTC"))
                val isFullYear = fromUtc != null &&
                        fromUtc.monthValue == 1 && fromUtc.dayOfMonth == 1 &&
                        toUtc?.let { it.monthValue == 12 && it.dayOfMonth == 31 } == true
                if (isFullYear) {
                    yearMode = true
                    selectedYear = fromUtc.year
                } else {
                    yearMode = false
                }
            }
        }
    }

    /** 按月查看：TimePeriod(month, year)。 */
    private fun pickMonth(year: Int, month: Int) {
        selectedYear = year
        selectedMonth = month
        yearMode = false
        val current = filter ?: return
        viewModelScope.launch(Dispatchers.IO) {
            setFilter(
                    current.copy(
                        period = TimePeriod(
                            month = com.ivy.legacy.data.model.Month.fromMonthValue(month),
                            year = year
                        )
                    )
                )
        }
    }

    /** 按年查看：显式全年 fromToRange（toRange 对 month=null 不产出全年）。 */
    private fun pickYear(year: Int) {
        selectedYear = year
        yearMode = true
        val current = filter ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val from = com.ivy.legacy.utils.startOfMonth(
                java.time.LocalDate.of(year, 1, 1), timeConverter
            )
            val to = com.ivy.legacy.utils.endOfMonth(
                java.time.LocalDate.of(year, 12, 31), timeConverter
            )
            setFilter(
                current.copy(
                    period = TimePeriod(
                        fromToRange = com.ivy.legacy.data.model.FromToTimeRange(from = from, to = to)
                    )
                )
            )
        }
    }

    private suspend fun setFilter(reportFilter: ReportFilter?) {
        scopedIOThread { scope ->
            if (reportFilter == null) {
                setReportValues(
                    income = 0.00,
                    expense = 0.00,
                    upcomingIncomeExpenseTransferPair = IncomeExpenseTransferPair.zero(),
                    overDueIncomeExpenseTransferPair = IncomeExpenseTransferPair.zero(),
                    history = persistentListOf(),
                    upcomingTransactions = persistentListOf(),
                    overdueTransactions = persistentListOf(),
                    accounts = accountsAct(Unit),
                    reportFilter = filter,
                    accountIdFilters = persistentListOf(),
                    transactions = persistentListOf(),
                    balanceValue = 0.00
                )
                return@scopedIOThread
            }

            if (!reportFilter.validate()) return@scopedIOThread
            val tempAccounts = reportFilter.accounts
            val baseCurrency = baseCurrency
            loading = true

            val transactionsList = filterTransactions(
                baseCurrency = baseCurrency,
                accounts = tempAccounts,
                filter = reportFilter
            )

            val tempHistory = transactionsList
                .sortedByDescending { it.time }

            val historyWithDateDividers = scope.async {
                trnsWithDateDivsAct(
                    TrnsWithDateDivsAct.Input(
                        baseCurrency = baseCurrency,
                        transactions = tempHistory
                    )
                )
            }

            historyIncomeExpense = calcTrnsIncomeExpenseAct(
                CalcTrnsIncomeExpenseAct.Input(
                    transactions = tempHistory,
                    accounts = tempAccounts,
                    baseCurrency = baseCurrency
                )
            )

            val tempIncome = historyIncomeExpense.income.toDouble() +
                    if (treatTransfersAsIncExp) historyIncomeExpense.transferIncome.toDouble() else 0.0

            val tempExpenses = historyIncomeExpense.expense.toDouble() +
                    if (treatTransfersAsIncExp) historyIncomeExpense.transferExpense.toDouble() else 0.0

            val tempBalance = calculateBalance(historyIncomeExpense).toDouble()

            val accountFilterIdList = scope.async { reportFilter.accounts.map { it.id } }

            val timeNowUTC = timeNowUTC()

            // Upcoming
            val upcomingTransactionsList = transactionsList
                .filter {
                    !it.settled && it.time.atZone(ZoneId.systemDefault()).toLocalDateTime()
                        .isAfter(timeNowUTC)
                }
                .sortedBy { it.time }
                .toImmutableList()

            val upcomingIncomeExpense = calcTrnsIncomeExpenseAct(
                CalcTrnsIncomeExpenseAct.Input(
                    transactions = upcomingTransactionsList,
                    accounts = tempAccounts,
                    baseCurrency = baseCurrency
                )
            )
            // Overdue
            val overdue = transactionsList.filter {
                !it.settled && it.time.atZone(ZoneId.systemDefault()).toLocalDateTime()
                    .isBefore(timeNowUTC)
            }.sortedByDescending {
                it.time
            }.toImmutableList()
            val overdueIncomeExpense = calcTrnsIncomeExpenseAct(
                CalcTrnsIncomeExpenseAct.Input(
                    transactions = overdue,
                    accounts = tempAccounts,
                    baseCurrency = baseCurrency
                )
            )

            setReportValues(
                income = tempIncome,
                expense = tempExpenses,
                upcomingIncomeExpenseTransferPair = upcomingIncomeExpense,
                overDueIncomeExpenseTransferPair = overdueIncomeExpense,
                history = historyWithDateDividers.await().toImmutableList(),
                upcomingTransactions = upcomingTransactionsList.map {
                    it.toLegacy(transactionMapper)
                }.toImmutableList(),
                overdueTransactions = overdue.map {
                    it.toLegacy(transactionMapper)
                }.toImmutableList(),
                accounts = tempAccounts.toImmutableList(),
                reportFilter = reportFilter,
                accountIdFilters = accountFilterIdList.await().toImmutableList(),
                transactions = transactionsList.map {
                    it.toLegacy(transactionMapper)
                }.toImmutableList(),
                balanceValue = tempBalance
            )

            loading = false
        }
    }

    private fun setReportValues(
        income: Double,
        expense: Double,
        upcomingIncomeExpenseTransferPair: IncomeExpenseTransferPair,
        overDueIncomeExpenseTransferPair: IncomeExpenseTransferPair,
        history: ImmutableList<TransactionHistoryItem>,
        upcomingTransactions: ImmutableList<LegacyTransaction>,
        overdueTransactions: ImmutableList<LegacyTransaction>,
        accounts: ImmutableList<Account>,
        reportFilter: ReportFilter? = null,
        accountIdFilters: ImmutableList<UUID>,
        transactions: ImmutableList<LegacyTransaction>,
        balanceValue: Double
    ) {
        this.income = income
        this.expenses = expense
        this.upcomingExpenses = upcomingIncomeExpenseTransferPair.expense.toDouble()
        this.upcomingIncome = upcomingIncomeExpenseTransferPair.income.toDouble()
        this.overdueIncome = overDueIncomeExpenseTransferPair.income.toDouble()
        this.overdueExpenses = overDueIncomeExpenseTransferPair.expense.toDouble()
        this.history = history
        this.upcomingTransactions = upcomingTransactions
        this.overdueTransactions = overdueTransactions
        this.accounts = accounts
        this.filter = reportFilter
        this.accountIdFilters = accountIdFilters
        this.transactions = transactions
        this.balance = balanceValue
        this.showTransfersAsIncExpCheckbox =
            reportFilter?.trnTypes?.contains(TransactionType.TRANSFER) ?: false
    }

    private suspend fun filterTransactions(
        baseCurrency: String,
        accounts: List<Account>,
        filter: ReportFilter,
    ): ImmutableList<Transaction> {
        val filterAccountIds = filter.accounts.map { it.id }
        val filterCategoryIds =
            filter.categories.map { if (it.id.value == unSpecifiedCategory.id.value) null else it.id }
        val filterRange =
            filter.period?.toRange(ivyContext.startDayOfMonth, timeConverter, timeProvider)

        val transactions = if (filter.includedTags.isNotEmpty()) {
            tagRepository.findByAllAssociatedIdForTagId(filter.includedTags)
                .asSequence()
                .flatMap { it.value }
                .map { TransactionId(it.associatedId.value) }
                .distinct()
                .toList()
                .let {
                    transactionRepository.findByIds(it)
                }
        } else {
            transactionRepository.findAll()
        }

        val excludeableByTagTransactionsIds = if (filter.excludedTags.isNotEmpty()) {
            tagRepository.findByAllAssociatedIdForTagId(filter.excludedTags)
                .asSequence()
                .flatMap { it.value }
                .distinct()
                .map { TransactionId(it.associatedId.value) }
                .toList()
                .let {
                    transactionRepository.findByIds(it)
                }.map {
                    it.id
                }
        } else {
            emptyList()
        }

        return transactions
            .filter { !excludeableByTagTransactionsIds.contains(it.id) }
            .filter {
                with(transactionMapper) {
                    filter.trnTypes.contains(it.getTransactionType())
                }
            }
            .filter {
                // Filter by Time Period

                filterRange ?: return@filter false

                filterRange.includes(it.time)
            }
            .filter { trn ->
                // Filter by Accounts
                when (trn) {
                    is Transfer -> {
                        filterAccountIds.contains(trn.fromAccount.value) || // Transfers Out
                                (filterAccountIds.contains(trn.toAccount.value)) // Transfers In
                    }

                    is Expense -> {
                        filterAccountIds.contains(trn.account.value)
                    }

                    is Income -> {
                        filterAccountIds.contains(trn.account.value)
                    }
                }
            }
            .filter { trn ->
                // Filter by Categories

                filterCategoryIds.contains(trn.category) || with(transactionMapper) {
                    (trn.getTransactionType() == TransactionType.TRANSFER)
                }
            }
            .filterSuspend {
                // Filter by Amount
                // !NOTE: Amount must be converted to baseCurrency amount

                val trnAmountBaseCurrency = exchangeAct(
                    ExchangeAct.Input(
                        data = ExchangeData(
                            baseCurrency = baseCurrency,
                            fromCurrency = trnCurrency(it, accounts, baseCurrency),
                        ),
                        amount = it.getValue()
                    )
                ).orZero().toDouble()

                (filter.minAmount == null || trnAmountBaseCurrency >= filter.minAmount) &&
                        (filter.maxAmount == null || trnAmountBaseCurrency <= filter.maxAmount)
            }
            .filter {
                // Filter by Included Keywords

                val includeKeywords = filter.includeKeywords
                if (includeKeywords.isEmpty()) return@filter true

                it.title?.let { title ->
                    includeKeywords.forEach { keyword ->
                        if (title.value.containsLowercase(keyword)) {
                            return@filter true
                        }
                    }
                }

                it.description?.let { description ->
                    includeKeywords.forEach { keyword ->
                        if (description.value.containsLowercase(keyword)) {
                            return@filter true
                        }
                    }
                }

                false
            }
            .filter {
                // Filter by Excluded Keywords

                val excludedKeywords = filter.excludeKeywords
                if (excludedKeywords.isEmpty()) return@filter true

                it.title?.let { title ->
                    excludedKeywords.forEach { keyword ->
                        if (title.value.containsLowercase(keyword)) {
                            return@filter false
                        }
                    }
                }
                it.description?.let { description ->
                    excludedKeywords.forEach { keyword ->
                        if (description.value.containsLowercase(keyword)) {
                            return@filter false
                        }
                    }
                }
                true
            }.toImmutableList()
    }

    private fun String.containsLowercase(anotherString: String): Boolean {
        return this.toLowerCaseLocal().contains(anotherString.toLowerCaseLocal())
    }

    private fun calculateBalance(incomeExpenseTransferPair: IncomeExpenseTransferPair): BigDecimal {
        return incomeExpenseTransferPair.income + incomeExpenseTransferPair.transferIncome - incomeExpenseTransferPair.expense - incomeExpenseTransferPair.transferExpense
    }

    private suspend fun export(context: Context) {
        val filter = filter ?: return
        if (!filter.validate()) return

        ivyContext.createNewFile(
            "IvyWalletReport-${
                timeNowUTC().getISOFormattedDateTime()
            }.csv"
        ) { fileUri ->
            viewModelScope.launch {
                loading = true

                exportCsvUseCase.exportToFile(
                    outputFile = fileUri,
                    exportScope = {
                        filterTransactions(
                            baseCurrency = baseCurrency,
                            accounts = accounts,
                            filter = filter
                        )
                    }
                )

                (context as RootScreen).shareCSVFile(
                    fileUri = fileUri
                )

                loading = false
            }
        }
    }

    private fun setUpcomingExpandedValue(expanded: Boolean) {
        upcomingExpanded = expanded
    }

    private fun setOverdueExpandedValue(expanded: Boolean) {
        overdueExpanded = expanded
    }

    private suspend fun payOrGet(transaction: Transaction) {
        uiThread {
            plannedPaymentsLogic.payOrGet(
                transaction = transaction
            ) {
                start()
                setFilter(filter)
            }
        }
    }

    @Deprecated("Uses legacy Transaction")
    private suspend fun payOrGetLegacy(transaction: com.ivy.base.legacy.Transaction) {
        uiThread {
            plannedPaymentsLogic.payOrGetLegacy(transaction = transaction) {
                start()
                setFilter(filter)
            }
        }
    }

    private fun setFilterOverlayVisibleValue(visible: Boolean) {
        filterOverlayVisible = visible
    }

    private fun onTreatTransfersAsIncomeExpense(transfersAsIncExp: Boolean) {
        income = historyIncomeExpense.income.toDouble() +
                if (transfersAsIncExp) historyIncomeExpense.transferIncome.toDouble() else 0.0
        expenses = historyIncomeExpense.expense.toDouble() +
                if (transfersAsIncExp) historyIncomeExpense.transferExpense.toDouble() else 0.0
        treatTransfersAsIncExp = transfersAsIncExp
    }

    private suspend fun skipTransaction(transaction: Transaction) {
        uiThread {
            plannedPaymentsLogic.payOrGet(
                transaction = transaction,
                skipTransaction = true
            ) {
                start()
                setFilter(filter)
            }
        }
    }

    @Deprecated("Uses legacy Transaction")
    private suspend fun skipTransactionLegacy(transaction: com.ivy.base.legacy.Transaction) {
        uiThread {
            plannedPaymentsLogic.payOrGetLegacy(
                transaction = transaction,
                skipTransaction = true
            ) {
                start()
                setFilter(filter)
            }
        }
    }

    private suspend fun skipTransactions(transactions: List<Transaction>) {
        uiThread {
            plannedPaymentsLogic.payOrGet(
                transactions = transactions,
                skipTransaction = true
            ) {
                start()
                setFilter(filter)
            }
        }
    }

    @Deprecated("Uses legacy Transaction")
    private suspend fun skipTransactionsLegacy(transactions: List<com.ivy.base.legacy.Transaction>) {
        uiThread {
            plannedPaymentsLogic.payOrGetLegacy(
                transactions = transactions,
                skipTransaction = true
            ) {
                start()
                setFilter(filter)
            }
        }
    }
}