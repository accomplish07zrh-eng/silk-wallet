package app.silk.wallet.ui.edit

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import app.silk.wallet.domain.CardBrand
import app.silk.wallet.domain.CardMath

/**
 * 卡号显示格式：底层值只存数字，显示时按卡组织分组插空格。
 * 偏移映射：原始第 i 位之前插了多少个空格，就往后挪多少。
 */
class CardNumberMask : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val groups = CardMath.groups(CardBrand.detect(raw), raw.length)
        val breaks = groups.runningFold(0) { acc, g -> acc + g }.drop(1).dropLast(1).filter { it < raw.length }
        val out = buildString {
            raw.forEachIndexed { i, c ->
                if (i in breaks) append(' ')
                append(c)
            }
        }
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = offset + breaks.count { it < offset }
            override fun transformedToOriginal(offset: Int): Int {
                var o = offset
                breaks.forEachIndexed { k, b -> if (offset > b + k) o-- }
                return o.coerceIn(0, raw.length)
            }
        }
        return TransformedText(AnnotatedString(out), mapping)
    }
}

/** 有效期显示：MMYY → MM/YY（输入满两位后出现斜杠） */
class ExpiryMask : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val out = if (raw.length > 2) raw.take(2) + "/" + raw.drop(2) else raw
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = if (offset > 2 && raw.length > 2) offset + 1 else offset
            override fun transformedToOriginal(offset: Int) = if (offset > 2 && raw.length > 2) (offset - 1).coerceAtMost(raw.length) else offset.coerceAtMost(raw.length)
        }
        return TransformedText(AnnotatedString(out), mapping)
    }
}
