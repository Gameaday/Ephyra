package ephyra.data.backup.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/**
 * NOTE ON @SerialName: protobuf polymorphic serialization uses the fully-qualified class
 * name as the type discriminator. Backups created by Tachiyomi/Mihon (and forks) encode
 * these values under the `eu.kanade.tachiyomi.data.backup.models` package, so every
 * [PreferenceValue] subclass must pin its serial name to the original Tachiyomi FQN.
 * Without these annotations, restoring a `.tachibk` backup fails with:
 * `Serializer for subclass 'eu.kanade.tachiyomi...StringPreferenceValue' is not found
 * in the polymorphic scope of 'PreferenceValue'`.
 */
@Serializable
data class BackupPreference(
    @ProtoNumber(1) val key: String,
    @ProtoNumber(2) val value: PreferenceValue,
)

@Serializable
data class BackupSourcePreferences(
    @ProtoNumber(1) val sourceKey: String,
    @ProtoNumber(2) val prefs: List<BackupPreference>,
)

@Serializable
sealed class PreferenceValue

@Serializable
@SerialName("eu.kanade.tachiyomi.data.backup.models.IntPreferenceValue")
data class IntPreferenceValue(val value: Int) : PreferenceValue()

@Serializable
@SerialName("eu.kanade.tachiyomi.data.backup.models.LongPreferenceValue")
data class LongPreferenceValue(val value: Long) : PreferenceValue()

@Serializable
@SerialName("eu.kanade.tachiyomi.data.backup.models.FloatPreferenceValue")
data class FloatPreferenceValue(val value: Float) : PreferenceValue()

@Serializable
@SerialName("eu.kanade.tachiyomi.data.backup.models.StringPreferenceValue")
data class StringPreferenceValue(val value: String) : PreferenceValue()

@Serializable
@SerialName("eu.kanade.tachiyomi.data.backup.models.BooleanPreferenceValue")
data class BooleanPreferenceValue(val value: Boolean) : PreferenceValue()

@Serializable
@SerialName("eu.kanade.tachiyomi.data.backup.models.StringSetPreferenceValue")
data class StringSetPreferenceValue(val value: Set<String>) : PreferenceValue()
