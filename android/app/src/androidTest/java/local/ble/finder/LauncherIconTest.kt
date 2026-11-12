package local.ble.finder

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Native drawable previews, not screenshots of a physical launcher. */
class LauncherIconTest {
    @Test fun adaptiveLayersRenderAcrossMasksAndSurroundings() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(context.getDrawable(R.mipmap.ic_launcher) is AdaptiveIconDrawable)
        assertTrue(context.getDrawable(R.mipmap.ic_launcher_round) is AdaptiveIconDrawable)
        val dir = context.getExternalFilesDir("formal-icons")!!
        check(dir.exists() || dir.mkdirs())
        for (dark in listOf(false, true)) for (mask in listOf("circle", "rounded-square", "square")) {
            val bitmap = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val background = if (dark) Color.rgb(16,26,22) else Color.rgb(244,247,245)
            canvas.drawColor(background)
            canvas.save()
            canvas.translate(42f,42f)
            val path = Path().apply {
                when (mask) {
                    "circle" -> addCircle(108f,108f,108f,Path.Direction.CW)
                    "rounded-square" -> addRoundRect(RectF(0f,0f,216f,216f),48f,48f,Path.Direction.CW)
                    else -> addRect(0f,0f,216f,216f,Path.Direction.CW)
                }
            }
            canvas.clipPath(path)
            // Adaptive layers occupy 108 dp around a visible 72 dp mask, including overscan.
            for (id in listOf(R.drawable.ic_launcher_background, R.drawable.ic_launcher_foreground)) {
                context.getDrawable(id)!!.apply { setBounds(-54,-54,270,270); draw(canvas) }
            }
            canvas.restore()
            assertEquals(background,bitmap.getPixel(0,0))
            assertEquals(255,Color.alpha(bitmap.getPixel(150,150)))
            assertTrue(Color.red(bitmap.getPixel(150,150)) < Color.red(bitmap.getPixel(150,100)))
            File(dir,"icon-$mask-${if(dark) "dark" else "light"}.png").outputStream().use {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))
            }
            bitmap.recycle()
        }
    }
}
