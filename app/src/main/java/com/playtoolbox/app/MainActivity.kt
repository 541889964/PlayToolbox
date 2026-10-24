package com.playtoolbox.app
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) { super.onCreate(b); setContent { App() } }
}

data class AppInfo(val pkg: String, val name: String, val isSystem: Boolean)

val CAT_COLORS = mapOf(
    "本机应用" to Color(0xFFFF5722), "特色功能" to Color(0xFFE91E63), "系统设置" to Color(0xFF9C6BFF),
    "系统服务" to Color(0xFF22C1C3), "硬件信息" to Color(0xFF00BCD4),
    "常用工具" to Color(0xFF6C9EFF), "日志分析" to Color(0xFF607D8B),
    "运行状态" to Color(0xFF00C48C), "系统属性" to Color(0xFF8BC34A), "全部" to Color(0xFF6C9EFF)
)
fun catColor(c: String) = CAT_COLORS[c] ?: Color(0xFF6C9EFF)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf("全部") }
    
    var installedApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var loadingApps by remember { mutableStateOf(true) }
    
    var activeFeature by remember { mutableStateOf<Feature?>(null) }
    var activeApp by remember { mutableStateOf<AppInfo?>(null) }
    
    var result by remember { mutableStateOf<ParsedOutput?>(null) }
    var running by remember { mutableStateOf(false) }
    var arg by remember { mutableStateOf("") }
    var rootStatus by remember { mutableStateOf<Boolean?>(RootManager.isCached()) }
    var showRootDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (RootManager.suExists() && rootStatus == null) rootStatus = RootManager.request()
        loadingApps = true
        installedApps = withContext(Dispatchers.IO) {
            try {
                val pm = ctx.packageManager
                pm.getInstalledApplications(PackageManager.GET_META_DATA).map { info ->
                    AppInfo(
                        pkg = info.packageName,
                        name = pm.getApplicationLabel(info).toString(),
                        isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    )
                }.sortedBy { it.name }
            } catch (e: Exception) { emptyList() }
        }
        loadingApps = false
    }

    val cats = remember { listOf("全部", "本机应用") + ALL_FEATURES.map { it.category }.distinct().sorted() }
    
    val filteredFeatures = remember(query, cat) {
        if (cat == "本机应用") emptyList() else ALL_FEATURES.filter { f ->
            (cat == "全部" || f.category == cat) &&
            (query.isBlank() || f.name.contains(query, true) || f.desc.contains(query, true))
        }
    }
    
    val filteredApps = remember(query, installedApps) {
        if (query.isBlank()) installedApps else installedApps.filter { 
            it.name.contains(query, true) || it.pkg.contains(query, true) 
        }
    }

    fun doExec(cmd: String, needsRoot: Boolean) {
        scope.launch {
            running = true; result = null
            val finalCmd = if (cmd.contains("{val}")) cmd.replace("{val}", arg.ifBlank { "1" }) else cmd
            val r = ShellExecutor.run(finalCmd, needsRoot)
            result = OutputParser.parse(finalCmd, if (r.ok) r.output else r.error, r.ok)
            running = false
        }
    }

    fun tryRunFeature(f: Feature) {
        if (f.needsRoot && rootStatus != true) { showRootDialog = true } else doExec(f.command, f.needsRoot)
    }
    
    fun runAppAction(app: AppInfo, action: String, needsRoot: Boolean = true) {
        val cmd = when(action) {
            "冻结" -> "pm disable-user --user 0 ${app.pkg}"
            "解冻" -> "pm enable ${app.pkg}"
            "卸载" -> "pm uninstall --user 0 ${app.pkg}"
            "清除数据" -> "pm clear ${app.pkg}"
            "强停" -> "am force-stop ${app.pkg}"
            "详情" -> "dumpsys package ${app.pkg}"
            else -> "echo ok"
        }
        doExec(cmd, needsRoot)
    }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Color(0xFF6C9EFF), background = Color(0xFF0A0E14),
        surface = Color(0xFF151B24), onSurface = Color(0xFFE6EDF3), onSurfaceVariant = Color(0xFF9AA5B4)
    )) {
        Scaffold(containerColor = MaterialTheme.colorScheme.background) { pad ->
            Column(Modifier.padding(pad).fillMaxSize()) {
                Box(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(Color(0xFF4F8CFF), Color(0xFF9C6BFF)))).padding(20.dp, 18.dp)) {
                    Column {
                        Text("玩机工作箱 v8.0", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("纯中文 · 动态读取真实应用", fontSize = 13.sp, color = Color.White.copy(alpha = 0.85f))
                            Spacer(Modifier.width(12.dp))
                            val rs = when (rootStatus) {
                                true -> "Root 已授权" to Color(0xFF00E676)
                                false -> "无 Root 权限" to Color(0xFFFFB300)
                                null -> "检测中" to Color(0xFFB0BEC5)
                            }
                            Row(Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.25f)).padding(horizontal = 10.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Lock, null, tint = rs.second, modifier = Modifier.size(12.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(rs.first, fontSize = 11.sp, color = rs.second)
                            }
                        }
                    }
                }
                
                Surface(Modifier.padding(16.dp, 12.dp, 16.dp, 6.dp).fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp)) {
                        Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(10.dp))
                        TextField(value = query, onValueChange = { query = it }, placeholder = { Text(if(cat=="本机应用") "搜索应用名称..." else "搜索功能...", fontSize = 14.sp) }, modifier = Modifier.weight(1f), singleLine = true, colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent))
                        if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Clear, "清除", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
                
                LazyRow(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(cats.size) { i ->
                        val c = cats[i]; val isSel = c == cat; val col = catColor(c)
                        Box(Modifier.clip(RoundedCornerShape(50)).background(if (isSel) col else MaterialTheme.colorScheme.surface).clickable { cat = c; query = "" }.padding(horizontal = 14.dp, vertical = 7.dp)) {
                            Text(c, fontSize = 13.sp, color = if (isSel) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal)
                        }
                    }
                }
                
                if (cat == "本机应用") {
                    if (loadingApps) {
                        Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                    } else {
                        LazyColumn(Modifier.weight(1f).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                            items(filteredApps, key = { it.pkg }) { app ->
                                AppCard(app) { activeApp = app; result = null }
                            }
                        }
                    }
                } else {
                    LazyColumn(Modifier.weight(1f).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                        items(filteredFeatures, key = { it.id }) { f ->
                            FeatureCard(f) { activeFeature = f; arg = ""; result = null; tryRunFeature(f) }
                        }
                    }
                }
            }
        }

        if (showRootDialog) {
            AlertDialog(onDismissRequest = { showRootDialog = false }, title = { Text("需要 Root 权限") }, text = { Text("此操作涉及系统底层，需要超级管理员权限。请在 Magisk/KernelSU 中授权。") }, confirmButton = {
                TextButton(onClick = { showRootDialog = false; scope.launch { val ok = RootManager.request(); rootStatus = ok } }) { Text("去授权") }
            }, dismissButton = { TextButton(onClick = { showRootDialog = false }) { Text("取消") } })
        }

        activeFeature?.let { f ->
            ModalBottomSheet(onDismissRequest = { activeFeature = null; result = null }, containerColor = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(20.dp).fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(4.dp, 24.dp).clip(RoundedCornerShape(2.dp)).background(catColor(f.category)))
                        Spacer(Modifier.width(10.dp))
                        Text(f.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.weight(1f))
                        if (f.needsRoot) Icon(Icons.Default.Lock, null, tint = Color(0xFFFFB300), modifier = Modifier.size(14.dp))
                    }
                    Spacer(Modifier.height(10.dp))
                    Surface(color = Color(0xFF1D2530), shape = RoundedCornerShape(10.dp)) { Text(f.desc, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(12.dp)) }
                    
                    if (f.hint.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(value = arg, onValueChange = { arg = it }, label = { Text(f.hint) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true)
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { tryRunFeature(f) }, enabled = !running, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = catColor(f.category))) {
                        if (running) { CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp); Spacer(Modifier.width(10.dp)); Text("执行中…") }
                        else { Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("执行") }
                    }
                    AnimatedVisibility(visible = result != null) { result?.let { ResultCard(it) } }
                }
            }
        }
        
        activeApp?.let { app ->
            ModalBottomSheet(onDismissRequest = { activeApp = null; result = null }, containerColor = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(20.dp).fillMaxWidth()) {
                    Text(app.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text(app.pkg, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = Color.Gray)
                    Text(if (app.isSystem) "系统应用" else "用户应用", fontSize = 12.sp, color = Color(0xFFFFB300), modifier = Modifier.padding(top = 4.dp))
                    
                    Spacer(Modifier.height(16.dp))
                    Text("请选择要对该应用执行的操作：", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(onClick = { runAppAction(app, "冻结") }, enabled = !running, modifier = Modifier.weight(1f).height(44.dp), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))) { Text("冻结", fontSize = 13.sp) }
                        Button(onClick = { runAppAction(app, "解冻") }, enabled = !running, modifier = Modifier.weight(1f).height(44.dp), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))) { Text("解冻", fontSize = 13.sp) }
                        Button(onClick = { runAppAction(app, "强停") }, enabled = !running, modifier = Modifier.weight(1f).height(44.dp), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))) { Text("强停", fontSize = 13.sp) }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(onClick = { runAppAction(app, "清除数据") }, enabled = !running, modifier = Modifier.weight(1f).height(44.dp), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336))) { Text("清除数据", fontSize = 13.sp) }
                        Button(onClick = { runAppAction(app, "卸载") }, enabled = !running, modifier = Modifier.weight(1f).height(44.dp), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C27B0))) { Text("卸载应用", fontSize = 13.sp) }
                        Button(onClick = { runAppAction(app, "详情", false) }, enabled = !running, modifier = Modifier.weight(1f).height(44.dp), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF607D8B))) { Text("查看详情", fontSize = 13.sp) }
                    }
                    
                    AnimatedVisibility(visible = result != null, enter = fadeIn(tween(250)) + slideInVertically(tween(250)) { it / 4 }) {
                        result?.let { ResultCard(it) }
                    }
                }
            }
        }
    }
}

