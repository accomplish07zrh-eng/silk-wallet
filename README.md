# Silk

一个只在本地保存的银行卡包，Android 应用。纯白底、液态玻璃、会流动的丝绸卡面。

## 功能

- **卡面**：AGSL 着色器实时渲染的流动丝绸，跟随手机倾斜改变光泽；22 款基础配色，按银行 / 卡组织自动匹配
- **自定义背景**：给任意卡导入照片或 GIF 作为卡面，保留光泽与边缘效果
- **抽卡**：每日一张「今日之卡」，以及 15 款按品阶划分的皮肤
  - 稀有：永久保留
  - 史诗：保留 3 天
  - 传说：保留 1 天，并带旋转边缘光等特效
- **卡片堆叠**：长按拖动排序，左右滑动归档 / 删除（可撤销），点开查看详情、翻面看 CVV
- **识别**：输入卡号即识别卡组织（银联、Visa、Mastercard、JCB、AmEx 等）与常见发卡行；可选联网查询 BIN，支持 HTTP / SOCKS 代理
- **NFC 读卡**：把卡片贴到手机背面，读出卡号与有效期，直接进入添加页
- **到期提醒**：卡片临近到期时推送通知，提前天数可设置

## 隐私与安全

- 全部数据保存在本机，数据库由 SQLCipher 加密，密钥受 Android Keystore 保护
- 支持 6 位 PIN 与生物识别解锁；离开应用超过设定时间自动锁定，也可设为息屏即锁
- 卡号默认打码显示，可开启防截屏（同时隐藏最近任务预览）；复制卡号后按设定的秒数自动清空剪贴板
- 备份文件用你设置的密码加密（PBKDF2 + AES-GCM）。卡面照片与皮肤时效只存在本机，不进入备份
- 除了你主动开启的 BIN 查询和检查更新，应用不发起任何网络请求；也可以在设置里开启完全离线

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

## 技术栈

Kotlin · Jetpack Compose · Material 3 · Room + SQLCipher · DataStore · WorkManager · OkHttp · [Haze](https://github.com/chrisbanes/haze)（毛玻璃）

最低支持 Android 8.0，流动卡面需要 Android 13 及以上，更低的版本显示静态渐变。

## 字体

- Playfair Display（字标），SIL Open Font License 1.1
- 思源宋体 Noto Serif SC（标题，子集化），SIL Open Font License 1.1

新增中文文案后，运行 `python tools/subset_fonts.py .` 重新生成宋体子集。

## 许可

[MIT](LICENSE)
