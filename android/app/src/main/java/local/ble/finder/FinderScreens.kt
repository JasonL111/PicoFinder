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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant, minLines: Int = 1) {
    AnimatedContent(text, modifier = modifier, transitionSpec = {
        ((if (motionEnabled) fadeIn(tween(FinderStyle.stateMotionMs)) else EnterTransition.None) togetherWith
            (if (motionEnabled) fadeOut(tween(120)) else ExitTransition.None)).using(
                if (motionEnabled) SizeTransform(clip = false, sizeAnimationSpec = { _, _ -> tween(FinderStyle.stateMotionMs) }) else null)
    }, label = "state-label") { value ->
        Text(value, style = MaterialTheme.typography.labelLarge, color = color, minLines = minLines)
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
                SoftStateText(if (ui.scanning) stringResource(R.string.scan_active) else stringResource(R.string.scan_stopped), motionEnabled,
                    modifier = Modifier.testTag("scan-status"), color = color)
                Text(if (ui.scanning) stringResource(R.string.scan_foreground_limit) else stringResource(R.string.scan_retained),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (!compact || !ui.scanning) Text(ui.message.asString(), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (ui.calibrationDeviceId != null) Text(stringResource(R.string.calibration_remaining, ui.calibrationSeconds),
            color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
        if (compact) {
            if (ui.scanning) OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.stop_scan)) }
            else Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.start_scan)) }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onStart, enabled = !ui.scanning, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.start_scan)) }
                OutlinedButton(onClick = onStop, enabled = ui.scanning, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.stop_scan)) }
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
            Text(stringResource(R.string.list_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.list_description),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp))
        }
        item { ScanControls(ui, onStart, onStop, motionEnabled) }
        if (DemoSupport.available) item {
            val demoLabel = stringResource(R.string.demo_toggle)
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.demo_toggle), style = MaterialTheme.typography.labelLarge)
                    Text(stringResource(R.string.demo_description), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(ui.demo, onDemo, modifier = Modifier.testTag("demo-mode")
                    .semantics {
                        contentDescription = demoLabel
                    })
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.list_tags), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(pluralStringResource(R.plurals.device_count, ui.devices.size, ui.devices.size), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (ui.devices.isEmpty()) item {
            FinderCard {
                Icon(FinderIcons.Tag, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp))
                Text(stringResource(R.string.no_tags_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.no_tags_description),
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
            Text(stringResource(R.string.app_build, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
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
                Text(device.localizedName(), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                // Text owns line measurement, including nonlinear system font scaling.
                SoftStateText(statusText(device.deviceStatus), motionEnabled,
                    minLines = if (LocalDensity.current.fontScale > 1.35f) 3 else 2)
                Text(device.rssi?.let { stringResource(R.string.last_rssi, it) } ?: stringResource(R.string.waiting_first_packet),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(FinderIcons.Next, contentDescription = stringResource(R.string.find_this_tag), tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
internal fun PermissionHelp(onBluetooth: () -> Unit, onLocation: () -> Unit, onPermissions: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.permission_explanation),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // Wrapping is intentional on small screens and with large system text.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onBluetooth) { Text(stringResource(R.string.enable_bluetooth)) }
            TextButton(onClick = onLocation) { Text(stringResource(R.string.location_settings)) }
            TextButton(onClick = onPermissions) { Text(stringResource(R.string.permission_settings)) }
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
            Text(device.localizedName(), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.device_id, device.idHex()), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        }
        item { SignalHero(device, ui.scanning, ui.veryStrongSignal, motionEnabled) }
        item { DeviceOverview(device, live, motionEnabled) }
        item {
            OutlinedButton(onClick = onSettings, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text(stringResource(R.string.open_device_settings))
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
    val labels = when {
        !live -> R.string.strength_waiting to R.string.strength_short_waiting
        confirmed -> R.string.strength_very_strong to R.string.strength_short_very_strong
        device.signalBars >= 3 -> R.string.strength_strong to R.string.strength_short_strong
        device.signalBars == 2 -> R.string.strength_medium to R.string.strength_short_medium
        else -> R.string.strength_weak to R.string.strength_short_weak
    }
    val strength = stringResource(labels.first)
    val shortStrength = stringResource(labels.second)
    val signalDescription = stringResource(R.string.relative_signal_description, strength)
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
                    contentDescription = signalDescription
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
                        Text(shortStrength,
                            style = MaterialTheme.typography.headlineSmall, color = accent, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.relative_signal), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            val messageHeight = with(LocalDensity.current) {
                MaterialTheme.typography.titleMedium.lineHeight.toDp() *
                    if (fontScale > 1.35f) 3 else if (fontScale > 1.1f) 2 else 1
            }
            Box(Modifier.fillMaxWidth().heightIn(min = messageHeight), contentAlignment = Alignment.Center) {
                AnimatedContent(if (confirmed) stringResource(R.string.strong_signal_guidance) else strength,
                    transitionSpec = {
                        ((if (motionEnabled) fadeIn(tween(FinderStyle.stateMotionMs)) else EnterTransition.None) togetherWith
                            (if (motionEnabled) fadeOut(tween(120)) else ExitTransition.None)).using(
                                if (motionEnabled) SizeTransform(clip = false,
                                    sizeAnimationSpec = { _, _ -> tween(FinderStyle.stateMotionMs) }) else null)
                    }, label = "confirmed-message") { text ->
                    Text(text, style = MaterialTheme.typography.titleMedium, color = accent, textAlign = TextAlign.Center,
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
        Trend.APPROACHING -> stringResource(R.string.trend_approaching)
        Trend.AWAY -> stringResource(R.string.trend_away)
        Trend.STABLE -> stringResource(R.string.trend_stable)
        null -> stringResource(R.string.trend_waiting)
    }
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.primary)
            SoftStateText(text, motionEnabled, modifier = Modifier.weight(1f).testTag("trend-feedback"),
                minLines = if (LocalDensity.current.fontScale > 1.1f) 2 else 1)
        }
    }
}

@Composable
private fun DeviceOverview(device: DeviceState, live: Boolean, motionEnabled: Boolean) {
    FinderCard {
        Text(stringResource(R.string.device_overview), style = MaterialTheme.typography.titleMedium)
        val distance = device.estimatedDistance.takeIf { live }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.distance_label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(distance?.let { approximateDistance(it) } ?: stringResource(R.string.value_unavailable), style = MaterialTheme.typography.headlineSmall)
                if (distance == null) Text(stringResource(R.string.distance_unavailable), style = MaterialTheme.typography.bodySmall)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.status_label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SoftStateText(statusText(device.deviceStatus), motionEnabled)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(FinderIcons.Clock, contentDescription = null, modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.last_received, lastSeenText(device)), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SignalDetails(device: DeviceState, live: Boolean, motionEnabled: Boolean) {
    var expanded by rememberSaveable(device.deviceId) { mutableStateOf(false) }
    FinderCard {
        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth().testTag("signal-details-toggle"),
            contentPadding = PaddingValues(0.dp)) {
            Text(if (expanded) stringResource(R.string.collapse_details) else stringResource(R.string.expand_details), modifier = Modifier.weight(1f))
            Icon(FinderIcons.Expand, contentDescription = null, modifier = Modifier.size(20.dp))
        }
        AnimatedVisibility(expanded,
            enter = if (motionEnabled) expandVertically(tween(220)) + fadeIn(tween(220)) else EnterTransition.None,
            exit = if (motionEnabled) shrinkVertically(tween(180)) + fadeOut(tween(150)) else ExitTransition.None) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                DetailRow(stringResource(R.string.raw_rssi), device.rssi?.let { stringResource(R.string.rssi_value, it) } ?: stringResource(R.string.value_unavailable), "raw-rssi-value")
                DetailRow(stringResource(R.string.smoothed_rssi), if (live) device.smoothedRssi?.let { stringResource(R.string.smoothed_rssi_value, it) } ?: stringResource(R.string.value_unavailable) else stringResource(R.string.not_live),
                    "smoothed-rssi-value")
                DetailRow(stringResource(R.string.reference_rssi), stringResource(R.string.smoothed_rssi_value, device.calibrationParameters.rssiAt1m
                    ?: device.advertisedReference.toDouble()))
                DetailRow(stringResource(R.string.path_loss), device.calibrationParameters.pathLoss.toString())
                DetailRow(stringResource(R.string.battery), device.batteryLevel?.let { stringResource(R.string.battery_value, it) } ?: stringResource(R.string.unknown))
                DetailRow(stringResource(R.string.protocol), stringResource(R.string.protocol_value, device.protocolVersion))
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
        Text(stringResource(R.string.signal_caution),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun statusText(status: DeviceStatus) = when (status) {
    DeviceStatus.NEVER_SEEN -> stringResource(R.string.status_never_seen)
    DeviceStatus.PRESENT -> stringResource(R.string.status_present)
    DeviceStatus.WEAK -> stringResource(R.string.status_weak)
    DeviceStatus.LOST -> stringResource(R.string.status_lost)
    DeviceStatus.SCAN_STOPPED -> stringResource(R.string.scan_stopped)
}

@Composable
private fun lastSeenText(device: DeviceState) = device.lastSeenWallMs?.let {
    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(it))
} ?: stringResource(R.string.never_received)

@Composable
private fun approximateDistance(distance: Double) = when {
    distance >= 100 -> stringResource(R.string.distance_over_max)
    distance < 0.1 -> stringResource(R.string.distance_under_min)
    else -> stringResource(R.string.distance_approx, distance)
}
