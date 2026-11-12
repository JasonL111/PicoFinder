package local.ble.finder

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import local.ble.core.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.Locale

/** Actual resource resolution and production components; all tag states are simulated. */
class LocalizationUiTest {
    @get:Rule val rule = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun localized(tag: String): Context = context.createConfigurationContext(
        Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) })
    private fun s(tag: String, @StringRes id: Int, vararg args: Any) = localized(tag).getString(id, *args)

    // API31 has no public per-app LocaleManager. Scope the override to this test
    // Activity's Resources, including native Dialog windows, and restore it below.
    @Suppress("DEPRECATION")
    private fun configureHost(context: Context, configuration: Configuration) {
        context.resources.updateConfiguration(configuration, context.resources.displayMetrics)
    }

    @Composable private fun WithLocale(tag: String, dark: Boolean = false, scale: Float = 1f, content: @Composable () -> Unit) {
        val base = LocalContext.current
        val configuration = LocalConfiguration.current
        val translated = remember(base, configuration, tag, scale) { base.createConfigurationContext(
            Configuration(configuration).apply { setLocale(Locale.forLanguageTag(tag)); fontScale = scale }) }
        DisposableEffect(base, tag, scale) {
            val original = Configuration(configuration)
            configureHost(base, Configuration(original).apply {
                setLocale(Locale.forLanguageTag(tag)); fontScale = scale
            })
            onDispose { configureHost(base, original) }
        }
        val density = LocalDensity.current.density
        CompositionLocalProvider(LocalContext provides translated,
            LocalConfiguration provides translated.resources.configuration,
            LocalDensity provides Density(density, scale)) {
            PicoFinderTheme(dark) { Surface(Modifier.width(320.dp).fillMaxHeight(), content = content) }
        }
    }

    private fun device() = DeviceState(1, 1, "legacy fallback", 1000, 1700000000000,
        -42, -43.0, 1.4, null, DeviceStatus.PRESENT, Trend.APPROACHING, DeviceSettings(), -59, 4)

    private fun assertFits(value: String, dialog: Boolean = false) {
        val layouts = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText(value, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue("Missing text layout: $value", layouts.isNotEmpty())
        // Paragraphs can retain the wider measurement constraint even when Text
        // wraps its width to its glyphs (e.g. 264 px paragraph, 110 px button text).
        // Check actual lines and visible characters, not that empty paragraph area.
        val problems = layouts.mapNotNull { layout ->
            val clipped = (0 until layout.lineCount).any { line ->
                layout.isLineEllipsized(line) || layout.getLineLeft(line) < -0.5f ||
                    layout.getLineRight(line) > layout.size.width + 0.5f ||
                    layout.getLineBottom(line) > layout.size.height + 0.5f
            }
            val complete = layout.lineCount > 0 &&
                layout.getLineEnd(layout.lineCount - 1) == layout.layoutInput.text.length
            if (clipped || !complete) "size=${layout.size}, lines=${layout.lineCount}, complete=$complete" else null
        }
        if (problems.isNotEmpty()) {
            saveUiScreenshot(rule, "overflow-${value.hashCode()}.png", dialog)
            fail("Text overflow: $value; ${problems.joinToString()}")
        }
    }

    @Test fun retainedMessagesAndNamesResolveAfterLocaleChanges() {
        var tag by mutableStateOf("en")
        val message = UiText.CalibrationCancelled(text(R.string.scan_stopped_message))
        val unnamed = device()
        val named = unnamed.copy(calibrationParameters = DeviceSettings(name = "Travel 钥匙"))
        rule.setContent { WithLocale(tag) { Column {
            Text(message.asString())
            Text(unnamed.localizedName())
            Text(named.localizedName())
        } } }
        for (language in listOf("en", "zh-CN", "en")) {
            rule.runOnIdle { tag = language }
            rule.onNodeWithText(s(language, R.string.calibration_cancelled,
                s(language, R.string.scan_stopped_message))).assertExists()
            rule.onNodeWithText(s(language, R.string.device_default_name, "00000001")).assertExists()
            rule.onNodeWithText("Travel 钥匙").assertExists()
        }
    }

    @Test fun englishDarkLargeTextPagesAndDialog() = pages("en", true)
    @Test fun chineseLightLargeTextPagesAndDialog() = pages("zh-CN", false)

    private fun pages(language: String, dark: Boolean) {
        var page by mutableStateOf(Page.LIST)
        val d = device()
        val ui = FinderUiState(devices = listOf(d), scanning = true, demo = true,
            message = text(R.string.demo_mode_enabled), veryStrongSignal = true)
        rule.setContent { WithLocale(language, dark, 1.5f) {
            Scaffold(topBar = { FinderHeader(page, true) { page = Page.LIST } }) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    when (page) {
                        Page.LIST -> DeviceListPage(ui, false, { page = Page.FIND }, {}, {}, {}, {}, {}, {})
                        Page.FIND -> FindDevice(d, ui, false, { page = Page.SETTINGS }, {}, {})
                        Page.SETTINGS -> LazyColumn(Modifier.fillMaxSize().testTag("localized-settings"),
                            contentPadding = PaddingValues(20.dp)) {
                            item { DeviceSettingsPanel(d, false, ui.message, { _, _, _, _ -> }, {}, {}, {}, {}, {}) }
                        }
                    }
                }
            }
        } }
        rule.onNodeWithTag("experience-version").assertTextEquals(BuildConfig.VERSION_NAME)
        assertFits(s(language, R.string.list_title))
        rule.onNodeWithTag("device-list").performScrollToNode(hasTestTag("device-1"))
        rule.onNodeWithText(s(language, R.string.device_default_name, "00000001")).assertExists()
        saveUiScreenshot(rule, "v1-$language-list.png")
        rule.onNodeWithTag("device-1").performClick()
        rule.onNodeWithTag("find-page").performScrollToNode(hasTestTag("signal-ring"))
        rule.onNodeWithTag("signal-feedback").assertTextEquals(s(language, R.string.strong_signal_guidance))
        saveUiScreenshot(rule, "v1-$language-find.png")
        rule.onNodeWithTag("find-page").performScrollToNode(hasText(s(language, R.string.open_device_settings)))
        assertFits(s(language, R.string.open_device_settings))
        rule.onNodeWithText(s(language, R.string.open_device_settings)).performClick()
        rule.onNodeWithTag("near-alert-vibration").assertIsDisplayed().assertIsOn()
        assertFits(s(language, R.string.near_alert_title))
        saveUiScreenshot(rule, "v1-$language-settings.png")
        for (id in listOf(R.string.setting_name, R.string.setting_path_loss, R.string.setting_reference, R.string.setting_timeout)) {
            rule.onNodeWithTag("localized-settings").performScrollToNode(hasText(s(language, id)))
            assertFits(s(language, id))
        }
        val forgetLabel = hasText(s(language, R.string.forget_device))
        val forgetButton = hasClickAction() and (forgetLabel or hasAnyDescendant(forgetLabel))
        // Scroll the complete touch target into view, including its padding.
        rule.onNodeWithTag("localized-settings").performScrollToNode(forgetButton)
        rule.onNode(forgetButton).assertIsDisplayed().performClick()
        // A platform Dialog has its own window; Compose idleness alone does not
        // guarantee that WindowManager has made it visible on a software emulator.
        try {
            rule.waitUntil(10_000) { rule.onNodeWithText(s(language, R.string.forget_title)).isDisplayed() }
        } catch (failure: ComposeTimeoutException) {
            val capture = runCatching { saveUiScreenshot(rule, "dialog-not-visible-$language.png", dialog = true) }
            val tree = runCatching { rule.onNode(isDialog()).printToString() }.getOrElse { it.message.orEmpty() }
            throw AssertionError("Dialog not visible: $tree" +
                "\nCapture: ${capture.exceptionOrNull()}", failure)
        }
        rule.onNodeWithText(s(language, R.string.forget_title)).assertIsDisplayed()
        assertFits(s(language, R.string.forget_title), dialog = true)
        assertFits(s(language, R.string.forget_description), dialog = true)
        saveUiScreenshot(rule, "v1-$language-dialog.png", dialog = true)
        rule.onNodeWithText(s(language, R.string.cancel)).performClick()
    }

    @Test fun permissionsAndEveryCalibrationFailureAreLocalized() {
        var language by mutableStateOf("en")
        var error by mutableStateOf(CalibrationError.NOT_FINISHED)
        rule.setContent { WithLocale(language) { Column(Modifier.padding(16.dp)) {
            PermissionHelp({}, {}, {})
            Text(error.toUiText().asString())
        } } }
        for (tag in listOf("en", "zh-CN")) {
            rule.runOnIdle { language = tag }
            rule.onNodeWithText(s(tag, R.string.permission_explanation)).assertExists()
            for (id in listOf(R.string.enable_bluetooth, R.string.location_settings, R.string.permission_settings))
                assertFits(s(tag, id))
            for (reason in CalibrationError.entries) {
                rule.runOnIdle { error = reason }
                val id = (reason.toUiText() as UiText.Resource).id
                rule.onNodeWithText(s(tag, id)).assertExists()
            }
        }
    }
}
