package com.example.newtube.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.example.newtube.model.Video

/** Portrait 9:16 tile used by the Home Shorts shelf. */
@Composable
fun ShortsTile(
    video: Video,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    showMetadata: Boolean = true,
) {
    val context = LocalContext.current
    val thumbnail =
        remember(video.id, context) {
            ImageRequest
                .Builder(context)
                .data(ThumbnailUrls.mq(video.id))
                .size(ThumbnailUrls.SHORTS_TILE_SIZE_PX)
                .build()
        }
    Box(
        modifier =
            modifier
                .width(150.dp)
                .aspectRatio(9f / 16f)
                .clip(RoundedCornerShape(12.dp))
                .clickable { onClick(video.id) },
    ) {
        Image(
            painter = rememberAsyncImagePainter(thumbnail),
            contentDescription = video.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                        ),
                    ),
        )
        Text(
            text = video.title,
            color = Color.White,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
        )
    }
}
