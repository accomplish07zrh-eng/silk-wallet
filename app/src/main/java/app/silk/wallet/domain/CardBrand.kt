package app.silk.wallet.domain

/**
 * 卡组织。按公开的 IIN（发卡机构识别号）区间识别：
 * 先匹配更具体的区间，银联 62 优先于 Discover 的联名区间。
 */
enum class CardBrand(val displayName: String, private val ranges: List<IntRange>) {
    UNIONPAY("银联 UnionPay", listOf(62..62, 81..81)),
    MIR("Mir", listOf(2200..2204)),
    AMEX("American Express", listOf(34..34, 37..37)),
    JCB("JCB", listOf(3528..3589)),
    DINERS("Diners Club", listOf(300..305, 36..36, 38..39)),
    MAESTRO("Maestro", listOf(5018..5018, 5020..5020, 5038..5038, 5893..5893, 6304..6304, 6759..6759, 6761..6763)),
    MASTERCARD("Mastercard", listOf(51..55, 2221..2720)),
    DISCOVER("Discover", listOf(6011..6011, 644..649, 65..65)),
    VISA("Visa", listOf(4..4)),
    UNKNOWN("未知", emptyList());

    private fun matches(digits: String): Boolean = ranges.any { range ->
        val len = range.first.toString().length
        digits.length >= len && digits.substring(0, len).toInt() in range
    }

    companion object {
        fun detect(number: String): CardBrand {
            val digits = number.filter(Char::isDigit)
            if (digits.isEmpty()) return UNKNOWN
            return entries.firstOrNull { it != UNKNOWN && it.matches(digits) } ?: UNKNOWN
        }

        fun of(name: String?): CardBrand = entries.firstOrNull { it.name == name } ?: UNKNOWN
    }
}
