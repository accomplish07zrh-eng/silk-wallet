package app.silk.wallet.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(entities = [Card::class], version = 1, exportSchema = false)
abstract class SilkDatabase : RoomDatabase() {
    abstract fun cards(): CardDao

    companion object {
        /** 整库 SQLCipher 加密；口令由 KeyVault 用 Keystore 包裹保存 */
        fun open(context: Context, passphrase: ByteArray): SilkDatabase {
            System.loadLibrary("sqlcipher")
            return Room.databaseBuilder(context, SilkDatabase::class.java, "silk.db")
                .openHelperFactory(SupportOpenHelperFactory(passphrase))
                .build()
        }
    }
}
