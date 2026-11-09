package local.ble.core

/** Foreground transport boundary; no MAC, chipset or connection concepts. */
interface BleScanner {
    /** Return an immediate failure, or report later platform errors via onError. */
    fun start(onSample: (ScanSample) -> Unit, onError: (String) -> Unit): String?
    fun stop()
}
