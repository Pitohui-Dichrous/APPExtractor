# APPExtractor 1.2.2 验证

发布包：`APPExtractor-1.2.2.apk`，1,444,422 字节。

SHA-256：`385cb5add6653b8b4f1ca8a8d7f84550d00365fc48a3c8ecd5f70d6c7835b5b8`

- 直接打包 1024×1024 的 AE.png 原图，源码资源逐字节一致，APK 内图像尺寸及所有像素与原图相同。
- 取消主体识别、安全圆缩小、多密度缩小图、额外留白、底板、边框及自定义遮罩，原图铺满实际可见图标区域。
- 资源布局仅补偿 Android 系统过扫描空间；桌面自带的圆形／圆角形状由系统决定。
- Android 14（API 34）模拟器覆盖安装 1.2.1 → 1.2.2 成功，已目视核对实际桌面图标与应用启动。
- 发布构建及 Android Lint 通过，0 个静态错误。

本机记录：`dist/verification-1.2.2.json`；实际桌面截图：`dist/preview-1.2.2-launcher.png`。说明见 [ICON.md](ICON.md)。
