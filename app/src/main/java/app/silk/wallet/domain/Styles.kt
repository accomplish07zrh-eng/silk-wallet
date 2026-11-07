package app.silk.wallet.domain

import app.silk.wallet.R
import androidx.compose.ui.graphics.Color

/**
 * 卡面视觉预设：三段渐变底色 + 着色器特效 + 稀有度。
 * styleId 为空时按发卡行 / 卡组织自动挑一款基础配色。
 */
data class CardStylePreset(
    val id: String,
    val label: String,
    val colors: List<Color>,
    val shimmer: Boolean = true,
    /** 浅色卡面（如鎏金）使用深色文字 */
    val darkText: Boolean = false,
    /** 稀有度：基础配色为 COMMON，其余只能抽卡获得 */
    val rarity: Rarity = Rarity.COMMON,
    /** 着色器特效编号，见 FlowShader 的 uMode */
    val effect: Int = CardEffect.NONE,
    /** 贴图底（drawable 资源）：着色器在贴图之上叠光效，用于手绘 / 生成底图的皮肤 */
    val texture: Int? = null,
)

/**
 * 抽卡稀有度：权重决定抽中概率；days 为抽到后的使用期（null 表示永久）。
 * 越稀有越短：稀有永久，史诗 3 天，传说 1 天。
 */
enum class Rarity(val label: String, val weight: Int, val glow: Color, val days: Int?) {
    COMMON("普通", 0, Color(0xFFBFC3CC), null),
    RARE("稀有", 60, Color(0xFF5AA9FF), null),
    EPIC("史诗", 30, Color(0xFFB36BFF), 3),
    LEGENDARY("传说", 10, Color(0xFFFFC857), 1),
}

/**
 * 抽卡皮肤的当前使用权：id → 到期时间（毫秒，Long.MAX_VALUE 为永久）。
 * 由 DrawStore 维护；样式解析时据此判断皮肤是否仍可用，过期即回落到自动配色。
 */
object SkinAccess {
    @Volatile
    var expiries: Map<String, Long> = emptyMap()

    fun isActive(id: String, now: Long = System.currentTimeMillis()): Boolean = (expiries[id] ?: 0L) > now
}

/** 卡面着色器特效（与 FlowShader 中 uMode 的分支一一对应） */
object CardEffect {
    const val NONE = 0
    const val HOLO = 1
    const val STARS = 2
    const val GOLD = 3
    const val AURORA = 4
    const val INK = 5
    const val CRACKLE = 6
    const val LAVA = 7
    const val NEON = 8
    const val PETALS = 9
    const val SCALES = 10
    const val VORTEX = 11
    const val CHROME = 12
    /** 贴图鳞片 + 随倾斜流转的虹彩 + 鳞缘扫光 + 游动的金色光带与星尘 */
    const val DRAGON_SILK = 13
}

object CardStyles {

