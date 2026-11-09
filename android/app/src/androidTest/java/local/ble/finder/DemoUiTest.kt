package local.ble.finder

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import org.junit.Rule
import org.junit.Test

/** Real Compose UI with debug fake transport. Does not claim any physical BLE validation. */
class DemoUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun demoDiscoveryRenameAndStopHideDistance() {
        val vm = rule.runOnIdle { ViewModelProvider(rule.activity)[DeviceViewModel::class.java] }
        rule.onNode(isToggleable()).performClick()
        rule.onNodeWithText("开始扫描").performClick()
        rule.waitUntil(10000) { vm.ui.value.devices.any { it.deviceId == 0x12345678L } }
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("标签 12345678"))
        rule.onNodeWithText("标签 12345678").performClick()
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("设备设置与 1 米校准"))
        rule.onNodeWithText("设备设置与 1 米校准").performClick()
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("本地名称（最多 40 字）"))
        rule.onNodeWithText("本地名称（最多 40 字）").performTextReplacement("演示钥匙")
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("保存设置"))
        rule.onNodeWithText("保存设置").performClick()
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("返回"))
        rule.onNodeWithText("返回").performClick()
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("停止扫描"))
        rule.onNodeWithText("停止扫描").performClick()
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("暂无实时距离"))
        rule.onNodeWithText("暂无实时距离").assertExists()
        rule.onNodeWithText("演示钥匙").assertExists()
    }

    @Test fun backgroundingStopsForegroundScan() {
        rule.onNode(isToggleable()).performClick()
        rule.onNodeWithText("开始扫描").performClick()
        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        rule.onNodeWithText("开始扫描").assertIsEnabled()
        rule.onNodeWithText("停止扫描").assertIsNotEnabled()
        rule.onNodeWithText("应用进入后台，扫描已停止；返回后请主动开始").assertExists()
    }
}
