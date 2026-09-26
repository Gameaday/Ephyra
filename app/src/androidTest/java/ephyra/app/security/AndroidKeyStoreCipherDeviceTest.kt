package ephyra.app.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ephyra.core.common.preference.DataStorePreferenceStore
import ephyra.core.common.preference.Preference
import ephyra.core.common.preference.SecretStringPreference
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves on a real device that a secret is encrypted at rest.
 *
 * The unit suite covers this policy with a fake cipher, which cannot show that the *bytes on disk*
 * are ciphertext or that the platform Keystore actually works. This asserts the property that
 * matters: the plaintext appears nowhere in the persisted file, and the value reads back correctly
 * through the same wrapper production uses.
 *
 * The file is read as raw bytes rather than parsed, so the assertion does not depend on
 * DataStore's serialisation format. Checking two encodings matters, because a UTF-16 store would
 * hide plaintext from a byte-level Latin-1 scan.
 */
@RunWith(AndroidJUnit4::class)
class AndroidKeyStoreCipherDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun dataStoreFile() =
        java.io.File(context.filesDir, "datastore/$STORE_NAME.preferences_pb")

    /** A value unique to this test, so it cannot collide with a real credential. */
    private val canary = "e4lab-keystore-canary-7f3a9c1e"

    /**
     * Uses a dedicated DataStore file rather than the app's live `ephyra_preferences` store.
     *
     * DataStore refuses two active instances on one file, and the running app already holds the
     * real one — reusing it would need a test hook in production DI purely for testing. A separate
     * file exercises the identical code path: the same `DataStorePreferenceStore`, the same
     * `SecretStringPreference`, the same platform cipher, the same on-disk format.
     */
    private fun canaryPreference(): Preference<String> = SecretStringPreference(
        DataStorePreferenceStore(context, STORE_NAME).getString(
            Preference.privateKey("e4lab_keystore_canary"),
            "",
        ),
        AndroidKeyStoreCipher(),
    )

    @Test
    fun secretIsCiphertextOnDiskAndReadsBack() {
        val preference = canaryPreference()

        preference.set(canary)

        // Round trip: the value must be recoverable, or the user is silently signed out.
        //
        // `set` is fire-and-forget (`storeScope.launch { dataStore.edit { ... } }`) while `get`
        // reads the flow, so an immediate read can observe the pre-write state. Polling for the
        // value — rather than reading once — is what makes this a round-trip assertion instead of a
        // race that passes or fails on scheduling.
        val recovered = pollUntil(expected = canary) { runBlocking { preference.get() } }
        assertEquals(
            "the secret must round trip through the platform cipher",
            canary,
            recovered,
        )

        // At rest: the plaintext must not appear in the persisted bytes in any encoding.
        val file = dataStoreFile()
        assertTrue("expected the preference file at ${file.path}", file.exists())
        val raw = file.readBytes()

        assertFalse(
            "the plaintext must not appear in the persisted bytes",
            String(raw, Charsets.ISO_8859_1).contains(canary),
        )
        assertFalse(
            "no UTF-16 encoding of the plaintext may appear either",
            String(raw, Charsets.UTF_16LE).contains(canary),
        )

        preference.delete()
    }

    /** Polls [read] until it returns [expected], or the deadline passes. Returns the last value. */
    private fun pollUntil(timeoutMs: Long = 10_000, expected: String, read: () -> String): String {
        val deadline = System.currentTimeMillis() + timeoutMs
        var last = read()
        while (last != expected && System.currentTimeMillis() < deadline) {
            Thread.sleep(100)
            last = read()
        }
        return last
    }

    private companion object {
        const val STORE_NAME = "e4lab_keystore_canary_prefs"
    }
}
