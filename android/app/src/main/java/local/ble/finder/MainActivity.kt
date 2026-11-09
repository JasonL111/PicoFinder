package local.ble.finder

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import local.ble.core.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val vm: DeviceViewModel by viewModels()
    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (AndroidBleScanner.hasPermissions(this) && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) vm.start()
        else vm.notify("权限未完整授予：需要附近设备和精确位置；可点击应用权限设置后重试")
    }
    private val enableBluetooth = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        vm.notify("蓝牙设置已返回，请点击开始扫描")
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val ui by vm.ui.collectAsStateWithLifecycle()
            val dark = isSystemInDarkTheme()
            val scheme = if (dark) darkColorScheme(primary = Color(0xff89d5c6))
                else lightColorScheme(primary = Color(0xff006b5b))
            MaterialTheme(colorScheme = scheme) {
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_STOP) vm.stop("应用进入后台，扫描已停止；返回后请主动开始")
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }
                FinderApp(ui, vm, onStart = {
                    if (ui.demo || !packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) ||
                        AndroidBleScanner.hasPermissions(this)) vm.start()
                    else permissionRequest.launch(AndroidBleScanner.permissions)
                }, onBluetooth = {
                    if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                        try { enableBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }
                        catch (_: android.content.ActivityNotFoundException) { vm.notify("没有蓝牙设置界面，请从系统设置开启") }
                        catch (_: SecurityException) { vm.notify("请先授予附近设备权限") }
                    } else permissionRequest.launch(AndroidBleScanner.permissions)
                })
            }
        }
    }
}

private enum class Page { LIST, FIND, SETTINGS }

