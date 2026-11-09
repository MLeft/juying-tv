package com.juying.tv.ui.components

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec

/**
 * 全应用统一的焦点滚动动画。
 *
 * 默认的 bring-into-view 是一条刚度 1500 的快簧，跨板块跳转时起步很猛、收尾突兀，
 * 在电视上表现为“顿一下”或“一下滑过去”。这里换成无回弹、刚度 360 的弹簧：
 * 相邻卡片约 200ms，跨板块约 350ms，起步和收尾都更柔和。
 *
 * 同时保留一圈安全边距：首页卡片的标题在图片下方、不属于聚焦节点，
 * 只按焦点矩形滚动会把标题切掉；统一留出 [edgeMarginPx] 后，标题和呼吸空间一起进入视口。
 */
@OptIn(ExperimentalFoundationApi::class)
class JuyingFocusScrollSpec(
    private val edgeMarginPx: Float
) : BringIntoViewSpec {

    override val scrollAnimationSpec: AnimationSpec<Float> = spring(
        dampingRatio = 1f,
        stiffness = 360f,
        visibilityThreshold = 0.5f
    )

    override fun calculateScrollDistance(
        initialOffset: Float,
        size: Float,
        containerSize: Float
    ): Float {
        // 小屏或很矮的容器里，边距最多占一半，避免把可视区压没。
        val margin = edgeMarginPx.coerceIn(0f, containerSize / 2f)
        val viewportStart = margin
        val viewportEnd = containerSize - margin
        val itemEnd = initialOffset + size
        return when {
            initialOffset < viewportStart -> initialOffset - viewportStart
            itemEnd > viewportEnd -> itemEnd - viewportEnd
            else -> 0f
        }
    }
}
