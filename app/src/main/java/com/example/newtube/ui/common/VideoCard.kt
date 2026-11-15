package com.example.newtube.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.example.newtube.R
import com.example.newtube.model.Video

@Composable
fun VideoCard(
    video: Video,
    onClick: (String) -> Unit,
    onChannelClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val meta = videoMetaLine(video)
    val viewsLabel =
        if (meta.showViews) {
            stringResource(R.string.views_count, formatCount(video.views))
        } else {
            null
        }
    val metadata =
        listOfNotNull(
            video.channelName.takeIf { it.isNotBlank() },
            viewsLabel,
            meta.uploadedAt,
        ).joinToString(" · ")

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable { onClick(video.id) }
                .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            Image(
                painter = rememberAsyncImagePainter(video.thumbnailUrl),
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
            )
            when {
                video.isLive -> LiveBadge(Modifier.align(Alignment.BottomEnd).padding(8.dp))
                video.duration > 0L ->
                    DurationBadge(
                        text = formatDuration(video.duration),
                        modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                    )
            }
        }
        Row(modifier = Modifier.padding(top = 12.dp)) {
            ChannelAvatar(
                url = video.authorAvatarUrl,
                name = video.channelName,
                modifier =
                    Modifier.clickable(enabled = video.channelId.isNotBlank()) {
                        onChannelClick(video.channelId)
                    },
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = metadata,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                        Modifier.clickable(enabled = video.channelId.isNotBlank()) {
                            onChannelClick(video.channelId)
                        },
                )
            }
        }
    }
}

@Composable
private fun DurationBadge(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        modifier =
            modifier
                .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun LiveBadge(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.live_badge),
        color = Color.White,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        modifier =
            modifier
                .background(Color(0xFFCC0000), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