@Composable
fun AppCard(app: AppInfo, onClick: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(if (app.isSystem) Color(0xFF424242) else Color(0xFF1565C0)), Alignment.Center) {
                Icon(Icons.Default.Apps, null, tint = Color.White)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(app.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text(app.pkg, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Spacer(Modifier.weight(1f))
            if (app.isSystem) Box(Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0x33FFB300)).padding(horizontal = 4.dp, vertical = 2.dp)) {
                Text("系统", fontSize = 10.sp, color = Color(0xFFFFB300))
            }
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Default.ChevronRight, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun FeatureCard(f: Feature, onClick: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
        Row(Modifier.padding(0.dp)) {
            Box(Modifier.width(4.dp).fillMaxHeight().heightIn(min = 70.dp).background(catColor(f.category)))
            Column(Modifier.padding(14.dp).fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(f.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f), maxLines = 2)
                    if (f.needsRoot) Icon(Icons.Default.Lock, null, tint = Color(0xFFFFB300), modifier = Modifier.size(14.dp))
                }
                Spacer(Modifier.height(3.dp))
                Text(f.desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                Spacer(Modifier.height(6.dp))
                Box(Modifier.clip(RoundedCornerShape(6.dp)).background(catColor(f.category).copy(alpha = 0.15f)).padding(horizontal = 6.dp, vertical = 2.dp)) { Text(f.category, fontSize = 10.sp, color = catColor(f.category)) }
            }
        }
    }
}

@Composable
fun ResultCard(r: ParsedOutput) {
    val clip = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    val color = if (r.isError) Color(0xFFFF5252) else Color(0xFF00E676)
    Column(Modifier.padding(top = 16.dp)) {
        Surface(color = color.copy(alpha = 0.08f), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(14.dp))) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (r.isError) Icons.Default.Error else Icons.Default.CheckCircle, null, tint = color, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(r.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = color, modifier = Modifier.weight(1f))
                    IconButton(onClick = { clip.setText(AnnotatedString(r.raw.ifBlank { r.body })); copied = true }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.ContentCopy, "复制", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp)) }
                }
                Spacer(Modifier.height(8.dp))
                Text(r.body, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, lineHeight = 18.sp, modifier = Modifier.heightIn(max = 320.dp))
                if (copied) { Spacer(Modifier.height(6.dp)); Text("已复制到剪贴板", fontSize = 11.sp, color = color) }
            }
        }
    }
}
