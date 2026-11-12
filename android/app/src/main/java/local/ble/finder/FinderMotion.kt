package local.ble.finder

import android.animation.ValueAnimator
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

internal val FinderMotionEnabled = SemanticsPropertyKey<Boolean>("FinderMotionEnabled")
internal var SemanticsPropertyReceiver.finderMotionEnabled by FinderMotionEnabled

private fun systemMotionEnabled(context: Context): Boolean = try {
    // Read the setting itself so a foreground change to zero never races ValueAnimator's cache.
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
} catch (_: SecurityException) {
    ValueAnimator.areAnimatorsEnabled()
}

/** Only visual motion is observed here. No scanner, measurement or haptic state is touched. */
@Composable
internal fun rememberFinderMotionEnabled(): Boolean {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var systemEnabled by remember { mutableStateOf(systemMotionEnabled(context)) }
    var foreground by remember(lifecycle) {
        mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    DisposableEffect(context, lifecycle) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                systemEnabled = systemMotionEnabled(context)
            }
        }
        val events = LifecycleEventObserver { _, event ->
            foreground = event.targetState.isAtLeast(Lifecycle.State.STARTED)
            if (event == Lifecycle.Event.ON_START) systemEnabled = systemMotionEnabled(context)
        }
        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        lifecycle.addObserver(events)
        onDispose {
            context.contentResolver.unregisterContentObserver(observer)
            lifecycle.removeObserver(events)
        }
    }
    return systemEnabled && foreground
}

/** Display-only interpolation; callers keep the original RSSI and confirmed state intact. */
@Composable
internal fun animateSignalProgress(target: Float, motionEnabled: Boolean): State<Float> =
    if (motionEnabled) animateFloatAsState(
        targetValue = target, animationSpec = tween(FinderStyle.signalMotionMs, easing = FastOutSlowInEasing),
        label = "relative-signal")
    else rememberUpdatedState(target)

@Composable
internal fun finderAnimatedColor(target: Color, motionEnabled: Boolean): State<Color> =
    if (motionEnabled) animateColorAsState(target, tween(FinderStyle.signalMotionMs), label = "signal-color")
    else rememberUpdatedState(target)
