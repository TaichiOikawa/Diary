package com.amanospica.diary.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppReleaseTest {

    @Test
    fun `数値ごとに比較する`() {
        assertTrue(compareVersions("1.2.3", "1.2.2") > 0)
        assertTrue(compareVersions("1.2.3", "1.3.0") < 0)
        assertEquals(0, compareVersions("1.2.3", "1.2.3"))
    }

    /** 文字列比較だと "1.10.0" < "1.9.0" になってしまうので、ここを外さない。 */
    @Test
    fun `2桁の数字を文字列として比べない`() {
        assertTrue(compareVersions("1.10.0", "1.9.0") > 0)
        assertTrue(compareVersions("2.0.0", "10.0.0") < 0)
    }

    @Test
    fun `先頭の v は付いていても付いていなくてもよい`() {
        assertEquals(0, compareVersions("v1.2.3", "1.2.3"))
        assertEquals(0, compareVersions("V1.2.3", "v1.2.3"))
    }

    /** タグは "v1.2" のように桁を省くことがある。足りない桁は 0 とみなす。 */
    @Test
    fun `桁数が違っても同じ値なら等しい`() {
        assertEquals(0, compareVersions("1.2", "1.2.0"))
        assertTrue(compareVersions("1.2.1", "1.2") > 0)
    }

    @Test
    fun `プレリリース表記は無視する`() {
        assertEquals(0, compareVersions("1.2.3-beta.1", "1.2.3"))
        assertTrue(compareVersions("1.2.4-rc1", "1.2.3") > 0)
    }

    @Test
    fun `新しいリリースだけ更新の対象になる`() {
        val release = release(tagName = "v1.1.0")
        assertTrue(release.isNewerThan("1.0.0"))
        assertFalse(release.isNewerThan("1.1.0"))
        // 手元のビルドが配布物より新しいこともある（開発中の端末など）
        assertFalse(release.isNewerThan("1.2.0"))
    }

    private fun release(tagName: String) = AppRelease(
        tagName = tagName,
        versionName = tagName.removePrefix("v"),
        releaseNotes = "",
        apkUrl = "https://example.com/diary.apk",
        apkSizeBytes = 0L,
    )
}
