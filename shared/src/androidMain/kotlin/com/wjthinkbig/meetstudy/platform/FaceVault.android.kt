package com.wjthinkbig.meetstudy.platform

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val ALIAS = "meetstudy_face"
private const val KEY_CT = "ct"
private const val KEY_IV = "iv"

actual object FaceVault {
    private var prefs: SharedPreferences? = null

    /** Call once from the Activity before any composition. */
    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(ALIAS, Context.MODE_PRIVATE)
    }

    private fun requirePrefs() = checkNotNull(prefs) { "FaceVault.init not called" }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
        }.generateKey()
    }

    actual fun save(embedding: FloatArray) {
        val plain = ByteBuffer.allocate(embedding.size * 4).apply { asFloatBuffer().put(embedding) }.array()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val ct = cipher.doFinal(plain)
        requirePrefs().edit()
            .putString(KEY_CT, Base64.encodeToString(ct, Base64.NO_WRAP))
            .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .commit()
    }

    /** Null when nothing is enrolled or the data cannot be decrypted (e.g. key gone after a backup restore). */
    actual fun load(): FloatArray? {
        val p = requirePrefs()
        val ct = p.getString(KEY_CT, null) ?: return null
        val iv = p.getString(KEY_IV, null) ?: return null
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
            val fb = ByteBuffer.wrap(cipher.doFinal(Base64.decode(ct, Base64.NO_WRAP))).asFloatBuffer()
            FloatArray(fb.remaining()).also { fb.get(it) }
        } catch (e: Exception) {
            Log.w(FACE_TAG, "vault load failed: " + e.javaClass.simpleName)
            null
        }
    }

    actual fun clear() {
        requirePrefs().edit().clear().commit()
    }
}
