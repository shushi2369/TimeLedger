package com.ivy.legacy

import android.net.Uri
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ivy.design.IvyContext
import com.ivy.base.legacy.SharedPrefs
import com.ivy.data.model.Category
import com.ivy.legacy.datamodel.Account
import java.time.LocalDate
import java.time.LocalTime
import java.util.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Deprecated("Legacy code. Don't use it, please.")
@Singleton
class IvyWalletCtx @Inject constructor() : IvyContext() {
    // ------------------------------------------ State ---------------------------------------------
    @Deprecated("Legacy code. Don't use it, please.")
    var startDayOfMonth = 1
        private set

    @Deprecated("Legacy code. Don't use it, please.")
    fun setStartDayOfMonth(day: Int) {
        startDayOfMonth = day
    }

    // ---------------------- Optimization  ----------------------------
    @Deprecated("Legacy code. Don't use it, please.")
    val categoryMap: MutableMap<UUID, Category> = mutableMapOf()

    @Deprecated("Legacy code. Don't use it, please.")
    val accountMap: MutableMap<UUID, Account> = mutableMapOf()
    // ---------------------- Optimization  ----------------------------

    @Deprecated("Legacy code. Don't use it, please.")
    var dataBackupCompleted = false

    @Deprecated("Legacy code. Don't use it, please.")
    fun initStartDayOfMonthInMemory(sharedPrefs: SharedPrefs): Int {
        startDayOfMonth = sharedPrefs.getInt(SharedPrefs.START_DATE_OF_MONTH, 1)
        return startDayOfMonth
    }

    @Deprecated("Legacy code. Don't use it, please.")
    var selectedPeriod: com.ivy.legacy.data.model.TimePeriod =
        com.ivy.legacy.data.model.TimePeriod.currentMonth(
            startDayOfMonth = startDayOfMonth // this is default value
        )

    @Deprecated("Legacy code. Don't use it, please.")
    private var selectedPeriodInitialized = false

    @Deprecated("Legacy code. Don't use it, please.")
    fun initSelectedPeriodInMemory(
        startDayOfMonth: Int,
        forceReinitialize: Boolean = false
    ): com.ivy.legacy.data.model.TimePeriod {
        if (!selectedPeriodInitialized || forceReinitialize) {
            selectedPeriod = com.ivy.legacy.data.model.TimePeriod.currentMonth(
                startDayOfMonth = startDayOfMonth
            )
            selectedPeriodInitialized = true
        }

        return selectedPeriod
    }

    @Deprecated("Legacy code. Don't use it, please.")
    fun updateSelectedPeriodInMemory(period: com.ivy.legacy.data.model.TimePeriod) {
        selectedPeriod = period
    }

    @Deprecated("Legacy code. Don't use it, please.")
    var transactionsListState: LazyListState? = null

    @Deprecated("Legacy code. Don't use it, please.")
    var categoriesListState: LazyListState? = null

    @Deprecated("Legacy code. Don't use it, please.")
    var accountsListState: LazyListState? = null

    @Deprecated("Legacy code. Don't use it, please.")
    var loanListState: LazyListState? = null

    @Deprecated("Legacy code. Don't use it, please.")
    var mainTab by mutableStateOf(com.ivy.legacy.data.model.MainTab.HOME)
        private set

    @Deprecated("Legacy code. Don't use it, please.")
    fun selectMainTab(tab: com.ivy.legacy.data.model.MainTab) {
        mainTab = tab
    }

    @Deprecated("Legacy code. Don't use it, please.")
    var moreMenuExpanded = false
        private set

    @Deprecated("Legacy code. Don't use it, please.")
    fun setMoreMenuExpanded(expanded: Boolean) {
        moreMenuExpanded = expanded
    }
    // ------------------------------------------ State ---------------------------------------------

    // Activity help -------------------------------------------------------------------------------
    @Deprecated("Legacy code. Don't use it, please.")
    lateinit var onShowDatePicker: (
        minDate: LocalDate?,
        maxDate: LocalDate?,
        initialDate: LocalDate?,
        onDatePicked: (LocalDate) -> Unit
    ) -> Unit
    lateinit var onShowTimePicker: (
        initialTime: LocalTime?,
        onDatePicked: (LocalTime) -> Unit
    ) -> Unit

    @Deprecated("Legacy code. Don't use it, please.")
    fun datePicker(
        minDate: LocalDate? = null,
        maxDate: LocalDate? = null,
        initialDate: LocalDate?,
        onDatePicked: (LocalDate) -> Unit
    ) {
        onShowDatePicker(minDate, maxDate, initialDate, onDatePicked)
    }

    @Deprecated("Legacy code. Don't use it, please.")
    fun timePicker(
        initialTime: LocalTime?,
        onTimePicked: (LocalTime) -> Unit
    ) {
        onShowTimePicker(initialTime, onTimePicked)
    }
    // Activity help -------------------------------------------------------------------------------

    // Billing -------------------------------------------------------------------------------------
    @Deprecated("Legacy code. Don't use it, please.")
    var isPremium = true // if (BuildConfig.DEBUG) Constants.PREMIUM_INITIAL_VALUE_DEBUG else false
    // Billing -------------------------------------------------------------------------------------

    @Deprecated("Legacy code. Don't use it, please.")
    lateinit var googleSignIn: (idTokenResult: (String?) -> Unit) -> Unit

    @Deprecated("Legacy code. Don't use it, please.")
    lateinit var createNewFile: (fileName: String, onCreated: (Uri) -> Unit) -> Unit

    @Deprecated("Legacy code. Don't use it, please.")
    lateinit var openFile: (onOpened: (Uri) -> Unit) -> Unit

    // Snackbar（全局，跨屏幕）----------------------------------------------------------------------
    data class IvySnackbar(
        val message: String,
        val actionLabel: String? = null,
        val onAction: (() -> Unit)? = null,
    )

    private val _snackbarEvents = kotlinx.coroutines.flow.MutableSharedFlow<IvySnackbar>(
        extraBufferCapacity = 4
    )
    val snackbarEvents = _snackbarEvents.asSharedFlow()

    /**
     * 全局 Snackbar：任意屏幕发出，根布局统一展示。
     * onAction 在用户点击动作时回调（发出方页面可能已销毁，请勿捕获其 ViewModel/作用域）。
     */
    fun showSnackbar(
        message: String,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null,
    ) {
        _snackbarEvents.tryEmit(IvySnackbar(message, actionLabel, onAction))
    }

    // 数据变更信号：后台动作（如撤销）改库后，观察者（主页等）据此刷新
    private val _dataVersion = kotlinx.coroutines.flow.MutableStateFlow(0L)
    val dataVersion = _dataVersion.asStateFlow()
    fun notifyDataChanged() {
        _dataVersion.value += 1
    }

    // Testing --------------------------------------------------------------------------------------
    @Deprecated("Legacy code. Don't use it, please.")
    fun reset() {
        mainTab = com.ivy.legacy.data.model.MainTab.HOME
        startDayOfMonth = 1
        isPremium = true
        transactionsListState = null
        categoriesListState = null
        accountsListState = null
    }
}
