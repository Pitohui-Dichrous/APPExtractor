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
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val model: ExtractorViewModel by viewModels()
    private val save = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        model.destination(if (result.resultCode == RESULT_OK) result.data?.data else null)
    }

    private val folder = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            model.setDefaultFolder(result.data?.data, result.data?.flags ?: 0)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (android.os.Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        setContent {
            ExtractorTheme {
                val dark = LocalAcidColors.current.dark
                SideEffect {
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !dark
                        isAppearanceLightNavigationBars = !dark
                    }
                }
                val state by model.state.collectAsStateWithLifecycle()
                ExtractorScreen(state, model, ::export, ::chooseFolder)
            }
        }
    }

    private fun chooseFolder() {
        try {
            folder.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                putExtra(Intent.EXTRA_LOCAL_ONLY, true)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
            })
        } catch (e: Exception) { model.pickerFailed(e) }
    }

    private fun export(apps: List<InstalledApp>) {
        if (model.exportToDefault(apps)) return
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
private fun ExtractorScreen(state: ExtractorState, model: ExtractorViewModel, export: (List<InstalledApp>) -> Unit, chooseFolder: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var system by rememberSaveable { mutableStateOf(false) }
    var sort by rememberSaveable { mutableIntStateOf(0) }
    var selecting by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var settings by rememberSaveable { mutableStateOf(false) }
    var manualCleanup by rememberSaveable { mutableStateOf(false) }
    var confirmSplit by remember { mutableStateOf<List<InstalledApp>?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val appListState = rememberLazyListState()
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
    LaunchedEffect(state.error) { if (state.error != null) settings = false }
    LaunchedEffect(state.apps) { selected = selected.filter { pkg -> state.apps.any { it.packageName == pkg } } }
    BackHandler(selecting && !state.exporting) { selecting = false; selected = emptyList() }

    if (settings) {
        BackHandler { settings = false }
        SettingsScreen(state, model, snackbar, chooseFolder) { settings = false }
        return
    }
    val acid = LocalAcidColors.current
    val colors = MaterialTheme.colorScheme
    Scaffold(
        containerColor = colors.background,
        topBar = {
            Surface(color = acid.main, contentColor = acid.onMain) {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("01 / LIBRARY", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                    ToolButton("refresh", "刷新", !state.busy && !state.loading) { model.refresh() }
                    Spacer(Modifier.width(8.dp))
                    ToolButton("settings", "设置与帮助", !state.exporting) { settings = true }
                }
            }
        },
        bottomBar = {
            if (selecting) Surface(color = colors.surface) {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = { selecting = false; selected = emptyList() }, enabled = !state.busy) { Text("取消") }
                    Button(onClick = { requestExport(selectedApps) }, enabled = enabled && selectedApps.isNotEmpty(), shape = Corner, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                        Glyph("download", null)
                        Spacer(Modifier.width(10.dp))
                        Text("提取已选 · ${selectedApps.size}", fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { insets ->
        Box(Modifier.fillMaxSize().padding(insets), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                state = appListState,
                modifier = Modifier.widthIn(max = 720.dp).fillMaxSize().imePadding(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                item(key = "hero") { PageHeading("EXTRACT", "应用提取", "APK / APKS / ZIP") }
                item(key = "search") {
                    OutlinedTextField(
                        value = query, onValueChange = { query = it }, singleLine = true,
                        placeholder = { Text("搜索名称或包名", style = MaterialTheme.typography.bodyMedium) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp).semantics { contentDescription = "搜索应用" }, shape = Corner,
                        leadingIcon = { Glyph("search", null, color = colors.primary) },
                        trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Glyph("close", "清除搜索", color = colors.primary) } },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        textStyle = MaterialTheme.typography.bodyMedium,
                        colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = colors.surface, focusedContainerColor = colors.surface, focusedBorderColor = colors.primary, cursorColor = colors.primary, unfocusedBorderColor = colors.outline),
                    )
                }
                item(key = "filter") {
                    Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CategoryTab("用户应用", "01", !system, Modifier.weight(1f)) { system = false }
                        CategoryTab("系统应用", "02", system, Modifier.weight(1f)) { system = true }
                    }
                }
                item(key = "order") {
                    var sorting by remember { mutableStateOf(false) }
                    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (state.loading) "正在读取…" else "${filtered.size.toString().padStart(2, '0')} 个应用", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
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
                    HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = colors.outline)
                }
                if (state.loading && state.apps.isEmpty()) item {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(28.dp), color = colors.primary, strokeWidth = 3.dp) }
                }
                if (!state.loading && filtered.isEmpty()) item {
                    Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Glyph("package", null, Modifier.size(40.dp), colors.primary)
                        Spacer(Modifier.height(16.dp))
                        Text(if (query.isBlank()) "暂无应用" else "未找到应用", style = MaterialTheme.typography.titleMedium)
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
            confirmButton = { TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.secondary), onClick = { confirmSplit = null; export(apps) }) { Text("继续") } },
            dismissButton = { TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.secondary), onClick = { confirmSplit = null }) { Text("取消") } }, shape = Corner)
    }
    if (state.exporting) {
        AlertDialog(onDismissRequest = {}, title = { Text(if (state.cancelled) "正在取消…" else "正在提取") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(state.filename, fontSize = 14.sp)
                LinearProgressIndicator(color = MaterialTheme.colorScheme.primary, progress = { if (state.total > 0) (state.done.toDouble() / state.total).toFloat().coerceIn(0f, 1f) else 0f }, modifier = Modifier.fillMaxWidth())
                Text("${bytes(state.done)} / ${bytes(state.total)}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            } },
            confirmButton = { TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.secondary), onClick = { model.cancel() }, enabled = !state.cancelled) { Text("取消") } }, shape = Corner)
    }
    state.error?.let { error ->
        AlertDialog(onDismissRequest = { model.dismissError() }, title = { Text(if (state.cleanupPending) "需要清理" else "操作失败") },
            text = { LazyColumn { item { Text(error) } } },
            confirmButton = { TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.secondary), onClick = { if (state.cleanupPending) model.cleanup() else model.dismissError() }, enabled = !state.busy) { Text(if (state.cleanupPending) "重试清理" else "知道了") } },
            dismissButton = { if (state.cleanupPending) TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.secondary), onClick = { manualCleanup = true }, enabled = !state.busy) { Text("手动处理") } }, shape = Corner)
    }
    if (manualCleanup) AlertDialog(onDismissRequest = { manualCleanup = false }, title = { Text("解除清理记录") },
        text = { Text("请先在文件管理器删除未完成文件。解除记录后，本工具不再自动清理该文件。") },
        confirmButton = { TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.secondary), onClick = { manualCleanup = false; model.forgetUnfinished() }) { Text("已删除，解除记录") } },
        dismissButton = { TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.secondary), onClick = { manualCleanup = false }) { Text("返回") } }, shape = Corner)
}

