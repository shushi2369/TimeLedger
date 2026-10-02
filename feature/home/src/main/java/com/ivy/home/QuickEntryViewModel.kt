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
import com.ivy.data.model.Tag
import com.ivy.data.model.TagId
import com.ivy.data.model.primitive.AssociationId
import com.ivy.data.repository.AccountRepository
import com.ivy.data.repository.CategoryRepository
import com.ivy.data.repository.TagRepository
import com.ivy.data.repository.TransactionRepository
import com.ivy.data.repository.mapper.TransactionMapper
import com.ivy.legacy.IvyWalletCtx
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
import java.math.BigDecimal
import java.math.RoundingMode
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
    private val tagRepository: TagRepository,
    private val ivyWalletCtx: IvyWalletCtx,
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
    private var availableTags by mutableStateOf<List<Tag>>(emptyList())
    private var selectedTagIds by mutableStateOf<Set<UUID>>(emptySet())
    private var selectedDate by mutableStateOf<java.time.LocalDate?>(null)
    private var categoriesVersion by mutableStateOf(0)

    init {
        viewModelScope.launch {
            // 查询在 IO 线程，Compose 状态赋值必须回主线程（后台线程写 snapshot 会崩）
            val (loadedCategories, currency) = withContext(Dispatchers.IO) {
                categoryRepository.findAll().sortedBy { it.orderNum } to
                        settingsDao.findFirst().currency
            }
            baseCurrency = currency
            categories = loadedCategories
            availableTags = runCatching { tagRepository.findAll() }.getOrDefault(emptyList())
        }
    }

    /** 新建类别（名称 + 颜色），保存后刷新类别网格。 */
    fun addCategory(name: String, colorArgb: Long) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val orderNum = (categories.maxOfOrNull { it.orderNum } ?: 0.0) + 1.0
                val category = com.ivy.data.model.Category(
                    id = com.ivy.data.model.CategoryId(UUID.randomUUID()),
                    name = com.ivy.data.model.primitive.NotBlankTrimmedString.from(trimmed)
                        .getOrNull() ?: return@withContext,
                    color = com.ivy.data.model.primitive.ColorInt(colorArgb.toInt()),
                    icon = null,
                    orderNum = orderNum,
                )
                runCatching { categoryRepository.save(category) }
            }
            withContext(Dispatchers.IO) {
                categories = categoryRepository.findAll().sortedBy { it.orderNum }
            }
            categoriesVersion += 1
        }
    }

    /** 选择记账日期（null = 今天）。 */
    fun selectDate(date: java.time.LocalDate?) {
        selectedDate = date
    }

    fun toggleTag(id: UUID) {
        selectedTagIds = if (id in selectedTagIds) selectedTagIds - id else selectedTagIds + id
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
            availableTags = availableTags,
            selectedTagIds = selectedTagIds,
            selectedDate = selectedDate,
            categoriesVersion = categoriesVersion,
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

            ch == '-' -> {
                // 开头的负号 = 记收入；跟在数字后 = 减法
                if (cur.isEmpty()) input = "-"
                else if (cur.last().isDigit()) input = cur + "-"
            }

            ch in "+×÷" -> {
                if (cur.isEmpty() || cur.last() in OPERATORS || cur.last() == '.') return
                input = cur + ch
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
        val result = evaluateExpression(input.trimEnd('+', '-', '×', '÷', '.')) ?: return
        val amount = abs(result)
        if (amount < 0.01) return

        saving = true
        val tagIds = selectedTagIds
        viewModelScope.launch {
            var savedTransactionId: UUID? = null
            try {
                withContext(Dispatchers.IO) {
                    val account = accountDao.findAll()
                        .map { it.toLegacyDomain() }
                        .firstOrNull()
                        ?: createDefaultAccount(baseCurrency)

                    val legacy = Transaction(
                        accountId = account.id,
                        type = if (result >= 0) TransactionType.EXPENSE else TransactionType.INCOME,
                        amount = java.math.BigDecimal.valueOf(amount),
                        categoryId = selectedCategoryId,
                        title = note.ifBlank { null },
                        dateTime = selectedDate?.let { date ->
                            val nowLocal = with(timeConverter) { timeNowLocal() }
                            java.time.LocalDateTime.of(
                                date.year, date.monthValue, date.dayOfMonth,
                                nowLocal.hour, nowLocal.minute, nowLocal.second
                            )
                        }?.let { with(timeConverter) { it.toUTC() } }
                            ?: with(timeConverter) { timeNowLocal().toUTC() },
                    )
                    with(transactionMapper) {
                        legacy.toEntity().toDomain().getOrNull()?.let {
                            transactionRepository.save(it)
                            savedTransactionId = it.id.value
                        }
                    }
                }

                // 标签关联落库（tags 存 TagAssociation 表，与交易本体分离）
                val savedId = savedTransactionId
                if (savedId != null && tagIds.isNotEmpty()) {
                    withContext(Dispatchers.IO) {
                        tagIds.forEach { tagId ->
                            runCatching {
                                tagRepository.associateTagToEntity(
                                    associationId = AssociationId(savedId),
                                    tagId = TagId(tagId),
                                )
                            }
                        }
                    }
                }

                // 全局 Snackbar：主页可见，支持撤销（闭包只捕获单例仓库，不依赖本 VM 生命周期）
                val direction = if (result >= 0) "支出" else "收入"
                val amountText = java.text.DecimalFormat("#,##0.00").format(amount)
                savedId?.let { id ->
                    ivyWalletCtx.showSnackbar(
                        message = "已记录 $direction $amountText 元",
                        actionLabel = "撤销",
                        onAction = {
                            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                                runCatching {
                                    transactionRepository.deleteById(
                                        com.ivy.data.model.TransactionId(id)
                                    )
                                    ivyWalletCtx.notifyDataChanged()
                                }
                            }
                        }
                    )
                }

                onDone()
            } finally {
                saving = false
                // 保存后清空输入，避免下次进入残留上一次的表达式
                input = ""
                note = ""
                selectedCategoryId = null
                selectedTagIds = emptySet()
                selectedDate = null
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
        private val OPERATORS = setOf('+', '-', '×', '÷')
        private val NUMBER = Regex("\\d+(?:\\.\\d*)?|\\.\\d+")

        /**
         * 求值四则运算表达式，运算优先级：先 ×÷ 后 +−；
         * 结果四舍五入精确到两位小数（HALF_UP）。如 "12+3×4" → 24.00、"10÷3" → 3.33、"-5-3" → -8.00。
         * 输入不合法（缺口、非法字符、除以零）返回 null。开头的负号 = 负数（记收入）。
         */
        fun evaluateExpression(input: String): Double? {
            val cleaned = input.trim()
            if (cleaned.isEmpty()) return null

            // ① tokenize：[可选首位负号] 数字 (运算符 数字)*
            var i = 0
            var negative = false
            when (cleaned.first()) {
                '-' -> { negative = true; i = 1 }
                '+' -> i = 1
            }
            val first = NUMBER.find(cleaned, i) ?: return null
            if (first.range.first != i) return null
            val tokens = ArrayList<Any>(8) // BigDecimal | Char
            tokens.add(BigDecimal(first.value))
            i = first.range.last + 1
            while (i < cleaned.length) {
                val op = cleaned[i]
                if (op !in "+-×÷") return null
                tokens.add(op)
                i++
                val m = NUMBER.find(cleaned, i) ?: return null
                if (m.range.first != i) return null
                tokens.add(BigDecimal(m.value))
                i = m.range.last + 1
            }

            // ② 第一遍：× ÷（除以零 → 非法）
            val stack = ArrayList<Any>(8)
            stack.add(tokens[0])
            var k = 1
            while (k < tokens.size) {
                val op = tokens[k] as Char
                val num = tokens[k + 1] as BigDecimal
                if (op == '×' || op == '÷') {
                    val prev = stack.removeAt(stack.size - 1) as BigDecimal
                    val r = if (op == '×') {
                        prev.multiply(num)
                    } else {
                        if (num.signum() == 0) return null
                        prev.divide(num, 10, RoundingMode.HALF_UP)
                    }
                    stack.add(r)
                } else {
                    stack.add(op)
                    stack.add(num)
                }
                k += 2
            }

            // ③ 第二遍：+ −
            var acc = stack[0] as BigDecimal
            k = 1
            while (k < stack.size) {
                val op = stack[k] as Char
                val num = stack[k + 1] as BigDecimal
                acc = if (op == '+') acc.add(num) else acc.subtract(num)
                k += 2
            }

            if (negative) acc = acc.negate()
            return acc.setScale(2, RoundingMode.HALF_UP).toDouble()
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
    val availableTags: List<Tag> = emptyList(),
    val selectedTagIds: Set<UUID> = emptySet(),
    val selectedDate: java.time.LocalDate? = null,
    val categoriesVersion: Int = 0,
)
