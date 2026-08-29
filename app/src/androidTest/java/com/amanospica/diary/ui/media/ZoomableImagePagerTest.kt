package com.amanospica.diary.ui.media

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [ZoomableImage] をページャの中に入れたときの、指1本の取り合いを固定する。
 *
 * 拡大の移動とページ送りはどちらも「1本指で横へ払う」動きなので、
 * 消費の条件を間違えると「ページが送れない」か「拡大したまま送られる」の
 * どちらかに倒れる。どちらも画面を見ただけでは気づきにくいのでテストで押さえる。
 */
@RunWith(AndroidJUnit4::class)
class ZoomableImagePagerTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var pagerState: PagerState

    /** ファイルは実在しなくてよい（読めなくても、当たり判定は領域いっぱいに出る）。 */
    private fun setUpPager() {
        composeRule.setContent {
            pagerState = rememberPagerState(initialPage = 0) { PAGE_COUNT }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                ZoomableImage(
                    absolutePath = "/not/a/real/file-$page.jpg",
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("page-$page"),
                )
            }
        }
    }

    private fun doubleTapFirstPage() {
        composeRule.onNodeWithTag("page-0").performTouchInput { doubleClick() }
        composeRule.waitForIdle()
    }

    private fun swipeLeftOnFirstPage() {
        composeRule.onNodeWithTag("page-0").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
    }

    @Test
    fun 等倍なら横スワイプはページ送りになる() {
        setUpPager()

        swipeLeftOnFirstPage()

        composeRule.runOnIdle { assertEquals(1, pagerState.currentPage) }
    }

    @Test
    fun 拡大中の横スワイプはページを送らない() {
        setUpPager()

        doubleTapFirstPage()
        swipeLeftOnFirstPage()

        // 拡大中の1本指は画像の移動にあてるので、ページは動かない
        composeRule.runOnIdle { assertEquals(0, pagerState.currentPage) }
    }

    @Test
    fun ダブルタップで等倍に戻せばまたページを送れる() {
        setUpPager()

        doubleTapFirstPage()
        doubleTapFirstPage()
        swipeLeftOnFirstPage()

        composeRule.runOnIdle { assertEquals(1, pagerState.currentPage) }
    }

    private companion object {
        const val PAGE_COUNT = 3
    }
}
