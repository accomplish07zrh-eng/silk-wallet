package app.silk.wallet.ui.edit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.silk.wallet.data.Card
import app.silk.wallet.data.CardRepository
import app.silk.wallet.data.DuplicateCardException
import app.silk.wallet.data.Prefs
import app.silk.wallet.domain.BankDirectory
import app.silk.wallet.domain.CardBrand
import app.silk.wallet.domain.CardMath
import app.silk.wallet.net.BinLookup
import app.silk.wallet.nfc.NfcDraft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

sealed interface Lookup {
    data object Idle : Lookup
    data object Loading : Lookup
    data class Done(val message: String) : Lookup
    data class Failed(val message: String) : Lookup
}

/** 添加 / 编辑卡片的表单状态。字段用 Compose 状态，输入法组合不会被异步回流打断 */
class EditViewModel(
    private val repo: CardRepository,
    private val prefs: Prefs,
    private val cardId: Long,
    draft: NfcDraft?,
) : ViewModel() {

    var loaded by mutableStateOf(cardId <= 0)
        private set
    var holder by mutableStateOf("")
    var number by mutableStateOf("")
        private set
    /** 有效期原始数字 MMYY（最多 4 位） */
    var expiry by mutableStateOf("")
    var cvv by mutableStateOf("")
    var bank by mutableStateOf("")
        private set
    var bankCode by mutableStateOf<String?>(null)
        private set
    var note by mutableStateOf("")
    var kind by mutableStateOf<String?>(null)
    var styleId by mutableStateOf<String?>(null)
    var brandOverride by mutableStateOf<CardBrand?>(null)
    var lookup by mutableStateOf<Lookup>(Lookup.Idle)
        private set
    var saving by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private var bankTouched = false
    private var original: Card? = null

    val detected: CardBrand get() = CardBrand.detect(number)
    val brand: CardBrand get() = brandOverride ?: detected

    init {
        if (cardId > 0) viewModelScope.launch {
            repo.get(cardId)?.let { c ->
                original = c
                holder = c.holder
                number = c.number
                expiry = if (c.expMonth != null && c.expYear != null) "%02d%02d".format(c.expMonth, c.expYear % 100) else ""
                cvv = c.cvv.orEmpty()
                bank = c.bank
                bankCode = c.bankCode
                note = c.note
                kind = c.kind
                styleId = c.styleId
                brandOverride = CardBrand.of(c.brand).takeIf { it != CardBrand.detect(c.number) }
                bankTouched = c.bank.isNotBlank() && c.bank != BankDirectory.find(c.number)?.name
            }
            loaded = true
        } else if (draft != null) {
            onNumber(draft.number)
            if (draft.expMonth != null && draft.expYear != null) expiry = "%02d%02d".format(draft.expMonth, draft.expYear % 100)
            draft.holder?.let { holder = it }
        }
    }

    fun onNumber(raw: String) {
        number = CardMath.digits(raw).take(19)
        lookup = Lookup.Idle
        if (!bankTouched) {
            val found = BankDirectory.find(number)
            bank = found?.name.orEmpty()
            bankCode = found?.code
        }
    }

    fun onBank(text: String) {
        bank = text
        bankTouched = text.isNotBlank()
        bankCode = BankDirectory.find(number)?.takeIf { it.name == text }?.code
    }

    fun lookupOnline() {
        if (lookup == Lookup.Loading) return
        viewModelScope.launch {
            val s = prefs.settings.first()
            if (s.offline) {
                lookup = Lookup.Failed("已开启完全离线，在线查询不可用")
                return@launch
            }
            lookup = Lookup.Loading
            BinLookup.query(number, s.proxy.ifBlank { null })
                .onSuccess { info ->
                    if (info.bank != null) {
                        bank = info.bank
                        bankTouched = true
                    }
                    lookup = Lookup.Done(listOfNotNull(info.bank, info.scheme?.uppercase(), info.type, info.country).joinToString(" · ").ifBlank { "没有更多信息" })
                }
                .onFailure { lookup = Lookup.Failed(it.message ?: "查询失败") }
        }
    }

    private val expMonth get() = expiry.take(2).toIntOrNull()
    private val expYear get() = expiry.drop(2).takeIf { it.length == 2 }?.toIntOrNull()?.let { 2000 + it }

    fun validate(): String? = when {
        holder.isBlank() -> "请填写持卡人"
        number.length < 12 -> "卡号至少 12 位"
        expiry.isNotEmpty() && expiry.length < 4 -> "有效期请填写完整的 MM/YY"
        expiry.length == 4 && expMonth !in 1..12 -> "有效期月份不对"
        else -> null
    }

    /** 预览用：表单的当前内容拼成一张卡 */
    fun preview(): Card = Card(
        id = if (cardId > 0) cardId else 0,
        holder = holder,
        number = number,
        expMonth = expMonth,
        expYear = expYear,
        cvv = cvv.ifBlank { null },
        brand = brand.name,
        bank = bank.trim(),
        bankCode = bankCode,
        kind = kind,
        note = note.trim(),
        styleId = styleId,
        archived = original?.archived ?: false,
        position = original?.position ?: 0,
        createdAt = original?.createdAt ?: 0,
        updatedAt = 0,
    )

    fun save(done: () -> Unit) {
        validate()?.let { error = it; return }
        if (saving) return
        saving = true
        error = null
        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                repo.save(preview().copy(createdAt = original?.createdAt ?: now, updatedAt = now))
                done()
            } catch (e: DuplicateCardException) {
                error = e.message
            } catch (e: Exception) {
                error = e.message ?: "保存失败"
            } finally {
                saving = false
            }
        }
    }

    companion object {
        fun yearHint(): String = "%02d".format((LocalDate.now().year + 3) % 100)
    }
}
