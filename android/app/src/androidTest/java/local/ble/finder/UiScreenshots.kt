package local.ble.finder

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onRoot
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/** Saves actual rendered pixels. Test fixtures are explicitly labelled as simulated data. */
internal fun saveUiScreenshot(rule: ComposeTestRule, name: String) {
    val dir = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir("v12-screens")!!
    check(dir.exists() || dir.mkdirs())
    File(dir, name).outputStream().use {
        check(rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
    }
}
