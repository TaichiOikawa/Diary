package com.amanospica.diary.update

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * GitHub Releases API から最新リリースを取得する。
 *
 * 更新確認は起動時に1回叩くだけなので、HTTP ライブラリを増やさず
 * HttpURLConnection と org.json（どちらも OS 同梱）で済ませる。
 */
class GitHubReleaseClient(private val repository: String) {

    /** 最新リリース。まだ1件も公開されていなければ null。 */
    suspend fun fetchLatestRelease(): Result<AppRelease?> = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL("https://api.github.com/repos/$repository/releases/latest")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                setRequestProperty("User-Agent", USER_AGENT)
            }

            try {
                when (val code = connection.responseCode) {
                    // リリースが1件も無いリポジトリは 404 を返す。エラーではなく「更新なし」
                    HttpURLConnection.HTTP_NOT_FOUND -> null
                    in 200..299 ->
                        parseRelease(connection.inputStream.bufferedReader().use { it.readText() })

                    else -> throw IllegalStateException("GitHub API がエラーを返しました (HTTP $code)")
                }
            } finally {
                connection.disconnect()
            }
        }.onFailure { Log.w(TAG, "最新リリースの取得に失敗", it) }
    }

    /** APK が添付されていないリリース（ビルド失敗など）は、更新先にできないので null にする。 */
    private fun parseRelease(body: String): AppRelease? {
        val json = JSONObject(body)
        if (json.optBoolean("draft", false)) return null

        val tagName = json.optString("tag_name").takeIf(String::isNotBlank) ?: return null

        var apkUrl: String? = null
        var apkSizeBytes = 0L
        val assets = json.optJSONArray("assets")
        for (index in 0 until (assets?.length() ?: 0)) {
            val asset = assets?.optJSONObject(index) ?: continue
            if (!asset.optString("name").endsWith(".apk", ignoreCase = true)) continue
            apkUrl = asset.optString("browser_download_url").takeIf(String::isNotBlank)
            apkSizeBytes = asset.optLong("size", 0L)
            break
        }

        return AppRelease(
            tagName = tagName,
            versionName = tagName.removePrefix("v").removePrefix("V"),
            releaseNotes = json.optString("body").trim(),
            apkUrl = apkUrl ?: return null,
            apkSizeBytes = apkSizeBytes,
        )
    }

    private companion object {
        const val TAG = "GitHubReleaseClient"
        const val TIMEOUT_MS = 15_000
        const val USER_AGENT = "amanospica-diary"
    }
}
