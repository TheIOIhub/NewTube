package com.example.newtube.ui.common

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Placeholder card shown while the first Home page loads. */
@Composable
fun VideoCardSkeleton(modifier: Modifier = Modifier) {
    val alpha = skeletonAlpha()
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)),
        )
        Row(modifier = Modifier.padding(top = 12.dp)) {
            Box(
                modifier =
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                SkeletonLine(widthFraction = 0.9f, height = 14.dp, alpha = alpha)
                Spacer(modifier = Modifier.height(6.dp))
                SkeletonLine(widthFraction = 0.6f, height = 12.dp, alpha = alpha)
            }
        }
    }
}

@Composable
private fun SkeletonLine(
    widthFraction: Float,
    height: Dp,
    alpha: Float,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth(widthFraction)
                .height(height)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)),
    )
}

@Composable
private fun skeletonAlpha(): Float {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by
        transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 0.8f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "skeleton-alpha",
        )
    return alpha
}
