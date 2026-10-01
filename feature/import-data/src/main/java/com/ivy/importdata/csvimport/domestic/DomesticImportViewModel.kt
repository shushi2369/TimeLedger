package com.ivy.importdata.csvimport.domestic

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivy.base.time.TimeConverter
import com.ivy.data.model.Account
import com.ivy.data.model.AccountId
import com.ivy.data.model.Expense
import com.ivy.data.model.Income
import com.ivy.data.model.PositiveValue
import com.ivy.data.model.TransactionId
import com.ivy.data.model.TransactionMetadata
import com.ivy.data.model.primitive.NotBlankTrimmedString
import com.ivy.data.model.primitive.PositiveDouble
import com.ivy.data.repository.AccountRepository
import com.ivy.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class DomesticImportViewModel @Inject constructor(
    private val accounts: AccountRepository,
    private val transactions: TransactionRepository,
    private val timeConverter: TimeConverter,
) : ViewModel() {
    data class Preview(
        val parsed: DomesticParse,
        val ready: List<DomesticRow>,
        val duplicates: Int,
    ) {
        val skipCount get() = parsed.skipped.size + duplicates
    }

    data class State(
        val source: DomesticSource? = null,
        val accounts: List<Account> = emptyList(),
        val accountId: AccountId? = null,
        val preview: Preview? = null,
        val busy: Boolean = false,
        val imported: Int? = null,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    fun selectSource(source: DomesticSource) {
        _state.value = State(source = source)
        viewModelScope.launch {
            runCatching { accounts.findAll() }.onSuccess { found ->
                _state.value = _state.value.copy(accounts = found)
            }.onFailure { failure -> _state.value = _state.value.copy(error = failure.message ?: "账户加载失败") }
        }
    }

    fun selectAccount(id: AccountId) {
        _state.value = _state.value.copy(accountId = id, preview = null, imported = null, error = null)
    }

    fun open(context: Context, uri: Uri) {
        val source = state.value.source ?: return
        if (state.value.accountId == null || state.value.busy) return
        _state.value = state.value.copy(busy = true, preview = null, error = null)
        viewModelScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("无法读取所选文件")
                }
                val parsed = withContext(Dispatchers.Default) { DomesticCsvParser.parse(bytes, source) }
                val existing = transactions.findByIds(parsed.rows.map { TransactionId(it.id) })
                    .map { it.id.value }.toSet()
                val seen = existing.toMutableSet()
                val ready = parsed.rows.filter { seen.add(it.id) }
                _state.value = state.value.copy(
                    busy = false, preview = Preview(parsed, ready, parsed.rows.size - ready.size),
                )
            } catch (failure: Exception) {
                _state.value = state.value.copy(busy = false, error = failure.message ?: "文件解析失败")
            }
        }
    }

    fun confirm() {
        val preview = state.value.preview ?: return
        val account = state.value.accounts.firstOrNull { it.id == state.value.accountId } ?: return
        if (state.value.busy) return
        _state.value = state.value.copy(busy = true, error = null)
        viewModelScope.launch {
            var imported = 0
            try {
                for (row in preview.ready) {
                    val id = TransactionId(row.id)
                    if (transactions.findById(id) != null) continue
                    val amount = PositiveDouble.from(row.amount.toDouble()).getOrNull()
                        ?: error("金额无效：${row.orderId}")
                    val value = PositiveValue(amount, account.asset)
                    val metadata = TransactionMetadata(null, null, loanRecordId = null)
                    val title = NotBlankTrimmedString.from(row.merchant.ifBlank { "${state.value.source?.label}交易" }).getOrNull()
                    val description = NotBlankTrimmedString.from(
                        "${state.value.source?.label} ${if (row.refund) "退款" else "交易"} #${row.orderId}" +
                            row.description.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
                    ).getOrNull()
                    val time = with(timeConverter) { row.date.toUTC() }
                    val transaction = if (row.type == com.ivy.base.model.TransactionType.INCOME) {
                        Income(id, title, description, null, time, true, metadata, emptyList(), value, account.id)
                    } else {
                        Expense(id, title, description, null, time, true, metadata, emptyList(), value, account.id)
                    }
                    transactions.save(transaction)
                    imported++
                }
                _state.value = state.value.copy(busy = false, imported = imported, preview = null)
            } catch (failure: Exception) {
                _state.value = state.value.copy(busy = false, imported = imported, error = failure.message ?: "导入失败")
            }
        }
    }
}
