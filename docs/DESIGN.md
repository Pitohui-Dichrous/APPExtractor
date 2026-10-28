# 1.1.3 视觉设计

参考 [Marathon 官方网站](https://marathonthegame.com/)及其设计合作方 [Kurppa Hosk 的 Bungie 项目](https://www.kurppahosk.com/work/bungie)。采用大字号压缩字体、非对称构图、细分隔线、硬边色块和少量荧光色；操作仍使用中文短标签。

| 角色 | 深色主题 | 亮色主题 |
| --- | --- | --- |
| 主视觉色块 | `#000000` | `#C0FE03` |
| 操作强调色 | `#C0FE03` | `#000000` |
| 操作文字 | `#000000` | `#C0FE03` |
| 中性底色 | 黑、深灰 | 白、浅灰 |

中性底色和表面使用纯灰阶，不混入有色滤镜。深浅主题跟随系统。信息以明显的字号和字重差异组织，元数据使用等宽字体，关键操作图标继续采用现成 [Lucide 矢量资源](ICONS.md)。

## 字体与素材

英文展示字采用 **Barlow Condensed ExtraBold**，由 The Barlow Project Authors 发布，使用 SIL Open Font License 1.1。原始字体来自 [Google Fonts 固定提交](https://github.com/google/fonts/tree/60824dce48f7dd28fe7d65559f2da1f6e04e585b/ofl/barlowcondensed)，未修改字体文件；中文使用系统无衬线字体。

- 字体文件：`app/src/main/res/font/barlow_condensed_extra_bold.ttf`
- 源地址及校验值：[source.json](../third_party/barlow-condensed/source.json)
- 完整许可：[OFL.txt](../third_party/barlow-condensed/OFL.txt)，同时包含在 APK 的 `assets/licenses/barlow-condensed.txt` 中

游戏页面和海报仅作为构图参考，未打包游戏标志、角色图或商业素材。启动图标继续使用用户提供的图片，字体和图标均随应用离线提供。
