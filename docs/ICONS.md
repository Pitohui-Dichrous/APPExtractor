# 界面图标

界面操作图标来自公开开源项目 [Lucide](https://github.com/lucide-icons/lucide)，通过 GitHub 搜索 `lucide icons` 找到其官方仓库。

固定源码提交：[`be09619c1c1124ad09c19fc3e87ee3b939409bbd`](https://github.com/lucide-icons/lucide/tree/be09619c1c1124ad09c19fc3e87ee3b939409bbd/icons)。图标在构建前随源码提供，应用运行时不联网。

| 用途 | 原始 SVG |
| --- | --- |
| 刷新 | `refresh-cw.svg` |
| 设置 | `settings.svg` |
| 搜索 | `search.svg` |
| 关闭、清除 | `x.svg` |
| 勾选 | `check.svg` |
| 排序 | `arrow-down-wide-narrow.svg` |
| 提取 | `download.svg` |
| 应用占位 | `package.svg` |
| 导出文件夹 | `folder.svg` |

原始文件位于 `third_party/lucide/`，Android 资源位于 `app/src/main/res/drawable/ic_lucide_*.xml`。SVG 路径保持原样；圆和折线仅转换为等价的 Android 路径语法，保留 24×24 画布、2 单位线宽与圆角端点。未自行设计图标。

完整许可证见 [Lucide LICENSE](../third_party/lucide/LICENSE)。Lucide 使用 ISC 许可，其中列出的 Feather 衍生图标同时保留 MIT 许可；完整许可也包含在 APK 的 `assets/licenses/lucide.txt` 中。

APP 启动图标使用用户提供的图片，与这些界面矢量图标分别管理。
