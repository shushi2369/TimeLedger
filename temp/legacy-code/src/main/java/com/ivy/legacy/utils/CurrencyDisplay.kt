package com.ivy.legacy.utils

/**
 * fork 增补（2026-09-28）：货币代码 → 中文易读名称，用于界面显示。
 * CNY 显示为"元"（而非"人民币"），USD 显示为"美元"等；
 * 未映射的币种回退到 ICU 中文名，再回退到原始代码。
 * 仅用于显示，不影响汇率、导入导出等使用货币代码的逻辑。
 */
private val currencyChineseDisplayNames = mapOf(
    "CNY" to "元",
    "USD" to "美元",
    "EUR" to "欧元",
    "JPY" to "日元",
    "HKD" to "港元",
    "TWD" to "新台币",
    "GBP" to "英镑",
    "AUD" to "澳元",
    "CAD" to "加元",
    "KRW" to "韩元",
    "SGD" to "新加坡元",
    "RUB" to "卢布",
    "THB" to "泰铢",
    "CHF" to "瑞郎",
    "INR" to "卢比",
    "VND" to "越南盾",
    "NZD" to "纽元",
    "MYR" to "林吉特",
    "PHP" to "比索",
    "BRL" to "雷亚尔",
)

fun currencyDisplay(code: String?): String {
    if (code.isNullOrBlank()) return code ?: ""
    val upper = code.uppercase().trim()
    currencyChineseDisplayNames[upper]?.let { return it }
    return try {
        android.icu.util.Currency.getInstance(upper)
            ?.getDisplayName(java.util.Locale.SIMPLIFIED_CHINESE)
            ?: code
    } catch (e: Exception) {
        code
    }
}

/** 判断显示名是否为 CJK 文本，用于决定货币名放在金额前还是后。 */
fun currencyDisplayIsChinese(name: String): Boolean =
    name.any { it.code in 0x4E00..0x9FFF }
