package com.elozino.splitfy.screens.onboarding.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elozino.splitfy.ui.generated.resources.UiRes
import com.elozino.splitfy.ui.generated.resources.app_name
import com.elozino.splitfy.ui.theme.primaryLight
import kotlin.math.absoluteValue
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import com.elozino.splitfy.screens.expenses.add.label

private val Ink = Color(0xFF191915)
private val MutedInk = Color(0xFF706F68)
private val Canvas = Color(0xFFFFFCF6)
private val SoftYellow = Color(0xFFFFEAB5)
private val Success = Color(0xFF2D7D46)

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun OnboardingScreen(
    onNavigateToAuth: () -> Unit = {},
) {
    val pagerState = rememberPagerState { onboardingSlides.size }
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == onboardingSlides.lastIndex

    CompositionLocalProvider(LocalOverscrollFactory provides null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Canvas),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing),
            ) {
                OnboardingHeader(onSkip = onNavigateToAuth)

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f),
                    beyondViewportPageCount = 1,
                ) { page ->
                    val pageOffset =
                        (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                    OnboardingPage(
                        slide = onboardingSlides[page],
                        pageOffset = pageOffset,
                    )
                }

                OnboardingFooter(
                    pageCount = onboardingSlides.size,
                    currentPage = pagerState.currentPage,
                    isLastPage = isLastPage,
                    onContinue = {
                        scope.launch {
                            if (isLastPage) {
                                onNavigateToAuth()
                            } else {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun OnboardingHeader(onSkip: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(primaryLight),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Category,
                    contentDescription = "Logo",
                    modifier = Modifier.size(20.dp),
                    tint = Color.Black
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(UiRes.string.app_name),
                color = Ink,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp,
            )
            Box(
                modifier = Modifier
                    .padding(start = 2.dp, top = 12.dp)
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(primaryLight),
            )
        }

        TextButton(
            onClick = onSkip,
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.textButtonColors(contentColor = Ink),
        ) {
            Text("Skip", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun OnboardingPage(
    slide: OnboardingState,
    pageOffset: Float,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
    ) {
        val compact = maxHeight < 590.dp
        val heroHeight = if (compact) 254.dp else 342.dp
        val titleSize = if (compact) 28.sp else 34.sp

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(if (compact) 4.dp else 12.dp))
            HeroArtwork(
                slide = slide,
                pageOffset = pageOffset,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(heroHeight),
            )

            Spacer(Modifier.height(if (compact) 18.dp else 26.dp))

            Text(
                text = slide.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = 1f - pageOffset.absoluteValue.coerceIn(0f, 1f) * 0.35f
                    },
                color = Ink,
                fontSize = titleSize,
                lineHeight = (titleSize.value + 3).sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.7).sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = slide.subtitle,
                modifier = Modifier.fillMaxWidth(0.92f),
                color = MutedInk,
                fontSize = 16.sp,
                lineHeight = 23.sp,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun HeroArtwork(
    slide: OnboardingState,
    pageOffset: Float,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(36.dp))
            .background(primaryLight),
    ) {
        Box(
            Modifier
                .size(maxWidth * 0.72f)
                .offset(x = -(maxWidth * 0.18f), y = -(maxHeight * 0.28f))
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.18f)),
        )
        Box(
            Modifier
                .size(maxWidth * 0.46f)
                .align(Alignment.BottomEnd)
                .offset(x = maxWidth * 0.16f, y = maxHeight * 0.20f)
                .clip(CircleShape)
                .background(Color(0xFFE19A00).copy(alpha = 0.30f)),
        )

        ProductPreview(
            slide = slide,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .width(maxWidth * 0.68f)
                .height(maxHeight * 0.86f)
                .graphicsLayer {
                    translationX = pageOffset * size.width * 0.22f
                    translationY = pageOffset.absoluteValue * 10.dp.toPx()
                    val scale = 1f - pageOffset.absoluteValue.coerceIn(0f, 1f) * 0.06f
                    scaleX = scale
                    scaleY = scale
                },
        )

        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 14.dp, bottom = 22.dp)
                .shadow(10.dp, RoundedCornerShape(18.dp))
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White)
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE4F4E8)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = Success,
                    modifier = Modifier.size(14.dp),
                )
            }
            Spacer(Modifier.width(7.dp))
            Text(
                text = slide.badge,
                color = Ink,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun ProductPreview(
    slide: OnboardingState,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .shadow(20.dp, RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
            .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
            .background(Ink)
            .padding(start = 7.dp, top = 8.dp, end = 7.dp),
    ) {
        Image(
            painter = painterResource(slide.image),
            contentDescription = slide.imageDescription,
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(topStart = 23.dp, topEnd = 23.dp)),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 10.dp)
                .width(44.dp)
                .height(12.dp)
                .clip(CircleShape)
                .background(Ink),
        )
    }
}

@Composable
private fun OnboardingFooter(
    pageCount: Int,
    currentPage: Int,
    isLastPage: Boolean,
    onContinue: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PagerIndicator(pageCount = pageCount, currentPage = currentPage)
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            shape = RoundedCornerShape(50.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = primaryLight,
                contentColor = Color.White,
            ),
        ) {
            Text(
                text = if (isLastPage) "Start splitting" else "Continue",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(10.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun PagerIndicator(pageCount: Int, currentPage: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { index ->
            val selected = index == currentPage
            val width by animateDpAsState(
                targetValue = if (selected) 26.dp else 7.dp,
                label = "onboardingIndicatorWidth",
            )
            val color by animateColorAsState(
                targetValue = if (selected) primaryLight else SoftYellow,
                label = "onboardingIndicatorColor",
            )
            Box(
                modifier = Modifier
                    .width(width)
                    .height(7.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}
