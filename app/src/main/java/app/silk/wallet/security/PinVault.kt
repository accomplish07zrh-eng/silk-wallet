package app.silk.wallet.security

import android.content.Context
import android.os.SystemClock
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * 应用锁 PIN：PBKDF2-HMAC-SHA256（12 万次）加盐存储，只存哈希。
 * 连续输错 5 次锁定 30 秒；同时开启生物识别时每 7 天强制输一次 PIN，防止遗忘。
 */
class PinVault(context: Context) {

    private val prefs = context.getSharedPreferences("silk_pin", Context.MODE_PRIVATE)

    val pinSet: Boolean get() = prefs.contains(KEY_HASH)
    val biometricOn: Boolean get() = prefs.getBoolean(KEY_BIO, false)
    val lockEnabled: Boolean get() = pinSet || biometricOn

    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(KEY_SALT, b64(salt))
            .putString(KEY_HASH, b64(derive(pin, salt)))
            .putLong(KEY_LAST_PIN, System.currentTimeMillis())
            .putInt(KEY_FAILS, 0)
            .apply()
    }

    fun clearPin() {
        prefs.edit().remove(KEY_HASH).remove(KEY_SALT).remove(KEY_FAILS).remove(KEY_LOCK_UNTIL).apply()
    }

    fun setBiometric(on: Boolean) = prefs.edit().putBoolean(KEY_BIO, on).apply()

    /** 剩余锁定毫秒数；0 表示可输入 */
    fun lockoutLeft(): Long {
        val until = prefs.getLong(KEY_LOCK_UNTIL, 0L)
        return (until - SystemClock.elapsedRealtime()).coerceAtLeast(0L).takeIf { it <= LOCKOUT_MS } ?: 0L
    }

    sealed interface Result {
        data object Ok : Result
        data class Wrong(val attemptsLeft: Int) : Result
        data class LockedOut(val millis: Long) : Result
    }

    fun verify(pin: String): Result {
        lockoutLeft().takeIf { it > 0 }?.let { return Result.LockedOut(it) }
        val salt = prefs.getString(KEY_SALT, null)?.let(::unb64) ?: return Result.Wrong(0)
        val expected = prefs.getString(KEY_HASH, null)?.let(::unb64) ?: return Result.Wrong(0)
        return if (MessageDigest.isEqual(derive(pin, salt), expected)) {
            prefs.edit().putInt(KEY_FAILS, 0).putLong(KEY_LAST_PIN, System.currentTimeMillis()).apply()
            Result.Ok
        } else {
            val fails = prefs.getInt(KEY_FAILS, 0) + 1
            if (fails >= MAX_FAILS) {
                prefs.edit().putInt(KEY_FAILS, 0).putLong(KEY_LOCK_UNTIL, SystemClock.elapsedRealtime() + LOCKOUT_MS).apply()
                Result.LockedOut(LOCKOUT_MS)
            } else {
                prefs.edit().putInt(KEY_FAILS, fails).apply()
                Result.Wrong(MAX_FAILS - fails)
            }
        }
    }

    /** 生物识别与 PIN 都开启时，超过 7 天没输过 PIN 就要求输一次 */
    fun pinDue(): Boolean =
        pinSet && System.currentTimeMillis() - prefs.getLong(KEY_LAST_PIN, 0L) > 7L * 24 * 3600 * 1000

    private fun derive(pin: String, salt: ByteArray): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(pin.toCharArray(), salt, 120_000, 256))
            .encoded

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun unb64(s: String) = Base64.decode(s, Base64.NO_WRAP)

    companion object {
        const val LENGTH = 6
        private const val MAX_FAILS = 5
        private const val LOCKOUT_MS = 30_000L
        private const val KEY_HASH = "hash"
        private const val KEY_SALT = "salt"
        private const val KEY_BIO = "bio"
        private const val KEY_FAILS = "fails"
        private const val KEY_LOCK_UNTIL = "lock_until"
        private const val KEY_LAST_PIN = "last_pin"
    }
}
