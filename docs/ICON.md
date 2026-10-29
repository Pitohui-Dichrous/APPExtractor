# 启动图标

1.2.2 直接使用用户提供的 `AE.png` 原图。`assets/app-icon.png` 和 Android 的 `drawable-nodpi/ic_launcher_artwork.png` 均与原文件逐字节一致，尺寸为 1024×1024。

SHA-256：`0b3080861190de771dd08ab3b28dd35b37b4951de06e78a01e479d966f6fdb19`

不重采样，不识别或缩小主体，不添加像素留白、边框、底板或自定义遮罩。图像铺满实际可见的图标方框。

Android 自适应图层自带 150% 过扫描空间，资源布局只补偿这部分系统空间，使原图四边与可见区域四边重合。手机桌面统一的圆角／圆形外观由系统决定。

复制原图资源及同一原图预览：

```powershell
python scripts/prepare_launcher_icon.py --preview
```

脚本仅复制文件，不再依赖 Pillow，也不再生成不同密度的缩小图或遮罩预览。
