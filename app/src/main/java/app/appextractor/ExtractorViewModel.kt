package app.appextractor

import android.app.Application
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.appextractor.core.ExportEngine
import java.io.FileNotFoundException
import java.io.IOException
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class ExtractorState(
    val apps: List<InstalledApp> = emptyList(),
    val loading: Boolean = true,
    val picking: Boolean = false,
    val exporting: Boolean = false,
    val cancelled: Boolean = false,
    val done: Long = 0,
    val total: Long = 0,
    val filename: String = "",
    val message: String? = null,
    val error: String? = null,
    val cleanupPending: Boolean = false,
    val cacheBytes: Long = 0,
    val cleaning: Boolean = true,
) {
    val busy get() = picking || exporting || cleaning
}

// The recovery record must reach disk before APK writing and before releasing URI access.
@SuppressLint("ApplySharedPref")
class ExtractorViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val catalog = AppCatalog(app)
    private val journal = app.getSharedPreferences("unfinished_export", 0)
    private val mutable = MutableStateFlow(ExtractorState())
    val state = mutable.asStateFlow()
    private var pendingPlan: ExportEngine.Plan? = null
    private var pendingApps: List<InstalledApp> = emptyList()
    private val stop = AtomicBoolean(false)
    private val work = Mutex()
    @Volatile private var unfinished: Uri? = null
    private var deletionError: String? = null

    init {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { work.withLock { recoverUnfinished(); cleanCache() } }
            mutable.update { it.copy(cleaning = false) }
            refresh()
        }
    }

    fun refresh() {
        if (mutable.value.busy) return
        mutable.update { it.copy(loading = true) }
        viewModelScope.launch {
            try {
                val apps = withContext(Dispatchers.IO) { catalog.scan() }
                mutable.update { it.copy(apps = apps, loading = false) }
            } catch (e: Exception) {
                mutable.update { it.copy(loading = false, error = "读取应用失败：${e.message ?: e.javaClass.simpleName}") }
            }
        }
    }

    fun prepare(apps: List<InstalledApp>): ExportEngine.Plan? {
        if (mutable.value.busy || mutable.value.cleanupPending || apps.isEmpty()) return null
        return try {
            ExportEngine.plan(apps.map { it.exportApp() }).also { plan ->
                pendingPlan = plan
                pendingApps = apps.toList()
                mutable.update { it.copy(picking = true, message = null, error = null, filename = plan.filename()) }
            }
        } catch (e: Exception) {
            mutable.update { it.copy(error = "无法提取：${e.message ?: e.javaClass.simpleName}") }
            null
        }
    }

    fun pickerFailed(error: Exception) {
        pendingPlan = null
        pendingApps = emptyList()
        mutable.update { it.copy(picking = false, error = "无法打开保存位置：${error.message ?: error.javaClass.simpleName}") }
    }

    fun destination(uri: Uri?) {
        val plan = pendingPlan
        val apps = pendingApps
        pendingPlan = null
        pendingApps = emptyList()
        if (uri == null) {
            mutable.update { it.copy(picking = false) }
            return
        }
        stop.set(false)
        mutable.update { it.copy(picking = false, exporting = true, cancelled = false, done = 0, total = plan?.totalBytes() ?: 0) }
        viewModelScope.launch {
            withContext(Dispatchers.IO) { work.withLock {
                // Keep only an unfinished target URI, never an app list or export history.
                unfinished = uri
                val recorded = journal.edit().putString("uri", uri.toString()).putString("name", plan?.filename() ?: "未完成文件").commit()
                try {
                    app.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                } catch (_: SecurityException) {
                    // Do not start writing where process-death cleanup cannot retain access.
                }
                try {
                    if (!recorded) throw IOException("无法记录导出状态，请重试")
                    if (app.contentResolver.persistedUriPermissions.none { it.uri == uri && it.isWritePermission })
                        throw IOException("该位置不支持异常中断后的清理，请选择本机下载目录")
                    if (plan == null) throw IOException("应用已重启，请重新选择要提取的应用")
                    apps.forEach { catalog.validate(it) }
                    app.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                        var lastUpdate = 0L
                        ExportEngine.write(plan, output, { stop.get() }, { done, total ->
                            val now = android.os.SystemClock.elapsedRealtime()
                            if (now - lastUpdate >= 80 || done == total) {
                                mutable.update { it.copy(done = done, total = total) }
                                lastUpdate = now
                            }
                        })
                        apps.forEach { catalog.validate(it) }
                        if (stop.get()) throw CancellationException("已取消")
                    } ?: throw IOException("无法写入所选位置")
                    if (stop.get()) throw CancellationException("已取消")
                    // Clear the recovery record only after the provider closes successfully.
                    if (!journal.edit().clear().commit()) throw IOException("无法完成导出状态记录")
                    unfinished = null
                    release(uri)
                    mutable.update { it.copy(message = "已保存 · ${plan.filename()}", error = null, cleanupPending = false) }
                } catch (e: Exception) {
                    val removed = deleteTarget(uri)
                    val cleared = removed && journal.edit().clear().commit()
                    if (cleared) { unfinished = null; release(uri) }
                    else journal.edit().putString("uri", uri.toString()).putString("name", plan?.filename() ?: "未完成文件").commit()
                    val cancelled = e is CancellationException || stop.get()
                    mutable.update {
                        it.copy(
                            message = if (cancelled && cleared) "已取消，未完成文件已清理" else null,
                            error = if (!removed) "未完成文件未能删除：${plan?.filename() ?: uri.lastPathSegment}\n请在文件管理器删除后重试清理。\n${deletionError.orEmpty()}\n${e.message ?: e.javaClass.simpleName}"
                                else if (!cleared) "文件已删除，但无法清除恢复记录，请重试清理。"
                                else if (cancelled) null else "提取失败：${e.message ?: e.javaClass.simpleName}",
                            cleanupPending = !cleared,
                        )
                    }
                } finally {
                    cleanCache()
                    mutable.update { it.copy(exporting = false, cancelled = false) }
                }
            } }
        }
    }

    fun cancel() {
        stop.set(true)
        mutable.update { it.copy(cancelled = true) }
    }

    fun dismissMessage() { mutable.update { it.copy(message = null) } }
    fun dismissError() { if (!mutable.value.cleanupPending) mutable.update { it.copy(error = null) } }

    fun cleanup() {
        if (mutable.value.busy) return
        mutable.update { it.copy(cleaning = true) }
        viewModelScope.launch(Dispatchers.IO) {
            work.withLock { recoverUnfinished(); cleanCache() }
            mutable.update { it.copy(cleaning = false) }
            if (!mutable.value.cleanupPending) mutable.update { it.copy(message = if (it.cacheBytes == 0L) "缓存已清理" else "部分缓存暂时无法清理") }
        }
    }

    fun forgetUnfinished() {
        if (mutable.value.busy) return
        mutable.update { it.copy(cleaning = true) }
        viewModelScope.launch(Dispatchers.IO) {
            work.withLock {
                val uri = unfinished ?: journal.getString("uri", null)?.let(Uri::parse)
                if (journal.edit().clear().commit()) {
                    unfinished = null
                    uri?.let(::release)
                    mutable.update { it.copy(cleanupPending = false, error = null, message = "已解除清理记录") }
                } else mutable.update { it.copy(error = "无法清除恢复记录，请重试。") }
            }
            mutable.update { it.copy(cleaning = false) }
        }
    }

    private fun recoverUnfinished() {
        val uri = unfinished ?: journal.getString("uri", null)?.let { Uri.parse(it) } ?: return
        unfinished = uri
        if (deleteTarget(uri)) {
            if (!journal.edit().clear().commit()) {
                journal.edit().putString("uri", uri.toString()).commit()
                mutable.update { it.copy(cleanupPending = true, error = "文件已删除，但无法清除恢复记录，请重试清理。") }
                return
            }
            unfinished = null
            release(uri)
            mutable.update { it.copy(cleanupPending = false, error = null, message = "已清理上次未完成的导出") }
        } else {
            mutable.update { it.copy(cleanupPending = true, error = "上次未完成文件未能删除：${journal.getString("name", "未完成文件")}\n请在文件管理器删除后重试清理。\n${deletionError.orEmpty()}") }
        }
    }

    private fun deleteTarget(uri: Uri): Boolean = try {
        deletionError = null
        DocumentsContract.deleteDocument(app.contentResolver, uri).also { if (!it) deletionError = "保存位置未确认文件删除" }
    } catch (_: FileNotFoundException) { true } catch (e: Exception) {
        deletionError = e.message ?: e.javaClass.simpleName
        false
    }

    private fun release(uri: Uri) {
        try { app.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        catch (_: Exception) { }
    }

    private fun cleanCache() {
        app.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        val bytes = app.cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        mutable.update { it.copy(cacheBytes = bytes) }
    }

    override fun onCleared() { stop.set(true); super.onCleared() }
}
