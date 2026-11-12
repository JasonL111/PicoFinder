package local.ble.finder

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.annotation.StringRes
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import local.ble.core.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Actual Compose rendering with controlled, explicitly simulated states, never physical BLE. */
@OptIn(ExperimentalTestApi::class)
class MotionUiTest {
    private val durationScale = object : MotionDurationScale { override val scaleFactor = 1f }
    @get:Rule val rule = createComposeRule(durationScale)

    private fun s(@StringRes id: Int, vararg args: Any) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

    private fun device(id: Long = 1, rssi: Int = -62, bars: Int = 3,
        status: DeviceStatus = DeviceStatus.PRESENT, trend: Trend = Trend.STABLE) =
        DeviceState(id, 1, if (id == 1L) "Keys" else "Backpack", 1000, 1700000000000,
            rssi, rssi.toDouble(), 1.4, null, status, trend, DeviceSettings(name = if (id == 1L) "Keys" else "Backpack"), -59, bars)

    @Test fun gradesAndVeryStrongUseConfirmedStateRatherThanRawPeakOrProgress() {
        var current by mutableStateOf(device(rssi = -92, bars = 1))
        var confirmed by mutableStateOf(false)
        rule.setContent { PicoFinderTheme { Surface {
            SignalHero(current, scanning = true, veryStrong = confirmed, motionEnabled = false)
        } } }
        rule.onNodeWithTag("signal-feedback").assertTextEquals(s(R.string.strength_weak))
        rule.runOnIdle { current = device(rssi = -72, bars = 2) }
        rule.onNodeWithTag("signal-feedback").assertTextEquals(s(R.string.strength_medium))
        rule.runOnIdle { current = device(rssi = -40, bars = 4, trend = Trend.APPROACHING) }
        rule.onNodeWithTag("signal-feedback").assertTextEquals(s(R.string.strength_strong))
        rule.onNodeWithText(s(R.string.strong_signal_guidance)).assertDoesNotExist()
        rule.runOnIdle { confirmed = true }
        rule.onNodeWithTag("signal-feedback").assertTextEquals(s(R.string.strong_signal_guidance))
        rule.onNodeWithText(s(R.string.trend_approaching)).assertExists()
    }

    @Test fun lossAndStopMaskEvenStaleStrongPresentation() {
        var current by mutableStateOf(device(rssi = -40, bars = 4))
        var scanning by mutableStateOf(true)
        rule.setContent { PicoFinderTheme { Surface {
            SignalHero(current, scanning, veryStrong = true, motionEnabled = false)
        } } }
        rule.onNodeWithTag("signal-feedback").assertTextEquals(s(R.string.strong_signal_guidance))
        // Deliberately retain old RSSI/confirmation to exercise the UI's stale-data guard.
        rule.runOnIdle { current = current.copy(deviceStatus = DeviceStatus.LOST) }
        rule.onNodeWithText(s(R.string.status_lost)).assertExists()
        rule.onNodeWithTag("signal-feedback").assertTextEquals(s(R.string.strength_waiting))
        rule.onNodeWithTag("signal-ring").assert(SemanticsMatcher.expectValue(
            SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(0f, 0f..1f)))
        rule.runOnIdle { current = device(rssi = -40, bars = 4); scanning = false }
        rule.onNodeWithTag("signal-feedback").assertTextEquals(s(R.string.strength_waiting))
        rule.onNodeWithText(s(R.string.trend_waiting)).assertExists()
    }

    @Test fun finiteProgressInterpolatesAndReducedMotionSnaps() {
        rule.mainClock.autoAdvance = false
        var target by mutableFloatStateOf(0.2f)
        var allowed by mutableStateOf(true)
        lateinit var display: State<Float>
        rule.setContent {
            display = animateSignalProgress(target, allowed)
            Text("Measurement stays at -62.0 dBm")
        }
        rule.mainClock.advanceTimeByFrame()
        rule.runOnIdle { target = 0.9f }
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeBy(225)
        rule.runOnIdle { assertTrue(display.value > 0.2f && display.value < 0.9f) }
        rule.onNodeWithText("Measurement stays at -62.0 dBm").assertExists()
        rule.mainClock.advanceTimeBy(600)
        rule.runOnIdle { assertEquals(0.9f, display.value, 0.001f); target = 0.35f; allowed = false }
        rule.mainClock.advanceTimeByFrame()
        rule.runOnIdle { assertEquals(0.35f, display.value, 0f) }
        rule.onNodeWithText("Measurement stays at -62.0 dBm").assertExists()
    }