    // 配色原则：同色相内由亮到暗的三段，饱和度收敛，避免荧光感；
    // 高光与质感交给卡面的光泽层，渐变本身只负责“底色”
    val presets: List<CardStylePreset> = listOf(
        // —— 银行主题色 ——
        CardStylePreset("icbc", "工行红", listOf(Color(0xFFC9383F), Color(0xFF8C1C25), Color(0xFF470B12))),
        CardStylePreset("abc", "农行青", listOf(Color(0xFF2A8C80), Color(0xFF125A52), Color(0xFF06302C))),
        CardStylePreset("boc", "中行绛红", listOf(Color(0xFFA0303A), Color(0xFF62141C), Color(0xFF2E070B))),
        CardStylePreset("ccb", "建行蓝", listOf(Color(0xFF3473D1), Color(0xFF173F88), Color(0xFF0A1D47))),
        CardStylePreset("bocom", "交行深蓝", listOf(Color(0xFF34588F), Color(0xFF172C50), Color(0xFF0A1428))),
        CardStylePreset("cmb", "招行红金", listOf(Color(0xFFB8333A), Color(0xFF731A1E), Color(0xFF3B0B0D))),
        CardStylePreset("psbc", "邮储绿", listOf(Color(0xFF337E52), Color(0xFF184A2E), Color(0xFF0A2716))),
        CardStylePreset("citic", "中信绯红", listOf(Color(0xFFAA2E36), Color(0xFF6C161D), Color(0xFF36090D))),
        CardStylePreset("ceb", "光大紫金", listOf(Color(0xFF6E48A3), Color(0xFF3F216B), Color(0xFF1E0E38))),
        CardStylePreset("cib", "兴业蓝", listOf(Color(0xFF2A6FAE), Color(0xFF12426E), Color(0xFF07213A))),
        CardStylePreset("pab", "平安橙", listOf(Color(0xFFDD6A35), Color(0xFFA43E17), Color(0xFF561D08))),

        // —— 通用主题 ——
        CardStylePreset("midnight", "午夜黑", listOf(Color(0xFF34363F), Color(0xFF1C1D23), Color(0xFF0C0C10))),
        CardStylePreset("aurora", "极光", listOf(Color(0xFF4FA0B5), Color(0xFF5B4FA0), Color(0xFF221A45))),
        CardStylePreset("ocean", "深海蓝", listOf(Color(0xFF337FA0), Color(0xFF1A4A6C), Color(0xFF0B2136))),
        CardStylePreset("sunset", "落日", listOf(Color(0xFFDB7A5E), Color(0xFFA9415F), Color(0xFF4A1A39))),
        CardStylePreset("forest", "松林绿", listOf(Color(0xFF2F8C77), Color(0xFF195C4E), Color(0xFF0B2F28))),
        CardStylePreset("violet", "星夜紫", listOf(Color(0xFF52337A), Color(0xFF2F1B4A), Color(0xFF150B24))),
        CardStylePreset("gold", "鎏金", listOf(Color(0xFFF4E6B8), Color(0xFFDCC07A), Color(0xFFB28F45)), darkText = true),
        CardStylePreset("rose", "玫瑰金", listOf(Color(0xFFF3D5CC), Color(0xFFE0AB9B), Color(0xFFB97E6B)), darkText = true),
        CardStylePreset("titanium", "钛金", listOf(Color(0xFF9EA2AA), Color(0xFF656970), Color(0xFF34363B))),
        CardStylePreset("obsidian", "曜石黑", listOf(Color(0xFF26262B), Color(0xFF131316), Color(0xFF050506))),
        CardStylePreset("glacier", "冰川", listOf(Color(0xFFF1F4F8), Color(0xFFD3DCE7), Color(0xFFAAB7C8)), darkText = true),
    )

