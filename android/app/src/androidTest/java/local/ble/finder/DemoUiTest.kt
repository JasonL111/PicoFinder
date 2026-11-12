package local.ble.finder

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
    private val discoveryWaitMs = 30_000L

    @Test fun demoDiscoveryRenameAndStopHideDistance() {
        val vm = rule.runOnIdle { ViewModelProvider(rule.activity)[DeviceViewModel::class.java] }
        rule.onNodeWithTag("demo-mode").performClick().assertIsOn()
        rule.onNodeWithText("开始扫描").performClick()
        rule.waitUntil(discoveryWaitMs) { vm.ui.value.devices.any { it.deviceId == 0x12345678L } }
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("标签 12345678"))
        rule.onNodeWithText("标签 12345678").performClick()
        rule.onNodeWithTag("signal-ring").assertIsDisplayed()
        saveUiScreenshot(rule, "v12-app-find.png")
        // Stop before editing: keep this functional flow within the real two-minute scan cap.
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("停止扫描"))
        rule.onNodeWithText("停止扫描").performClick()
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("暂无实时距离"))
        rule.onNodeWithText("暂无实时距离").assertExists()
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("设备设置与 1 米校准"))
        rule.onNodeWithText("设备设置与 1 米校准").performClick()
        rule.onNodeWithTag("near-alert-vibration").assertIsDisplayed()
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("本地名称（最多 40 字）"))
        rule.onNodeWithText("本地名称（最多 40 字）").performTextReplacement("演示钥匙")
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("保存设置"))
        rule.onNodeWithText("保存设置").performClick()
        rule.onNodeWithContentDescription("返回").performClick()
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("演示钥匙"))
        rule.onNodeWithText("演示钥匙").assertExists()
        rule.onNodeWithContentDescription("返回").performClick()
        rule.onNodeWithTag("device-list").assertIsDisplayed()
    }

    @Test fun backgroundingStopsForegroundScan() {
        rule.onNodeWithTag("demo-mode").performClick().assertIsOn()
        rule.onNodeWithText("开始扫描").performClick()
        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        rule.onNodeWithText("开始扫描").assertIsEnabled()
        rule.onNodeWithText("停止扫描").assertIsNotEnabled()
        rule.onNodeWithText("应用进入后台，扫描已停止；返回后请主动开始").assertExists()
    }

    @Test fun vibrationSwitchSavesImmediatelyAndHasNoStrongRssiEditor() {
        val vm = rule.runOnIdle { ViewModelProvider(rule.activity)[DeviceViewModel::class.java] }
        rule.onNodeWithTag("demo-mode").performClick().assertIsOn()
        rule.onNodeWithText("开始扫描").performClick()
        rule.waitUntil(discoveryWaitMs) { vm.ui.value.devices.any { it.deviceId == 0x12345678L } }
        rule.onNodeWithText("停止扫描").performClick()
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("标签 12345678"))
        rule.onNodeWithText("标签 12345678").performClick()
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("设备设置与 1 米校准"))
        rule.onNodeWithText("设备设置与 1 米校准").performClick()
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("near-alert-vibration"))
        rule.onNodeWithTag("near-alert-vibration").assertIsOn().performClick().assertIsOff()
        saveUiScreenshot(rule, "v12-app-settings.png")
        rule.runOnIdle {
            org.junit.Assert.assertFalse(vm.ui.value.devices.first { it.deviceId == 0x12345678L }.calibrationParameters.nearAlertVibration)
        }
        // Only the existing name, path-loss, 1 m calibration and lost-timeout inputs.
        rule.onAllNodes(hasSetTextAction()).assertCountEquals(4)
        rule.onNodeWithText("-45 dBm", substring = true).assertDoesNotExist()
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("恢复默认校准参数"))
        rule.onNodeWithText("恢复默认校准参数").performClick()
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("near-alert-vibration"))
        rule.onNodeWithTag("near-alert-vibration").assertIsOff()
        rule.onNodeWithContentDescription("返回").performClick()
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("设备设置与 1 米校准"))
        rule.onNodeWithText("设备设置与 1 米校准").performClick()
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
            rule.onNodeWithTag("experience-version").assertTextEquals("V1.2")
            rule.onNodeWithText("PicoFinder").assertExists()
        } finally {
            shell("settings put global animator_duration_scale " + old)
        }
    }
}
