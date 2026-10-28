package app.appextractor

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.os.Build
import app.appextractor.core.ExportEngine
import java.io.File
import java.io.IOException
import java.text.Collator
import java.util.Locale

data class InstalledApp(
    val label: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val sizeBytes: Long,
    val updatedAt: Long,
    val system: Boolean,
    val sources: List<ExportEngine.Source>,
    val icon: Drawable? = null,
) {
    val split: Boolean get() = sources.size > 1

    fun exportApp(): ExportEngine.App = ExportEngine.App(label, packageName, versionName, sources)
}

/** Reads installed package metadata only. Call scan and validate off the main thread. */
class AppCatalog(private val context: Context) {
    private val packageManager: PackageManager get() = context.packageManager

    fun scan(): List<InstalledApp> {
        // Let a failure of the complete package query reach the caller; an empty result would
        // incorrectly suggest that the phone has no installed apps.
        val packages = installedPackages()
        val apps = ArrayList<InstalledApp>(packages.size)
        for (info in packages) {
            if (info.packageName == context.packageName) continue
            try {
                val application = info.applicationInfo ?: continue
                val files = packageFiles(application)
                if (files.any { !it.isFile || !it.canRead() || it.length() <= 0L }) continue
                val sources = files.mapIndexed { index, file ->
                    ExportEngine.Source(if (index == 0) "base.apk" else file.name, file)
                }
                val label = try {
                    packageManager.getApplicationLabel(application).toString().ifBlank { info.packageName }
                } catch (_: Resources.NotFoundException) {
                    info.packageName
                }
                apps += InstalledApp(
                    label = label,
                    packageName = info.packageName,
                    versionName = info.versionName.orEmpty(),
                    versionCode = versionCode(info),
                    sizeBytes = files.sumOf { it.length() },
                    updatedAt = info.lastUpdateTime,
                    system = application.flags and
                        (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0,
                    sources = sources.toList(),
                )
            } catch (_: PackageManager.NameNotFoundException) {
                // The package may have been removed during the scan.
            } catch (_: SecurityException) {
                // Some manufacturers restrict access to particular installed system packages.
            } catch (_: IOException) {
                // A missing or unreadable source is not an exportable package.
            } catch (_: IllegalArgumentException) {
                // Source construction may race with an uninstall or package update.
            }
        }
        val collator = Collator.getInstance(Locale.SIMPLIFIED_CHINESE)
        return apps.sortedWith { left, right ->
            val labelOrder = collator.compare(left.label, right.label)
            if (labelOrder == 0) left.packageName.compareTo(right.packageName) else labelOrder
        }
    }

    fun loadIcon(packageName: String): Drawable? = try {
        packageManager.getApplicationIcon(packageName)
    } catch (_: PackageManager.NameNotFoundException) {
        null
    } catch (_: Resources.NotFoundException) {
        null
    } catch (_: SecurityException) {
        null
    }

    /** Reject an export when installation changed after the user chose the application. */
    @Throws(IOException::class)
    fun validate(app: InstalledApp) {
        val info = try {
            packageInfo(app.packageName)
        } catch (error: PackageManager.NameNotFoundException) {
            throw IOException("应用已卸载，请刷新列表：${app.label}", error)
        } catch (error: SecurityException) {
            throw IOException("无法读取应用，请刷新列表：${app.label}", error)
        }
        val application = info.applicationInfo
            ?: throw IOException("无法读取应用，请刷新列表：${app.label}")
        val files = try {
            packageFiles(application)
        } catch (error: SecurityException) {
            throw IOException("无法读取应用，请刷新列表：${app.label}", error)
        }
        val sourcePaths = app.sources.map { it.file().absolutePath }
        if (versionCode(info) != app.versionCode || info.lastUpdateTime != app.updatedAt ||
            files.map { it.absolutePath } != sourcePaths
        ) {
            throw IOException("应用已更新，请刷新列表后重试：${app.label}")
        }
        if (files.any { !it.isFile || !it.canRead() || it.length() <= 0L }) {
            throw IOException("无法读取安装包：${app.label}")
        }
    }

    @Throws(IOException::class)
    private fun packageFiles(application: ApplicationInfo): List<File> {
        val basePath = application.sourceDir?.takeIf { it.isNotBlank() }
            ?: throw IOException("安装包路径不可用")
        return buildList {
            add(File(basePath).absoluteFile)
            application.splitSourceDirs.orEmpty().forEach { add(File(it).absoluteFile) }
        }
    }

    @Suppress("DEPRECATION")
    private fun installedPackages(): List<PackageInfo> = if (Build.VERSION.SDK_INT >= 33) {
        packageManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(0L))
    } else {
        packageManager.getInstalledPackages(0)
    }

    @Suppress("DEPRECATION")
    private fun packageInfo(packageName: String): PackageInfo = if (Build.VERSION.SDK_INT >= 33) {
        packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L))
    } else {
        packageManager.getPackageInfo(packageName, 0)
    }

    @Suppress("DEPRECATION")
    private fun versionCode(info: PackageInfo): Long = if (Build.VERSION.SDK_INT >= 28) {
        info.longVersionCode
    } else {
        info.versionCode.toLong()
    }
}
