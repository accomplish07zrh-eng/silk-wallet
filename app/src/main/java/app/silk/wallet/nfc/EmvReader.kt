package app.silk.wallet.nfc

import android.nfc.Tag
import android.nfc.tech.IsoDep
import java.time.LocalDate

/** NFC 读出的卡片信息：只取卡号、有效期与持卡人（若卡片公开），从不读取 PIN / CVV */
data class NfcDraft(val number: String, val expMonth: Int?, val expYear: Int?, val holder: String?)

/**
 * 最小化的 EMV 非接读卡流程：
 * SELECT PPSE → 取第一个 AID → SELECT AID → GET PROCESSING OPTIONS → 按 AFL 读记录，
 * 在返回的 TLV 里找 Track2 等价数据(57) / PAN(5A) / 有效期(5F24) / 持卡人(5F20)。
 */
class EmvReader {

    fun read(tag: Tag): NfcDraft? {
        val iso = IsoDep.get(tag) ?: return null
        iso.use { dep ->
            dep.connect()
            dep.timeout = 3000
            val ppse = dep.apdu(select("2PAY.SYS.DDF01".toByteArray())) ?: return null
            val aid = Tlv.find(ppse, 0x4F) ?: return null
            val fci = dep.apdu(select(aid)) ?: return null

            val pdol = Tlv.find(fci, 0x9F38)
            val gpo = dep.apdu(gpoCommand(pdol)) ?: return null
            val found = mutableListOf(gpo)

            // AFL：格式 2 在 77 模板的 94 里；格式 1(80) 去掉前 2 字节 AIP 就是 AFL
            val afl = Tlv.find(gpo, 0x94) ?: Tlv.find(gpo, 0x80)?.let { if (it.size > 2) it.copyOfRange(2, it.size) else null }
            afl?.toList()?.chunked(4)?.forEach { entry ->
                if (entry.size < 4) return@forEach
                val sfi = (entry[0].toInt() and 0xF8) shr 3
                val first = entry[1].toInt() and 0xFF
                val last = entry[2].toInt() and 0xFF
                for (rec in first..last) {
                    dep.apdu(byteArrayOf(0x00, 0xB2.toByte(), rec.toByte(), ((sfi shl 3) or 4).toByte(), 0x00))?.let(found::add)
                }
            }
            return parse(found)
        }
    }

    private fun parse(chunks: List<ByteArray>): NfcDraft? {
        var pan: String? = null
        var month: Int? = null
        var year: Int? = null
        var holder: String? = null
        for (c in chunks) {
            Tlv.find(c, 0x57)?.let { t2 ->
                val hex = t2.hex().trimEnd('F')
                val sep = hex.indexOf('D')
                if (sep > 0 && pan == null) {
                    pan = hex.substring(0, sep)
                    val yymm = hex.substring(sep + 1).take(4)
                    if (yymm.length == 4) {
                        year = 2000 + yymm.take(2).toInt()
                        month = yymm.drop(2).toInt()
                    }
                }
            }
            if (pan == null) Tlv.find(c, 0x5A)?.let { pan = it.hex().trimEnd('F') }
            if (month == null) Tlv.find(c, 0x5F24)?.let { d ->
                val s = d.hex()
                if (s.length >= 4) {
                    year = 2000 + s.take(2).toInt()
                    month = s.substring(2, 4).toInt()
                }
            }
            if (holder == null) Tlv.find(c, 0x5F20)?.let { n ->
                holder = String(n).trim().replace('/', ' ').takeIf { it.isNotBlank() && it.any(Char::isLetter) }
            }
        }
        val number = pan?.takeIf { it.length in 12..19 && it.all(Char::isDigit) } ?: return null
        val validMonth = month?.takeIf { it in 1..12 }
        val validYear = year?.takeIf { it in 2000..LocalDate.now().year + 20 }
        return NfcDraft(number, validMonth, if (validMonth != null) validYear else null, holder)
    }

