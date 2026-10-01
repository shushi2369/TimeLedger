package com.ivy.importdata.csvimport.domestic

import com.ivy.base.model.TransactionType
import com.opencsv.CSVReaderBuilder
import java.io.StringReader
import java.math.BigDecimal
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.Charset
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

enum class DomesticSource(val label: String) { ALIPAY("支付宝"), WECHAT("微信支付") }

data class DomesticRow(
    val id: UUID,
    val type: TransactionType,
    val amount: BigDecimal,
    val date: LocalDateTime,
    val merchant: String,
    val description: String,
    val orderId: String,
    val refund: Boolean,
)

data class DomesticParse(
    val rows: List<DomesticRow>,
    val skipped: List<String>,
    val errors: List<String>,
)

/** Only recognizable provider headers are accepted; ambiguous files must not be imported. */
object DomesticCsvParser {
    private val dateFormats = listOf("yyyy-MM-dd HH:mm:ss", "yyyy/MM/dd HH:mm:ss", "yyyy-MM-dd HH:mm")
        .map(DateTimeFormatter::ofPattern)

    fun parse(bytes: ByteArray, source: DomesticSource): DomesticParse {
        val candidates = if (bytes.startsWith(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))) {
            listOf("UTF-8" to 3)
        } else if (bytes.startsWith(byteArrayOf(0xFF.toByte(), 0xFE.toByte()))) {
            listOf("UTF-16LE" to 2)
        } else if (bytes.startsWith(byteArrayOf(0xFE.toByte(), 0xFF.toByte()))) {
            listOf("UTF-16BE" to 2)
        } else {
            listOf("UTF-8" to 0, "GB18030" to 0, "UTF-16LE" to 0, "UTF-16BE" to 0)
        }
        for ((encoding, offset) in candidates) {
            val text = try {
                Charset.forName(encoding).newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, offset, bytes.size - offset)).toString()
            } catch (_: CharacterCodingException) {
                continue
            }
            if ('\u0000' in text) continue
            val records = try {
                CSVReaderBuilder(StringReader(text.removePrefix("\uFEFF"))).build().use { reader ->
                    reader.readAll().map { it.toList() }
                }
            } catch (_: Exception) {
                continue
            }
            val headerIndex = records.indexOfFirst { header(it, source) != null }
            if (headerIndex < 0) continue
            val columns = header(records[headerIndex], source)!!
            val rows = mutableListOf<DomesticRow>()
            val skipped = mutableListOf<String>()
            val errors = mutableListOf<String>()
            for ((index, record) in records.drop(headerIndex + 1).withIndex()) {
                if (record.all { it.isBlank() } || record.firstOrNull()?.startsWith("----") == true) continue
                val line = "记录 ${index + 1}"
                fun field(name: String): String = record.getOrNull(columns[name] ?: -1)?.trim().orEmpty()
                val direction = field("direction")
                val status = field("status")
                val refund = status.contains("退款") || field("kind").contains("退款") || direction.contains("退款")
                if (status.contains("关闭") || status.contains("撤销") || status.contains("失败") ||
                    status.contains("未支付") || status.contains("待支付") || status.contains("处理中") ||
                    (!refund && direction in setOf("不计收支", "中性交易", "其他", "--", ""))) {
                    skipped += "$line：未完成或不计收支"
                    continue
                }
                val type = when {
                    refund -> TransactionType.INCOME
                    direction == "支出" -> TransactionType.EXPENSE
                    direction == "收入" -> TransactionType.INCOME
                    else -> null
                }
                if (type == null || (!refund && !isCompleted(status))) {
                    skipped += "$line：不支持的状态或收支方向"
                    continue
                }
                val order = field("order").ifBlank { field("merchantOrder") }
                if (order.isBlank() || order == "--" || order == "/") {
                    skipped += "$line：缺少交易单号，未导入以避免重复"
                    continue
                }
                val amount = field("amount").replace(",", "").replace("¥", "")
                    .replace("￥", "").replace("元", "").trim().toBigDecimalOrNull()?.abs()
                val date = dateFormats.firstNotNullOfOrNull { format ->
                    runCatching { LocalDateTime.parse(field("date"), format) }.getOrNull()
                }
                if (amount == null || amount <= BigDecimal.ZERO || date == null) {
                    errors += "$line：金额或日期无效"
                    continue
                }
                val key = "domestic:${source.name}:$order:${if (refund) "refund" else "payment"}"
                rows += DomesticRow(
                    id = UUID.nameUUIDFromBytes(key.toByteArray(Charsets.UTF_8)),
                    type = type, amount = amount, date = date,
                    merchant = field("merchant"),
                    description = listOf(field("product"), field("memo"))
                        .filter { it.isNotBlank() && it != "--" }.joinToString(" · "),
                    orderId = order, refund = refund,
                )
            }
            return DomesticParse(rows, skipped, errors)
        }
        throw IllegalArgumentException("无法识别${source.label}账单的编码或表头，请选择原始 CSV 文件")
    }

    private fun isCompleted(status: String) = status.contains("成功") || status.contains("完成") ||
        status.contains("已收钱") || status.contains("已转账") || status.contains("已存入")

    private fun header(row: List<String>, source: DomesticSource): Map<String, Int>? {
        val names = row.map { it.trim().removePrefix("\uFEFF") }
        fun find(vararg aliases: String): Int = names.indexOfFirst { it in aliases }
        val columns = mapOf(
            "date" to find("交易创建时间", "交易时间"),
            "amount" to find("金额(元)", "金额（元）"),
            "direction" to find("收/支", "收／支"),
            "status" to find("交易状态", "当前状态"),
            "order" to find(if (source == DomesticSource.ALIPAY) "交易号" else "交易单号"),
            "merchantOrder" to find(if (source == DomesticSource.ALIPAY) "商家订单号" else "商户单号"),
            "merchant" to find("交易对方"),
            "product" to find("商品名称", "商品"),
            "memo" to find("备注"),
            "kind" to find("交易类型"),
        )
        val providerColumn = if (source == DomesticSource.ALIPAY) "交易状态" else "当前状态"
        return columns.takeIf { names.contains(providerColumn) &&
            listOf("date", "amount", "direction", "status", "order").all { columns[it]!! >= 0 } }
    }
}

private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
    size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }
