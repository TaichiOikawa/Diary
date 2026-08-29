package com.amanospica.diary

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import coil3.video.VideoFrameDecoder
import com.amanospica.diary.di.AppContainer

class DiaryApplication : Application(), SingletonImageLoader.Factory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // アプリがバックグラウンドへ回ったら施錠し、次に前面へ来たときに認証を求める
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStop(owner: LifecycleOwner) {
                    container.appLockController.lock()
                }
            }
        )
    }

    /**
     * 内部ストレージの画像に加え、動画ファイルの先頭フレームもサムネイルとして
     * 読み込めるよう `VideoFrameDecoder` を登録する。
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(VideoFrameDecoder.Factory()) }
            .crossfade(true)
            .build()
}

/** Composable / ViewModel から DI コンテナへ到達するためのヘルパー。 */
val Context.appContainer: AppContainer
    get() = (applicationContext as DiaryApplication).container
