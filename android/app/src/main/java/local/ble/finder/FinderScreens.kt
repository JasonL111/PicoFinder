package local.ble.finder

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import local.ble.core.DeviceState
import local.ble.core.DeviceStatus
import local.ble.core.Trend
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun FinderCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth(), shape = RoundedCornerShape(FinderStyle.cardRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}

@Composable
internal fun SoftStateText(text: String, motionEnabled: Boolean, modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    AnimatedContent(text, modifier = modifier, transitionSpec = {
        ((if (motionEnabled) fadeIn(tween(FinderStyle.stateMotionMs)) else EnterTransition.None) togetherWith
            (if (motionEnabled) fadeOut(tween(120)) else ExitTransition.None)).using(
                if (motionEnabled) SizeTransform(clip = false, sizeAnimationSpec = { _, _ -> tween(FinderStyle.stateMotionMs) }) else null)
    }, label = "state-label") { value ->
        Text(value, style = MaterialTheme.typography.labelLarge, color = color)
    }
}

@Composable
internal fun ScanControls(ui: FinderUiState, onStart: () -> Unit, onStop: () -> Unit,
    motionEnabled: Boolean, compact: Boolean = false) {
    FinderCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // This is a finite reveal on start/stop, never a continuously spinning indicator.
            val color by finderAnimatedColor(if (ui.scanning) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant, motionEnabled)
            Icon(FinderIcons.Search, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f)) {
                SoftStateText(if (ui.scanning) "正在扫描" else "扫描已停止", motionEnabled,
                    modifier = Modifier.testTag("scan-status"), color = color)
                Text(if (ui.scanning) "仅在前台 · 最长 2 分钟" else "已保留最后发现的信息",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (!compact || !ui.scanning) Text(ui.message, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (ui.calibrationDeviceId != null) Text("1 米校准：还需 %d 秒".format(ui.calibrationSeconds),
            color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
        if (compact) {
            if (ui.scanning) OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) { Text("停止扫描") }
            else Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text("开始扫描") }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onStart, enabled = !ui.scanning, modifier = Modifier.weight(1f)) { Text("开始扫描") }
                OutlinedButton(onClick = onStop, enabled = ui.scanning, modifier = Modifier.weight(1f)) { Text("停止扫描") }
            }
        }
    }
}

