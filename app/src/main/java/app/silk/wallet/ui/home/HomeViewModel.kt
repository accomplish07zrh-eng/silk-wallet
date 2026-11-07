package app.silk.wallet.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.silk.wallet.data.Card
import app.silk.wallet.data.CardRepository
import app.silk.wallet.data.Prefs
import app.silk.wallet.data.SortMode
import app.silk.wallet.domain.CardBrand
import app.silk.wallet.domain.CardMath
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class CardFilter(val label: String) {
    ALL("全部"),
    EXPIRING("即将到期"),
    EXPIRED("已过期"),
    ARCHIVED("已归档"),
}

data class HomeState(
    val loading: Boolean = true,
    val cards: List<Card> = emptyList(),
    val total: Int = 0,
    val expiring: Int = 0,
    val archived: Int = 0,
    val sort: SortMode = SortMode.MANUAL,
    val noticeDays: Int = 30,
)

class HomeViewModel(private val repo: CardRepository, private val prefs: Prefs) : ViewModel() {

    val filter = MutableStateFlow(CardFilter.ALL)
    val query = MutableStateFlow("")

    val state: StateFlow<HomeState> = combine(repo.observe(), prefs.settings, filter, query) { all, s, f, q ->
        val active = all.filterNot { it.archived }
        fun state(c: Card) = CardMath.expiry(c.expMonth, c.expYear, s.noticeDays)
        val visible = all
            .filter { c ->
                when (f) {
                    CardFilter.ALL -> !c.archived
                    CardFilter.EXPIRING -> !c.archived && state(c) == CardMath.Expiry.SOON
                    CardFilter.EXPIRED -> !c.archived && state(c) == CardMath.Expiry.EXPIRED
                    CardFilter.ARCHIVED -> c.archived
                }
            }
            .filter { matches(it, q) }
            .let { sort(it, s.sort) }
        HomeState(
            loading = false,
            cards = visible,
            total = active.size,
            expiring = active.count { state(it) == CardMath.Expiry.SOON },
            archived = all.count { it.archived },
            sort = s.sort,
            noticeDays = s.noticeDays,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    private fun sort(list: List<Card>, mode: SortMode): List<Card> = when (mode) {
        SortMode.MANUAL -> list
        SortMode.RECENT -> list.sortedByDescending { it.createdAt }
        SortMode.BANK -> list.sortedWith(compareBy({ it.bank.ifBlank { "￿" } }, { it.position }))
        SortMode.EXPIRY -> list.sortedWith(compareBy({ CardMath.daysLeft(it.expMonth, it.expYear) ?: Long.MAX_VALUE }, { it.position }))
    }

    /** 模糊搜索：持卡人 / 银行 / 备注 / 卡组织 / 卡号（含末四位）；中文按子串，字母数字允许跳字 */
    private fun matches(c: Card, raw: String): Boolean {
        val q = raw.trim().lowercase()
        if (q.isEmpty()) return true
        val fields = listOf(c.holder, c.bank, c.note, CardBrand.of(c.brand).displayName, c.number).map { it.lowercase() }
        if (fields.any { q in it }) return true
        val compact = q.replace(" ", "")
        return compact.all { it.isLetterOrDigit() } && fields.any { subsequence(compact, it) }
    }

    private fun subsequence(needle: String, hay: String): Boolean {
        var i = 0
        for (ch in hay) if (i < needle.length && ch == needle[i]) i++
        return i == needle.length
    }

    fun setSort(mode: SortMode) = viewModelScope.launch { prefs.setSort(mode) }
    fun delete(card: Card) = viewModelScope.launch { repo.delete(card) }
    fun restore(card: Card) = viewModelScope.launch { repo.restore(card) }
    fun setArchived(card: Card, archived: Boolean) = viewModelScope.launch { repo.setArchived(card, archived) }
    fun reorder(ids: List<Long>) = viewModelScope.launch { repo.reorder(ids) }
    fun setStyle(card: Card, styleId: String?) = viewModelScope.launch {
        repo.save(card.copy(styleId = styleId, updatedAt = System.currentTimeMillis()))
    }
}
