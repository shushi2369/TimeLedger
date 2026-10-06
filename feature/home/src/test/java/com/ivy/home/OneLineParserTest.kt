package com.ivy.home

import com.ivy.data.model.Category
import com.ivy.data.model.CategoryId
import com.ivy.data.model.primitive.NotBlankTrimmedString
import com.ivy.data.model.primitive.ColorInt
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OneLineParserTest {

    private val today: LocalDate = LocalDate.of(2026, 10, 2)

    private val categories = listOf(
        cat("交通"), cat("食物和饮料"), cat("购物"), cat("娱乐"), cat("医疗"),
    )

    private fun cat(name: String) = Category(
        id = CategoryId(UUID.randomUUID()),
        name = NotBlankTrimmedString.from(name).getOrNull()!!,
        color = ColorInt(0xFF2196F3.toInt()),
        icon = null,
        orderNum = 1.0,
    )

    @Test
    fun `昨天打车12元 解析为昨天 交通 支出12`() {
        val p = OneLineParser.parse("昨天打车12元", categories, today)!!
        assertEquals(12.0, p.amount, 0.001)
        assertEquals(false, p.isIncome)
        assertEquals(today.minusDays(1), p.date)
        assertEquals("交通", p.categoryName)
    }

    @Test
    fun `千分位金额 1,000元 解析为1000`() {
        val p = OneLineParser.parse("买手机花了1,000元", categories, today)!!
        assertEquals(1000.0, p.amount, 0.001)
    }

    @Test
    fun `退款20元 记为收入`() {
        val p = OneLineParser.parse("退款20元到账", categories, today)!!
        assertEquals(20.0, p.amount, 0.001)
        assertEquals(true, p.isIncome)
    }

    @Test
    fun `午饭花了35块5 解析为食物和饮料`() {
        val p = OneLineParser.parse("午饭花了35块5", categories, today)!!
        // fork 修复：口语角数 35块5 = 35.5（原被截成 35 少记 5 毛）
        assertEquals(35.5, p.amount, 0.001)
        assertEquals("食物和饮料", p.categoryName)
        assertEquals(today, p.date)
    }

    @Test
    fun `工资到账8000 解析为收入`() {
        val p = OneLineParser.parse("工资到账8000", categories, today)!!
        assertEquals(8000.0, p.amount, 0.001)
        assertTrue(p.isIncome)
        assertEquals(today, p.date)
    }

    @Test
    fun `奶茶18元 解析为娱乐外分类时用名称命中`() {
        val p = OneLineParser.parse("奶茶18元", categories, today)!!
        assertEquals(18.0, p.amount, 0.001)
        // "奶茶" 关键词组候选含"食物和饮料"
        assertEquals("食物和饮料", p.categoryName)
    }

    @Test
    fun `无金额返回null`() {
        assertNull(OneLineParser.parse("今天天气不错", categories, today))
    }

    @Test
    fun `零金额返回null`() {
        assertNull(OneLineParser.parse("0元", categories, today))
    }

    @Test
    fun `无匹配类别时为未分类`() {
        val p = OneLineParser.parse("买了个奇怪的东西 66元", listOf(cat("旅行")), today)!!
        assertEquals(66.0, p.amount, 0.001)
        assertNull(p.categoryId)
    }

    @Test
    fun `小数金额与四舍五入无关 解析原值`() {
        val p = OneLineParser.parse("打车13.7元", categories, today)!!
        assertEquals(13.7, p.amount, 0.001)
        assertEquals("交通", p.categoryName)
    }
}
