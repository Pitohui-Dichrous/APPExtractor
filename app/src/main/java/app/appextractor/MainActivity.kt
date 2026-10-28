package app.appextractor

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

private val Corner = RoundedCornerShape(12.dp)

class MainActivity : ComponentActivity() {
    private val model: ExtractorViewModel by viewModels()
    private val save = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        model.destination(if (result.resultCode == RESULT_OK) result.data?.data else null)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ExtractorTheme {
                val state by model.state.collectAsStateWithLifecycle()
                ExtractorScreen(state, model, ::export)
            }
        }
    }

    private fun export(apps: List<InstalledApp>) {
        val plan = model.prepare(apps) ?: return
        try {
            save.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = plan.mimeType()
                putExtra(Intent.EXTRA_TITLE, plan.filename())
                // Local export keeps the core workflow fully offline.
                putExtra(Intent.EXTRA_LOCAL_ONLY, true)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            })
        } catch (e: Exception) { model.pickerFailed(e) }
    }
}

@Composable
private fun ExtractorTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) darkColorScheme(
        primary = Color(0xFFAAC7FF), onPrimary = Color(0xFF002F6B),
        secondaryContainer = Color(0xFF223A5C), onSecondaryContainer = Color(0xFFCCE0FF),
        background = Color(0xFF11151B), onBackground = Color(0xFFE4E9F0),
        surface = Color(0xFF1C222B), onSurface = Color(0xFFE4E9F0),
        onSurfaceVariant = Color(0xFFB4BFCD), outline = Color(0xFF8695A7),
        outlineVariant = Color(0xFF3F4C5D), error = Color(0xFFFFB3B3),
    ) else lightColorScheme(
        primary = Color(0xFF2667E8), onPrimary = Color.White,
        secondaryContainer = Color(0xFFE8EFFF), onSecondaryContainer = Color(0xFF2667E8),
        background = Color(0xFFF5F6F8), onBackground = Color(0xFF16202A),
        surface = Color.White, onSurface = Color(0xFF16202A),
        onSurfaceVariant = Color(0xFF526170), outline = Color(0xFF667789),
        outlineVariant = Color(0xFFD8DFE7), error = Color(0xFFAF2F33),
    )
    MaterialTheme(colorScheme = colors, shapes = Shapes(small = Corner, medium = Corner, large = Corner), content = content)
}

