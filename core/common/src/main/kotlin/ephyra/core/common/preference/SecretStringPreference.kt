package ephyra.core.common.preference

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Encrypts and decrypts a secret held as a string.
 *
 * Deliberately narrow so the policy around a credential can be tested without a device: the
 * Android implementation is backed by the platform `android.security.keystore` and needs no
 * third-party dependency, but it cannot run on the JVM, and a rule that can only be exercised on
 * hardware is a rule that goes unexercised.
 */
interface SecretCipher {
    /**
     * @return an opaque, storable form of [plaintext]. Must not contain the plaintext.
     * @throws SecretCipherException if the value cannot be protected.
     */
    fun encrypt(plaintext: String): String

    /**
     * Reverses [encrypt].
     *
     * @throws SecretCipherException if [stored] cannot be read — a rotated or invalidated key, a
     *   restored backup whose key was never on this device, or corrupted data. Callers must treat
     *   this as "the credential is gone", not as a fatal error.
     */
    fun decrypt(stored: String): String
}

class SecretCipherException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * A [Preference] whose value is encrypted at rest.
 *
 * This exists because tracker OAuth tokens and account passwords were written to the preference
 * store as plaintext strings. The `privateKey` prefix keeps them out of backups, which is a
 * different property entirely: it hides them from export, but the bytes on disk were still
 * readable to anything with access to app storage.
 *
 * Wrapping the existing [Preference] rather than replacing the store means every existing call
 * site — `getSync()`, `set()`, `delete()`, `changes()` — keeps working, and one wrapper fixes
 * every credential at the single point where they are declared.
 *
 * An empty value is treated as absent and deletes the entry rather than storing an encrypted
 * empty string, so "no credential" has one representation instead of two.
 *
 * **A credential that cannot be decrypted is dropped, not thrown.** Keys are invalidated by
 * device-lock changes and are absent on any device a backup was restored to, so a thrown
 * exception here would make the app unlaunchable after a restore. The user is asked to sign in
 * again, which is recoverable; a crash loop is not.
 */
class SecretStringPreference(
    private val delegate: Preference<String>,
    private val cipher: SecretCipher,
    private val defaultValue: String = "",
) : Preference<String> {

    override fun key(): String = delegate.key()

    override fun defaultValue(): String = defaultValue

    override fun getSync(): String = read(delegate.getSync())

    override suspend fun get(): String = read(delegate.get())

    override fun set(value: String) {
        if (value.isEmpty()) {
            delegate.delete()
            return
        }
        delegate.set(cipher.encrypt(value))
    }

    override fun isSet(): Boolean = delegate.isSet()

    override fun delete(): Unit = delegate.delete()

    override fun changes(): Flow<String> = delegate.changes().map { read(it) }

    override fun stateIn(scope: CoroutineScope): StateFlow<String> =
        delegate.changes()
            .map { read(it) }
            .stateIn(scope, SharingStarted.Eagerly, getSync())

    /**
     * Decrypts a stored value, clearing it if it cannot be read.
     *
     * Clearing is deliberate: leaving an undecryptable blob in place means every subsequent read
     * pays the same failed decryption, and the value is worthless either way.
     */
    private fun read(stored: String): String {
        if (stored.isEmpty()) return defaultValue
        return try {
            cipher.decrypt(stored)
        } catch (_: SecretCipherException) {
            runCatching { delegate.delete() }
            defaultValue
        }
    }
}
