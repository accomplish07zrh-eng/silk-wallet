package app.silk.wallet.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class BadBackupPassword : Exception("备份密码不对，或文件已损坏")
class NotABackup : Exception("不是 Silk 的备份文件")

/**
 * 加密备份文件格式：
 *   "SILKBK1" (7 字节魔数) | salt 16 | iv 12 | AES-256-GCM(JSON)
 * 密钥由备份密码经 PBKDF2-HMAC-SHA256（21 万次）派生，与设备无关，换机可用。
 */
object Backup {

    private val MAGIC = "SILKBK1".toByteArray()
    private const val ITER = 210_000

    fun write(cards: List<Card>, password: String, out: OutputStream) {
        val json = JSONObject()
            .put("format", 1)
            .put("exportedAt", System.currentTimeMillis())
            .put("cards", JSONArray(cards.map(::toJson)))
            .toString()
            .toByteArray()
        val salt = random(16)
        val iv = random(12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
        }
        out.write(MAGIC)
        out.write(salt)
        out.write(iv)
        out.write(cipher.doFinal(json))
    }

    fun read(input: InputStream, password: String): List<Card> {
        val bytes = input.readBytes()
        if (bytes.size < MAGIC.size + 28 || !bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) throw NotABackup()
        var i = MAGIC.size
        val salt = bytes.copyOfRange(i, i + 16).also { i += 16 }
        val iv = bytes.copyOfRange(i, i + 12).also { i += 12 }
        val plain = try {
            Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
            }.doFinal(bytes, i, bytes.size - i)
        } catch (e: AEADBadTagException) {
            throw BadBackupPassword()
        }
        val arr = JSONObject(String(plain)).getJSONArray("cards")
        return List(arr.length()) { fromJson(arr.getJSONObject(it)) }
    }

    private fun key(password: String, salt: ByteArray) = SecretKeySpec(
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(password.toCharArray(), salt, ITER, 256)).encoded,
        "AES",
    )

    private fun random(n: Int) = ByteArray(n).also { SecureRandom().nextBytes(it) }

    private fun toJson(c: Card) = JSONObject()
        .put("holder", c.holder)
        .put("number", c.number)
        .put("expMonth", c.expMonth ?: JSONObject.NULL)
        .put("expYear", c.expYear ?: JSONObject.NULL)
        .put("cvv", c.cvv ?: JSONObject.NULL)
        .put("brand", c.brand)
        .put("bank", c.bank)
        .put("bankCode", c.bankCode ?: JSONObject.NULL)
        .put("kind", c.kind ?: JSONObject.NULL)
        .put("note", c.note)
        .put("styleId", c.styleId ?: JSONObject.NULL)
        .put("archived", c.archived)
        .put("createdAt", c.createdAt)
        .put("updatedAt", c.updatedAt)

    private fun JSONObject.str(k: String): String? = if (isNull(k)) null else optString(k)
    private fun JSONObject.int(k: String): Int? = if (isNull(k) || !has(k)) null else optInt(k)

    private fun fromJson(o: JSONObject): Card {
        val now = System.currentTimeMillis()
        return Card(
            holder = o.optString("holder"),
            number = o.getString("number"),
            expMonth = o.int("expMonth"),
            expYear = o.int("expYear"),
            cvv = o.str("cvv"),
            brand = o.optString("brand", "UNKNOWN"),
            bank = o.optString("bank"),
            bankCode = o.str("bankCode"),
            kind = o.str("kind"),
            note = o.optString("note"),
            styleId = o.str("styleId"),
            archived = o.optBoolean("archived"),
            createdAt = o.optLong("createdAt", now),
            updatedAt = o.optLong("updatedAt", now),
        )
    }
}
