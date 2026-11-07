package app.silk.wallet.domain

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** 卡号、有效期的纯函数工具：校验、分组、打码、到期状态 */
object CardMath {

    fun digits(s: String): String = s.filter(Char::isDigit)

    /** Luhn 校验（模 10） */
    fun luhn(number: String): Boolean {
        val d = digits(number)
        if (d.length < 12) return false
        var sum = 0
        d.reversed().forEachIndexed { i, c ->
            var n = c - '0'
            if (i % 2 == 1) {
                n *= 2
                if (n > 9) n -= 9
            }
            sum += n
        }
        return sum % 10 == 0
    }

    /** 分组长度：运通 4-6-5，大来 14 位 4-6-4，其余每 4 位一组 */
    fun groups(brand: CardBrand, length: Int): List<Int> = when {
        brand == CardBrand.AMEX -> listOf(4, 6, 5)
        brand == CardBrand.DINERS && length == 14 -> listOf(4, 6, 4)
        else -> List((length + 3) / 4) { 4 }
    }

    fun format(number: String, brand: CardBrand = CardBrand.detect(number)): String {
        val d = digits(number)
        val out = StringBuilder()
        var i = 0
        for (g in groups(brand, d.length)) {
            if (i >= d.length) break
            if (out.isNotEmpty()) out.append(' ')
            out.append(d, i, minOf(i + g, d.length))
            i += g
        }
        if (i < d.length) out.append(' ').append(d.substring(i))
        return out.toString()
    }

    /** 打码：只留末四位，前面按四位一组显示圆点 */
    fun mask(number: String): String {
        val d = digits(number)
        if (d.length <= 4) return d
        val hidden = (d.length - 4 + 3) / 4
        return List(hidden.coerceAtMost(3)) { "••••" }.joinToString(" ") + " " + d.takeLast(4)
    }

    fun expiryText(month: Int?, year: Int?): String =
        if (month == null || year == null) "--/--" else "%02d/%02d".format(month, year % 100)

    enum class Expiry { NONE, VALID, SOON, EXPIRED }

    /** 卡在有效期所在月的最后一天当天仍可用 */
    private fun lastDay(month: Int, year: Int): LocalDate = YearMonth.of(year, month).atEndOfMonth()

    fun daysLeft(month: Int?, year: Int?, today: LocalDate = LocalDate.now()): Long? {
        if (month == null || year == null || month !in 1..12) return null
        return ChronoUnit.DAYS.between(today, lastDay(month, year))
    }

    fun expiry(month: Int?, year: Int?, noticeDays: Int, today: LocalDate = LocalDate.now()): Expiry {
        val left = daysLeft(month, year, today) ?: return Expiry.NONE
        return when {
            left < 0 -> Expiry.EXPIRED
            left <= noticeDays -> Expiry.SOON
            else -> Expiry.VALID
        }
    }
}
