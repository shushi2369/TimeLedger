package com.ivy.home

import com.ivy.data.model.Category
import java.time.LocalDate
import java.util.UUID

/**
 * 一句话记账解析器（纯本地正则，无云端依赖）。
 * 输入如 "昨天打车12元"、"工资到账8000"、"午饭花了35.5"，
 * 解析出金额、收支方向、日期与分类建议。
 */
object OneLineParser {

    data class Parsed(
        val amount: Double,
        val isIncome: Boolean,
        val date: LocalDate,
        val categoryId: UUID?,   // 匹配到的用户分类，null = 未分类
        val categoryName: String?, // 分类名（预览用）
    )

    /** 口语角数：35块5 / 35块毛5 = 35.5 */
    private val AMOUNT_JIAO = Regex("(\\d+)块(?:毛)?([0-9])(?![0-9.])")
    /** 带单位的金额优先：12元 / 35.5块 / 1,000块钱 */
    private val AMOUNT_WITH_UNIT = Regex("(\\d{1,3}(?:,\\d{3})+(?:\\.\\d+)?|\\d+(?:\\.\\d+)?)\\s*(?:元|块|块钱)")
    /** 裸数字（无单位时取最后一个，避免误抓日期里的数字） */
    private val BARE_NUMBER = Regex("(\\d+(?:\\.\\d+)?)")

    /** 收入信号词（fork 补：退款应记收入） */
    private val INCOME_WORDS =
        listOf("到账", "收入", "工资", "红包", "收款", "收到", "报销", "进账", "奖金", "分红", "退款")

    /** 关键词组 → 类别候选名（按用户已有类别名匹配，未命中则未分类） */
    private val CATEGORY_HINTS: List<Pair<List<String>, List<String>>> = listOf(
        listOf("打车", "出租车", "滴滴", "地铁", "公交", "加油", "高铁", "火车", "机票", "飞机", "停车", "顺风车")
            to listOf("交通", "出行"),
        listOf("饭", "餐", "吃", "喝", "奶茶", "咖啡", "外卖", "菜", "零食", "早点", "早饭", "午饭", "晚饭", "宵夜", "饮料", "酒")
            to listOf("食物和饮料", "餐饮", "吃饭"),
        listOf("买", "购物", "淘宝", "京东", "拼多多", "衣服", "鞋", "超市") to listOf("购物"),
        listOf("游戏", "电影", "电影票", "ktv", "KTV", "娱乐", "旅游", "门票") to listOf("娱乐"),
        listOf("药", "医院", "看病", "挂号", "体检") to listOf("医疗", "健康"),
        listOf("房租", "水电", "物业", "燃气", "宽带") to listOf("居住", "住房", "房租"),
        listOf("话费", "流量", "网费", "充值") to listOf("通讯", "话费"),
    )

    fun parse(text: String, categories: List<Category>, today: LocalDate): Parsed? {
        val t = text.trim()
        if (t.isEmpty()) return null

        // 金额：口语角数（35块5）> 带单位（千分位去逗号）> 最后一个独立数字
        val amount: Double = AMOUNT_JIAO.find(t)
            ?.let { m ->
                val yuan = m.groupValues[1].toDoubleOrNull() ?: return@let null
                val jiao = m.groupValues[2].toDoubleOrNull() ?: 0.0
                yuan + jiao / 10.0
            }
            ?: AMOUNT_WITH_UNIT.find(t)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()
            ?: BARE_NUMBER.findAll(t).lastOrNull()?.groupValues?.get(1)?.toDoubleOrNull()
            ?: return null
        if (amount <= 0.0) return null

        // 收支方向
        val isIncome = INCOME_WORDS.any { it in t }

        // 日期
        val date = when {
            "大前天" in t -> today.minusDays(3)
            "前天" in t -> today.minusDays(2)
            "昨天" in t -> today.minusDays(1)
            else -> today
        }

        // 分类：关键词组命中 → 在用户类别里按名称匹配
        var categoryId: UUID? = null
        var categoryName: String? = null
        for ((keywords, candidateNames) in CATEGORY_HINTS) {
            if (keywords.any { it in t }) {
                val match = categories.firstOrNull { cat ->
                    val cn = cat.name.value
                    candidateNames.any { cand -> cn.contains(cand) || cand.contains(cn) }
                }
                if (match != null) {
                    categoryId = match.id.value
                    categoryName = match.name.value
                }
                break
            }
        }

        return Parsed(
            amount = amount,
            isIncome = isIncome,
            date = date,
            categoryId = categoryId,
            categoryName = categoryName,
        )
    }
}
