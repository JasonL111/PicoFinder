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
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import local.ble.core.*

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
            PicoFinderTheme {
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
    override fun onStart() {
        super.onStart()
        vm.setForeground(true)
    }
    override fun onStop() {
        vm.setForeground(false)
        super.onStop()
    }
}

private enum class Page { LIST, FIND, SETTINGS }

@Composable
private fun FinderApp(ui: FinderUiState, vm: DeviceViewModel, onStart: () -> Unit, onBluetooth: () -> Unit) {
    var page by rememberSaveable { mutableStateOf(Page.LIST) }
    var selected by rememberSaveable { mutableStateOf<Long?>(null) }
    val context = LocalContext.current
    val motionEnabled = rememberFinderMotionEnabled()
    fun navigate(destination: Page, id: Long? = selected) {
        // Session boundaries remain immediate, independent of outgoing visual transitions.
        vm.setFindDevice(id.takeIf { destination == Page.FIND })
        selected = id
        page = destination
    }
    DisposableEffect(page, selected) {
        vm.setFindDevice(selected.takeIf { page == Page.FIND })
        onDispose { vm.setFindDevice(null) }
    }
    BackHandler(page != Page.LIST) { navigate(if (page == Page.SETTINGS) Page.FIND else Page.LIST) }
    Scaffold(
        modifier = Modifier.testTag("finder-app").semantics { finderMotionEnabled = motionEnabled },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { FinderHeader(page, ui.demo) { navigate(if (page == Page.SETTINGS) Page.FIND else Page.LIST) } },
    ) { padding ->
        AnimatedContent(targetState = page to selected,
            modifier = Modifier.fillMaxSize().padding(padding).imePadding(),
            transitionSpec = {
                val enter = if (motionEnabled) fadeIn(tween(FinderStyle.pageMotionMs)) +
                    slideInVertically(tween(FinderStyle.pageMotionMs)) { it / 40 } else EnterTransition.None
                val exit = if (motionEnabled) fadeOut(tween(140)) else ExitTransition.None
                (enter togetherWith exit).using(if (motionEnabled) SizeTransform(clip = false,
                    sizeAnimationSpec = { _, _ -> tween(FinderStyle.pageMotionMs) }) else null)
            }, label = "finder-page") { route ->
            val device = ui.devices.firstOrNull { it.deviceId == route.second }
            // Each route owns its scroll state. Opening settings or a tag starts at its top.
            when (route.first) {
                Page.LIST -> DeviceListPage(ui, motionEnabled,
                    onSelect = { navigate(Page.FIND, it) }, onStart = onStart, onStop = { vm.stop() },
                    onDemo = { navigate(Page.LIST, null); vm.setDemo(it) }, onBluetooth = onBluetooth,
                    onLocation = { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
                    onPermissions = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        "package:".plus(context.packageName).toUri())) })
                Page.FIND -> if (device == null) MissingDevice() else FindDevice(device, ui, motionEnabled,
                    onSettings = { navigate(Page.SETTINGS) }, onStart = onStart, onStop = { vm.stop() })
                Page.SETTINGS -> if (device == null) MissingDevice() else {
                    LazyColumn(Modifier.fillMaxSize().testTag("settings-page"),
                        contentPadding = PaddingValues(FinderStyle.pagePadding),
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        item {
                            DeviceSettingsPanel(device, ui.calibrationDeviceId != null, ui.message,
                                onSave = { name, n, reference, timeout -> vm.save(device.deviceId, name, n, reference, timeout) },
                                onVibration = { vm.setNearAlertVibration(device.deviceId, it) },
                                onCalibrate = { vm.calibrate(device.deviceId) }, onReset = { vm.reset(device.deviceId) },
                                onForget = { vm.forget(device.deviceId); navigate(Page.LIST, null) },
                                calibrationControls = { ScanControls(ui, onStart, { vm.stop() }, motionEnabled) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FinderHeader(page: Page, demo: Boolean, onBack: () -> Unit) {
    Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (page != Page.LIST) IconButton(onClick = onBack, modifier = Modifier.testTag("navigate-back")) {
                Icon(FinderIcons.Back, contentDescription = "返回")
            } else Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(when (page) {
                    Page.LIST -> "PicoFinder"; Page.FIND -> "查找标签"; Page.SETTINGS -> "设置与校准"
                }, style = MaterialTheme.typography.headlineSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(if (demo) "模拟模式 · 不触发真实振动" else "本地蓝牙 · 无需配对",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                        Text("V1.2", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp).testTag("experience-version"))
                    }
                }
            }
        }
    }
}

@Composable
private fun MissingDevice() {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text("设备已删除，请返回列表", style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun DeviceSettingsPanel(device: DeviceState, calibrating: Boolean, message: String,
    onSave: (String, String, String, String) -> Unit, onVibration: (Boolean) -> Unit,
    onCalibrate: () -> Unit, onReset: () -> Unit, onForget: () -> Unit,
    calibrationControls: @Composable () -> Unit) {
    val settings = device.calibrationParameters
    var name by remember(device.deviceId, settings.name) { mutableStateOf(settings.name) }
    var n by remember(device.deviceId, settings.pathLoss) { mutableStateOf(settings.pathLoss.toString()) }
    var reference by remember(device.deviceId, settings.rssiAt1m) { mutableStateOf(settings.rssiAt1m?.toString() ?: "") }
    var timeout by remember(device.deviceId, settings.lostTimeoutMs) { mutableStateOf((settings.lostTimeoutMs / 1000).toString()) }
    var confirmDelete by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("ID %08X · 协议 v%d".format(device.deviceId, device.protocolVersion),
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("提醒偏好", style = MaterialTheme.typography.titleMedium)
        FinderCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("极近提醒振动", style = MaterialTheme.typography.titleMedium)
                    Text("当蓝牙信号持续非常强时，手机会短暂振动一次。",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = settings.nearAlertVibration, onCheckedChange = onVibration,
                    modifier = Modifier.testTag("near-alert-vibration").semantics { contentDescription = "极近提醒振动" })
            }
            Text("自动保存到当前设备；关闭振动仍可查看全部信号变化。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("标签与测距参数", style = MaterialTheme.typography.titleMedium)
        FinderCard {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("本地名称（最多 40 字）") },
                singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !calibrating)
            OutlinedTextField(value = n, onValueChange = { n = it }, label = { Text("环境衰减系数 n（1..6）") },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(), enabled = !calibrating)
            OutlinedTextField(value = reference, onValueChange = { reference = it }, label = { Text("1 米 RSSI（-100..-20 dBm）") },
                supportingText = { Text("留空时采用广播参考值 %d dBm".format(device.advertisedReference)) },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.fillMaxWidth(), enabled = !calibrating)
            OutlinedTextField(value = timeout, onValueChange = { timeout = it }, label = { Text("失联阈值（5..120 秒）") },
                supportingText = { Text("阈值一半时提示弱信号并隐藏陈旧距离") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(), enabled = !calibrating)
            Button(onClick = { onSave(name, n, reference, timeout) }, enabled = !calibrating,
                modifier = Modifier.fillMaxWidth()) { Text("保存设置") }
            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("1 米校准", style = MaterialTheme.typography.titleMedium)
        calibrationControls()
        FinderCard {
            Text("手机与标签相距约 1 米，远离金属和人体遮挡。先保存手动编辑，开始扫描，固定姿态后校准 8 秒。",
                style = MaterialTheme.typography.bodyMedium)
            Text("样本不足或波动过大时，不会覆盖已有校准。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onCalibrate, enabled = !calibrating, modifier = Modifier.fillMaxWidth()) {
                Text(if (calibrating) "正在收集样本…" else "1 米校准")
            }
            OutlinedButton(onClick = onReset, enabled = !calibrating, modifier = Modifier.fillMaxWidth()) {
                Text("恢复默认校准参数")
            }
        }
        TextButton(onClick = { confirmDelete = true }, enabled = !calibrating) {
            Text("删除本地设备记录", color = MaterialTheme.colorScheme.error)
        }
        FinderCard {
            Text("关于 PicoFinder", style = MaterialTheme.typography.titleMedium)
            Text("界面版本 V1.2", style = MaterialTheme.typography.bodyMedium)
            Text("当前安装版本：" + BuildConfig.VERSION_NAME + "（" + BuildConfig.VERSION_CODE + "）",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("installed-version"))
            Text("完全本地运行。界面动画仅显示相对信号，不影响蓝牙测量或极近提醒。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SignalCaution()
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false },
        title = { Text("删除本地记录？") },
        text = { Text("删除名称、校准和振动设置，不会关闭标签。如果仍在广播，扫描时会再次发现。") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onForget() }) { Text("删除") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } })
}