@Composable
internal fun DeviceListPage(ui: FinderUiState, motionEnabled: Boolean, onSelect: (Long) -> Unit,
    onStart: () -> Unit, onStop: () -> Unit, onDemo: (Boolean) -> Unit,
    onBluetooth: () -> Unit, onLocation: () -> Unit, onPermissions: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().testTag("device-list"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(FinderStyle.pagePadding)) {
        item {
            Text("找回身边的小物件", style = MaterialTheme.typography.headlineSmall)
            Text("选择一个标签，慢慢走动，观察信号的变化。",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp))
        }
        item { ScanControls(ui, onStart, onStop, motionEnabled) }
        if (DemoSupport.available) item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("调试演示（虚拟数据）", style = MaterialTheme.typography.labelLarge)
                    Text("仅预览界面，不触发真实振动", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(ui.demo, onDemo, modifier = Modifier.testTag("demo-mode")
                    .semantics {
                        contentDescription = "调试演示（虚拟数据）"
                    })
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("附近与已保存的标签", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("%d 个".format(ui.devices.size), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (ui.devices.isEmpty()) item {
            FinderCard {
                Icon(FinderIcons.Tag, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp))
                Text("还没有发现兼容标签", style = MaterialTheme.typography.titleMedium)
                Text("给 Pico 2 W 接上 USB 电源，授予权限并开始扫描。标签无需出现在系统蓝牙配对列表中。",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        // Keep repository order and stable identity; RSSI never drives item order or entry motion.
        items(ui.devices, key = { it.deviceId }) { device ->
            DeviceListCard(device, motionEnabled) { onSelect(device.deviceId) }
        }
        if (!ui.demo && !ui.scanning) item {
            PermissionHelp(onBluetooth, onLocation, onPermissions)
        }
        item { SignalCaution() }
        item {
            Text("PicoFinder · " + BuildConfig.VERSION_NAME + "（" + BuildConfig.VERSION_CODE + "）",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("list-installed-version"))
        }
    }
}

@Composable
internal fun DeviceListCard(device: DeviceState, motionEnabled: Boolean, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().testTag("device-" + device.deviceId),
        shape = RoundedCornerShape(FinderStyle.cardRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))) {
        Row(Modifier.padding(18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center) {
                Icon(FinderIcons.Tag, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(device.deviceName, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val statusHeight = with(LocalDensity.current) {
                    (MaterialTheme.typography.labelLarge.lineHeight * 2).toDp()
                }
                Box(Modifier.heightIn(min = statusHeight), contentAlignment = Alignment.CenterStart) {
                    SoftStateText(statusText(device.deviceStatus), motionEnabled)
                }
                Text(device.rssi?.let { "末次 $it dBm" } ?: "等待首次广播",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(FinderIcons.Next, contentDescription = "查找此标签", tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
internal fun PermissionHelp(onBluetooth: () -> Unit, onLocation: () -> Unit, onPermissions: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("附近设备与位置权限用于蓝牙扫描和粗略估距。不读取 GPS，不联网，不后台扫描。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // Wrapping is intentional on small screens and with large system text.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onBluetooth) { Text("开启蓝牙") }
            TextButton(onClick = onLocation) { Text("位置开关") }
            TextButton(onClick = onPermissions) { Text("权限设置") }
        }
    }
}

@Composable
internal fun FindDevice(device: DeviceState, ui: FinderUiState, motionEnabled: Boolean,
    onSettings: () -> Unit, onStart: () -> Unit, onStop: () -> Unit) {
    val live = hasLiveSignal(device, ui.scanning)
    LazyColumn(Modifier.fillMaxSize().testTag("find-page"),
        contentPadding = PaddingValues(FinderStyle.pagePadding),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text(device.deviceName, style = MaterialTheme.typography.headlineMedium)
            Text("ID %08X".format(device.deviceId), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        }
        item { SignalHero(device, ui.scanning, ui.veryStrongSignal, motionEnabled) }
        item { DeviceOverview(device, live, motionEnabled) }
        item {
            OutlinedButton(onClick = onSettings, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text("设备设置与 1 米校准")
                Spacer(Modifier.width(8.dp))
                Icon(FinderIcons.Next, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
        item { SignalDetails(device, live, motionEnabled) }
        item { ScanControls(ui, onStart, onStop, motionEnabled, compact = true) }
        item { SignalCaution() }
    }
}

internal fun hasLiveSignal(device: DeviceState, scanning: Boolean) =
    scanning && device.smoothedRssi != null &&
        (device.deviceStatus == DeviceStatus.PRESENT || device.deviceStatus == DeviceStatus.WEAK)

/** The V1.1 display mapping is retained. Very strong uses only the confirmed business flag. */
@Composable
internal fun SignalHero(device: DeviceState, scanning: Boolean, veryStrong: Boolean, motionEnabled: Boolean) {
    val live = hasLiveSignal(device, scanning)
    val confirmed = live && veryStrong
    val strength = when {
        !live -> "等待信号"
        confirmed -> "信号非常强"
        device.signalBars >= 3 -> "信号较强"
        device.signalBars == 2 -> "信号中等"
        else -> "信号较弱"
    }
    val targetColor = when {
        !live -> MaterialTheme.colorScheme.onSurfaceVariant
        confirmed -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    val accent by finderAnimatedColor(targetColor, motionEnabled)
    // Two-percent visual steps suppress tiny display noise. Measurement and grade are untouched.
    val target = if (!live) 0f else
        ((((device.smoothedRssi!! + 100) / 65).coerceIn(0.0, 1.0) * 50).roundToInt() / 50f)
    val displayed by animateSignalProgress(target, motionEnabled)
    val halo by animateSignalProgress(if (confirmed) 0.12f else 0f, motionEnabled)
    FinderCard {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SoftStateText(statusText(device.deviceStatus), motionEnabled)
            BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val diameter = minOf(maxWidth, 248.dp)
                Box(Modifier.size(diameter).clearAndSetSemantics {
                    testTag = "signal-ring"
                    contentDescription = "相对蓝牙信号强度，" + strength
                    progressBarRangeInfo = ProgressBarRangeInfo(target, 0f..1f)
                }.drawBehind {
                    if (halo > 0f) drawCircle(accent.copy(alpha = halo))
                }, contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(progress = { displayed }, modifier = Modifier.fillMaxSize().padding(10.dp),
                        color = accent, trackColor = MaterialTheme.colorScheme.surfaceVariant, strokeWidth = 10.dp)
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(if (confirmed) FinderIcons.Check else FinderIcons.Bluetooth,
                            contentDescription = null, tint = accent, modifier = Modifier.size(28.dp))
                        Text(if (!live) "等待" else strength.removePrefix("信号"),
                            style = MaterialTheme.typography.headlineSmall, color = accent, fontWeight = FontWeight.SemiBold)
                        Text("相对信号", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            val messageHeight = with(LocalDensity.current) {
                (MaterialTheme.typography.titleMedium.lineHeight * if (fontScale > 1.35f) 3 else 2).toDp()
            }
            Box(Modifier.fillMaxWidth().heightIn(min = messageHeight), contentAlignment = Alignment.Center) {
                AnimatedContent(if (confirmed) "信号非常强 · 仔细找找附近" else strength,
                    transitionSpec = {
                        ((if (motionEnabled) fadeIn(tween(FinderStyle.stateMotionMs)) else EnterTransition.None) togetherWith
                            (if (motionEnabled) fadeOut(tween(120)) else ExitTransition.None)).using(
                                if (motionEnabled) SizeTransform(clip = false,
                                    sizeAnimationSpec = { _, _ -> tween(FinderStyle.stateMotionMs) }) else null)
                    }, label = "confirmed-message") { text ->
                    Text(text, style = MaterialTheme.typography.titleMedium, color = accent,
                        modifier = Modifier.testTag("signal-feedback"))
                }
            }
            TrendFeedback(if (live) device.trend else null, motionEnabled)
        }
    }
}

@Composable
private fun TrendFeedback(trend: Trend?, motionEnabled: Boolean) {
    val icon = when (trend) {
        Trend.APPROACHING -> FinderIcons.TrendUp
        Trend.AWAY -> FinderIcons.TrendDown
        Trend.STABLE -> FinderIcons.Stable
        null -> FinderIcons.Search
    }
    val text = when (trend) {
        Trend.APPROACHING -> "正在靠近 · 信号变强"
        Trend.AWAY -> "正在远离 · 信号变弱"
        Trend.STABLE -> "基本不变"
        null -> "等待新的有效广播"
    }
    val textHeight = with(LocalDensity.current) {
        (MaterialTheme.typography.labelLarge.lineHeight * 2).toDp()
    }
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.primary)
            Box(Modifier.weight(1f).heightIn(min = textHeight), contentAlignment = Alignment.CenterStart) {
                SoftStateText(text, motionEnabled, modifier = Modifier.testTag("trend-feedback"))
            }
        }
    }
}

@Composable
private fun DeviceOverview(device: DeviceState, live: Boolean, motionEnabled: Boolean) {
    FinderCard {
        Text("设备概况", style = MaterialTheme.typography.titleMedium)
        val distance = device.estimatedDistance.takeIf { live }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("粗略估算", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(distance?.let { approximateDistance(it) } ?: "—", style = MaterialTheme.typography.headlineSmall)
                if (distance == null) Text("暂无实时距离", style = MaterialTheme.typography.bodySmall)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("当前状态", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SoftStateText(statusText(device.deviceStatus), motionEnabled)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(FinderIcons.Clock, contentDescription = null, modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("最后收到：" + lastSeenText(device), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SignalDetails(device: DeviceState, live: Boolean, motionEnabled: Boolean) {
    var expanded by rememberSaveable(device.deviceId) { mutableStateOf(false) }
    FinderCard {
        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth().testTag("signal-details-toggle"),
            contentPadding = PaddingValues(0.dp)) {
            Text(if (expanded) "收起信号详情" else "查看信号详情", modifier = Modifier.weight(1f))
            Icon(FinderIcons.Expand, contentDescription = null, modifier = Modifier.size(20.dp))
        }
        AnimatedVisibility(expanded,
            enter = if (motionEnabled) expandVertically(tween(220)) + fadeIn(tween(220)) else EnterTransition.None,
            exit = if (motionEnabled) shrinkVertically(tween(180)) + fadeOut(tween(150)) else ExitTransition.None) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                DetailRow("末次 RSSI", device.rssi?.let { "$it dBm" } ?: "—", "raw-rssi-value")
                DetailRow("平滑 RSSI", if (live) device.smoothedRssi?.let { "%.1f dBm".format(it) } ?: "—" else "—（非实时）",
                    "smoothed-rssi-value")
                DetailRow("1 米参考 RSSI", "%.1f dBm".format(device.calibrationParameters.rssiAt1m
                    ?: device.advertisedReference.toDouble()))
                DetailRow("环境衰减系数 n", device.calibrationParameters.pathLoss.toString())
                DetailRow("电量", device.batteryLevel?.let { "$it%" } ?: "未知")
                DetailRow("广播协议", "v%d".format(device.protocolVersion))
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, tag: String = "") {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = if (tag.isEmpty()) Modifier else Modifier.testTag(tag))
    }
}

@Composable
internal fun SignalCaution() {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(FinderIcons.Info, contentDescription = null, modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("距离仅供参考。人体、家具和墙壁会影响信号；观察数秒趋势，无法据此判断方向或房间。扫描不到也不能断言物品在室外。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

internal fun statusText(status: DeviceStatus) = when (status) {
    DeviceStatus.NEVER_SEEN -> "尚未接收"
    DeviceStatus.PRESENT -> "正常发现"
    DeviceStatus.WEAK -> "信号弱 / 可能失联"
    DeviceStatus.LOST -> "已失联"
    DeviceStatus.SCAN_STOPPED -> "扫描已停止"
}

private fun lastSeenText(device: DeviceState) = device.lastSeenWallMs?.let {
    SimpleDateFormat("HH:mm:ss", Locale.SIMPLIFIED_CHINESE).format(Date(it))
} ?: "从未接收"

private fun approximateDistance(distance: Double) = when {
    distance >= 100 -> ">100 米"
    distance < 0.1 -> "<0.1 米"
    else -> "约 %.1f 米".format(distance)
}
