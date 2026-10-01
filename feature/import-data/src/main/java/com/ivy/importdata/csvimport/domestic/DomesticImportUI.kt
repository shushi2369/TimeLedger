package com.ivy.importdata.csvimport.domestic

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ivy.navigation.navigation

@Composable
fun DomesticImportUI(source: DomesticSource, onFinish: (Boolean) -> Unit) {
    val viewModel: DomesticImportViewModel = viewModel()
    val state by viewModel.state.collectAsState()
    if (state.source != source) viewModel.selectSource(source)
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.open(context, uri)
    }
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp)) {
        val nav = navigation()
        Button(onClick = { nav.onBackPressed() }) { Text("返回") }
        Text("导入${source.label}账单")
        Spacer(Modifier.height(12.dp))
        Text("选择导入账户")
        LazyColumn(Modifier.weight(1f)) {
            items(state.accounts) { account ->
                Row(
                    Modifier.fillMaxWidth().clickable { viewModel.selectAccount(account.id) },
                    horizontalArrangement = Arrangement.Start,
                ) {
                    RadioButton(
                        selected = state.accountId == account.id,
                        onClick = { viewModel.selectAccount(account.id) },
                    )
                    Text(account.name.value, Modifier.padding(top = 12.dp))
                }
            }
            item {
                val preview = state.preview
                if (preview != null) {
                    Spacer(Modifier.height(16.dp))
                    Text("待导入 ${preview.ready.size} · 跳过 ${preview.skipCount} · 错误 ${preview.parsed.errors.size}")
                    Text("退款单独记为收入；无交易单号的记录不会导入。")
                    preview.ready.take(20).forEach { row ->
                        Text("${row.date}  ${if (row.refund) "退款" else if (row.type == com.ivy.base.model.TransactionType.INCOME) "收入" else "支出"}  ${row.amount}  ${row.merchant}")
                    }
                    preview.parsed.errors.take(5).forEach { Text(it) }
                    preview.parsed.skipped.take(5).forEach { Text(it) }
                }
                state.error?.let { Text(it) }
                state.imported?.let { Text("已导入 $it 笔交易") }
            }
        }
        if (state.imported != null) {
            Button(onClick = { onFinish(state.imported!! > 0) }, modifier = Modifier.fillMaxWidth()) {
                Text("完成")
            }
        } else {
            Button(
                onClick = { picker.launch(arrayOf("text/*", "application/csv", "application/octet-stream")) },
                enabled = state.accountId != null && !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("选择 CSV 文件") }
            if (state.preview != null) {
                Button(
                    onClick = viewModel::confirm,
                    enabled = !state.busy && state.preview!!.ready.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("确认导入 ${state.preview!!.ready.size} 笔") }
            }
        }
        if (state.busy) Text("处理中…")
    }
}
