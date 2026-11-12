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
import androidx.compose.ui.res.stringResource
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
        else vm.notify(R.string.permissions_incomplete)
    }
    private val enableBluetooth = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        vm.notify(R.string.bluetooth_settings_returned)
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
                        catch (_: android.content.ActivityNotFoundException) { vm.notify(R.string.no_bluetooth_settings) }
                        catch (_: SecurityException) { vm.notify(R.string.nearby_permission_required) }
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

internal enum class Page { LIST, FIND, SETTINGS }

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
internal fun FinderHeader(page: Page, demo: Boolean, onBack: () -> Unit) {
    Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (page != Page.LIST) IconButton(onClick = onBack, modifier = Modifier.testTag("navigate-back")) {
                Icon(FinderIcons.Back, contentDescription = stringResource(R.string.navigate_back))
            } else Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(when (page) {
                    Page.LIST -> stringResource(R.string.app_name); Page.FIND -> stringResource(R.string.page_find); Page.SETTINGS -> stringResource(R.string.page_settings)
                }, style = MaterialTheme.typography.headlineSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(if (demo) stringResource(R.string.demo_subtitle) else stringResource(R.string.local_subtitle),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                        Text(BuildConfig.VERSION_NAME, style = MaterialTheme.typography.labelMedium,
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
        Text(stringResource(R.string.device_missing), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
internal fun DeviceSettingsPanel(device: DeviceState, calibrating: Boolean, message: UiText,
    onSave: (String, String, String, String) -> Unit, onVibration: (Boolean) -> Unit,
    onCalibrate: () -> Unit, onReset: () -> Unit, onForget: () -> Unit,
    calibrationControls: @Composable () -> Unit) {
    val vibrationLabel = stringResource(R.string.near_alert_title)
    val settings = device.calibrationParameters
    var name by remember(device.deviceId, settings.name) { mutableStateOf(settings.name) }
    var n by remember(device.deviceId, settings.pathLoss) { mutableStateOf(settings.pathLoss.toString()) }
    var reference by remember(device.deviceId, settings.rssiAt1m) { mutableStateOf(settings.rssiAt1m?.toString() ?: "") }
    var timeout by remember(device.deviceId, settings.lostTimeoutMs) { mutableStateOf((settings.lostTimeoutMs / 1000).toString()) }
    var confirmDelete by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(stringResource(R.string.device_id_protocol, device.idHex(), device.protocolVersion),
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.reminder_preferences), style = MaterialTheme.typography.titleMedium)
        FinderCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.near_alert_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.near_alert_description),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = settings.nearAlertVibration, onCheckedChange = onVibration,
                    modifier = Modifier.testTag("near-alert-vibration").semantics { contentDescription = vibrationLabel })
            }
            Text(stringResource(R.string.near_alert_saved),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(stringResource(R.string.tag_parameters), style = MaterialTheme.typography.titleMedium)
        FinderCard {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.setting_name)) },
                supportingText = { Text(stringResource(R.string.setting_name_hint)) },
                singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !calibrating)
            OutlinedTextField(value = n, onValueChange = { n = it }, label = { Text(stringResource(R.string.setting_path_loss)) },
                supportingText = { Text(stringResource(R.string.path_loss_hint)) },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(), enabled = !calibrating)
            OutlinedTextField(value = reference, onValueChange = { reference = it }, label = { Text(stringResource(R.string.setting_reference)) },
                supportingText = { Text(stringResource(R.string.reference_hint, device.advertisedReference)) },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.fillMaxWidth(), enabled = !calibrating)
            OutlinedTextField(value = timeout, onValueChange = { timeout = it }, label = { Text(stringResource(R.string.setting_timeout)) },
                supportingText = { Text(stringResource(R.string.timeout_hint)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(), enabled = !calibrating)
            Button(onClick = { onSave(name, n, reference, timeout) }, enabled = !calibrating,
                modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.save_settings)) }
            Text(message.asString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(stringResource(R.string.calibration_title), style = MaterialTheme.typography.titleMedium)
        calibrationControls()
        FinderCard {
            Text(stringResource(R.string.calibration_instructions),
                style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.calibration_retains_old),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onCalibrate, enabled = !calibrating, modifier = Modifier.fillMaxWidth()) {
                Text(if (calibrating) stringResource(R.string.calibration_collecting) else stringResource(R.string.calibration_title))
            }
            OutlinedButton(onClick = onReset, enabled = !calibrating, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.reset_calibration))
            }
        }
        TextButton(onClick = { confirmDelete = true }, enabled = !calibrating) {
            Text(stringResource(R.string.forget_device), color = MaterialTheme.colorScheme.error)
        }
        FinderCard {
            Text(stringResource(R.string.about_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.installed_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("installed-version"))
            Text(stringResource(R.string.about_local),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SignalCaution()
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false },
        title = { Text(stringResource(R.string.forget_title)) },
        text = { Text(stringResource(R.string.forget_description)) },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onForget() }) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } })
}
