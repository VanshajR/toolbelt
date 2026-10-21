package com.utilityapp.ui

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.IntSize

/** Shared motion so every screen moves the same way. */
object Motion {
    const val SHORT = 180
    const val MEDIUM = 320

    /** Colour and alpha changes (mode colours, selection, dimming). */
    fun <T> fade() = tween<T>(MEDIUM, easing = FastOutSlowInEasing)

    /** Panels growing or shrinking as options appear. */
    val resize = spring<IntSize>(stiffness = Spring.StiffnessMediumLow)

    /** Going deeper (forward) or back between screens: a short slide with a crossfade. */
    fun push(forward: Boolean): ContentTransform {
        val direction = if (forward) 1 else -1
        return (
            slideInHorizontally(tween(MEDIUM, easing = FastOutSlowInEasing)) { it / 5 * direction } +
                fadeIn(tween(MEDIUM, delayMillis = 60))
            ) togetherWith (
            slideOutHorizontally(tween(MEDIUM, easing = FastOutSlowInEasing)) { -it / 5 * direction } +
                fadeOut(tween(SHORT))
            )
    }

    /** A value replacing another in place, like the hero headline: the new one rises in. */
    fun swap(): ContentTransform =
        (slideInVertically(tween(MEDIUM, easing = FastOutSlowInEasing)) { it / 3 } + fadeIn(tween(MEDIUM))) togetherWith
            (slideOutVertically(tween(SHORT)) { -it / 3 } + fadeOut(tween(SHORT)))
}
