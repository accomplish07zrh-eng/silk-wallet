<p align="center">
  <img src="docs/banner.webp" alt="Silk 卡包界面一览" width="100%">
</p>

<h1 align="center">Silk</h1>

<p align="center">
  <b>一个只存在你手机里的银行卡包</b><br>
  纯白底 · 液态玻璃 · 会随手机倾斜流动的丝绸卡面
</p>

<p align="center">
  <a href="https://github.com/accomplish07zrh-eng/silk-wallet/releases/latest"><img src="https://img.shields.io/github/v/release/accomplish07zrh-eng/silk-wallet?label=%E4%B8%8B%E8%BD%BD&color=111111" alt="下载最新版"></a>
  <img src="https://img.shields.io/badge/Android-8.0%2B-111111" alt="Android 8.0+">
  <img src="https://img.shields.io/badge/%E6%95%B0%E6%8D%AE-%E4%BB%85%E5%AD%98%E6%9C%AC%E6%9C%BA%20%C2%B7%20%E5%8A%A0%E5%AF%86-111111" alt="数据仅存本机，加密保存">
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-111111" alt="MIT"></a>
</p>

Silk 把银行卡号、有效期、CVV 收进一个加密的本地卡包：不注册、不联网、不上传。打开它，像把一叠实体卡摊在桌上。每张卡都有自己的光泽，倾斜手机时光会在卡面上流动。

> 截图里的卡号、姓名都是虚构的演示数据。

## 下载

