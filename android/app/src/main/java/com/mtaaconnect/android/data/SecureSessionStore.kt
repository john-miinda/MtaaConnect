package com.mtaaconnect.android.data

import android.content.Context
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonParseException
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.IllegalBlockSizeException
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface SessionStorage {
    fun save(session: Session)
    fun read(): Session?
    fun clear()
}

class SecureSessionStore(context: Context) : SessionStorage {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    @Synchronized
    override fun save(session: Session) {
        val packed = encrypt(gson.toJson(session).toByteArray(Charsets.UTF_8))
        check(preferences.edit().putString(SESSION_KEY, Base64.encodeToString(packed, Base64.NO_WRAP)).commit()) {
            "Could not persist the authenticated session"
        }
    }

    @Synchronized
    override fun read(): Session? {
        val encoded = preferences.getString(SESSION_KEY, null) ?: return null
        val packed = try {
            Base64.decode(encoded, Base64.NO_WRAP)
        } catch (_: IllegalArgumentException) {
            clear()
            return null
        }
        if (packed.size < GCM_IV_BYTES + GCM_TAG_BYTES) {
            clear()
            return null
        }

        val session: Session? = try {
            val buffer = ByteBuffer.wrap(packed)
            val iv = ByteArray(GCM_IV_BYTES)
            buffer.get(iv)
            val ciphertext = ByteArray(buffer.remaining())
            buffer.get(ciphertext)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            gson.fromJson(
                cipher.doFinal(ciphertext).toString(Charsets.UTF_8),
                Session::class.java,
            )
        } catch (_: KeyPermanentlyInvalidatedException) {
            clear()
            return null
        } catch (_: BadPaddingException) {
            clear()
            return null
        } catch (_: IllegalBlockSizeException) {
            clear()
            return null
        } catch (_: JsonParseException) {
            clear()
            return null
        } catch (_: IllegalArgumentException) {
            clear()
            return null
        }
        if (
            session == null ||
            session.token.isBlank() ||
            session.email.isBlank() ||
            session.expiresAtEpochSeconds <= System.currentTimeMillis() / 1000
        ) {
            clear()
            return null
        }
        return session
    }

    @Synchronized
    override fun clear() {
        check(preferences.edit().remove(SESSION_KEY).commit()) {
            "Could not clear the authenticated session"
        }
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) {
            keyStore.deleteEntry(KEY_ALIAS)
        }
    }

    private fun encrypt(plaintext: ByteArray): ByteArray {
        try {
            return encryptWithCurrentKey(plaintext)
        } catch (_: KeyPermanentlyInvalidatedException) {
            clear()
            return encryptWithCurrentKey(plaintext)
        }
    }

    private fun encryptWithCurrentKey(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(plaintext)
        return ByteBuffer.allocate(cipher.iv.size + encrypted.size)
            .put(cipher.iv)
            .put(encrypted)
            .array()
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val PREFERENCES_NAME = "mtaaconnect_secure_session"
        const val SESSION_KEY = "encrypted_session"
        const val KEY_ALIAS = "mtaaconnect_auth_v1"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_BYTES = 12
        const val GCM_TAG_BYTES = 16
        const val GCM_TAG_BITS = 128
    }
}