@Composable
private fun ToolButton(kind: String, description: String, enabled: Boolean = true, action: () -> Unit) {
    val acid = LocalAcidColors.current
    FilledIconButton(onClick = action, enabled = enabled, shape = Corner, modifier = Modifier.size(48.dp),
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = acid.action, contentColor = acid.onAction)) {
        Glyph(kind, description, Modifier.size(22.dp))
    }
}

@Composable
private fun PageHeading(word: String, title: String, detail: String) {
    val acid = LocalAcidColors.current
    Surface(color = acid.main, contentColor = acid.onMain, shape = CutCornerShape(bottomEnd = 24.dp)) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 20.dp)) {
            HorizontalDivider(color = acid.onMain.copy(alpha = 0.45f))
            BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp)) {
                val measurer = rememberTextMeasurer()
                val style = MaterialTheme.typography.displayLarge
                val available = with(LocalDensity.current) { maxWidth.roundToPx() }
                var lower = 12f
                var upper = (maxWidth.value * 0.255f).coerceAtMost(112f)
                // Measure the actual font; Android's large-text scaling is not linear.
                repeat(10) {
                    val candidate = (lower + upper) / 2
                    val width = measurer.measure(word, style = style.copy(fontSize = candidate.sp), softWrap = false, maxLines = 1).size.width
                    if (width <= available) lower = candidate else upper = candidate
                }
                Text(word, style = style, fontSize = lower.sp, lineHeight = (lower * 1.06f).sp, maxLines = 1, softWrap = false)
            }
            if (LocalDensity.current.fontScale > 1.2f) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(title, style = MaterialTheme.typography.headlineSmall)
                    Text(detail, style = MaterialTheme.typography.labelSmall)
                }
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    Text(detail, style = MaterialTheme.typography.labelSmall, maxLines = 2)
                }
            }
        }
    }
}

@Composable
private fun CategoryTab(title: String, number: String, selected: Boolean, modifier: Modifier, click: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val largeText = LocalDensity.current.fontScale > 1.2f
    Surface(onClick = click, selected = selected, modifier = modifier.heightIn(min = 50.dp), shape = Corner,
        color = if (selected) colors.primary else colors.surface,
        contentColor = if (selected) colors.onPrimary else colors.onSurfaceVariant,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) colors.primary else colors.outline)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!largeText) Text(number, style = MaterialTheme.typography.labelSmall)
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (selected && !largeText) Glyph("check", null, Modifier.size(16.dp))
        }
    }
}

