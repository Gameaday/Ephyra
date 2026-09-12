package ephyra.feature.browse.extension.details

import android.content.SharedPreferences
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import androidx.preference.TwoStatePreference
import ephyra.presentation.core.preference.SharedPreferencesDataStore
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.sourcePreferences

@Composable
fun SourcePreferenceContent(
    source: ConfigurableSource,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val sharedPreferences = remember(source) { source.sourcePreferences() }

    val preferenceScreen: PreferenceScreen = remember(source, context) {
        val pm = PreferenceManager(context)
        val dataStore = SharedPreferencesDataStore(sharedPreferences)
        pm.preferenceDataStore = dataStore
        val screen = pm.createPreferenceScreen(context)
        source.setupPreferenceScreen(screen)
        screen
    }

    var updateTick by remember { mutableIntStateOf(0) }
    DisposableEffect(sharedPreferences) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            updateTick++
        }
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    val flattenedPreferences = remember(preferenceScreen, updateTick) {
        preferenceScreen.flattenPreferences()
    }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(flattenedPreferences, key = { it.key ?: "${it.title}_${it.order}" }) { pref ->
            if (pref.isVisible) {
                PreferenceItemRow(
                    pref = pref,
                    onValueChanged = { updateTick++ },
                )
            }
        }
    }
}

private fun PreferenceGroup.flattenPreferences(): List<Preference> {
    val list = mutableListOf<Preference>()
    for (i in 0 until preferenceCount) {
        val pref = getPreference(i)
        list.add(pref)
        if (pref is PreferenceGroup && pref !is PreferenceScreen) {
            list.addAll(pref.flattenPreferences())
        }
    }
    return list
}

@Composable
private fun PreferenceItemRow(
    pref: Preference,
    onValueChanged: () -> Unit,
) {
    when (pref) {
        is PreferenceCategory -> {
            PreferenceCategoryHeader(title = pref.title?.toString().orEmpty())
        }

        is TwoStatePreference -> {
            TwoStatePreferenceRow(
                pref = pref,
                onValueChanged = onValueChanged,
            )
        }

        is ListPreference -> {
            ListPreferenceRow(
                pref = pref,
                onValueChanged = onValueChanged,
            )
        }

        is MultiSelectListPreference -> {
            MultiSelectListPreferenceRow(
                pref = pref,
                onValueChanged = onValueChanged,
            )
        }

        is EditTextPreference -> {
            EditTextPreferenceRow(
                pref = pref,
                onValueChanged = onValueChanged,
            )
        }

        is PreferenceGroup -> {
            if (!pref.title.isNullOrEmpty()) {
                PreferenceCategoryHeader(title = pref.title.toString())
            }
        }

        else -> {
            BasePreferenceRow(
                pref = pref,
                onValueChanged = onValueChanged,
            )
        }
    }
}

@Composable
private fun PreferenceCategoryHeader(title: String) {
    if (title.isBlank()) return
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 6.dp),
    )
}

@Composable
private fun TwoStatePreferenceRow(
    pref: TwoStatePreference,
    onValueChanged: () -> Unit,
) {
    val title = pref.title?.toString().orEmpty()
    val summary = pref.summary?.toString()
    val checked = pref.isChecked

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = pref.isEnabled) {
                val newChecked = !checked
                if (pref.callChangeListener(newChecked)) {
                    pref.isChecked = newChecked
                    onValueChanged()
                }
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val titleColor = if (pref.isEnabled) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        }
        val summaryColor = if (pref.isEnabled) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor,
            )
            if (!summary.isNullOrEmpty()) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = summaryColor,
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Switch(
            checked = checked,
            enabled = pref.isEnabled,
            onCheckedChange = { newChecked ->
                if (pref.callChangeListener(newChecked)) {
                    pref.isChecked = newChecked
                    onValueChanged()
                }
            },
        )
    }
}

