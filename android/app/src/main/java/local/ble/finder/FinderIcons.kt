package local.ble.finder

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Small consistent vector set; no extended icon bundle, bitmaps or third-party UI dependency. */
internal object FinderIcons {
    private fun line(name: String, block: PathBuilder.() -> Unit) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = block)
        }.build()

    val Bluetooth = line("Bluetooth") {
        moveTo(7f, 7f); lineTo(17f, 17f); lineTo(12f, 21f); lineTo(12f, 3f)
        lineTo(17f, 7f); lineTo(7f, 17f)
    }
    val Tag = line("Tag") {
        moveTo(4f, 4f); lineTo(12f, 4f); lineTo(21f, 13f); lineTo(13f, 21f)
        lineTo(4f, 12f); close()
        moveTo(8f, 7.5f); lineTo(8.5f, 8f)
    }
    val Search = line("Search") {
        moveTo(16f, 10f); curveTo(16f, 13.3f, 13.3f, 16f, 10f, 16f)
        curveTo(6.7f, 16f, 4f, 13.3f, 4f, 10f); curveTo(4f, 6.7f, 6.7f, 4f, 10f, 4f)
        curveTo(13.3f, 4f, 16f, 6.7f, 16f, 10f); moveTo(14.5f, 14.5f); lineTo(21f, 21f)
    }
    val Back = line("Back") {
        moveTo(14f, 5f); lineTo(7f, 12f); lineTo(14f, 19f); moveTo(7f, 12f); lineTo(21f, 12f)
    }
    val Next = line("Next") { moveTo(9f, 5f); lineTo(16f, 12f); lineTo(9f, 19f) }
    val Expand = line("Expand") { moveTo(5f, 9f); lineTo(12f, 16f); lineTo(19f, 9f) }
    val TrendUp = line("TrendUp") {
        moveTo(3f, 17f); lineTo(9f, 11f); lineTo(13f, 14f); lineTo(21f, 6f)
        moveTo(15f, 6f); lineTo(21f, 6f); lineTo(21f, 12f)
    }
    val TrendDown = line("TrendDown") {
        moveTo(3f, 7f); lineTo(9f, 13f); lineTo(13f, 10f); lineTo(21f, 18f)
        moveTo(15f, 18f); lineTo(21f, 18f); lineTo(21f, 12f)
    }
    val Stable = line("Stable") { moveTo(4f, 12f); lineTo(20f, 12f); moveTo(4f, 16f); lineTo(4f, 8f) }
    val Clock = line("Clock") {
        moveTo(21f, 12f); curveTo(21f, 17f, 17f, 21f, 12f, 21f)
        curveTo(7f, 21f, 3f, 17f, 3f, 12f); curveTo(3f, 7f, 7f, 3f, 12f, 3f)
        curveTo(17f, 3f, 21f, 7f, 21f, 12f); moveTo(12f, 7f); lineTo(12f, 12f); lineTo(16f, 14f)
    }
    val Check = line("Check") {
        moveTo(21f, 12f); curveTo(21f, 17f, 17f, 21f, 12f, 21f)
        curveTo(7f, 21f, 3f, 17f, 3f, 12f); curveTo(3f, 7f, 7f, 3f, 12f, 3f)
        moveTo(8f, 11f); lineTo(12f, 15f); lineTo(21f, 5f)
    }
    val Info = line("Info") {
        moveTo(21f, 12f); curveTo(21f, 17f, 17f, 21f, 12f, 21f)
        curveTo(7f, 21f, 3f, 17f, 3f, 12f); curveTo(3f, 7f, 7f, 3f, 12f, 3f)
        curveTo(17f, 3f, 21f, 7f, 21f, 12f)
        moveTo(12f, 11f); lineTo(12f, 17f); moveTo(12f, 7f); lineTo(12f, 7.3f)
    }
}