到 [Releases](https://github.com/accomplish07zrh-eng/silk-wallet/releases/latest) 下载最新的 `Silk-x.y.z.apk` 直接安装。以后在应用里「设置 → 检查更新」就能一键升级。

## 卡面会动

<table>
  <tr>
    <td align="center" width="33%"><img src="docs/tilt.webp" width="240" alt="拖动卡片，光影随之流动"></td>
    <td align="center" width="33%"><img src="docs/open.webp" width="240" alt="卡片从堆叠中飞出到详情"></td>
    <td align="center" width="33%"><img src="docs/dock.webp" width="240" alt="底栏在分类间液态流动"></td>
  </tr>
  <tr>
    <td align="center"><b>光会流动</b><br><sub>卡面由着色器实时渲染，倾斜或拖动卡片时，光泽、虹彩和覆膜反光跟着变化</sub></td>
    <td align="center"><b>拿起一张卡</b><br><sub>点卡片，它从堆叠里飞出、放大到眼前；关闭时原路飞回</sub></td>
    <td align="center"><b>液态底栏</b><br><sub>底栏在分类之间像液体一样流过去，整条玻璃会轻微晃动</sub></td>
  </tr>
</table>

细节上也下了功夫：深色卡面的卡号是银箔凸印字；按钮和底栏的玻璃边会随倾斜闪出高光；复制后图标变成对勾；数字变化时上下翻动；点卡、翻面、复制都有轻微震动。冷启动时还有一段「Silk」字标动画。

## 一叠卡，一眼看清

<table>
  <tr>
    <td align="center" width="33%"><img src="docs/01_home.webp" width="240" alt="首页卡片堆叠"></td>
    <td align="center" width="33%"><img src="docs/02_detail.webp" width="240" alt="卡片详情"></td>
    <td align="center" width="33%"><img src="docs/03_detail_back.webp" width="240" alt="翻到背面看 CVV"></td>
  </tr>
  <tr>
    <td align="center"><b>卡片堆叠</b><br><sub>长按拖动排序，左滑归档或删除（可撤销），快到期的卡会标出来</sub></td>
    <td align="center"><b>详情</b><br><sub>背景磨砂虚化；每一项点一下就复制，剪贴板到时自动清空</sub></td>
    <td align="center"><b>翻面</b><br><sub>点卡面翻到背面看 CVV；默认打码，点眼睛才显示</sub></td>
  </tr>
</table>

<table>
  <tr>
    <td align="center" width="50%"><img src="docs/04_edit.webp" width="240" alt="添加和编辑卡片"></td>
    <td align="center" width="50%"><img src="docs/10_lock.webp" width="240" alt="锁屏"></td>
  </tr>
  <tr>
    <td align="center"><b>添加卡片</b><br><sub>边输入边预览；自动识别银联、Visa、Mastercard、AmEx、JCB 和常见发卡行；也能用 NFC 贴卡读取</sub></td>
    <td align="center"><b>上锁</b><br><sub>6 位 PIN 与指纹 / 面容解锁，离开一段时间或息屏后自动锁上</sub></td>
  </tr>
</table>

## 抽卡与皮肤

每天抽一张「今日之卡」决定今天刷哪张；还能抽皮肤，给任意一张卡换上。皮肤分三个品阶，越稀有特效越多，留存时间也越短：

| 品阶 | 概率 | 留存 | 特效 |
| --- | --- | --- | --- |
| 稀有 | 60% | 永久 | 镭射、水墨、冰裂等基础光效 |
| 史诗 | 30% | 3 天 | 更密的闪粉，熔岩、星河、霓虹、樱吹雪 |
| 传说 | 10% | 1 天 | 卡边绕圈流光，黑金流沙、北境极光、龙鳞、星璇、液态铬 |

<table>
  <tr>
    <td align="center" width="33%"><img src="docs/draw.webp" width="240" alt="抽皮肤：横向 3D 轮盘"></td>
    <td align="center" width="33%"><img src="docs/05_today.webp" width="240" alt="今日之卡"></td>
    <td align="center" width="33%"><img src="docs/08_gallery.webp" width="240" alt="皮肤图鉴"></td>
  </tr>
  <tr>
    <td align="center"><b>横向轮盘</b><br><sub>3D 轮盘转动、减速、停下，揭晓的那一刻有光</sub></td>
    <td align="center"><b>今日之卡</b><br><sub>从未过期、未归档的卡里随机抽一张</sub></td>
    <td align="center"><b>图鉴</b><br><sub>已获得的皮肤和剩余时间都在这里</sub></td>
  </tr>
</table>

### 五款传说皮肤

每一款都有自己独一份的招牌现象，倾斜手机时光彩各不相同：

<table>
  <tr>
    <td align="center" width="33%"><img src="docs/legend_dragon.webp" width="240" alt="龙鳞"></td>
    <td align="center" width="33%"><img src="docs/legend_aurora.webp" width="240" alt="北境极光"></td>
    <td align="center" width="33%"><img src="docs/legend_vortex.webp" width="240" alt="星璇"></td>
  </tr>
  <tr>
    <td align="center"><b>龙鳞</b><br><sub>幻彩鳞片随角度变色，三条金色光带在卡面游动</sub></td>
    <td align="center"><b>北境极光</b><br><sub>极光在雪峰上空舞动，冰面倒映、裂纹里流光，偶有流星划过</sub></td>
    <td align="center"><b>星璇</b><br><sub>星系中心是一个黑洞：光环与吸积盘飞转，彗星绕核公转</sub></td>
  </tr>
  <tr>
    <td align="center"><img src="docs/legend_gold.webp" width="240" alt="黑金流沙"></td>
    <td align="center"><img src="docs/legend_chrome.webp" width="240" alt="液态铬"></td>
    <td></td>
  </tr>
  <tr>
    <td align="center"><b>黑金流沙</b><br><sub>熔金在金脉里流动，金沙顺着手机倾斜的方向流淌、在低处聚光</sub></td>
    <td align="center"><b>液态铬</b><br><sub>一面黑镜，水银不时滴落荡开细环，灯带随倾斜滑过</sub></td>
    <td></td>
  </tr>
</table>

不想要动效的话，在「设置 → 外观 → 卡面动效」里关掉，卡面就会静止，更省电。

也可以不用皮肤，直接给卡片导入一张照片或 GIF 当卡面，光泽和边缘效果照样保留。

## 深色模式

<table>
  <tr>
    <td align="center" width="33%"><img src="docs/11_dark_home.webp" width="240" alt="深色首页"></td>
    <td align="center" width="33%"><img src="docs/12_dark_detail.webp" width="240" alt="深色详情"></td>
    <td align="center" width="33%"><img src="docs/13_dark_settings.webp" width="240" alt="深色设置"></td>
  </tr>
</table>

可以跟随系统，也可以在设置里固定为浅色或深色。

## 隐私与安全

- **只存在本机**：数据库由 SQLCipher 加密，密钥受 Android Keystore 保护；不需要账号，也没有服务器
- **上锁**：6 位 PIN 与生物识别；离开应用超过设定时间自动锁定，也可以设为息屏即锁
- **防偷看**：卡号默认打码；可开启防截屏（同时隐藏最近任务里的预览）；复制后按设定秒数自动清空剪贴板
- **加密备份**：备份文件用你设的密码加密（PBKDF2 + AES-GCM），换手机时导入即可。卡面照片与皮肤时效只存在本机，不进备份
- **不联网**：除了你主动发起的 BIN 查询（只发送卡号前 8 位）和检查更新，应用不发起任何网络请求；也可以在设置里开启完全离线

## 构建

需要 JDK 17+ 和 Android SDK（compileSdk 34）。

```bash
./gradlew assembleDebug
```

调试包的包名是 `app.silk.wallet.debug`，可以和正式版同时安装。

正式版签名从环境变量读取：`SILK_KEYSTORE`、`SILK_KEYSTORE_PASSWORD`、`SILK_KEY_ALIAS`、`SILK_KEY_PASSWORD`。这些变量未设置时，正式版会改用调试签名。

```bash
./gradlew assembleRelease
```

### 截图工具（仅调试包）

调试包带一个截图钩子，只截应用自己的窗口，不含状态栏和其他悬浮窗。README 里的图都是用它拍的：

```bash
# 换成一组演示卡（会清空调试包里现有的卡）
adb shell am broadcast -a app.silk.wallet.debug.SHOT -p app.silk.wallet.debug --ez seed true
# 截一张图
adb shell am broadcast -a app.silk.wallet.debug.SHOT -p app.silk.wallet.debug --es name home
# 连拍 3 秒，用来做动图
adb shell am broadcast -a app.silk.wallet.debug.SHOT -p app.silk.wallet.debug --es name open --ei burst 3000
```

图片保存在 `/sdcard/Android/data/app.silk.wallet.debug/files/shots/`。正式包里没有这段代码。

## 技术栈

Kotlin · Jetpack Compose · Material 3 · AGSL 着色器 · Room + SQLCipher · DataStore · WorkManager · OkHttp · [Haze](https://github.com/chrisbanes/haze)（毛玻璃）

最低支持 Android 8.0。流动卡面需要 Android 13 及以上，更低的版本显示静态渐变。

## 字体

- Playfair Display（字标），SIL Open Font License 1.1
- 思源宋体 Noto Serif SC（标题，子集化），SIL Open Font License 1.1

新增中文文案后，运行 `python tools/subset_fonts.py .` 重新生成宋体子集。

## 许可

[MIT](LICENSE)
