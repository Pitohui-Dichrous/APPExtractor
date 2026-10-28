# 启动图标

1.2.0 使用用户提供的黑底荧光绿图案。原图保存在 `assets/app-icon.png`，逐字节保留。

SHA-256：`b9f5819c28f90d09b0199aaf7c63eeea1ad51f029a20f98e05ce5b463949ad0a`

图标仅等比缩放和居中，不切割主体或重新绘制。Android 自适应图标使用纯黑背景及 108dp 前景层；整张原图占 76dp。主体（包含白色悬浮块）位于中心直径 66dp 的安全圆内，适配圆形、圆角方形等桌面遮罩。

`scripts/prepare_launcher_icon.py` 根据所有非黑主体像素的最远距离计算缩放比例，并检查五种屏幕密度。运行时无需 Pillow 或其他额外依赖。

重新生成资源和本机预览：

```powershell
python scripts/prepare_launcher_icon.py --preview
```

生成脚本需要 Pillow。本机圆形和圆角方形预览及逐密度检查记录输出到 `.test-data/qa/v1.2.0/`，不打入 APK。
