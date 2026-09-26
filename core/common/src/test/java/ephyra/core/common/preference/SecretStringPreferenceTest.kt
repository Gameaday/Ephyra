package ephyra.core.common.preference

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SecretStringPreferenceTest {

    /**
     * A reversible stand-in for the platform cipher.
     *
     * The point of extracting [SecretCipher] is that the policy around a credential — what gets
     * written, what happens to a value that cannot be read — is testable without a device. This
     * stands in for the Keystore; it is deliberately not encryption.
     */
    private class FakeCipher(
        private val prefix: String = "enc:",
        private val failOnDecrypt: Boolean = false,
    ) : SecretCipher {
        var encryptCalls = 0
        override fun encrypt(plaintext: String): String {
            encryptCalls++
            return prefix + plaintext.reversed()
        }

        override fun decrypt(stored: String): String {
            if (failOnDecrypt) throw SecretCipherException("key invalidated")
            // A value with no envelope is what a pre-encryption build left behind, or corruption.
            // The real cipher reports this as a SecretCipherException because that is what the
            // contract says it throws, and callers are entitled to rely on that.
            if (!stored.startsWith(prefix)) throw SecretCipherException("not produced by this cipher: $stored")
            return stored.removePrefix(prefix).reversed()
        }
    }

    private fun preference(cipher: SecretCipher) =
        SecretStringPreference(InMemoryPreferenceStore().getString(Preference.privateKey("k"), ""), cipher)

    // --- the credential is not stored in the clear ---------------------------------------------

    @Test
    fun `a stored credential does not contain the plaintext`() {
        val store = InMemoryPreferenceStore()
        val cipher = FakeCipher()
        val secret = SecretStringPreference(store.getString(Preference.privateKey("k"), ""), cipher)

        secret.set("super-secret-token")

        val raw = store.getString(Preference.privateKey("k"), "").getSync()
        assertFalse(
            raw.contains("super-secret-token"),
            "The preference store held the plaintext: $raw",
        )
        assertEquals("super-secret-token", secret.getSync(), "but it must still read back")
    }

    @Test
    fun `a token and a password are both protected by the same wrapper`() {
        val store = InMemoryPreferenceStore()
        val cipher = FakeCipher()
        val token = SecretStringPreference(store.getString(Preference.privateKey("token"), ""), cipher)
        val password = SecretStringPreference(store.getString(Preference.privateKey("password"), ""), cipher)

        token.set("bearer-abc")
        password.set("hunter2")

        val raw = store.getString(Preference.privateKey("token"), "").getSync() +
            store.getString(Preference.privateKey("password"), "").getSync()
        assertFalse(raw.contains("bearer-abc"), "token leaked: $raw")
        assertFalse(raw.contains("hunter2"), "password leaked: $raw")
    }

    // --- empty is absence, not an encrypted empty string ---------------------------------------

    @Test
    fun `setting an empty value deletes the entry rather than encrypting it`() {
        val store = InMemoryPreferenceStore()
        val cipher = FakeCipher()
        val secret = SecretStringPreference(store.getString(Preference.privateKey("k"), ""), cipher)

        secret.set("token")
        assertEquals(1, cipher.encryptCalls)

        secret.set("")
        assertEquals(1, cipher.encryptCalls, "an empty value must not be encrypted")
        assertFalse(secret.isSet(), "and the entry must be gone, so absence has one representation")
        assertEquals("", secret.getSync())
    }

    // --- an unreadable credential is dropped, not thrown ---------------------------------------

    @Test
    fun `an unreadable credential is dropped instead of throwing`() {
        val store = InMemoryPreferenceStore()
        val good = SecretStringPreference(store.getString(Preference.privateKey("k"), ""), FakeCipher())
        good.set("token")

        // The key is now invalidated — a device-lock change, or a backup restored onto a device
        // that never held the key.
        val broken =
            SecretStringPreference(store.getString(Preference.privateKey("k"), ""), FakeCipher(failOnDecrypt = true))

        assertEquals("", broken.getSync(), "must fall back to empty, not propagate the failure")
        assertFalse(broken.isSet(), "and must clear the unreadable blob, or every read retries it")
    }

    @Test
    fun `a plaintext value left by an older build is discarded rather than returned`() {
        // Before encryption existed the store held the token verbatim. Reading it must not silently
        // hand back a value that was never protected, and must not crash on the missing envelope.
        val store = InMemoryPreferenceStore()
        store.getString(Preference.privateKey("k"), "").set("legacy-plaintext")

        val secret = SecretStringPreference(store.getString(Preference.privateKey("k"), ""), FakeCipher())

        assertEquals("", secret.getSync(), "an un-enveloped value is not a credential we can trust")
    }

    // --- ordinary behaviour is preserved ------------------------------------------------------

    @Test
    fun `delete removes the entry`() {
        val secret = preference(FakeCipher())
        secret.set("token")
        secret.delete()
        assertFalse(secret.isSet())
        assertEquals("", secret.getSync())
    }

    @Test
    fun `key and default are delegated`() {
        val secret = preference(FakeCipher())
        assertEquals(Preference.privateKey("k"), secret.key())
        assertEquals("", secret.defaultValue())
    }

    @Test
    fun `an unset preference reads as the default`() {
        assertEquals("", preference(FakeCipher()).getSync())
        assertFalse(preference(FakeCipher()).isSet())
    }
}
