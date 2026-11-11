package local.ble.finder

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import local.ble.core.*

data class FinderUiState(
    val devices: List<DeviceState> = emptyList(),
    val scanning: Boolean = false,
    val demo: Boolean = false,
    val message: String = "点击开始扫描，查找附近兼容标签",
    val calibrationDeviceId: Long? = null,
    val calibrationSeconds: Int = 0,
    val veryStrongSignal: Boolean = false,
)

class DeviceViewModel(application: Application) : AndroidViewModel(application) {
    private val realRepository = DeviceRepository(PreferencesDeviceStore(
        application.getSharedPreferences("finder-devices-v1", Context.MODE_PRIVATE)))
    private val demoRepository = DeviceRepository(object : DeviceStore {
        private var settings = emptyMap<Long, DeviceSettings>()
        override fun load() = settings
        override fun save(devices: Map<Long, DeviceSettings>) { settings = devices.toMap() }
    })
    private var repository = realRepository
    private val realScanner = AndroidBleScanner(application)
    private val demoScanner = DemoSupport.create()
    private var scanner: BleScanner = realScanner
    private var scanStartedMs = 0L
    private var scanGeneration = 0L
    private var foreground = false
    private var findDeviceId: Long? = null
    private val findSession = FindSessionController(HapticFeedbackController(AndroidHapticDevice(application)))
    private var collector: CalibrationCollector? = null
    private val _ui = MutableStateFlow(FinderUiState())
    val ui = _ui.asStateFlow()

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF) != BluetoothAdapter.STATE_ON &&
                !_ui.value.demo && _ui.value.scanning) stop("蓝牙已关闭，扫描已停止")
        }
    }

    init {
        ContextCompat.registerReceiver(application, bluetoothReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED), ContextCompat.RECEIVER_EXPORTED)
        viewModelScope.launch {
            while (isActive) {
                delay(1000)
                val now = SystemClock.elapsedRealtime()
                if (_ui.value.scanning && now - scanStartedMs >= 120000) stop("已扫描 2 分钟并自动停止，可手动重新开始")
                if (_ui.value.scanning && !_ui.value.demo && !AndroidBleScanner.hasPermissions(application)) stop("权限已撤销，扫描已停止")
                val active = collector
                if (active != null) {
                    if (now - active.startedMs >= active.durationMs) finishCalibration(now)
                    else _ui.value = _ui.value.copy(calibrationSeconds = ((active.durationMs - now + active.startedMs + 999) / 1000).toInt())
                }
                refresh()
            }
        }
        refresh()
    }
    fun start() {
        if (_ui.value.scanning || !foreground) return
        scanStartedMs = SystemClock.elapsedRealtime()
        val generation = ++scanGeneration
        // Publish before start so asynchronous failures can atomically stop this session.
        _ui.value = _ui.value.copy(scanning = true, message = if (_ui.value.demo) "模拟扫描中 · 数据不是实际蓝牙测量" else "正在扫描附近标签（前台，最多 2 分钟）")
        updateFindContext()
        val failure = scanner.start({ sample ->
            val now = SystemClock.elapsedRealtime()
            if (_ui.value.scanning && foreground && generation == scanGeneration &&
                sample.timeMs >= scanStartedMs && sample.timeMs <= now && repository.receive(sample)) {
                if (_ui.value.calibrationDeviceId == sample.advertisement.deviceId) collector?.add(sample.rssi, sample.timeMs)
                val device = repository.snapshot(now, true).first { it.deviceId == sample.advertisement.deviceId }
                findSession.onMeasurement(device, sample.timeMs, now)
                // Keep V1's 1 Hz numeric UI cadence; BLE rate must not drive rendering.
                // Confirmation still reacts immediately and only to new measurements.
                val confirmed = findSession.isVeryStrong
                if (_ui.value.veryStrongSignal != confirmed) _ui.value = _ui.value.copy(veryStrongSignal = confirmed)
            }
        }, { error -> if (generation == scanGeneration) stop(error) })
        if (failure != null) stop(failure)
        refresh()
    }
    fun stop(message: String = "扫描已停止；显示的最后 RSSI 不是实时读数") {
        scanGeneration++
        scanner.stop()
        val wasCalibrating = collector != null
        collector = null
        repository.resetSignals()
        _ui.value = _ui.value.copy(scanning = false, calibrationDeviceId = null, calibrationSeconds = 0,
            message = if (wasCalibrating) "$message；校准已取消，未保存" else message)
        updateFindContext()
        refresh()
    }
    fun setForeground(active: Boolean) {
        foreground = active
        updateFindContext()
        if (!active) stop("应用进入后台，扫描已停止；返回后请主动开始")
    }
    fun setFindDevice(id: Long?) {
        findDeviceId = id
        updateFindContext()
        refresh()
    }
    private fun updateFindContext() = findSession.updateContext(findDeviceId, _ui.value.scanning,
        foreground, _ui.value.demo, SystemClock.elapsedRealtime())
    fun setDemo(enabled: Boolean) {
        if (!DemoSupport.available || enabled == _ui.value.demo) return
        stop()
        repository = if (enabled) demoRepository else realRepository
        scanner = if (enabled) demoScanner!! else realScanner
        _ui.value = _ui.value.copy(demo = enabled, message = if (enabled) "演示模式 · 虚拟数据，设置不写入真实设备库" else "真实蓝牙模式，请主动开始扫描")
        refresh()
    }
    fun notify(message: String) { _ui.value = _ui.value.copy(message = message) }
    fun save(id: Long, name: String, n: String, reference: String, timeout: String) {
        if (collector != null) { notify("请先等待校准完成或停止扫描"); return }
        val old = _ui.value.devices.firstOrNull { it.deviceId == id }?.calibrationParameters ?: return
        val settings = old.copy(name = name.trim(), rssiAt1m = reference.toDoubleOrNull(), pathLoss = n.toDoubleOrNull() ?: Double.NaN,
            lostTimeoutMs = timeout.toLongOrNull()?.takeIf { it in 5..120 }?.times(1000) ?: 0)
        if (reference.isNotBlank() && settings.rssiAt1m == null || !repository.update(id, settings)) {
            notify("参数无效：名称最多 40 字，RSSI -100..-20，n 1..6，失联阈值 5..120 秒")
        } else { notify("已保存本地设置"); refresh() }
    }
    fun setNearAlertVibration(id: Long, enabled: Boolean) {
        val settings = _ui.value.devices.firstOrNull { it.deviceId == id }?.calibrationParameters ?: return
        if (repository.update(id, settings.copy(nearAlertVibration = enabled))) {
            if (findDeviceId == id) findSession.updateVibrationEnabled(enabled)
            refresh()
        }
    }
    fun calibrate(id: Long) {
        if (!_ui.value.scanning) { notify("请先开始扫描，再将手机固定在距标签约 1 米处"); return }
        if (collector != null) return
        collector = CalibrationCollector(SystemClock.elapsedRealtime())
        _ui.value = _ui.value.copy(calibrationDeviceId = id, calibrationSeconds = 8,
            message = "校准中：保持约 1 米，固定手机与标签，等待 8 秒")
    }
    private fun finishCalibration(now: Long) {
        val result = collector?.finish(now) ?: return
        val id = _ui.value.calibrationDeviceId
        val settings = _ui.value.devices.firstOrNull { it.deviceId == id }?.calibrationParameters
        collector = null
        _ui.value = _ui.value.copy(calibrationDeviceId = null, calibrationSeconds = 0)
        if (result.rssiAt1m != null && id != null && settings != null && repository.update(id, settings.copy(rssiAt1m = result.rssiAt1m))) {
            notify("1 米校准已保存：%.1f dBm".format(result.rssiAt1m))
        } else notify(result.error ?: "设备不存在，校准未保存")
    }
    fun reset(id: Long) {
        if (collector != null) { notify("请先停止扫描以取消校准"); return }
        val settings = _ui.value.devices.firstOrNull { it.deviceId == id }?.calibrationParameters ?: return
        repository.update(id, DeviceSettings(name = settings.name, nearAlertVibration = settings.nearAlertVibration))
        notify("已恢复广播参考值、n=2.2、失联阈值 10 秒"); refresh()
    }
    fun forget(id: Long) {
        if (findDeviceId == id) setFindDevice(null)
        if (_ui.value.calibrationDeviceId == id) { collector = null; _ui.value = _ui.value.copy(calibrationDeviceId = null, calibrationSeconds = 0) }
        repository.forget(id); notify("本地记录已删除；若标签仍在广播，下次扫描会重新发现"); refresh()
    }
    private fun refresh() {
        val now = SystemClock.elapsedRealtime()
        val devices = repository.snapshot(now, _ui.value.scanning)
        findSession.refresh(devices.firstOrNull { it.deviceId == findDeviceId }, now)
        _ui.value = _ui.value.copy(devices = devices, veryStrongSignal = findSession.isVeryStrong)
    }
    override fun onCleared() {
        findSession.updateContext(null, false, false, _ui.value.demo, SystemClock.elapsedRealtime())
        realScanner.stop(); demoScanner?.stop()
        getApplication<Application>().unregisterReceiver(bluetoothReceiver)
    }
}
