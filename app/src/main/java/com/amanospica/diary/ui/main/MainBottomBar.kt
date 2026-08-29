package com.amanospica.diary.ui.main

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.amanospica.diary.R
import kotlin.math.atan2
import kotlin.math.sqrt

/** ＋ボタンの直径。 */
private val FabSize = 56.dp

/** 切り欠きと＋ボタンの間に開ける隙間。 */
private val FabGap = 6.dp

/** ＋ボタンをバーの上端からどれだけ沈めるか。半分ほど埋める。 */
private val FabSinkDepth = 24.dp

/** 平らな縁から切り欠きへ落とすときの、つなぎの丸み。 */
private val CradleCornerRadius = 8.dp

/**
 * タブを並べる帯の高さ（システムの操作領域は含まない）。
 *
 * [NavigationBar] の既定は 80dp だが、それはラベル付きのタブを見込んだ高さで、
 * アイコンだけのこのバーでは上下が余る。既定は変えられないので高さを直に与える。
 */
private val BarContentHeight = 64.dp

/** ＋ボタンがバーの上端から飛び出す高さ。 */
private val FabOverhang: Dp = FabSize - FabSinkDepth

/** 切り欠きがバーの上端より下へ食い込む深さ。＋ボタンの底より、隙間のぶんだけ深い。 */
private val CradleDepth: Dp = FabSinkDepth + FabGap

/**
 * バーの上端を挟んで、画面の中身が透ける帯の高さ。
 *
 * ＋ボタンが飛び出すぶん（上端より上）と、切り欠きがバーを食い破るぶん（上端より下）の合計。
 * この帯はバーで塞がれないので、載せる側（[MainScreen]）はここまで中身を伸ばす。
 * 空けたままにすると、一覧とバーの間や＋ボタンの下に、何も無い白い隙間が残ってしまう。
 */
val MainBottomBarSeeThroughHeight: Dp = FabOverhang + CradleDepth

/**
 * バーの両端に空ける重み。[NavigationBarItem] の `weight(1f)` に対する比。
 *
 * これがないとタブが画面の端まで広がり、＋ボタンだけが中央で浮いて見える。
 * 端を少し空けて、4 つのタブを中央寄りにまとめる。
 */
private const val EDGE_SLOT_WEIGHT = 0.4f

/**
 * 下部のタブバー。タブを左右に振り分け、中央に円形の＋ボタンをはめ込む。
 *
 * ＋ボタンはバーの上に浮かせず、上端に開けた丸い切り欠きへ半分ほど沈める。
 * タブは [NavigationBarItem] が自分で `weight(1f)` を持つので、真ん中に同じ重みの
 * [Spacer] を挟むだけで左右に振り分けられる。
 *
 * タブはアイコンだけにしている。ラベルを置くと切り欠きに幅を取られたぶん字が
 * 詰まって読みにくく、＋ボタンの収まりも悪くなるため。
 */
