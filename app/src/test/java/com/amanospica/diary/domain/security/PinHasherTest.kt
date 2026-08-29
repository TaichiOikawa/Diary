package com.amanospica.diary.domain.security

import com.amanospica.diary.domain.model.PinCredential
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinHasherTest {

    @Test
    fun `正しいPINは検証を通る`() {
        val credential = PinHasher.create("1234")
        assertTrue(PinHasher.verify("1234", credential))
    }

    @Test
    fun `異なるPINは弾かれる`() {
        val credential = PinHasher.create("1234")
        assertFalse(PinHasher.verify("1235", credential))
        assertFalse(PinHasher.verify("12340", credential))
        assertFalse(PinHasher.verify("", credential))
    }

    @Test
    fun `同じPINでも保存されるハッシュは毎回変わる`() {
        // ソルトが効いていれば、同一PINでもハッシュは一致しない
        val first = PinHasher.create("1234")
        val second = PinHasher.create("1234")

        assertNotEquals(first.salt, second.salt)
        assertNotEquals(first.hash, second.hash)
        assertTrue(PinHasher.verify("1234", first))
        assertTrue(PinHasher.verify("1234", second))
    }

    @Test
    fun `PINそのものは保存値に含まれない`() {
        val credential = PinHasher.create("824615")
        assertFalse(credential.hash.contains("824615"))
        assertFalse(credential.salt.contains("824615"))
    }

    @Test
    fun `壊れた保存値では例外を投げずに失敗を返す`() {
        val broken = PinCredential(hash = "not-base64!!", salt = "***")
        assertFalse(PinHasher.verify("1234", broken))
    }

    @Test
    fun `形式チェックは4から8桁の数字だけを通す`() {
        assertTrue(PinHasher.isValidFormat("1234"))
        assertTrue(PinHasher.isValidFormat("12345678"))
        assertFalse(PinHasher.isValidFormat("123"))
        assertFalse(PinHasher.isValidFormat("123456789"))
        assertFalse(PinHasher.isValidFormat("12a4"))
        assertFalse(PinHasher.isValidFormat(""))
    }
}