    /**
     * 抽卡皮肤：只能在「抽皮肤」里解锁，解锁后与基础配色一样出现在样式选择条里。
     * id 以 skin_ 开头，已保存到卡片上的 styleId 永不改名。
     */
    val skins: List<CardStylePreset> = listOf(
        // 稀有
        CardStylePreset(
            "skin_pearl_holo", "晨雾镭射",
            listOf(Color(0xFFF5F2FA), Color(0xFFDCD6EC), Color(0xFFB9B4CF)),
            darkText = true, rarity = Rarity.RARE, effect = CardEffect.HOLO,
        ),
        CardStylePreset(
            "skin_abyss_glow", "深海荧光",
            listOf(Color(0xFF0E5A6E), Color(0xFF07303F), Color(0xFF02121A)),
            rarity = Rarity.RARE, effect = CardEffect.STARS,
        ),
        CardStylePreset(
            "skin_rose_dawn", "玫瑰晨曦",
            listOf(Color(0xFFFBEDEE), Color(0xFFF1D2D8), Color(0xFFDDB2BD)),
            darkText = true, rarity = Rarity.RARE, effect = CardEffect.HOLO,
        ),
        CardStylePreset(
            "skin_ink", "水墨山河",
            listOf(Color(0xFFF4F1E9), Color(0xFFE6E1D6), Color(0xFFD2CCBE)),
            darkText = true, rarity = Rarity.RARE, effect = CardEffect.INK,
        ),
        CardStylePreset(
            "skin_celadon", "青瓷冰裂",
            listOf(Color(0xFFB9D8C8), Color(0xFF8DBBA6), Color(0xFF5E9580)),
            darkText = true, rarity = Rarity.RARE, effect = CardEffect.CRACKLE,
        ),
        // 史诗
        CardStylePreset(
            "skin_laser", "镭射全息",
            listOf(Color(0xFFB7BCC8), Color(0xFF7E8494), Color(0xFF3E4250)),
            rarity = Rarity.EPIC, effect = CardEffect.HOLO,
        ),
        CardStylePreset(
            "skin_galaxy", "星河",
            listOf(Color(0xFF2A2F7A), Color(0xFF15164A), Color(0xFF05051A)),
            rarity = Rarity.EPIC, effect = CardEffect.STARS,
        ),
        CardStylePreset(
            "skin_lava", "熔岩",
            listOf(Color(0xFF3A1A12), Color(0xFF1C0B07), Color(0xFF080302)),
            rarity = Rarity.EPIC, effect = CardEffect.LAVA,
        ),
        CardStylePreset(
            "skin_neon", "霓虹",
            listOf(Color(0xFF2A0F45), Color(0xFF130726), Color(0xFF05020D)),
            rarity = Rarity.EPIC, effect = CardEffect.NEON,
        ),
        CardStylePreset(
            "skin_sakura", "樱吹雪",
            listOf(Color(0xFFF7C9D6), Color(0xFFE79BB2), Color(0xFFC86E8D)),
            rarity = Rarity.EPIC, effect = CardEffect.PETALS,
        ),
        // 传说
        CardStylePreset(
            "skin_black_gold", "黑金流沙",
            listOf(Color(0xFF2A2420), Color(0xFF120F0C), Color(0xFF050403)),
            rarity = Rarity.LEGENDARY, effect = CardEffect.GOLD,
        ),
        CardStylePreset(
            "skin_borealis", "北境极光",
            listOf(Color(0xFF0B2A3A), Color(0xFF07182A), Color(0xFF030812)),
            rarity = Rarity.LEGENDARY, effect = CardEffect.AURORA,
        ),
        CardStylePreset(
            "skin_dragon", "龙鳞",
            listOf(Color(0xFF2BB59A), Color(0xFF3B2A8C), Color(0xFF0A0C1A)),
            rarity = Rarity.LEGENDARY, effect = CardEffect.DRAGON_SILK, texture = R.drawable.skin_dragon,
        ),
        CardStylePreset(
            "skin_vortex", "星璇",
            listOf(Color(0xFF1C1440), Color(0xFF0C0824), Color(0xFF03020C)),
            rarity = Rarity.LEGENDARY, effect = CardEffect.VORTEX,
        ),
        CardStylePreset(
            "skin_chrome", "液态铬",
            listOf(Color(0xFFD8DCE4), Color(0xFF9AA0AC), Color(0xFF4A4F5A)),
            rarity = Rarity.LEGENDARY, effect = CardEffect.CHROME,
        ),
    )

    /** 皮肤过期后不再解析出来：穿着它的卡自动回到银行 / 卡组织的默认配色 */
    fun byId(id: String?): CardStylePreset? =
        presets.firstOrNull { it.id == id } ?: skins.firstOrNull { it.id == id && SkinAccess.isActive(it.id) }

    /** 自动配色：发卡行主题色优先，其次按卡组织 */
    private val autoByBank = mapOf(
        "ICBC" to "icbc", "ABC" to "abc", "BOC" to "boc", "CCB" to "ccb", "BOCOM" to "bocom",
        "CMB" to "cmb", "PSBC" to "psbc", "CITIC" to "citic", "CEB" to "ceb", "CIB" to "cib",
        "PAB" to "pab", "SPDB" to "ocean", "CMBC" to "forest", "CGB" to "sunset", "HXB" to "sunset",
    )

    private fun autoByBrand(brand: CardBrand): String = when (brand) {
        CardBrand.UNIONPAY -> "cmb"
        CardBrand.VISA, CardBrand.MAESTRO -> "ocean"
        CardBrand.MASTERCARD -> "obsidian"
        CardBrand.AMEX -> "titanium"
        CardBrand.JCB -> "violet"
        CardBrand.MIR, CardBrand.DISCOVER -> "forest"
        CardBrand.DINERS -> "midnight"
        CardBrand.UNKNOWN -> "midnight"
    }

    fun resolve(styleId: String?, bankCode: String?, brand: CardBrand): CardStylePreset =
        byId(styleId)
            ?: autoByBank[bankCode]?.let(::byId)
            ?: byId(autoByBrand(brand))
            ?: presets.first()
}
