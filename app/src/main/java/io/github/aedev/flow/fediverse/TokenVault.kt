/*
 * This file is part of TubeHub, a fork of Flow.
 * Copyright (C) 2026 TubeHub contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package io.github.aedev.flow.fediverse

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypts the Fediverse API token at rest.
 *
 * The token is a Misskey *write* credential — it can post, react and follow as the user. Storing it
 * in plaintext would be poor on its own, but this app also inherits `android:allowBackup="true"`
 * from upstream, so a plaintext token would be swept into Android Auto Backup and leave the device.
 * The backup rules exclude the DataStore file as well; this is the second line of defence, and the
 * one that still holds if a future manifest change re-enables backup of that path.
 *
 * AES-256-GCM with a key held in the AndroidKeyStore, so the key material never enters app memory
 * and cannot be extracted from a backup or a rooted filesystem copy. Available unconditionally at
 * minSdk 26, hence no dependency — notably not `androidx.security:security-crypto`, which is
 * deprecated.
 */
@Singleton
class TokenVault @Inject constructor() {

    /**
     * Returns the encrypted form of [plaintext], or the plaintext itself if the keystore is
     * unavailable. Failing closed here would lock the user out of an account they legitimately
     * connected; the DataStore file is app-private and backup-excluded either way.
     */
    fun encrypt(plaintext: String): String = runCatching {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val payload = cipher.iv + encrypted
        PREFIX + Base64.encodeToString(payload, Base64.NO_WRAP)
    }.getOrElse {
        Log.w(TAG, "Keystore unavailable, storing token unencrypted", it)
        plaintext
    }

    /** Inverse of [encrypt]. Values without the marker are returned unchanged. */
    fun decrypt(stored: String): String {
        if (!stored.startsWith(PREFIX)) return stored
        return runCatching {
            val payload = Base64.decode(stored.removePrefix(PREFIX), Base64.NO_WRAP)
            val iv = payload.copyOfRange(0, IV_LENGTH)
            val body = payload.copyOfRange(IV_LENGTH, payload.size)
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
            }
            String(cipher.doFinal(body), Charsets.UTF_8)
        }.getOrElse {
            // Key lost — e.g. the user removed their screen lock, or app data was restored onto a
            // different device. The account simply has to be reconnected.
            Log.w(TAG, "Could not decrypt stored token", it)
            ""
        }
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                // Deliberately not requiring user authentication: playback actions must work
                // without a lock-screen prompt in the middle of a video.
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val TAG = "TokenVault"
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "tubehub_fediverse"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_SIZE_BITS = 256
        const val TAG_LENGTH_BITS = 128
        const val IV_LENGTH = 12
        /** Marks a value as ciphertext so tokens stored before this existed still work. */
        const val PREFIX = "enc:v1:"
    }
}