@Composable
private fun FinderApp(ui: FinderUiState, vm: DeviceViewModel, onStart: () -> Unit, onBluetooth: () -> Unit) {
    var page by rememberSaveable { mutableStateOf(Page.LIST) }
    var selected by rememberSaveable { mutableStateOf<Long?>(null) }
    val device = ui.devices.firstOrNull { it.deviceId == selected }
    val context = LocalContext.current
    BackHandler(page != Page.LIST) { page = if (page == Page.SETTINGS) Page.FIND else Page.LIST }
    Scaffold { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).imePadding().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (page != Page.LIST) TextButton(onClick = { page = if (page == Page.SETTINGS) Page.FIND else Page.LIST }) { Text("返回") }
                    Column(Modifier.weight(1f)) {
                        Text(if (page == Page.LIST) "Local BLE Finder" else if (page == Page.FIND) "查找标签" else "设置与校准",
                            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("本地蓝牙 · 无需配对", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            if (DemoSupport.available) item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("调试演示（虚拟数据）", Modifier.weight(1f))
                    Switch(checked = ui.demo, onCheckedChange = {
                        page = Page.LIST; selected = null; vm.setDemo(it)
                    })
                }
            }
            if (ui.demo) item {
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp)) {
                    Text("模拟模式 · 以下不是实际蓝牙测量\n第二个标签会周期性失联，用于检查界面。", Modifier.padding(14.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(if (ui.scanning) "● 正在扫描" else "扫描已停止", fontWeight = FontWeight.Bold)
                        Text(ui.message, style = MaterialTheme.typography.bodyMedium)
                        if (ui.calibrationDeviceId != null) Text("1 米校准：还需 ${ui.calibrationSeconds} 秒", color = MaterialTheme.colorScheme.primary)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = onStart, enabled = !ui.scanning) { Text("开始扫描") }
                            OutlinedButton(onClick = { vm.stop() }, enabled = ui.scanning) { Text("停止扫描") }
                        }
                    }
                }
            }
            if (!ui.demo && !ui.scanning) item {
                Text("权限说明：附近设备用于扫描和检查蓝牙状态；位置权限用于基于 RSSI 的相对距离估算。不读取 GPS、不联网、不后台扫描。", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onBluetooth) { Text("开启蓝牙") }
                    TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }) { Text("位置开关") }
                    TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        "package:${context.packageName}".toUri())) }) { Text("权限设置") }
                }
            }
            when (page) {
                Page.LIST -> {
                    item { Text("附近与已保存的标签", style = MaterialTheme.typography.titleMedium) }
                    if (ui.devices.isEmpty()) item {
                        Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("还没有发现兼容标签", style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.height(10.dp))
                            Text("给 Pico 2 W 接上 USB 电源，授予权限并开始扫描。\n标签不需要出现在系统蓝牙配对列表中。", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    items(ui.devices, key = { it.deviceId }) { item ->
                        Card(onClick = { selected = item.deviceId; page = Page.FIND }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(item.deviceName, style = MaterialTheme.typography.titleMedium)
                                Text("ID %08X · 协议 v%d".format(item.deviceId, item.protocolVersion), style = MaterialTheme.typography.labelMedium)
                                Text("${statusText(item.deviceStatus)} · ${item.rssi?.let { "$it dBm（末次）" } ?: "尚无读数"}")
                            }
                        }
                    }
                }
                Page.FIND -> item {
                    if (device == null) Text("设备已删除，请返回列表")
                    else FindDevice(device, ui.scanning, onSettings = { page = Page.SETTINGS })
                }
                Page.SETTINGS -> item {
                    if (device == null) Text("设备已删除，请返回列表")
                    else DeviceSettingsPanel(device, ui.calibrationDeviceId != null,
                        onSave = { name, n, reference, timeout -> vm.save(device.deviceId, name, n, reference, timeout) },
                        onCalibrate = { vm.calibrate(device.deviceId) }, onReset = { vm.reset(device.deviceId) },
                        onForget = { vm.forget(device.deviceId); selected = null; page = Page.LIST })
                }
            }
            item {
                HorizontalDivider()
                Text("BLE RSSI 只能粗略估算距离，不是精确测距。遮挡、人体、朝向和反射都会影响结果；请观察数秒的整体趋势。",
                    Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun FindDevice(device: DeviceState, scanning: Boolean, onSettings: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(device.deviceName, style = MaterialTheme.typography.headlineMedium)
        Text("ID %08X".format(device.deviceId), style = MaterialTheme.typography.labelLarge)
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val distance = device.estimatedDistance
                Text(if (distance == null) "—" else if (distance >= 100) ">100" else if (distance < 0.1) "<0.1" else "%.1f".format(distance),
                    fontSize = 64.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(if (distance == null) "暂无实时距离" else "约 · 米", style = MaterialTheme.typography.titleMedium)
                Text(statusText(device.deviceStatus), style = MaterialTheme.typography.titleLarge)
                Text(if (distance == null) "等待新的有效广播" else when (device.trend) {
                    Trend.APPROACHING -> "正在靠近"; Trend.AWAY -> "正在远离"; Trend.STABLE -> "基本不变"
                })
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("末次 RSSI：${device.rssi?.let { "$it dBm" } ?: "—"}")
                Text("平滑 RSSI：${device.smoothedRssi?.let { "%.1f dBm".format(it) } ?: "—"}")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.Bottom) {
                repeat(4) { bar ->
                    Box(Modifier.width(12.dp).height((12 + bar * 9).dp).background(
                        if (bar < device.signalBars) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(3.dp)))
                }
            }
        }
        Text("信号强度 ${device.signalBars}/4 · 电量 ${device.batteryLevel?.let { "$it%" } ?: "未知"}")
        val time = device.lastSeenWallMs?.let { SimpleDateFormat("HH:mm:ss", Locale.SIMPLIFIED_CHINESE).format(Date(it)) } ?: "从未接收"
        Text("最后收到：$time${if (!scanning) "（扫描已停止）" else ""}")
        Text("参考 RSSI：${device.calibrationParameters.rssiAt1m ?: device.advertisedReference.toDouble()} dBm · n=${device.calibrationParameters.pathLoss}",
            style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = onSettings, modifier = Modifier.fillMaxWidth()) { Text("设备设置与 1 米校准") }
    }
}

@Composable
private fun DeviceSettingsPanel(device: DeviceState, calibrating: Boolean,
    onSave: (String, String, String, String) -> Unit, onCalibrate: () -> Unit, onReset: () -> Unit, onForget: () -> Unit) {
    val settings = device.calibrationParameters
    var name by remember(device.deviceId, settings) { mutableStateOf(settings.name) }
    var n by remember(device.deviceId, settings) { mutableStateOf(settings.pathLoss.toString()) }
    var reference by remember(device.deviceId, settings) { mutableStateOf(settings.rssiAt1m?.toString() ?: "") }
    var timeout by remember(device.deviceId, settings) { mutableStateOf((settings.lostTimeoutMs / 1000).toString()) }
    var confirmDelete by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("ID %08X · 协议 v%d".format(device.deviceId, device.protocolVersion), style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("本地名称（最多 40 字）") },
            singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !calibrating)
        OutlinedTextField(value = n, onValueChange = { n = it }, label = { Text("环境衰减系数 n（1..6）") },
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(), enabled = !calibrating)
        OutlinedTextField(value = reference, onValueChange = { reference = it }, label = { Text("1 米 RSSI（-100..-20 dBm）") },
            supportingText = { Text("留空时采用广播参考值 ${device.advertisedReference} dBm") },
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text), modifier = Modifier.fillMaxWidth(), enabled = !calibrating)
        OutlinedTextField(value = timeout, onValueChange = { timeout = it }, label = { Text("失联阈值（5..120 秒）") },
            supportingText = { Text("阈值一半时提示弱信号并隐藏陈旧距离") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), enabled = !calibrating)
        Button(onClick = { onSave(name, n, reference, timeout) }, enabled = !calibrating, modifier = Modifier.fillMaxWidth()) { Text("保存设置") }
        HorizontalDivider()
        Text("将手机与标签相距约 1 米，远离金属和人体遮挡。先保存手动编辑，开始扫描，固定姿态后校准 8 秒。样本不足或波动过大不会覆盖旧值。")
        Button(onClick = onCalibrate, enabled = !calibrating, modifier = Modifier.fillMaxWidth()) { Text(if (calibrating) "正在收集样本…" else "1 米校准") }
        OutlinedButton(onClick = onReset, enabled = !calibrating, modifier = Modifier.fillMaxWidth()) { Text("恢复默认校准参数") }
        TextButton(onClick = { confirmDelete = true }, enabled = !calibrating) { Text("删除本地设备记录", color = MaterialTheme.colorScheme.error) }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false },
        title = { Text("删除本地记录？") }, text = { Text("仅删除名称和校准数据，不会关闭标签。如果仍在广播，扫描时会再次发现。") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onForget() }) { Text("删除") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } })
}

private fun statusText(status: DeviceStatus) = when (status) {
    DeviceStatus.NEVER_SEEN -> "正在寻找 · 尚未接收"
    DeviceStatus.PRESENT -> "正常发现"
    DeviceStatus.WEAK -> "信号弱 / 可能失联"
    DeviceStatus.LOST -> "已失联"
    DeviceStatus.SCAN_STOPPED -> "扫描已停止"
}
