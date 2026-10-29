# APPExtractor

轻量 Android 安装包提取工具，采用纯灰阶底色与酸性艺术排版。深色主题以黑色为主、#C0FE03 荧光绿强调操作；亮色主题交换黑、绿角色。默认跟随系统，可在「设置与帮助」页面底部的「外观主题」手动选择浅色或深色，选择会自动保存，重启后仍有效。支持 Android 8.0 及以上。

## 使用

安装 `dist/APPExtractor-1.2.2.apk`，搜索应用，点击应用右侧的下载箭头图标，选择本机保存位置；读屏名称为「提取 + 应用名称」。可切换用户/系统应用，按名称、体积或最近更新时间排序；点击「多选」可批量导出。

在「设置与帮助 → 默认导出文件夹」选择一个本机文件夹，此后提取会直接保存到该文件夹，重启后仍有效。同名文件会保留并生成新文件名。可更换文件夹，或选择「每次询问」恢复逐次选择保存位置。系统限制部分根目录作为目标时，请新建一个子文件夹。

默认文件夹被删除或授权失效时，应用会提示重新选择，不会覆盖或移动已导出的成品。

| 选择内容 | 导出结果 |
| --- | --- |
| 普通应用 | `.apk`，与手机上的安装包逐字节一致 |
| 分包应用 | `.apks`，ZIP 容器内含 `base.apk` 和全部已安装 split APK |
| 多个应用 | `.zip`，普通应用独立保存；每个分包应用放在独立文件夹 |

导出保留原签名。APKS 是完整分包集合，需要支持该格式的分包安装器，或解压后使用 `adb install-multiple base.apk split*.apk` 整组安装。不能把 APK 压缩集合改名为 `.apk` 后直接安装。当前设备安装的 ABI、语言或屏幕配置分包，在其他设备上可能不兼容。

提取范围为安装包，不包含账号、聊天记录、运行数据、OBB 文件或尚未安装的动态模块。部分系统应用需要系统环境或其他组件，提取后不保证能在其他手机独立运行。

## 缓存与清理

- 导出直接流式写入用户选定的文档，不在内部或外部目录复制中间 APK。
- 取消或失败后删除未完成目标。仅记录未完成文件的 URI，启动时重试清理；成功后删除该记录并释放访问授权。
- 要求保存位置提供持久写入授权，便于异常中断后清理。系统撤销授权或存储设备断开时会显示具体清理失败信息，供用户重试。
- 启动和导出结束时清理应用自身的临时缓存；设置中也可手动清理。
- 用户主动保存的成品始终保留，卸载本工具不会删除这些文件。没有额外的公共工作文件夹。

只声明 `QUERY_ALL_PACKAGES`，用于发现可提取的已安装应用。无联网权限、广告、分析 SDK、存储管理权限、Root 请求、后台服务、开机启动和云备份。不修改其他应用或其签名，不在磁盘保存应用清单和导出历史。

## 构建

需要 JDK 17、Android SDK Platform 37、Build Tools 37.0.0。Gradle Wrapper 9.4.1 会自动获取依赖。设置 `JAVA_HOME`、`ANDROID_HOME`，或在 `local.properties` 中填写 `sdk.dir`。

```powershell
.\gradlew.bat :core:test :app:assembleDebug :app:lintRelease
```

发布签名放在本机 `.signing/appextractor.p12`，别名 `appextractor`，密码放在 `.signing/password.txt`。这些文件已被忽略，不应公开。保留同一签名即可在未来覆盖升级。新环境可自行创建一套密钥，或直接构建 debug APK。

```powershell
.\BUILD.cmd
```

签名发布版输出到 `dist/APPExtractor-1.2.2.apk`。发布版开启 R8 和资源裁剪；构建默认最多两个工作线程。

## 源码

- `app/`：原生 Kotlin + Jetpack Compose 界面、已安装应用读取、系统保存选择器与清理。
- `core/`：不依赖 Android 的流式导出实现和 JUnit 测试。
- `scripts/`：本地验证辅助脚本，不打入应用。

参考：[Android 安装包路径](https://developer.android.com/reference/android/content/pm/ApplicationInfo)、[系统文档保存接口](https://developer.android.com/training/data-storage/shared/documents-files)。
