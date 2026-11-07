package app.silk.wallet.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CardDao {
    @Query("SELECT * FROM cards ORDER BY position ASC, id ASC")
    fun observeAll(): Flow<List<Card>>

    @Query("SELECT * FROM cards ORDER BY position ASC, id ASC")
    suspend fun all(): List<Card>

    @Query("SELECT * FROM cards WHERE id = :id")
    suspend fun get(id: Long): Card?

    @Query("SELECT * FROM cards WHERE number = :number LIMIT 1")
    suspend fun byNumber(number: String): Card?

    @Query("SELECT COALESCE(MAX(position), -1) FROM cards")
    suspend fun maxPosition(): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(card: Card): Long

    @Update
    suspend fun update(card: Card)

    @Delete
    suspend fun delete(card: Card)

    @Query("UPDATE cards SET archived = :archived, updatedAt = :now WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean, now: Long)

    @Query("UPDATE cards SET position = :position WHERE id = :id")
    suspend fun setPosition(id: Long, position: Long)

    @Transaction
    suspend fun reorder(ids: List<Long>) {
        ids.forEachIndexed { i, id -> setPosition(id, i.toLong()) }
    }
}