    @Test fun animatedScreenKeepsOriginalRssiAndCalibrationValues() {
        var current by mutableStateOf(device(rssi = -62))
        rule.setContent { PicoFinderTheme { Surface {
            FindDevice(current, FinderUiState(devices = listOf(current), scanning = true, demo = true),
                motionEnabled = true, onSettings = {}, onStart = {}, onStop = {})
        } } }
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("signal-details-toggle"))
        rule.onNodeWithTag("signal-details-toggle").performClick()
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("smoothed-rssi-value"))
        rule.onNodeWithTag("raw-rssi-value").assertTextEquals("-62 dBm")
        rule.onNodeWithTag("smoothed-rssi-value").assertTextEquals("-62.0 dBm")
        rule.runOnIdle { current = current.copy(rssi = -61, smoothedRssi = -61.375) }
        rule.onNodeWithTag("raw-rssi-value").assertTextEquals("-61 dBm")
        rule.onNodeWithTag("smoothed-rssi-value").assertTextEquals("-61.4 dBm")
        rule.runOnIdle { assertEquals(-61.375, current.smoothedRssi!!, 0.0) }
    }

    @Test fun listIdentityAndOrderStayStableWhenSignalsCross() {
        var devices by mutableStateOf(listOf(device(1, -90, 1, status = DeviceStatus.WEAK), device(2, -45, 4)))
        rule.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 1.8f)) {
                PicoFinderTheme {
                    Surface(Modifier.width(320.dp).fillMaxHeight()) {
                        DeviceListPage(FinderUiState(devices = devices, scanning = true, demo = true,
                            message = text(R.string.simulated_data)), motionEnabled = false, onSelect = {}, onStart = {},
                            onStop = {}, onDemo = {}, onBluetooth = {}, onLocation = {}, onPermissions = {})
                    }
                }
            }
        }
        // Exercise the production LazyColumn, including its keys and ordering, on a small viewport.
        rule.onNodeWithTag("device-list").performScrollToNode(hasTestTag("device-2"))
        val first = rule.onNodeWithTag("device-1").fetchSemanticsNode().boundsInRoot
        val second = rule.onNodeWithTag("device-2").fetchSemanticsNode().boundsInRoot
        assertTrue(first.top < second.top)
        rule.runOnIdle { devices = listOf(device(1, -40, 4), device(2, -90, 1, status = DeviceStatus.WEAK)) }
        assertEquals(first.top, rule.onNodeWithTag("device-1").fetchSemanticsNode().boundsInRoot.top, 0.5f)
        assertEquals(second.top, rule.onNodeWithTag("device-2").fetchSemanticsNode().boundsInRoot.top, 0.5f)
        rule.onNodeWithText("Keys").assertExists()
        rule.onNodeWithText("Backpack").assertExists()
    }

    @Test fun lightDarkAndLargeTypeRemainReadableWithStaticMotion() {
        var dark by mutableStateOf(false)
        var scale by mutableFloatStateOf(1f)
        val current = device(rssi = -40, bars = 4, trend = Trend.APPROACHING)
        rule.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, scale)) {
                PicoFinderTheme(dark) { Surface {
                    Column {
                        Text(stringResource(R.string.demo_screenshot_caption, BuildConfig.VERSION_NAME), modifier = Modifier.padding(16.dp))
                        Box(Modifier.weight(1f)) {
                            FindDevice(current, FinderUiState(devices = listOf(current), scanning = true,
                                demo = true, veryStrongSignal = true),
                                motionEnabled = false, onSettings = {}, onStart = {}, onStop = {})
                        }
                    }
                } }
            }
        }
        rule.onNodeWithTag("signal-ring").assertIsDisplayed()
        saveUiScreenshot(rule, "v12-find-light.png")
        rule.runOnIdle { dark = true }
        rule.onNodeWithTag("signal-feedback").assertTextEquals(s(R.string.strong_signal_guidance))
        saveUiScreenshot(rule, "v12-find-dark.png")
        rule.runOnIdle { scale = 1.8f }
        rule.onNodeWithTag("signal-ring").assertIsDisplayed()
        saveUiScreenshot(rule, "v12-find-large-type.png")
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(s(R.string.open_device_settings)))
        rule.onNodeWithText(s(R.string.open_device_settings)).assertIsDisplayed()
    }
}
