package local.ble.finder

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.annotation.StringRes
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import android.os.ParcelFileDescriptor
import org.junit.Rule
import org.junit.Test

/** Real Compose UI with debug fake transport. Does not claim any physical BLE validation. */
class DemoUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    // A functional wait, allowing cold software emulators to initialize; not a BLE latency claim.
    private fun s(@StringRes id: Int, vararg args: Any) = rule.activity.getString(id, *args)
    private val discoveryWaitMs = 30_000L

    @Test fun demoDiscoveryRenameAndStopHideDistance() {
        val vm = rule.runOnIdle { ViewModelProvider(rule.activity)[DeviceViewModel::class.java] }
        rule.onNodeWithTag("demo-mode").performClick().assertIsOn()
        rule.onNodeWithText(s(R.string.start_scan)).performClick()
        rule.waitUntil(discoveryWaitMs) { vm.ui.value.devices.any { it.deviceId == 0x12345678L } }
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(s(R.string.device_default_name, "12345678")))
        rule.onNodeWithText(s(R.string.device_default_name, "12345678")).performClick()
        rule.onNodeWithTag("signal-ring").assertIsDisplayed()
        saveUiScreenshot(rule, "v12-app-find.png")
        // Stop before editing: keep this functional flow within the real two-minute scan cap.
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(s(R.string.stop_scan)))
        rule.onNodeWithText(s(R.string.stop_scan)).performClick()
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(s(R.string.distance_unavailable)))
        rule.onNodeWithText(s(R.string.distance_unavailable)).assertExists()
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(s(R.string.open_device_settings)))
        rule.onNodeWithText(s(R.string.open_device_settings)).performClick()
        rule.onNodeWithTag("near-alert-vibration").assertIsDisplayed()
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(s(R.string.setting_name)))
        rule.onNodeWithText(s(R.string.setting_name)).performTextReplacement("演示钥匙")
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(s(R.string.save_settings)))
        rule.onNodeWithText(s(R.string.save_settings)).performClick()
        rule.onNodeWithContentDescription(s(R.string.navigate_back)).performClick()
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("演示钥匙"))
        rule.onNodeWithText("演示钥匙").assertExists()
        rule.onNodeWithContentDescription(s(R.string.navigate_back)).performClick()
        rule.onNodeWithTag("device-list").assertIsDisplayed()
    }

    @Test fun backgroundingStopsForegroundScan() {
        rule.onNodeWithTag("demo-mode").performClick().assertIsOn()
        rule.onNodeWithText(s(R.string.start_scan)).performClick()
        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        rule.onNodeWithText(s(R.string.start_scan)).assertIsEnabled()
        rule.onNodeWithText(s(R.string.stop_scan)).assertIsNotEnabled()
        rule.onNodeWithText(s(R.string.scan_background_stopped)).assertExists()
    }

    @Test fun vibrationSwitchSavesImmediatelyAndHasNoStrongRssiEditor() {
        val vm = rule.runOnIdle { ViewModelProvider(rule.activity)[DeviceViewModel::class.java] }
        rule.onNodeWithTag("demo-mode").performClick().assertIsOn()
        rule.onNodeWithText(s(R.string.start_scan)).performClick()
        rule.waitUntil(discoveryWaitMs) { vm.ui.value.devices.any { it.deviceId == 0x12345678L } }
        rule.onNodeWithText(s(R.string.stop_scan)).performClick()
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(s(R.string.device_default_name, "12345678")))
        rule.onNodeWithText(s(R.string.device_default_name, "12345678")).performClick()
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(s(R.string.open_device_settings)))
        rule.onNodeWithText(s(R.string.open_device_settings)).performClick()
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("near-alert-vibration"))
        rule.onNodeWithTag("near-alert-vibration").assertIsOn().performClick().assertIsOff()
        saveUiScreenshot(rule, "v12-app-settings.png")
        rule.runOnIdle {
            org.junit.Assert.assertFalse(vm.ui.value.devices.first { it.deviceId == 0x12345678L }.calibrationParameters.nearAlertVibration)
        }
        // Only the existing name, path-loss, 1 m calibration and lost-timeout inputs.
        rule.onAllNodes(hasSetTextAction()).assertCountEquals(4)
        rule.onNodeWithText("-45 dBm", substring = true).assertDoesNotExist()
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(s(R.string.reset_calibration)))
        rule.onNodeWithText(s(R.string.reset_calibration)).performClick()
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("near-alert-vibration"))
        rule.onNodeWithTag("near-alert-vibration").assertIsOff()
        rule.onNodeWithContentDescription(s(R.string.navigate_back)).performClick()
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(s(R.string.open_device_settings)))
        rule.onNodeWithText(s(R.string.open_device_settings)).performClick()
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("near-alert-vibration"))
        rule.onNodeWithTag("near-alert-vibration").assertIsOff()
        rule.activityRule.scenario.recreate()
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("near-alert-vibration"))
        rule.onNodeWithTag("near-alert-vibration").assertIsOff()
    }

    @Test fun systemAnimationScaleZeroUsesStaticPresentation() {
        fun shell(command: String): String {
            val fd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
            return ParcelFileDescriptor.AutoCloseInputStream(fd).bufferedReader().use { it.readText().trim() }
        }
        val old = shell("settings get global animator_duration_scale").toFloatOrNull() ?: 1f
        try {
            shell("settings put global animator_duration_scale 1")
            rule.waitUntil(5000) {
                rule.onNodeWithTag("finder-app").fetchSemanticsNode().config[FinderMotionEnabled]
            }
            shell("settings put global animator_duration_scale 0")
            rule.waitUntil(5000) {
                !rule.onNodeWithTag("finder-app").fetchSemanticsNode().config[FinderMotionEnabled]
            }
            rule.onNodeWithTag("finder-app").assert(SemanticsMatcher.expectValue(FinderMotionEnabled, false))
            rule.onNodeWithTag("experience-version").assertTextEquals(BuildConfig.VERSION_NAME)
            rule.onNodeWithText(s(R.string.app_name)).assertExists()
        } finally {
            shell("settings put global animator_duration_scale " + old)
        }
    }
}
