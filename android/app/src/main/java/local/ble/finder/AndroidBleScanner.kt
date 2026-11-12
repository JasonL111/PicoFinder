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
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) return context.getString(R.string.ble_unsupported)
        if (!hasPermissions(context)) return context.getString(R.string.ble_permissions_required)
        try {
            val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
                ?: return context.getString(R.string.ble_no_hardware)
            if (!adapter.isEnabled) return context.getString(R.string.ble_disabled)
            if (context.getSystemService(LocationManager::class.java)?.isLocationEnabled != true) {
                return context.getString(R.string.ble_location_disabled)
            }
            val remaining = nextStartMs - SystemClock.elapsedRealtime()
            if (remaining > 0) return context.getString(R.string.ble_wait, (remaining + 999) / 1000)
            val nativeScanner = adapter.bluetoothLeScanner ?: return context.getString(R.string.ble_scanner_unavailable)
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
                            SCAN_FAILED_ALREADY_STARTED -> context.getString(R.string.ble_already_started)
                            SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> context.getString(R.string.ble_registration_failed)
                            SCAN_FAILED_FEATURE_UNSUPPORTED -> context.getString(R.string.ble_feature_unsupported)
                            SCAN_FAILED_INTERNAL_ERROR -> context.getString(R.string.ble_internal_error)
                            SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES -> context.getString(R.string.ble_resources_exhausted)
                            SCAN_FAILED_SCANNING_TOO_FREQUENTLY -> context.getString(R.string.ble_throttled)
                            else -> context.getString(R.string.ble_scan_failed, errorCode)
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
            return context.getString(R.string.ble_permission_revoked)
        } catch (_: IllegalStateException) {
            stop()
            return context.getString(R.string.ble_state_changed)
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
