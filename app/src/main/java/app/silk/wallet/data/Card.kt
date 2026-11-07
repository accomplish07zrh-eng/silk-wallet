package app.silk.wallet.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 一张卡。整库由 SQLCipher 加密，卡号 / CVV 以明文列存于密文数据库内。 */
@Entity(tableName = "cards", indices = [Index(value = ["number"], unique = true)])
data class Card(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val holder: String,
    val number: String,
    val expMonth: Int? = null,
    val expYear: Int? = null,
    val cvv: String? = null,
    /** CardBrand 枚举名 */
    val brand: String,
    val bank: String = "",
    val bankCode: String? = null,
    /** CardKind.key */
    val kind: String? = null,
    val note: String = "",
    val styleId: String? = null,
    val archived: Boolean = false,
    /** 手动排序位置，越小越靠前 */
    val position: Long = 0,
    val createdAt: Long,
    val updatedAt: Long,
)

enum class CardKind(val key: String, val label: String) {
    DEBIT("debit", "借记卡"),
    CREDIT("credit", "信用卡"),
    PREPAID("prepaid", "预付卡"),
    VIRTUAL("virtual", "虚拟卡");

    companion object {
        fun of(key: String?): CardKind? = entries.firstOrNull { it.key == key }
    }
}

enum class SortMode(val label: String) {
    MANUAL("手动排序"),
    RECENT("最近添加"),
    BANK("按银行"),
    EXPIRY("按到期日"),
}
