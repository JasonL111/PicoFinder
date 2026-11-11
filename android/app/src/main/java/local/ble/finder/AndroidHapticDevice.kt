package local.ble.finder

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import android.provider.Settings

private const val NEAR_ALERT_DURATION_MS = 120L

/** Android 12+. No policy bypass flags, notification access or dangerous permissions. */
class AndroidHapticDevice(private val context: Context) : HapticDevice {
    private val vibrator get() = context.getSystemService(VibratorManager::class.java)?.defaultVibrator

    override fun isAvailableAndAllowed(): Boolean =
        vibrator?.hasVibrator() == true &&
            systemHapticsEnabled() &&
            context.getSystemService(NotificationManager::class.java)?.currentInterruptionFilter ==
                NotificationManager.INTERRUPTION_FILTER_ALL

    private fun systemHapticsEnabled(): Boolean {
        // On 13+ the vibration service applies touch intensity/opt-out to USAGE_TOUCH.
        // HAPTIC_FEEDBACK_ENABLED is the legacy 12/12L setting, deprecated in API33.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return true
        @Suppress("DEPRECATION")
        return Settings.System.getInt(context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0
    }

    override fun pulse() {
        val effect = VibrationEffect.createOneShot(NEAR_ALERT_DURATION_MS, VibrationEffect.DEFAULT_AMPLITUDE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator?.vibrate(effect, VibrationAttributes.Builder().setUsage(VibrationAttributes.USAGE_TOUCH).build())
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(effect, AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        }
    }

    override fun cancel() { vibrator?.cancel() }
}