@Composable
private fun ListPreferenceRow(
    pref: ListPreference,
    onValueChanged: () -> Unit,
) {
    val title = pref.title?.toString().orEmpty()
    val entry = pref.entry?.toString()
    val summary = if (!entry.isNullOrEmpty()) entry else pref.summary?.toString()
    var isDialogShown by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = pref.isEnabled) { isDialogShown = true }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val titleColor = if (pref.isEnabled) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        }
        val summaryColor = if (pref.isEnabled) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor,
            )
            if (!summary.isNullOrEmpty()) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = summaryColor,
                )
            }
        }
    }

    if (isDialogShown) {
        val entries = pref.entries ?: emptyArray()
        val entryValues = pref.entryValues ?: emptyArray()
        val currentValue = pref.value

        AlertDialog(
            onDismissRequest = { isDialogShown = false },
            title = { Text(text = title) },
            text = {
                LazyColumn {
                    items(entries.indices.toList()) { index ->
                        val itemValue = entryValues.getOrNull(index)?.toString()
                        val itemLabel = entries[index].toString()
                        val isSelected = itemValue == currentValue

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = isSelected,
                                    onClick = {
                                        if (itemValue != null && pref.callChangeListener(itemValue)) {
                                            pref.value = itemValue
                                            onValueChanged()
                                        }
                                        isDialogShown = false
                                    },
                                )
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = null,
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = itemLabel, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { isDialogShown = false }) {
                    Text(text = stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun MultiSelectListPreferenceRow(
    pref: MultiSelectListPreference,
    onValueChanged: () -> Unit,
) {
    val title = pref.title?.toString().orEmpty()
    val summary = pref.summary?.toString()
    var isDialogShown by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = pref.isEnabled) { isDialogShown = true }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val titleColor = if (pref.isEnabled) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        }
        val summaryColor = if (pref.isEnabled) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor,
            )
            if (!summary.isNullOrEmpty()) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = summaryColor,
                )
            }
        }
    }

    if (isDialogShown) {
        val entries = pref.entries ?: emptyArray()
        val entryValues = pref.entryValues ?: emptyArray()
        var selectedValues by remember { mutableStateOf((pref.values ?: emptySet()).toSet()) }

        AlertDialog(
            onDismissRequest = { isDialogShown = false },
            title = { Text(text = title) },
            text = {
                LazyColumn {
                    items(entries.indices.toList()) { index ->
                        val itemValue = entryValues.getOrNull(index)?.toString() ?: ""
                        val itemLabel = entries[index].toString()
                        val isChecked = selectedValues.contains(itemValue)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedValues = if (isChecked) {
                                        selectedValues - itemValue
                                    } else {
                                        selectedValues + itemValue
                                    }
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    selectedValues = if (checked) {
                                        selectedValues + itemValue
                                    } else {
                                        selectedValues - itemValue
                                    }
                                },
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = itemLabel, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (pref.callChangeListener(selectedValues)) {
                            pref.values = selectedValues
                            onValueChanged()
                        }
                        isDialogShown = false
                    },
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { isDialogShown = false }) {
                    Text(text = stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun EditTextPreferenceRow(
    pref: EditTextPreference,
    onValueChanged: () -> Unit,
) {
    val title = pref.title?.toString().orEmpty()
    val textValue = pref.text.orEmpty()
    val summary = pref.summary?.toString()?.let {
        if (it.contains("%s")) it.format(textValue) else it
    } ?: textValue
    var isDialogShown by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = pref.isEnabled) { isDialogShown = true }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val titleColor = if (pref.isEnabled) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        }
        val summaryColor = if (pref.isEnabled) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor,
            )
            if (summary.isNotEmpty()) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = summaryColor,
                )
            }
        }
    }

    if (isDialogShown) {
        var currentText by remember { mutableStateOf(textValue) }

        AlertDialog(
            onDismissRequest = { isDialogShown = false },
            title = { Text(text = title) },
            text = {
                OutlinedTextField(
                    value = currentText,
                    onValueChange = { currentText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (pref.callChangeListener(currentText)) {
                            pref.text = currentText
                            onValueChanged()
                        }
                        isDialogShown = false
                    },
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { isDialogShown = false }) {
                    Text(text = stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun BasePreferenceRow(
    pref: Preference,
    onValueChanged: () -> Unit,
) {
    val title = pref.title?.toString().orEmpty()
    val summary = pref.summary?.toString()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = pref.isEnabled) {
                pref.onPreferenceClickListener?.onPreferenceClick(pref)
                onValueChanged()
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val titleColor = if (pref.isEnabled) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        }
        val summaryColor = if (pref.isEnabled) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor,
            )
            if (!summary.isNullOrEmpty()) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = summaryColor,
                )
            }
        }
    }
}
