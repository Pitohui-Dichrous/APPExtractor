# 启动图标

1.2.1 使用用户提供的 `AE.png`，包含荧光绿主体、白色悬浮块与灰阶装饰背景。原图保存在 `assets/app-icon.png`，逐字节保留。

SHA-256：`0b3080861190de771dd08ab3b28dd35b37b4951de06e78a01e479d966f6fdb19`

仅对整张图片等比缩放和居中，不重新绘制。Android 自适应图标使用 `#242424` 背景及 108dp 前景层，整图占 64dp。荧光绿与白色主体均位于中心直径 66dp 的安全圆内；桌面遮罩仅影响装饰背景边角，主体完整保留。

`scripts/prepare_launcher_icon.py` 根据荧光绿和高亮白色像素的最远距离计算缩放比例，并检查五种屏幕密度。深灰／黑色装饰背景保留在原图中，不作为主体边界计算。运行时无需 Pillow 或其他额外依赖。

重新生成资源和本机预览：

```powershell
python scripts/prepare_launcher_icon.py --preview
```

生成脚本需要 Pillow。圆形、圆角方形预览及逐密度检查记录输出到 `.test-data/qa/v1.2.1/`，不打入 APK。
