package local.ble.finder

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import androidx.annotation.StringRes
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
    val message: UiText = text(R.string.scan_prompt),
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
                !_ui.value.demo && _ui.value.scanning) stop(text(R.string.bluetooth_turned_off))
        }
    }

    init {
        ContextCompat.registerReceiver(application, bluetoothReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED), ContextCompat.RECEIVER_EXPORTED)
        viewModelScope.launch {
            while (isActive) {
                delay(1000)
                val now = SystemClock.elapsedRealtime()
                if (_ui.value.scanning && now - scanStartedMs >= 120000) stop(text(R.string.scan_time_limit))
                if (_ui.value.scanning && !_ui.value.demo && !AndroidBleScanner.hasPermissions(application)) stop(text(R.string.scan_permission_revoked))
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
        _ui.value = _ui.value.copy(scanning = true, message = if (_ui.value.demo) text(R.string.scan_demo_message) else text(R.string.scan_real_message))
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
        }, { error -> if (generation == scanGeneration) stop(UiText.Dynamic(error)) })
        if (failure != null) stop(UiText.Dynamic(failure))
        refresh()
    }
    fun stop(message: UiText = text(R.string.scan_stopped_message)) {
        scanGeneration++
        scanner.stop()
        val wasCalibrating = collector != null
        collector = null
        repository.resetSignals()
        _ui.value = _ui.value.copy(scanning = false, calibrationDeviceId = null, calibrationSeconds = 0,
            message = if (wasCalibrating) UiText.CalibrationCancelled(message) else message)
        updateFindContext()
        refresh()
    }
    fun setForeground(active: Boolean) {
        foreground = active
        updateFindContext()
        if (!active) stop(text(R.string.scan_background_stopped))
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
        _ui.value = _ui.value.copy(demo = enabled, message = if (enabled) text(R.string.demo_mode_enabled) else text(R.string.real_mode_enabled))
        refresh()
    }
    fun notify(@StringRes id: Int, vararg arguments: Any) = notify(text(id, *arguments))
    private fun notify(message: UiText) { _ui.value = _ui.value.copy(message = message) }
    fun save(id: Long, name: String, n: String, reference: String, timeout: String) {
        if (collector != null) { notify(text(R.string.wait_for_calibration)); return }
        val old = _ui.value.devices.firstOrNull { it.deviceId == id }?.calibrationParameters ?: return
        val settings = old.copy(name = name.trim(), rssiAt1m = reference.toDoubleOrNull(), pathLoss = n.toDoubleOrNull() ?: Double.NaN,
            lostTimeoutMs = timeout.toLongOrNull()?.takeIf { it in 5..120 }?.times(1000) ?: 0)
        if (reference.isNotBlank() && settings.rssiAt1m == null || !repository.update(id, settings)) {
            notify(text(R.string.invalid_settings))
        } else { notify(text(R.string.settings_saved)); refresh() }
    }
    fun setNearAlertVibration(id: Long, enabled: Boolean) {
        val settings = _ui.value.devices.firstOrNull { it.deviceId == id }?.calibrationParameters ?: return
        if (repository.update(id, settings.copy(nearAlertVibration = enabled))) {
            if (findDeviceId == id) findSession.updateVibrationEnabled(enabled)
            refresh()
        }
    }
    fun calibrate(id: Long) {
        if (!_ui.value.scanning) { notify(text(R.string.calibration_start_scan)); return }
        if (collector != null) return
        collector = CalibrationCollector(SystemClock.elapsedRealtime())
        _ui.value = _ui.value.copy(calibrationDeviceId = id, calibrationSeconds = 8,
            message = text(R.string.calibration_started))
    }
    private fun finishCalibration(now: Long) {
        val result = collector?.finish(now) ?: return
        val id = _ui.value.calibrationDeviceId
        val settings = _ui.value.devices.firstOrNull { it.deviceId == id }?.calibrationParameters
        collector = null
        _ui.value = _ui.value.copy(calibrationDeviceId = null, calibrationSeconds = 0)
        if (result.rssiAt1m != null && id != null && settings != null && repository.update(id, settings.copy(rssiAt1m = result.rssiAt1m))) {
            notify(text(R.string.calibration_saved, result.rssiAt1m!!))
        } else notify(result.error?.toUiText() ?: text(R.string.calibration_device_missing))
    }
    fun reset(id: Long) {
        if (collector != null) { notify(text(R.string.stop_before_reset)); return }
        val settings = _ui.value.devices.firstOrNull { it.deviceId == id }?.calibrationParameters ?: return
        repository.update(id, DeviceSettings(name = settings.name, nearAlertVibration = settings.nearAlertVibration))
        notify(text(R.string.calibration_reset)); refresh()
    }
    fun forget(id: Long) {
        if (findDeviceId == id) setFindDevice(null)
        if (_ui.value.calibrationDeviceId == id) { collector = null; _ui.value = _ui.value.copy(calibrationDeviceId = null, calibrationSeconds = 0) }
        repository.forget(id); notify(text(R.string.device_forgotten)); refresh()
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