@Composable
private fun SettingsScreen(state: ExtractorState, model: ExtractorViewModel, snackbar: SnackbarHostState, chooseFolder: () -> Unit, close: () -> Unit) {
    val acid = LocalAcidColors.current
    val colors = MaterialTheme.colorScheme
    Scaffold(containerColor = colors.background, snackbarHost = { SnackbarHost(snackbar) }, topBar = {
        Surface(color = acid.main, contentColor = acid.onMain) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("02 / PREFERENCES", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                ToolButton("close", "关闭设置", action = close)
            }
        }
    }) { insets ->
        Box(Modifier.fillMaxSize().padding(insets), contentAlignment = Alignment.TopCenter) {
            LazyColumn(Modifier.widthIn(max = 720.dp).fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp)) {
                item { PageHeading("SETTINGS", "设置与帮助", "V 1.1.3") }
                item {
                    Column(Modifier.padding(20.dp)) {
                        SectionLabel("01", "导出位置")
                        Surface(color = colors.surface, border = BorderStroke(1.dp, colors.outline), shape = Corner) {
                            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                Glyph("folder", null, Modifier.size(28.dp), colors.primary)
                                Spacer(Modifier.height(16.dp))
                                Text("默认导出文件夹", style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(8.dp))
                                Text(state.defaultFolderName ?: "每次询问", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                                Spacer(Modifier.height(20.dp))
                                Button(shape = Corner, onClick = chooseFolder, enabled = !state.busy && !state.cleanupPending, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) {
                                    Text(if (state.defaultFolderName == null) "选择文件夹" else "更换文件夹")
                                }
                                if (state.defaultFolderName != null) TextButton(onClick = { model.clearDefaultFolder() }, enabled = !state.busy && !state.cleanupPending, modifier = Modifier.fillMaxWidth()) { Text("每次询问") }
                            }
                        }
                    }
                }
                item {
                    Column(Modifier.padding(horizontal = 20.dp)) {
                        SectionLabel("02", "存储清理")
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(bytes(state.cacheBytes), style = MaterialTheme.typography.displaySmall)
                                Text("临时缓存", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                            }
                            OutlinedButton(onClick = { model.cleanup() }, enabled = !state.busy, shape = Corner, modifier = Modifier.heightIn(min = 48.dp), border = BorderStroke(1.dp, colors.primary)) { Text("清理") }
                        }
                        Spacer(Modifier.height(16.dp))
                        Text("直接写入保存位置，不生成中间安装包。取消或失败时删除未完成文件；启动时重试清理。已保存的成品会保留。", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                        Spacer(Modifier.height(24.dp))
                        HorizontalDivider(color = colors.outlineVariant)
                    }
                }
                item {
                    Column(Modifier.padding(20.dp)) {
                        SectionLabel("03", "格式与权限")
                        SettingText("导出格式", "普通应用：APK\n分包应用：APKS（内含全部 APK）\n批量提取：ZIP")
                        Spacer(Modifier.height(20.dp))
                        SettingText("提取范围", "保留安装包原签名，不包含账号、聊天记录和其他应用数据。分包仅包含当前手机已安装的配置；换设备安装可能不兼容。")
                        Spacer(Modifier.height(20.dp))
                        SettingText("权限", "仅查询已安装应用。无需联网、存储管理权限、Root 或后台服务。")
                        Spacer(Modifier.height(28.dp))
                        HorizontalDivider(color = colors.outline)
                        Spacer(Modifier.height(12.dp))
                        Text("APPExtractor / 1.1.3", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(number: String, text: String) {
    Row(Modifier.padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(number, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun SettingText(title: String, detail: String) {
    Text(title, style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(6.dp))
    Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun AppRow(app: InstalledApp, selecting: Boolean, selected: Boolean, enabled: Boolean, toggle: () -> Unit, export: () -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val icon by produceState<ImageBitmap?>(null, app.packageName, app.updatedAt) {
        value = withContext(Dispatchers.IO) { try { AppCatalog(context.applicationContext).loadIcon(app.packageName)?.toBitmap(96, 96)?.asImageBitmap() } catch (_: Exception) { null } }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).background(if (selecting && selected) colors.secondaryContainer else colors.background)
        .then(if (selecting) Modifier.toggleable(value = selected, enabled = enabled, role = Role.Checkbox, onValueChange = { toggle() }) else Modifier)) {
        Row(Modifier.padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (icon != null) Image(icon!!, contentDescription = null, modifier = Modifier.size(40.dp))
            else Box(Modifier.size(40.dp).background(colors.surface, Corner), contentAlignment = Alignment.Center) { Glyph("package", null, color = colors.primary) }
            Column(Modifier.weight(1f)) {
                Text(app.label, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (selecting) Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Checkbox(checked = selected, onCheckedChange = null, enabled = enabled, colors = CheckboxDefaults.colors(checkedColor = colors.primary, checkmarkColor = colors.onPrimary))
            } else FilledIconButton(onClick = export, enabled = enabled, shape = Corner, modifier = Modifier.size(width = 48.dp, height = 52.dp)) {
                Glyph("download", "提取 ${app.label}")
            }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (app.split) "APKS" else "APK", style = MaterialTheme.typography.labelSmall, color = colors.onSurface, modifier = Modifier.border(1.dp, colors.outline, Corner).padding(horizontal = 7.dp, vertical = 3.dp))
            Text(app.versionName.ifBlank { app.versionCode.toString() }, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(bytes(app.sizeBytes), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        }
        HorizontalDivider(color = colors.outlineVariant)
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