@Composable
fun MainBottomBar(
    pages: List<MainPage>,
    currentPage: MainPage,
    onSelectPage: (MainPage) -> Unit,
    /** ＋ボタンを出すか。まとめ選択中など、書くことが主役でない場面では隠す。 */
    showCreateButton: Boolean,
    onCreateDiary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // ＋ボタンの上半分がバーからはみ出すぶん、バーの上に場所を空ける
    val barTopPadding = FabOverhang

    // 高さを直に与えると内側の余白も潰れてしまうので、システムの操作領域のぶんを足しておく
    val systemBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // ＋ボタンを隠すときは切り欠きも一緒に閉じる。開いたままだとバーが欠けて見えるため。
    // 半径と深さを同じ割合で縮めると、途中の形も破綻せずに平らな縁へ戻る。
    val cradleProgress by animateFloatAsState(
        targetValue = if (showCreateButton) 1f else 0f,
        label = "cradle",
    )

    Box(modifier = modifier.fillMaxWidth()) {
        NavigationBar(
            modifier = Modifier
                .padding(top = barTopPadding)
                .height(BarContentHeight + systemBottomInset)
                .clip(
                    CradledBarShape(
                        cradleRadius = (FabSize / 2 + FabGap) * cradleProgress,
                        // 沈めた深さの分だけ、切り欠きの中心はバーの上端より上にくる
                        cradleCenterY = -(FabSize / 2 - FabSinkDepth) * cradleProgress,
                        cornerRadius = CradleCornerRadius,
                    )
                ),
        ) {
            val centerIndex = pages.size / 2
            Spacer(Modifier.weight(EDGE_SLOT_WEIGHT))
            pages.forEachIndexed { index, page ->
                if (index == centerIndex) {
                    Spacer(Modifier.weight(1f))
                }
                val selected = page == currentPage
                NavigationBarItem(
                    selected = selected,
                    onClick = { onSelectPage(page) },
                    icon = {
                        Icon(
                            imageVector = if (selected) page.selectedIcon else page.unselectedIcon,
                            contentDescription = stringResource(page.titleRes),
                        )
                    },
                )
            }
            Spacer(Modifier.weight(EDGE_SLOT_WEIGHT))
        }

        // 切り欠きの開き具合に合わせて出入りさせる（AnimatedVisibility で先に消すと、
        // 切り欠きだけが残ってバーが欠けて見える瞬間ができてしまう）
        if (cradleProgress > 0f) {
            FloatingActionButton(
                onClick = onCreateDiary,
                shape = CircleShape,
                // 浮かせすぎると切り欠きから浮き上がって見えるので、影は控えめにする
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 3.dp,
                    pressedElevation = 6.dp,
                ),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .size(FabSize)
                    .alpha(cradleProgress),
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.action_create_diary),
                )
            }
        }
    }
}

/**
 * バーの上端に丸い切り欠きを開けた形。
 *
 * [cradleCenterY] はバーの上端を 0 とした切り欠きの中心で、上向きが負。
 * 中心をバーの外へ出すことで、上端に浅いくぼみだけが残る。
 * 切り欠きの円をそのまま引くと縁が角で折れるので、[cornerRadius] の円弧を
 * 平らな縁と切り欠きの両方に接するように挟み、なめらかにつなぐ。
 */
private data class CradledBarShape(
    private val cradleRadius: Dp,
    private val cradleCenterY: Dp,
    private val cornerRadius: Dp,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val radius = with(density) { cradleRadius.toPx() }
        val corner = with(density) { cornerRadius.toPx() }
        val centerY = with(density) { cradleCenterY.toPx() }
        val centerX = size.width / 2f

        // つなぎの円は平らな縁に接する（中心の高さ = corner）ので、
        // 切り欠きの円と外接する位置までの水平距離はこれで決まる
        val spanSquared = (radius + corner) * (radius + corner) -
            (corner - centerY) * (corner - centerY)
        if (radius <= 0f || spanSquared <= 0f) {
            return Outline.Rectangle(size.toRect())
        }
        val span = sqrt(spanSquared)
        // つなぎの円の中心から見た、切り欠きの円の中心の向き（画面座標なので下が正）
        val towardCradle = Math.toDegrees(
            atan2((centerY - corner).toDouble(), span.toDouble())
        ).toFloat()

        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(centerX - span, 0f)
            arcTo(
                rect = Rect(Offset(centerX - span, corner), corner),
                startAngleDegrees = -90f,
                sweepAngleDegrees = 90f + towardCradle,
                forceMoveTo = false,
            )
            arcTo(
                rect = Rect(Offset(centerX, centerY), radius),
                startAngleDegrees = 180f + towardCradle,
                sweepAngleDegrees = -(180f + 2f * towardCradle),
                forceMoveTo = false,
            )
            arcTo(
                rect = Rect(Offset(centerX + span, corner), corner),
                startAngleDegrees = 180f - towardCradle,
                sweepAngleDegrees = 90f + towardCradle,
                forceMoveTo = false,
            )
            lineTo(size.width, 0f)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        return Outline.Generic(path)
    }
}