    private fun select(aid: ByteArray) =
        byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00, aid.size.toByte()) + aid + byteArrayOf(0x00)

    /** PDOL 按要求长度填数据：终端交易属性给常见的非接值，其余补 0 */
    private fun gpoCommand(pdol: ByteArray?): ByteArray {
        val data = mutableListOf<Byte>()
        if (pdol != null) {
            Tlv.dol(pdol).forEach { (tag, len) ->
                val v = when (tag) {
                    0x9F66 -> byteArrayOf(0x36, 0x00, 0x40, 0x00).copyOf(len)        // 终端交易属性
                    0x9F02 -> ByteArray(len).also { if (len > 0) it[len - 1] = 0x01 } // 授权金额
                    0x5F2A -> byteArrayOf(0x01, 0x56).copyOf(len)                    // 货币：人民币
                    0x9A -> todayBcd().copyOf(len)                                   // 交易日期
                    0x9F37 -> ByteArray(len) { (it * 37 + 11).toByte() }              // 不可预知数
                    else -> ByteArray(len)
                }
                data.addAll(v.toList())
            }
        }
        val body = byteArrayOf(0x83.toByte(), data.size.toByte()) + data.toByteArray()
        return byteArrayOf(0x80.toByte(), 0xA8.toByte(), 0x00, 0x00, body.size.toByte()) + body + byteArrayOf(0x00)
    }

    private fun todayBcd(): ByteArray {
        val d = LocalDate.now()
        fun bcd(n: Int) = (((n / 10) shl 4) or (n % 10)).toByte()
        return byteArrayOf(bcd(d.year % 100), bcd(d.monthValue), bcd(d.dayOfMonth))
    }

    /** 发送 APDU，成功(90 00)返回去掉状态字的数据；61xx 自动取剩余响应 */
    private fun IsoDep.apdu(cmd: ByteArray): ByteArray? {
        var r = transceive(cmd)
        if (r.size >= 2 && r[r.size - 2] == 0x61.toByte()) {
            r = transceive(byteArrayOf(0x00, 0xC0.toByte(), 0x00, 0x00, r[r.size - 1]))
        }
        if (r.size < 2) return null
        val ok = r[r.size - 2] == 0x90.toByte() && r[r.size - 1] == 0x00.toByte()
        return if (ok) r.copyOf(r.size - 2) else null
    }

    private fun ByteArray.hex() = joinToString("") { "%02X".format(it) }
}

/** BER-TLV 的最小解析：递归搜索指定标签，以及解析 DOL（标签 + 长度列表） */
internal object Tlv {

    private fun readTag(b: ByteArray, start: Int): Pair<Int, Int> {
        var i = start
        var tag = b[i].toInt() and 0xFF
        if (tag and 0x1F == 0x1F) {
            do {
                i++
                tag = (tag shl 8) or (b[i].toInt() and 0xFF)
            } while (b[i].toInt() and 0x80 != 0)
        }
        return tag to i + 1
    }

    private fun readLen(b: ByteArray, start: Int): Pair<Int, Int> {
        val first = b[start].toInt() and 0xFF
        if (first < 0x80) return first to start + 1
        val n = first and 0x7F
        var len = 0
        for (k in 1..n) len = (len shl 8) or (b[start + k].toInt() and 0xFF)
        return len to start + 1 + n
    }

    fun find(data: ByteArray, want: Int): ByteArray? {
        var i = 0
        try {
            while (i < data.size) {
                if (data[i] == 0x00.toByte() || data[i] == 0xFF.toByte()) { i++; continue }
                val constructed = data[i].toInt() and 0x20 != 0
                val (tag, afterTag) = readTag(data, i)
                val (len, afterLen) = readLen(data, afterTag)
                val end = (afterLen + len).coerceAtMost(data.size)
                val value = data.copyOfRange(afterLen, end)
                if (tag == want) return value
                if (constructed) find(value, want)?.let { return it }
                i = end
            }
        } catch (_: IndexOutOfBoundsException) {
            return null
        }
        return null
    }

    fun dol(data: ByteArray): List<Pair<Int, Int>> {
        val out = mutableListOf<Pair<Int, Int>>()
        var i = 0
        while (i < data.size) {
            val (tag, afterTag) = readTag(data, i)
            if (afterTag >= data.size) break
            out += tag to (data[afterTag].toInt() and 0xFF)
            i = afterTag + 1
        }
        return out
    }
}
