package app.silk.wallet.ui.edit

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.silk.wallet.data.CardKind
import app.silk.wallet.domain.CardBrand
import app.silk.wallet.domain.CardStyles
import app.silk.wallet.domain.CardMath
import app.silk.wallet.ui.card.CardFront
import app.silk.wallet.ui.card.CardShape
import app.silk.wallet.ui.card.CardStylePicker
import app.silk.wallet.ui.theme.LargeTitleBar
import app.silk.wallet.ui.theme.LocalDarkTheme
import app.silk.wallet.ui.theme.groupedSurface
import app.silk.wallet.ui.theme.springClickable
import app.silk.wallet.ui.theme.enterRise

private val GroupShape = RoundedCornerShape(22.dp)
private val LabelWidth = 72.dp

/**
 * 添加 / 编辑卡片：分组表单（标签在左、输入在右，一组一张圆角卡），
 * 顶部卡面实时预览，底部黑色胶囊保存钮。
 */
@Composable
fun EditScreen(
    vm: EditViewModel,
    isNew: Boolean,
    onBack: () -> Unit,
) {
    var validationError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LargeTitleBar(title = if (isNew) "添加卡片" else "编辑卡片", onBack = onBack)
        },
    ) { padding ->
        if (!vm.loaded) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            // 实时预览：随输入即时更新，阴影染卡面主色
            val preview = vm.preview()
            val preset = CardStyles.resolve(vm.styleId, vm.bankCode, vm.brand)
            CardFront(
                card = preview,
                style = preset,
                masked = false,
                live = true,
                modifier = Modifier
                    .enterRise(0, true)
                    .padding(top = 4.dp)
                    .fillMaxWidth()
                    .aspectRatio(1.586f)
                    .shadow(
                        20.dp,
                        CardShape,
                        ambientColor = preset.colors.last(),
                        spotColor = preset.colors.last(),
                    ),
            )
            Spacer(Modifier.height(28.dp))

            SectionTitle("卡片信息", Modifier.enterRise(1, true))
            FormGroup(Modifier.enterRise(2, true)) {
                FormField(
                    label = "持卡人",
                    value = vm.holder,
                    onValueChange = { vm.holder = it },
                    placeholder = "姓名 / Cardholder",
                )
                FormDivider()
                FormField(
                    label = "卡号",
                    value = vm.number,
                    onValueChange = vm::onNumber,
                    placeholder = "0000 0000 0000 0000",
                    keyboardType = KeyboardType.Number,
                    visualTransformation = remember { CardNumberMask() },
                    monospace = true,
                )
                FormDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FormField(
                        label = "有效期",
                        value = vm.expiry,
                        onValueChange = { vm.expiry = it.filter { c -> c in '0'..'9' }.take(4) },
                        placeholder = "08/${EditViewModel.yearHint()}",
                        keyboardType = KeyboardType.Number,
                        visualTransformation = remember { ExpiryMask() },
                        monospace = true,
                        modifier = Modifier.weight(1.25f),
                    )
                    Box(
                        Modifier
                            .width(1.dp)
                            .height(28.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                    FormField(
                        label = "CVV",
                        value = vm.cvv,
                        onValueChange = { vm.cvv = it.filter { c -> c in '0'..'9' }.take(4) },
                        placeholder = "选填",
                        keyboardType = KeyboardType.NumberPassword,
                        monospace = true,
                        labelWidth = 44.dp,
                        modifier = Modifier.weight(1f),
                    )
                }
                FormDivider()
                FormField(
                    label = "发卡行",
                    value = vm.bank,
                    onValueChange = vm::onBank,
                    placeholder = "自动识别或手动填写",
                    trailing = {
                        val enabled = vm.number.length >= 6 && vm.lookup != Lookup.Loading
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                                .springClickable(enabled = enabled, pressedScale = 0.85f, onClick = vm::lookupOnline),
                            contentAlignment = Alignment.Center,
                        ) {
                            when (vm.lookup) {
                                Lookup.Loading -> CircularProgressIndicator(
                                    Modifier.size(16.dp), strokeWidth = 2.dp
                                )
                                is Lookup.Done -> Icon(
                                    Icons.Rounded.CloudDone, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(18.dp),
                                )
                                is Lookup.Failed -> Icon(
                                    Icons.Rounded.CloudOff, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp),
                                )
                                else -> Icon(
                                    Icons.Rounded.TravelExplore,
                                    contentDescription = "在线识别发卡行",
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.35f),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    },
                )
                FormDivider()
                FormField(
                    label = "备注",
                    value = vm.note,
                    onValueChange = { vm.note = it },
                    placeholder = "例如：工资卡、旅行用",
                    imeAction = ImeAction.Done,
                )
            }
            // 组下注释：卡组织识别、Luhn 校验、BIN 查询状态
            val numberHint = buildList {
                if (vm.brand != CardBrand.UNKNOWN) add("识别为 ${vm.brand.displayName}")
                if (vm.number.length >= 12 && !CardMath.luhn(vm.number)) add("Luhn 校验未通过，请核对卡号")
            }.joinToString(" · ")
            val lookupHint = when (val s = vm.lookup) {
                is Lookup.Done -> s.message
                is Lookup.Failed -> s.message
                else -> "点地球图标可在线查询发卡行（只上传卡号前 8 位）"
            }
            GroupFooter(
                listOf(numberHint, lookupHint).filter { it.isNotBlank() }.joinToString("\n"),
                isError = vm.lookup is Lookup.Failed ||
                    (vm.number.length >= 12 && !CardMath.luhn(vm.number)),
            )

            SectionTitle("分类", Modifier.enterRise(3, true))
            FormGroup(Modifier.enterRise(4, true)) {
                PickerRow(
                    label = "卡组织",
                    value = if (vm.brand == CardBrand.UNKNOWN) "未指定" else vm.brand.displayName,
                    options = CardBrand.entries.map { b ->
                        (if (b == CardBrand.UNKNOWN) "未指定" else b.displayName) to {
                            vm.brandOverride = b.takeIf { it != vm.detected }
                        }
                    },
                )
                FormDivider()
                PickerRow(
                    label = "卡类型",
                    value = CardKind.of(vm.kind)?.label ?: "未指定",
                    options = listOf<Pair<String, () -> Unit>>("未指定" to { vm.kind = null }) +
                        CardKind.entries.map { k -> k.label to { vm.kind = k.key } },
                )
            }
            Spacer(Modifier.height(24.dp))

            SectionTitle("卡面样式", Modifier.enterRise(5, true))
            CardStylePicker(
                selectedId = vm.styleId,
                onSelect = { vm.styleId = it },
                modifier = Modifier.enterRise(6, true),
            )
            Spacer(Modifier.height(28.dp))

            (validationError ?: vm.error)?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                )
            }

            // 主操作：整宽黑色胶囊，按下回弹
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = if (vm.saving) 0.5f else 1f))
                    .springClickable(enabled = !vm.saving, pressedScale = 0.96f) {
                        val err = vm.validate()
                        validationError = err
                        if (err == null) vm.save(onBack)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (vm.saving) "保存中…" else if (isNew) "添加卡片" else "保存修改",
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(36.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 6.dp, bottom = 8.dp),
    )
}

