package local.ble.finder

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import local.ble.core.CalibrationError
import local.ble.core.DeviceState
import java.util.Locale

/** Resolve resources in the current composition so retained ViewModels survive locale changes. */
sealed interface UiText {
    data class Resource(@param:StringRes val id: Int, val arguments: List<Any> = emptyList()) : UiText
    data class Dynamic(val value: String) : UiText
    data class CalibrationCancelled(val reason: UiText) : UiText
}

internal fun text(@StringRes id: Int, vararg arguments: Any): UiText = UiText.Resource(id, arguments.toList())

@Composable
internal fun UiText.asString(): String = when (this) {
    is UiText.Resource -> stringResource(id, *arguments.toTypedArray())
    is UiText.Dynamic -> value
    is UiText.CalibrationCancelled -> stringResource(R.string.calibration_cancelled, reason.asString())
}

internal fun DeviceState.idHex(): String = String.format(Locale.ROOT, "%08X", deviceId)

@Composable
internal fun DeviceState.localizedName(): String = calibrationParameters.name.ifBlank {
    stringResource(R.string.device_default_name, idHex())
}

internal fun CalibrationError.toUiText(): UiText = text(when (this) {
    CalibrationError.NOT_FINISHED -> R.string.calibration_not_finished
    CalibrationError.INSUFFICIENT_SAMPLES -> R.string.calibration_not_enough_samples
    CalibrationError.UNSTABLE_SIGNAL -> R.string.calibration_unstable
    CalibrationError.INSUFFICIENT_STABLE_SAMPLES -> R.string.calibration_not_enough_stable
    CalibrationError.REFERENCE_OUT_OF_RANGE -> R.string.calibration_reference_invalid
})
