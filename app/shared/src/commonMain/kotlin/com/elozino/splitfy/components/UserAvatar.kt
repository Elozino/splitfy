package com.elozino.splitfy.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage

/**
 * A person's profile photo, or their initials on a grey circle when they have none
 * (or it fails to load).
 */
@Composable
fun UserAvatar(
    avatarUrl: String?,
    name: String,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(Color.LightGray),
        contentAlignment = Alignment.Center,
    ) {
        val initials = @Composable {
            Text(
                text = initialsOf(name),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.36f).sp,
            )
        }
        if (avatarUrl.isNullOrBlank()) {
            initials()
        } else {
            SubcomposeAsyncImage(
                model = avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size),
                error = { initials() },
                loading = { initials() },
            )
        }
    }
}

/** First letters of the first two words: "Ada Lovelace" → "AL". */
internal fun initialsOf(name: String): String =
    name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2)
        .joinToString("") { it.first().uppercase() }
