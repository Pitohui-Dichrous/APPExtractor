package app.appextractor

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import java.io.IOException
import java.util.Locale

/** Uses only the user-granted document tree. No shared-storage permission or staging files. */
@SuppressLint("ApplySharedPref")
internal class DefaultExportFolder(context: Context) {
    private val resolver = context.contentResolver
    private val preferences = context.getSharedPreferences("export_folder", Context.MODE_PRIVATE)

    data class Folder(val uri: Uri, val name: String)
    data class Created(val uri: Uri, val name: String, val tree: Uri)
    class Unavailable(message: String, cause: Throwable? = null) : IOException(message, cause)

    fun configured(): Folder? = preferences.getString("uri", null)?.let {
        Folder(Uri.parse(it), preferences.getString("name", null) ?: "默认文件夹")
    }

    fun hasGrant(uri: Uri): Boolean = resolver.persistedUriPermissions.any {
        it.uri == uri && it.isReadPermission && it.isWritePermission
    }

    fun documentUri(tree: Uri): Uri = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))

    fun validate(folder: Folder): Folder = try {
        if (!DocumentsContract.isTreeUri(folder.uri) || !hasGrant(folder.uri)) throw Unavailable("文件夹访问授权已失效，请重新选择")
        val projection = arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_FLAGS)
        resolver.query(documentUri(folder.uri), projection, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) throw Unavailable("文件夹已删除或暂时不可用")
            if (cursor.getString(1) != DocumentsContract.Document.MIME_TYPE_DIR ||
                cursor.getLong(2) and DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE.toLong() == 0L)
                throw Unavailable("所选文件夹不支持创建文件")
            folder.copy(name = cursor.getString(0)?.takeIf(String::isNotBlank) ?: folder.name)
        } ?: throw Unavailable("无法读取所选文件夹")
    } catch (e: Unavailable) { throw e } catch (e: Exception) {
        throw Unavailable("文件夹不可用：${e.message ?: e.javaClass.simpleName}", e)
    }

    fun choose(uri: Uri, grantedFlags: Int): Folder {
        val previous = configured()
        val flags = grantedFlags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        if (flags != (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION))
            throw Unavailable("所选位置没有授予文件夹读写权限")
        try {
            resolver.takePersistableUriPermission(uri, flags)
            val folder = validate(Folder(uri, "默认文件夹"))
            if (!preferences.edit().putString("uri", uri.toString()).putString("name", folder.name).commit())
                throw IOException("无法保存默认文件夹设置")
            if (previous != null && previous.uri != uri) release(previous.uri)
            return folder
        } catch (e: Exception) {
            if (previous?.uri != uri) release(uri)
            throw e
        }
    }

    fun clear() {
        val previous = configured()
        if (!preferences.edit().clear().commit()) throw IOException("无法清除默认文件夹设置")
        previous?.let { release(it.uri) }
    }

    /** Returns only a newly created document; callers journal it before any data is written. */
    fun create(folder: Folder, filename: String, mime: String): Created {
        validate(folder)
        val names = HashSet<String>()
        val existingIds = HashSet<String>()
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(folder.uri, DocumentsContract.getTreeDocumentId(folder.uri))
        try {
            resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    cursor.getString(0)?.let(names::add)
                    cursor.getString(1)?.let(existingIds::add)
                }
            } ?: throw Unavailable("无法读取文件夹内容")
        } catch (e: SecurityException) { throw Unavailable("文件夹访问授权已失效，请重新选择", e) }
        val name = uniqueExportName(filename, names)
        val created = try {
            DocumentsContract.createDocument(resolver, documentUri(folder.uri), mime, name)
                ?: throw IOException("保存位置未创建文件")
        } catch (e: SecurityException) { throw Unavailable("文件夹访问授权已失效，请重新选择", e) }
        // Protect an existing document if a nonstandard provider returns its URI from create.
        val id = try { DocumentsContract.getDocumentId(created) } catch (e: IllegalArgumentException) {
            throw IOException("保存位置返回了无效文件地址", e)
        }
        if (created.authority != folder.uri.authority || id == DocumentsContract.getTreeDocumentId(folder.uri))
            throw IOException("保存位置未创建新文件，已保护原有文件夹")
        if (id in existingIds) throw IOException("保存位置未创建新文件，已保护原有文件")
        // Some providers return a bare document URI. Carry the existing tree grant explicitly.
        val uri = DocumentsContract.buildDocumentUriUsingTree(folder.uri, id)
        val actualName = try {
            resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        } catch (_: Exception) { null }
        return Created(uri, actualName ?: name, folder.uri)
    }

    fun hasPersistentAccess(uri: Uri, tree: Uri?): Boolean {
        if (tree == null) return resolver.persistedUriPermissions.any { it.uri == uri && it.isWritePermission }
        if (!hasGrant(tree) || uri.authority != tree.authority) return false
        if (!DocumentsContract.isTreeUri(uri) || DocumentsContract.getTreeDocumentId(uri) != DocumentsContract.getTreeDocumentId(tree) ||
            DocumentsContract.getDocumentId(uri) == DocumentsContract.getTreeDocumentId(tree)) return false
        if (Build.VERSION.SDK_INT >= 29) return DocumentsContract.isChildDocument(resolver, documentUri(tree), uri)
        // API 26-28 have no public isChildDocument API. Querying the tree-wrapped child lets
        // DocumentsProvider enforce membership and the persisted tree permission itself.
        return resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)?.use {
            it.moveToFirst() && it.getString(0) == DocumentsContract.getDocumentId(uri)
        } ?: false
    }

    fun requireDeletable(uri: Uri) {
        resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_FLAGS), null, null, null)?.use {
            if (!it.moveToFirst() || it.getLong(0) and DocumentsContract.Document.FLAG_SUPPORTS_DELETE.toLong() == 0L)
                throw IOException("所选保存位置不支持删除未完成文件")
        } ?: throw IOException("无法检查保存位置")
    }

    private fun release(uri: Uri) {
        try { resolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        catch (_: SecurityException) { }
    }
}

/** Names are planned before creation, retaining the original extension and Unicode label. */
internal fun uniqueExportName(requested: String, existing: Set<String>): String {
    val used = existing.mapTo(HashSet()) { it.lowercase(Locale.ROOT) }
    if (requested.lowercase(Locale.ROOT) !in used) return requested
    val dot = requested.lastIndexOf('.').takeIf { it > 0 } ?: requested.length
    val stem = requested.substring(0, dot)
    val suffix = requested.substring(dot)
    for (number in 2..10000) {
        val candidate = "$stem ($number)$suffix"
        if (candidate.lowercase(Locale.ROOT) !in used) return candidate
    }
    throw IOException("同名文件过多，请更换导出文件夹")
}
