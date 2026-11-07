package app.silk.wallet.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

class DuplicateCardException(val existingId: Long) : IllegalStateException("这个卡号已经存在")

class CardRepository(private val db: SilkDatabase) {

    private val dao = db.cards()

    fun observe(): Flow<List<Card>> = dao.observeAll()

    suspend fun all(): List<Card> = dao.all()

    suspend fun get(id: Long): Card? = dao.get(id)

    /** 新卡追加到末尾；同卡号已存在（且不是自己）时拒绝 */
    suspend fun save(card: Card): Long = db.withTransaction {
        val clash = dao.byNumber(card.number)
        if (clash != null && clash.id != card.id) throw DuplicateCardException(clash.id)
        if (card.id == 0L) {
            dao.insert(card.copy(position = dao.maxPosition() + 1))
        } else {
            dao.update(card)
            card.id
        }
    }

    suspend fun setArchived(card: Card, archived: Boolean) =
        dao.setArchived(card.id, archived, System.currentTimeMillis())

    suspend fun delete(card: Card) = dao.delete(card)

    /** 撤销删除：按原 id 原位置放回 */
    suspend fun restore(card: Card) = db.withTransaction {
        if (dao.get(card.id) == null && dao.byNumber(card.number) == null) dao.insert(card)
    }

    suspend fun reorder(ids: List<Long>) = dao.reorder(ids)

    /** 备份导入：按卡号合并，已存在则用导入的内容覆盖（保留本机 id 与位置） */
    suspend fun merge(imported: List<Card>): Pair<Int, Int> = db.withTransaction {
        var added = 0
        var updated = 0
        imported.forEach { incoming ->
            val existing = dao.byNumber(incoming.number)
            if (existing == null) {
                dao.insert(incoming.copy(id = 0, position = dao.maxPosition() + 1))
                added++
            } else {
                dao.update(incoming.copy(id = existing.id, position = existing.position))
                updated++
            }
        }
        added to updated
    }
}
