# APPExtractor 1.1.3 验证

发布包：`APPExtractor-1.1.3.apk`，1,548,226 字节。

SHA-256：`acd8ebdf1af9ce5e022dc6ebcfbdcb3bd64d8d383a6237d1b69ebd47716ad656`

- 首页与设置页改为酸性视觉排版，使用 Barlow Condensed 大字、等宽信息与现成 Lucide 矢量按钮。
- 纯灰阶底色；深色主视觉为黑色、强调色为 `#C0FE03`；亮色交换黑色与荧光绿角色，主题跟随系统。
- Android 10（API 29）与 Android 14（API 34）模拟器已检查实际深浅主题、系统栏和覆盖安装。
- 320dp 窄屏及 1.5 倍字体已检查；分类标签保持完整，内容可滚动。
- 搜索、筛选、排序、多选与从设置返回后的列表位置正常。
- 默认目录设置与清理反馈正常；单个 APK、完整 APKS 和批量 ZIP 导出均核对原始内容，压缩包 CRC 有效。
- 原发布签名保持一致，默认目录在升级后保留，同名导出生成新文件。
- 发布构建及 Lint 通过，0 个静态错误；现有 12 项单元测试通过，模拟器未发现崩溃。
- 字体 OFL 与图标许可证已包含在 APK，运行不需联网。

本机截图：`dist/preview-1.1.3-dark.png`、`dist/preview-1.1.3-light.png`。本机验证记录：`dist/verification-1.1.3.json`。
