package local.ble.finder

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.os.SystemClock
import local.ble.core.AdvertisementDecoder
import local.ble.core.BleScanner
import local.ble.core.ScanSample

class AndroidBleScanner(private val context: Context) : BleScanner {
    private var callback: ScanCallback? = null
    private var scanner: android.bluetooth.le.BluetoothLeScanner? = null
    private val main = Handler(Looper.getMainLooper())
    private var nextStartMs = 0L

    companion object {
        val permissions = arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
        fun hasPermissions(context: Context) = permissions.all {
            context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
        }
    }

    @SuppressLint("MissingPermission") // Every entry verifies runtime permissions; revocation is caught.
    override fun start(onSample: (ScanSample) -> Unit, onError: (String) -> Unit): String? {
        if (callback != null) return null
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) return "此设备不支持 BLE，请使用真实 BLE 手机或调试演示模式"
        if (!hasPermissions(context)) return "需要附近设备与精确位置权限；拒绝后可在系统设置中授权"
        try {
            val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
                ?: return "没有可用的蓝牙硬件"
            if (!adapter.isEnabled) return "蓝牙已关闭，请点击开启蓝牙"
            if (context.getSystemService(LocationManager::class.java)?.isLocationEnabled != true) {
                return "请开启系统位置开关。本应用仅估算 BLE 相对距离，不读取 GPS"
            }
            val remaining = nextStartMs - SystemClock.elapsedRealtime()
            if (remaining > 0) return "请等待 ${(remaining + 999) / 1000} 秒再开始，避免系统扫描节流"
            val nativeScanner = adapter.bluetoothLeScanner ?: return "蓝牙扫描器暂不可用"
            val session = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    val ad = result.scanRecord?.bytes?.let(AdvertisementDecoder::decode) ?: return
                    val sample = ScanSample(ad, result.rssi, result.timestampNanos / 1_000_000,
                        System.currentTimeMillis() - (SystemClock.elapsedRealtime() - result.timestampNanos / 1_000_000).coerceAtLeast(0))
                    main.post { if (callback === this) onSample(sample) }
                }
                override fun onBatchScanResults(results: MutableList<ScanResult>) {
                    results.forEach { onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, it) }
                }
                override fun onScanFailed(errorCode: Int) {
                    main.post {
                        if (callback !== this) return@post
                        stop()
                        onError(when (errorCode) {
                            SCAN_FAILED_ALREADY_STARTED -> "系统报告扫描已在运行，请停止后稍候重试"
                            SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "扫描注册失败，请稍候重试或重开蓝牙"
                            SCAN_FAILED_FEATURE_UNSUPPORTED -> "系统不支持此 BLE 扫描功能"
                            SCAN_FAILED_INTERNAL_ERROR -> "蓝牙内部错误，请稍候重试"
                            SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES -> "蓝牙扫描资源不足，请关闭其他扫描应用"
                            SCAN_FAILED_SCANNING_TOO_FREQUENTLY -> "系统已限制扫描频率，请等待至少 30 秒再重试"
                            else -> "扫描失败，错误代码 $errorCode"
                        })
                    }
                }
            }
            callback = session
            scanner = nativeScanner
            nextStartMs = SystemClock.elapsedRealtime() + 6000
            // Service UUID LIST is intentionally not required: v1 has only Service Data AD 0x21.
            val filter = ScanFilter.Builder().setServiceData(ParcelUuid(AdvertisementDecoder.serviceUuid),
                byteArrayOf(1), byteArrayOf(0xff.toByte())).build()
            nativeScanner.startScan(listOf(filter), ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).setReportDelay(0).build(), session)
            return null
        } catch (_: SecurityException) {
            stop()
            return "蓝牙权限已撤销，请重新授权"
        } catch (_: IllegalStateException) {
            stop()
            return "蓝牙状态已改变，请重新开启后扫描"
        }
    }

    @SuppressLint("MissingPermission")
    override fun stop() {
        val old = callback
        callback = null
        try { if (old != null) scanner?.stopScan(old) }
        catch (_: SecurityException) { /* Permissions may be revoked while in foreground. */ }
        catch (_: IllegalStateException) { /* Bluetooth may have been turned off. */ }
        scanner = null
    }
}
