package com.ivy.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivy.base.legacy.Transaction
import com.ivy.base.model.TransactionType
import com.ivy.base.time.TimeConverter
import com.ivy.data.db.dao.read.AccountDao
import com.ivy.data.db.dao.read.SettingsDao
import com.ivy.data.model.Category
import com.ivy.data.repository.AccountRepository
import com.ivy.data.repository.CategoryRepository
import com.ivy.data.repository.TransactionRepository
import com.ivy.data.repository.mapper.TransactionMapper
import com.ivy.legacy.datamodel.Account
import com.ivy.legacy.datamodel.temp.toLegacyDomain
import com.ivy.legacy.datamodel.toEntity
import com.ivy.legacy.utils.timeNowLocal
import com.ivy.wallet.domain.pure.util.nextOrderNum
import com.ivy.wallet.ui.theme.Green
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import kotlin.math.abs

/**
 * fork 增补（2026-09-28）：鲨鱼式快速记账。
 * 点"+"→选类别→加减键盘连算，表达式结果 ≥0 记支出、<0 记收入（取绝对值）。
 * 不再选择账户与收支类型：底层统一使用默认账户"现金"（无账户时自动创建）。
 */
@HiltViewModel
class QuickEntryViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository,
    private val accountDao: AccountDao,
    private val settingsDao: SettingsDao,
    private val currencyRepository: com.ivy.data.repository.CurrencyRepository,
    private val transactionRepository: TransactionRepository,
    private val transactionMapper: TransactionMapper,
    private val timeConverter: TimeConverter,
) : ViewModel() {

    private var categories by mutableStateOf<List<Category>>(emptyList())
    private var selectedCategoryId by mutableStateOf<UUID?>(null)
    private var input by mutableStateOf("")
    private var note by mutableStateOf("")
    private var baseCurrency by mutableStateOf("CNY")
    private var saving by mutableStateOf(false)

    init {
        viewModelScope.launch {
            // 查询在 IO 线程，Compose 状态赋值必须回主线程（后台线程写 snapshot 会崩）
            val (loadedCategories, currency) = withContext(Dispatchers.IO) {
                categoryRepository.findAll().sortedBy { it.orderNum } to
                        settingsDao.findFirst().currency
            }
            baseCurrency = currency
            categories = loadedCategories
        }
    }

    @Composable
    fun uiState(): QuickEntryState {
        return QuickEntryState(
            categories = categories,
            selectedCategoryId = selectedCategoryId,
            input = input,
            evaluated = evaluateExpression(input),
            note = note,
            baseCurrency = baseCurrency,
            saving = saving,
        )
    }

    fun onCategoryClick(categoryId: UUID?) {
        selectedCategoryId = categoryId
    }

    fun onKey(ch: Char) {
        Timber.d("QuickEntry onKey: %s (cur=%s)", ch, input)
        val cur = input
        if (cur.length >= MAX_INPUT_LENGTH) return
        when {
            ch.isDigit() -> input = cur + ch

            ch == '.' -> {
                // 小数点必须紧跟数字，且当前数字段内只能有一个
                if (cur.isEmpty() || cur.last() in OPERATORS || cur.last() == '.') return
                if (cur.takeLastWhile { it.isDigit() || it == '.' }.contains('.')) return
                input = cur + "."
            }

            ch == '+' -> {
                if (cur.isEmpty() || cur.last() in OPERATORS || cur.last() == '.') return
                input = cur + "+"
            }

            ch == '-' -> {
                // 开头的负号 = 记收入；跟在数字后 = 减法
                if (cur.isEmpty()) input = "-"
                else if (cur.last().isDigit()) input = cur + "-"
            }
        }
    }

    fun onBackspace() {
        input = input.dropLast(1)
    }

    fun onClear() {
        input = ""
    }

    fun onNoteChange(value: String) {
        note = value
    }

    fun finish(onDone: () -> Unit) {
        if (saving) return
        val result = evaluateExpression(input.trimEnd('+', '-', '.')) ?: return
        val amount = abs(result)
        if (amount < 0.01) return

        saving = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val account = accountDao.findAll()
                        .map { it.toLegacyDomain() }
                        .firstOrNull()
                        ?: createDefaultAccount(baseCurrency)

                    val legacy = Transaction(
                        accountId = account.id,
                        type = if (result >= 0) TransactionType.EXPENSE else TransactionType.INCOME,
                        amount = amount.toBigDecimal(),
                        categoryId = selectedCategoryId,
                        title = note.ifBlank { null },
                        dateTime = with(timeConverter) { timeNowLocal().toUTC() },
                    )
                    with(transactionMapper) {
                        legacy.toEntity().toDomain().getOrNull()?.let {
                            transactionRepository.save(it)
                        }
                    }
                }
                onDone()
            } finally {
                saving = false
                // 保存后清空输入，避免下次进入残留上一次的表达式
                input = ""
                note = ""
                selectedCategoryId = null
            }
        }
    }

    private suspend fun createDefaultAccount(baseCurrency: String): Account {
        val newAccount = Account(
            name = "现金",
            currency = baseCurrency,
            color = Green.toArgb(),
            icon = null,
            orderNum = accountDao.findMaxOrderNum().nextOrderNum(),
        )
        newAccount.toDomainAccount(currencyRepository).getOrNull()?.let {
            accountRepository.save(it)
        }
        return newAccount
    }

    companion object {
        private const val MAX_INPUT_LENGTH = 24
        private val OPERATORS = setOf('+', '-')

        /**
         * 求值 +/− 连算表达式（从左到右），如 "12+8-3.5" → 16.5、"-5-3" → -8。
         * 输入不合法（缺口、非法字符）返回 null。
         */
        fun evaluateExpression(input: String): Double? {
            val cleaned = input.trim()
            if (cleaned.isEmpty()) return null

            val token = Regex("([+-]?)(\\d+(?:\\.\\d*)?|\\.\\d+)")
            var index = 0
            var sum = 0.0
            var matchedAny = false
            for (m in token.findAll(cleaned)) {
                if (m.range.first != index) return null
                val sign = if (m.groupValues[1] == "-") -1.0 else 1.0
                sum += sign * (m.groupValues[2].toDoubleOrNull() ?: return null)
                index = m.range.last + 1
                matchedAny = true
            }
            if (!matchedAny || index != cleaned.length) return null
            return sum
        }
    }
}

data class QuickEntryState(
    val categories: List<Category>,
    val selectedCategoryId: UUID?,
    val input: String,
    val evaluated: Double?,
    val note: String,
    val baseCurrency: String,
    val saving: Boolean,
)
