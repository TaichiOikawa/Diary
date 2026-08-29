package com.amanospica.diary.domain.security

import com.amanospica.diary.domain.model.PinCredential
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * PIN をソルト付きハッシュに変換する。
 *
 * 4〜8桁の PIN は総当たりが容易なので、単純なハッシュではなく反復回数の多い
 * PBKDF2 を使って1回の試行コストを上げる。検証は定数時間比較で行い、
 * 応答時間から正解が漏れないようにする。
 */
object PinHasher {

    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 8

    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_LENGTH_BYTES = 16

    /** 数字のみ・規定の長さかどうか。 */
    fun isValidFormat(pin: String): Boolean =
        pin.length in MIN_LENGTH..MAX_LENGTH && pin.all { it.isDigit() }

    fun create(pin: String): PinCredential {
        val salt = ByteArray(SALT_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
        return PinCredential(
            hash = encode(derive(pin, salt)),
            salt = encode(salt),
        )
    }

    fun verify(pin: String, credential: PinCredential): Boolean {
        val salt = runCatching { decode(credential.salt) }.getOrNull() ?: return false
        val expected = runCatching { decode(credential.hash) }.getOrNull() ?: return false
        return MessageDigest.isEqual(derive(pin, salt), expected)
    }

    private fun derive(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        return try {
            SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun encode(bytes: ByteArray): String =
        Base64.getEncoder().withoutPadding().encodeToString(bytes)

    private fun decode(value: String): ByteArray = Base64.getDecoder().decode(value)
}