@Composable
private fun GroupFooter(text: String, isError: Boolean) {
    Text(
        text,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 8.dp, bottom = 22.dp),
    )
}

@Composable
private fun FormGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .groupedSurface(GroupShape),
        content = content,
    )
}

@Composable
private fun FormDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 18.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
    )
}

/** 表单行：左侧固定宽度标签，右侧无边框输入；聚焦时整行轻微提亮 */
@Composable
private fun FormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    monospace: Boolean = false,
    labelWidth: androidx.compose.ui.unit.Dp = LabelWidth,
    trailing: (@Composable () -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val dark = LocalDarkTheme.current
    val rowBg by animateColorAsState(
        if (focused) MaterialTheme.colorScheme.onSurface.copy(alpha = if (dark) 0.06f else 0.035f)
        else Color.Transparent,
        animationSpec = tween(180),
        label = "fieldFocus",
    )
    val labelColor by animateColorAsState(
        if (focused) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(180),
        label = "fieldLabel",
    )
    Row(
        modifier
            .heightIn(min = 56.dp)
            .background(rowBg)
            .padding(start = 18.dp, end = if (trailing != null) 10.dp else 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = labelColor,
            modifier = Modifier.width(labelWidth),
        )
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(
                    placeholder,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    maxLines = 1,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                interactionSource = interaction,
                visualTransformation = visualTransformation,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
                textStyle = LocalTextStyle.current.copy(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
                    letterSpacing = if (monospace) 0.5.sp else 0.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

/** 选择行：右侧当前值 + 上下箭头，点整行弹出圆角菜单 */
@Composable
private fun PickerRow(
    label: String,
    value: String,
    options: List<Pair<String, () -> Unit>>,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .springClickable(pressedScale = 0.98f) { expanded = true }
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                label,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    value,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Rounded.UnfoldMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(18.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            options.forEach { (text, action) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text,
                            fontWeight = if (text == value) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    },
                    onClick = {
                        action()
                        expanded = false
                    },
                )
            }
        }
    }
}
