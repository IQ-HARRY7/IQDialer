//**************************************************
// *
// * Copyright© IQ-STUDIO 2026 (ptv limited)
// * IQDialer project uses GPL3 (or later).
// *
//**************************************************

package com.iqstudio.dialer

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring

interface NestedPane {
    val paneDepth: Int
}

fun AnimatedContentTransitionScope<*>.nestedPaneTransitionSpec(): ContentTransform {
    val initialPane = initialState as NestedPane
    val targetPane = targetState as NestedPane
    val forward = targetPane.paneDepth > initialPane.paneDepth

    return if (forward) {
        (
            slideInHorizontally(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) { it / 3 } + fadeIn()
        ).togetherWith(
            slideOutHorizontally(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) { -it / 3 } + fadeOut()
        )
    } else {
        (
            slideInHorizontally(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) { -it / 3 } + fadeIn()
        ).togetherWith(
            slideOutHorizontally(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) { it / 3 } + fadeOut()
        )
    }
}