@Composable
private fun ExtractorScreen(state: ExtractorState, model: ExtractorViewModel, export: (List<InstalledApp>) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var system by rememberSaveable { mutableStateOf(false) }
    var sort by rememberSaveable { mutableIntStateOf(0) }
    var selecting by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var settings by rememberSaveable { mutableStateOf(false) }
    var manualCleanup by rememberSaveable { mutableStateOf(false) }
    var confirmSplit by remember { mutableStateOf<List<InstalledApp>?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val enabled = !state.busy && !state.cleanupPending
    val toggle: (InstalledApp) -> Unit = { app ->
        selected = if (app.packageName in selected) selected - app.packageName else selected + app.packageName
    }
    val requestExport: (List<InstalledApp>) -> Unit = { apps ->
        if (apps.any { it.split }) confirmSplit = apps else export(apps)
    }
    val filtered = remember(state.apps, query, system, sort) {
        val found = state.apps.filter { it.system == system && (query.isBlank() || it.label.contains(query.trim(), true) || it.packageName.contains(query.trim(), true)) }
        when (sort) { 1 -> found.sortedByDescending { it.sizeBytes }; 2 -> found.sortedByDescending { it.updatedAt }; else -> found }
    }
    val selectedApps = remember(state.apps, selected) { state.apps.filter { it.packageName in selected } }

    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); model.dismissMessage() }
    }
    LaunchedEffect(state.apps) { selected = selected.filter { pkg -> state.apps.any { it.packageName == pkg } } }
    BackHandler(selecting && !state.exporting) { selecting = false; selected = emptyList() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                Row(Modifier.widthIn(max = 720.dp).fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("APPExtractor", fontSize = 21.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1)
                    IconButton(onClick = { model.refresh() }, enabled = !state.busy && !state.loading) { Glyph("refresh", "刷新") }
                    IconButton(onClick = { settings = true }) { Glyph("settings", "设置与帮助") }
                }
            }
        },
        bottomBar = {
            if (selecting) {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Box(Modifier.fillMaxWidth().navigationBarsPadding().imePadding(), contentAlignment = Alignment.Center) {
                        Row(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { selecting = false; selected = emptyList() }, enabled = !state.busy) { Text("取消") }
                            Button(onClick = { requestExport(selectedApps) }, enabled = enabled && selectedApps.isNotEmpty(), shape = Corner, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                                Glyph("download", null); Spacer(Modifier.width(8.dp)); Text("提取（${selectedApps.size}）")
                            }
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { insets ->
        Box(Modifier.fillMaxSize().padding(insets), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 720.dp).fillMaxSize().imePadding(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    OutlinedTextField(
                        value = query, onValueChange = { query = it }, singleLine = true,
                        placeholder = { Text("搜索应用名称或包名") },
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "搜索应用" }, shape = Corner,
                        leadingIcon = { Glyph("search", null) },
                        trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Glyph("close", "清除搜索") } },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = MaterialTheme.colorScheme.surface, focusedContainerColor = MaterialTheme.colorScheme.surface, unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant),
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !system, onClick = { system = false }, label = { Text("用户应用") }, leadingIcon = { if (!system) Glyph("check", null, Modifier.size(18.dp)) }, shape = Corner)
                        FilterChip(selected = system, onClick = { system = true }, label = { Text("系统应用") }, leadingIcon = { if (system) Glyph("check", null, Modifier.size(18.dp)) }, shape = Corner)
                    }
                }
                item {
                    var sorting by remember { mutableStateOf(false) }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (state.loading) "正在读取…" else "${filtered.size} 个应用", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        Box {
                            TextButton(onClick = { sorting = true }) { Text(listOf("名称", "大小", "最近更新")[sort]); Spacer(Modifier.width(4.dp)); Glyph("sort", null, Modifier.size(18.dp)) }
                            DropdownMenu(expanded = sorting, onDismissRequest = { sorting = false }) {
                                listOf("名称", "大小", "最近更新").forEachIndexed { index, title ->
                                    DropdownMenuItem(text = { Text(title) }, onClick = { sort = index; sorting = false }, trailingIcon = { if (sort == index) Glyph("check", null) })
                                }
                            }
                        }
                        TextButton(onClick = {
                            if (selecting) selected = if (filtered.isNotEmpty() && filtered.all { it.packageName in selected }) selected - filtered.map { it.packageName }.toSet() else (selected + filtered.map { it.packageName }).distinct()
                            else selecting = true
                        }, enabled = enabled && filtered.isNotEmpty()) {
                            Text(if (selecting) { if (filtered.all { it.packageName in selected }) "取消全选" else "全选" } else "多选")
                        }
                    }
                }
                if (state.loading && state.apps.isEmpty()) item {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp) }
                }
                if (!state.loading && filtered.isEmpty()) item {
                    SurfaceBlock {
                        Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Glyph("package", null, Modifier.size(36.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(12.dp)); Text(if (query.isBlank()) "暂无应用" else "未找到应用", fontSize = 15.sp)
                        }
                    }
                }
                items(filtered, key = { it.packageName }) { app ->
                    AppRow(app, selecting, app.packageName in selected, enabled, { toggle(app) }, { requestExport(listOf(app)) })
                }
            }
        }
    }

    confirmSplit?.let { apps ->
        AlertDialog(onDismissRequest = { confirmSplit = null }, title = { Text("导出完整分包") },
            text = { Text(if (apps.size == 1) "此应用由 ${apps.first().sources.size} 个 APK 组成，将导出为 .apks 文件，保留原签名。安装时需要支持分包的安装器。" else "所选应用包含分包。将导出 ZIP，包含各应用的全部 APK；分包应用需整组安装。") },
            confirmButton = { TextButton(onClick = { confirmSplit = null; export(apps) }) { Text("继续") } },
            dismissButton = { TextButton(onClick = { confirmSplit = null }) { Text("取消") } }, shape = Corner)
    }
    if (state.exporting) {
        AlertDialog(onDismissRequest = {}, title = { Text(if (state.cancelled) "正在取消…" else "正在提取") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(state.filename, fontSize = 14.sp)
                LinearProgressIndicator(progress = { if (state.total > 0) (state.done.toDouble() / state.total).toFloat().coerceIn(0f, 1f) else 0f }, modifier = Modifier.fillMaxWidth())
                Text("${bytes(state.done)} / ${bytes(state.total)}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            } },
            confirmButton = { TextButton(onClick = { model.cancel() }, enabled = !state.cancelled) { Text("取消") } }, shape = Corner)
    }
    state.error?.let { error ->
        AlertDialog(onDismissRequest = { model.dismissError() }, title = { Text(if (state.cleanupPending) "需要清理" else "操作失败") },
            text = { LazyColumn { item { Text(error) } } },
            confirmButton = { TextButton(onClick = { if (state.cleanupPending) model.cleanup() else model.dismissError() }, enabled = !state.busy) { Text(if (state.cleanupPending) "重试清理" else "知道了") } },
            dismissButton = { if (state.cleanupPending) TextButton(onClick = { manualCleanup = true }, enabled = !state.busy) { Text("手动处理") } }, shape = Corner)
    }
    if (manualCleanup) AlertDialog(onDismissRequest = { manualCleanup = false }, title = { Text("解除清理记录") },
        text = { Text("请先在文件管理器删除未完成文件。解除记录后，本工具不再自动清理该文件。") },
        confirmButton = { TextButton(onClick = { manualCleanup = false; model.forgetUnfinished() }) { Text("已删除，解除记录") } },
        dismissButton = { TextButton(onClick = { manualCleanup = false }) { Text("返回") } }, shape = Corner)
    if (settings) {
        AlertDialog(onDismissRequest = { settings = false }, title = { Text("设置与帮助") },
            text = { LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item { SettingText("导出格式", "普通应用：APK\n分包应用：APKS（ZIP 内含全部 APK）\n批量提取：ZIP") }
                item { SettingText("提取范围", "保留安装包原签名，不包含账号、聊天记录和其他应用数据。分包仅包含当前手机上已安装的配置；换设备安装可能不兼容。") }
                item { SettingText("自动清理", "直接写入保存位置，不生成中间安装包。取消或失败时删除未完成文件；启动时重试清理。你保存的成品会保留。") }
                item { Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("临时缓存 · ${bytes(state.cacheBytes)}", modifier = Modifier.weight(1f), fontSize = 13.sp)
                    TextButton(onClick = { model.cleanup() }, enabled = !state.busy) { Text("清理") }
                } }
                item { SettingText("权限", "仅查询已安装应用。无需联网、存储管理权限、Root 或后台服务。") }
                item { Text("APPExtractor 1.0.0", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } }, confirmButton = { TextButton(onClick = { settings = false }) { Text("完成") } }, shape = Corner)
    }
}

@Composable
private fun SettingText(title: String, detail: String) {
    Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(4.dp))
    Text(detail, fontSize = 13.sp, lineHeight = 21.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun AppRow(app: InstalledApp, selecting: Boolean, selected: Boolean, enabled: Boolean, toggle: () -> Unit, export: () -> Unit) {
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(null, app.packageName, app.updatedAt) {
        value = withContext(Dispatchers.IO) { try { AppCatalog(context.applicationContext).loadIcon(app.packageName)?.toBitmap(96, 96)?.asImageBitmap() } catch (_: Exception) { null } }
    }
    Card(
        modifier = Modifier.fillMaxWidth().then(if (selecting) Modifier.toggleable(value = selected, enabled = enabled, role = Role.Checkbox, onValueChange = { toggle() }) else Modifier),
        shape = Corner,
        colors = CardDefaults.cardColors(containerColor = if (selecting && selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (icon != null) Image(icon!!, contentDescription = null, modifier = Modifier.size(44.dp))
            else Box(Modifier.size(44.dp).background(MaterialTheme.colorScheme.secondaryContainer, Corner), contentAlignment = Alignment.Center) { Glyph("package", null, color = MaterialTheme.colorScheme.primary) }
            Column(Modifier.weight(1f)) {
                Text(app.label, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(app.packageName, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text("${app.versionName.ifBlank { app.versionCode.toString() }} · ${bytes(app.sizeBytes)} · ${if (app.split) "APKS" else "APK"}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (selecting) Checkbox(checked = selected, onCheckedChange = null, enabled = enabled)
            else TextButton(onClick = export, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) { Text("提取") }
        }
    }
}

@Composable
private fun SurfaceBlock(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = Corner, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(0.dp)) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

fun bytes(value: Long): String {
    if (value < 1024) return "$value B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var count = value.toDouble() / 1024
    var unit = 0
    while (count >= 1024 && unit < units.lastIndex) { count /= 1024; unit++ }
    return String.format(Locale.ROOT, if (count >= 100) "%.0f %s" else "%.1f %s", count, units[unit])
}

@Composable
private fun Glyph(kind: String, description: String?, modifier: Modifier = Modifier, color: Color = LocalContentColor.current) {
    Canvas(modifier.size(24.dp).then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)) {
        val unit = size.minDimension / 24f
        val stroke = 1.8f * unit
        fun line(x: Float, y: Float, x2: Float, y2: Float) = drawLine(color, Offset(x * unit, y * unit), Offset(x2 * unit, y2 * unit), stroke, StrokeCap.Round)
        fun path(vararg points: Pair<Float, Float>) {
            val p = Path(); points.forEachIndexed { index, point -> if (index == 0) p.moveTo(point.first * unit, point.second * unit) else p.lineTo(point.first * unit, point.second * unit) }
            drawPath(p, color, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        when (kind) {
            "search" -> { drawCircle(color, 6.5f * unit, Offset(10f * unit, 10f * unit), style = Stroke(stroke)); line(15f, 15f, 21f, 21f) }
            "close" -> { line(6f, 6f, 18f, 18f); line(18f, 6f, 6f, 18f) }
            "check" -> path(5f to 12f, 10f to 17f, 19f to 7f)
            "sort" -> { line(5f, 7f, 19f, 7f); line(8f, 12f, 19f, 12f); line(12f, 17f, 19f, 17f) }
            "download" -> { path(7f to 10f, 12f to 15f, 17f to 10f); line(12f, 3f, 12f, 15f); path(4f to 16f, 4f to 21f, 20f to 21f, 20f to 16f) }
            "refresh" -> { drawArc(color, 35f, 285f, false, Offset(4f * unit, 4f * unit), Size(16f * unit, 16f * unit), style = Stroke(stroke, cap = StrokeCap.Round)); path(15f to 4f, 20f to 4f, 20f to 9f) }
            "settings" -> { drawCircle(color, 3f * unit, center, style = Stroke(stroke)); drawCircle(color, 8f * unit, center, style = Stroke(stroke)); for (i in 0..7) { val angle = i * Math.PI / 4; line((12 + kotlin.math.cos(angle) * 8).toFloat(), (12 + kotlin.math.sin(angle) * 8).toFloat(), (12 + kotlin.math.cos(angle) * 10).toFloat(), (12 + kotlin.math.sin(angle) * 10).toFloat()) } }
            else -> { path(3f to 7f, 12f to 3f, 21f to 7f, 21f to 17f, 12f to 22f, 3f to 17f, 3f to 7f, 12f to 12f, 21f to 7f); line(12f, 12f, 12f, 22f); line(7f, 5f, 17f, 10f) }
        }
    }
}
