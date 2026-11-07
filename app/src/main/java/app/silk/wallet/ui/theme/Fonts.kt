package app.silk.wallet.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import app.silk.wallet.R

/**
 * 品牌字标「Silk」：Playfair Display 粗斜体（OFL），高反差衬线带笔意，
 * 与流动丝绸卡面呼应。只含基本拉丁字符。
 */
val WordmarkFont = FontFamily(Font(R.font.playfair_bold_italic, FontWeight.Bold, FontStyle.Italic))

/**
 * 中文大标题：思源宋体 Bold（OFL）子集，只含源码字符串里出现过的字。
 * 新增标题文案后需重新生成子集（tools/subset_fonts.py），否则缺字会回落到系统黑体。
 */
val TitleSerif = FontFamily(Font(R.font.noto_serif_sc_bold, FontWeight.Bold))
