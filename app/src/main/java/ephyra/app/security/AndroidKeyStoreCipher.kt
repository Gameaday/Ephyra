package ephyra.app.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import ephyra.core.common.preference.SecretCipher
import ephyra.core.common.preference.SecretCipherException
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * [SecretCipher] backed by the platform Android Keystore.
 *
 * Uses `android.security.keystore` directly rather than `androidx.security-crypto`, so the app
 * gains encrypted credential storage without a new dependency edge. The key is a non-exportable
 * AES-256 key that never leaves the Keystore; only the ciphertext is written to the preference
 * store.
 *
 * **A fresh IV per encryption.** GCM reuses of an IV under one key are catastrophic — it leaks
 * plaintext XOR and can recover the authentication subkey — so the IV is generated per call and
 * stored alongside the ciphertext rather than being fixed.
 *
 * **No user authentication is required.** Tracker tokens are refreshed in the background by
 * scheduled work; gating the key on device unlock would break that and push users toward a weaker
 * unlock configuration. The protection here is that the key is non-exportable and hardware-backed
 * where the device provides a TEE, not that every read demands a biometric.
 *
 * Every failure is reported as [SecretCipherException] because that is what the contract promises
 * and callers rely on: a key that was invalidated, a backup restored onto a device that never held
 * the key, or corrupted data all mean the same thing to a caller — the credential is gone.
 */
class AndroidKeyStoreCipher(
    private val keyAlias: String = DEFAULT_KEY_ALIAS,
) : SecretCipher {

    private val keyStore: KeyStore
        get() = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    override fun encrypt(plaintext: String): String = try {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        // IV first, then ciphertext, both base64. Order is fixed so decode is unambiguous.
        Base64.getEncoder().encodeToString(cipher.iv) + SEPARATOR + Base64.getEncoder().encodeToString(ciphertext)
    } catch (e: Exception) {
        throw SecretCipherException("Could not encrypt a credential", e)
    }

    override fun decrypt(stored: String): String {
        val parts = stored.split(SEPARATOR)
        if (parts.size != 2) throw SecretCipherException("Malformed credential envelope")
        return try {
            val iv = Base64.getDecoder().decode(parts[0])
            val ciphertext = Base64.getDecoder().decode(parts[1])
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (e: Exception) {
            // KeyPermanentlyInvalidatedException, UnrecoverableKeyException and AEADBadTagException
            // all land here. None is recoverable by retrying, and none should surface as a crash.
            throw SecretCipherException("Could not decrypt a credential", e)
        }
    }

    /** Returns the existing key, generating one on first use. */
    private fun secretKey(): SecretKey {
        val store = keyStore
        (store.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                // Deliberately not requiring user authentication; see the class comment.
                .setUserAuthenticationRequired(false)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_SIZE_BITS = 256
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val SEPARATOR = ":"
        const val DEFAULT_KEY_ALIAS = "ephyra_credential_key_v1"
    }
}
