package ephyra.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ephyra.feature.settings.widget.EditTextPreferenceWidget
import ephyra.feature.settings.widget.InfoWidget
import ephyra.feature.settings.widget.ListPreferenceWidget
import ephyra.feature.settings.widget.MultiSelectListPreferenceWidget
import ephyra.feature.settings.widget.PrefsHorizontalPadding
import ephyra.feature.settings.widget.PrefsVerticalPadding
import ephyra.feature.settings.widget.SwitchPreferenceWidget
import ephyra.feature.settings.widget.TextPreferenceWidget
import ephyra.feature.settings.widget.TitleFontSize
import ephyra.feature.settings.widget.TrackingPreferenceWidget
import ephyra.presentation.core.components.BaseSliderItem
import ephyra.presentation.core.theme.MotionTokens
import ephyra.presentation.core.util.collectAsState
import kotlinx.coroutines.launch

val LocalPreferenceHighlighted = compositionLocalOf(structuralEqualityPolicy()) { false }
val LocalPreferenceMinHeight = compositionLocalOf(structuralEqualityPolicy()) { 56.dp }

@Composable
fun StatusWrapper(
    item: Preference.PreferenceItem<*, *>,
    highlightKey: String?,
    content: @Composable () -> Unit,
) {
    val enabled = item.enabled
    val highlighted = item.title == highlightKey
    AnimatedVisibility(
        visible = enabled,
        enter = expandVertically(animationSpec = MotionTokens.tweenEnter()) + fadeIn(
            animationSpec = MotionTokens.tweenEnter(),
        ),
        exit = shrinkVertically(animationSpec = MotionTokens.tweenExit()) + fadeOut(
            animationSpec = MotionTokens.tweenExit(),
        ),
        content = {
            CompositionLocalProvider(
                LocalPreferenceHighlighted provides highlighted,
                content = content,
            )
        },
    )
}

@Composable
internal fun PreferenceItem(
    item: Preference.PreferenceItem<*, *>,
    highlightKey: String?,
) {
    val scope = rememberCoroutineScope()
    StatusWrapper(
        item = item,
        highlightKey = highlightKey,
    ) {
        when (item) {
            is Preference.PreferenceItem.SwitchPreference -> {
                val value by item.preference.collectAsState()
                SwitchPreferenceWidget(
                    title = item.title,
                    subtitle = item.subtitle,
                    icon = item.icon,
                    checked = value,
                    onCheckedChanged = { newValue ->
                        scope.launch {
                            if (item.onValueChanged(newValue)) {
                                item.preference.set(newValue)
                            }
                        }
                    },
                )
            }

            is Preference.PreferenceItem.SliderPreference -> {
                // The slider tracks its value locally while the user is dragging and only commits
                // on release. Persisting on every tick wrote to DataStore dozens of times per drag
                // and, because the thumb was drawn from the round-tripped preference, a fast drag
                // could visibly lag or drop its final value. Local state keeps the thumb tied to the
                // finger; one write happens when the gesture ends.
                var localValue by remember(item) { mutableIntStateOf(item.value) }
                var dragging by remember(item) { mutableStateOf(false) }
                LaunchedEffect(item.value) {
                    if (!dragging) localValue = item.value
                }
                BaseSliderItem(
                    value = localValue,
                    valueRange = item.valueRange,
                    steps = item.steps,
                    title = item.title,
                    subtitle = item.subtitle,
                    valueString = item.valueString.takeUnless { it.isNullOrEmpty() } ?: localValue.toString(),
                    onChange = { localValue = it },
                    onValueChangeStart = { dragging = true },
                    onValueChangeFinished = {
                        dragging = false
                        scope.launch {
                            item.onValueChanged(localValue)
                        }
                    },
                    titleStyle = MaterialTheme.typography.titleLarge.copy(fontSize = TitleFontSize),
                    modifier = Modifier.padding(
                        horizontal = PrefsHorizontalPadding,
                        vertical = PrefsVerticalPadding,
                    ),
                )
            }

            is Preference.PreferenceItem.ListPreference<*> -> {
                val value by item.preference.collectAsState()
                ListPreferenceWidget(
                    value = value,
                    title = item.title,
                    subtitle = item.internalSubtitleProvider(value, item.entries),
                    icon = item.icon,
                    entries = item.entries,
                    onValueChange = { newValue ->
                        scope.launch {
                            if (item.internalOnValueChanged(newValue!!)) {
                                item.internalSet(newValue)
                            }
                        }
                    },
                )
            }

            is Preference.PreferenceItem.BasicListPreference -> {
                ListPreferenceWidget(
                    value = item.value,
                    title = item.title,
                    subtitle = item.subtitleProvider(item.value, item.entries),
                    icon = item.icon,
                    entries = item.entries,
                    onValueChange = { scope.launch { item.onValueChanged(it) } },
                )
            }

            is Preference.PreferenceItem.MultiSelectListPreference -> {
                val values by item.preference.collectAsState()
                MultiSelectListPreferenceWidget(
                    preference = item,
                    values = values,
                    onValuesChange = { newValues ->
                        scope.launch {
                            if (item.onValueChanged(newValues)) {
                                item.preference.set(newValues.toMutableSet())
                            }
                        }
                    },
                )
            }

            is Preference.PreferenceItem.TextPreference -> {
                TextPreferenceWidget(
                    title = item.title,
                    subtitle = item.subtitle,
                    icon = item.icon,
                    onPreferenceClick = item.onClick,
                )
            }

            is Preference.PreferenceItem.EditTextPreference -> {
                val values by item.preference.collectAsState()
                EditTextPreferenceWidget(
                    title = item.title,
                    subtitle = item.subtitle,
                    icon = item.icon,
                    value = values,
                    onConfirm = {
                        val accepted = item.onValueChanged(it)
                        if (accepted) item.preference.set(it)
                        accepted
                    },
                )
            }

            is Preference.PreferenceItem.TrackerPreference -> {
                val isLoggedIn by item.tracker.isLoggedInFlow.collectAsStateWithLifecycle(initialValue = false)
                TrackingPreferenceWidget(
                    tracker = item.tracker,
                    checked = isLoggedIn,
                    onClick = { if (isLoggedIn) item.logout() else item.login() },
                )
            }

            is Preference.PreferenceItem.InfoPreference -> {
                InfoWidget(text = item.title)
            }

            is Preference.PreferenceItem.CustomPreference -> {
                item.content()
            }
        }
    }
}
