package app.silk.wallet.domain

/**
 * 本地发卡行识别：常见国内银行的公开 BIN 前缀，按最长前缀匹配。
 * 只覆盖最常见的号段，识别不到时可在线查询或手动填写。
 */
object BankDirectory {

    data class Bank(val code: String, val name: String)

    private val banks = mapOf(
        "ICBC" to "中国工商银行",
        "ABC" to "中国农业银行",
        "BOC" to "中国银行",
        "CCB" to "中国建设银行",
        "BOCOM" to "交通银行",
        "PSBC" to "中国邮政储蓄银行",
        "CMB" to "招商银行",
        "CITIC" to "中信银行",
        "CEB" to "中国光大银行",
        "CIB" to "兴业银行",
        "SPDB" to "浦发银行",
        "CMBC" to "中国民生银行",
        "PAB" to "平安银行",
        "CGB" to "广发银行",
        "HXB" to "华夏银行",
    )

    private val prefixes: Map<String, String> = buildMap {
        fun add(code: String, vararg bins: String) = bins.forEach { put(it, code) }
        add("ICBC", "622202", "622208", "621226", "621225", "622200", "955880", "427018", "620200")
        add("ABC", "622848", "622845", "622846", "622841", "620059", "955990", "622823")
        add("BOC", "621661", "621660", "456351", "601382", "621785", "621786", "622760")
        add("CCB", "621700", "622700", "436742", "622280", "621284", "622707", "622166")
        add("BOCOM", "622260", "622262", "622261", "601428", "405512", "622258")
        add("PSBC", "621799", "622188", "621098", "620062", "622150", "622151")
        add("CMB", "622588", "621483", "622575", "439225", "410062", "621286", "622580")
        add("CITIC", "622690", "621771", "622691", "433670", "622692")
        add("CEB", "622660", "622663", "622665", "622666", "621489")
        add("CIB", "622908", "622909", "438588", "622901", "622902")
        add("SPDB", "622521", "622518", "622522", "622523", "621792")
        add("CMBC", "622622", "415599", "421393", "622615", "622617")
        add("PAB", "622155", "622156", "622298", "621626", "623058")
        add("CGB", "622568", "621462", "622558", "622559", "623506")
        add("HXB", "622632", "622630", "622631", "622633")
    }

    fun find(number: String): Bank? {
        val d = CardMath.digits(number)
        for (len in 8 downTo 4) {
            if (d.length < len) continue
            val code = prefixes[d.substring(0, len)] ?: continue
            return Bank(code, banks.getValue(code))
        }
        return null
    }

    fun nameOf(code: String?): String? = code?.let { banks[it] }
}
