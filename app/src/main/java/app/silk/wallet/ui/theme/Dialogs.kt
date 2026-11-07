package app.silk.wallet.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * 统一弹窗：白色大圆角卡片 + 宋体标题 + 胶囊按钮（主按钮墨黑，危险操作红色）。
 * 内容用插槽传入，输入框请用 [DialogField]。
 */
@Composable
fun SilkDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmText: String,
    onConfirm: () -> Unit,
    confirmEnabled: Boolean = true,
    destructive: Boolean = false,
    busy: Boolean = false,
    dismissText: String = "取消",
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .padding(horizontal = 28.dp)
                .fillMaxWidth()
                .floatingCard(RoundedCornerShape(30.dp))
                .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 18.dp)
        ) {
            Text(title, fontFamily = TitleSerif, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(14.dp))
            content()
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
                DialogButton(dismissText, primary = false, enabled = !busy, onClick = onDismiss)
                DialogButton(
                    confirmText,
                    primary = true,
                    enabled = confirmEnabled && !busy,
                    busy = busy,
                    color = if (destructive) Color(0xFFFF3B30) else MaterialTheme.colorScheme.onSurface,
                    onClick = onConfirm,
                )
            }
        }
    }
}

@Composable
private fun DialogButton(
    text: String,
    primary: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    busy: Boolean = false,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    val bg = if (primary) color else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    val fg = if (primary) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier
            .alpha(if (enabled || busy) 1f else 0.35f)
            .height(42.dp)
            .clip(CircleShape)
            .background(bg)
            .springClickable(enabled = enabled, pressedScale = 0.94f, onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = fg)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** 弹窗内的无边框输入框：浅灰圆角底，聚焦时加深；错误时描红边 */
@Composable
fun DialogField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    secret: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Text,
    mono: Boolean = false,
    isError: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(shape)
            .background(cs.onSurface.copy(alpha = if (focused) 0.08f else 0.05f))
            .border(1.dp, if (isError) cs.error.copy(alpha = 0.7f) else Color.Transparent, shape)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) Text(placeholder, fontSize = 15.sp, color = cs.onSurfaceVariant.copy(alpha = 0.6f))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            interactionSource = interaction,
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            textStyle = LocalTextStyle.current.copy(
                fontSize = 16.sp,
                color = cs.onSurface,
                fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
                letterSpacing = if (secret) 4.sp else 0.sp,
            ),
            cursorBrush = SolidColor(cs.onSurface),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun DialogNote(text: String, error: Boolean = false) {
    Text(
        text,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, start = 4.dp),
    )
}
