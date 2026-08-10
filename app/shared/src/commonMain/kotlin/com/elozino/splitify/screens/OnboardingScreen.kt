package com.elozino.splitify.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elozino.splitify.ui.theme.primaryLight
import kotlinx.coroutines.launch

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun OnboardingScreen(
    onNavigateToAuth: () -> Unit = { }
) {
    val pagerState = rememberPagerState { onboardingSlides.size }
    val scope = rememberCoroutineScope()

    CompositionLocalProvider(
        LocalOverscrollFactory provides null
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
        ) { page ->
            val slide = onboardingSlides[page]
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier.weight(0.6f).fillMaxWidth().background(
                        color = primaryLight,
                        shape = RoundedCornerShape(bottomStart = 150.dp, bottomEnd = 150.dp)
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Phone Image for: ${slide.title}", textAlign = TextAlign.Center)
                }
                Box(
                    modifier = Modifier.weight(0.4f).padding(top = 24.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()
                    ) {
                        Text(
                            slide.title,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            lineHeight = 36.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            slide.subtitle,
                            textAlign = TextAlign.Center,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Light,
                            lineHeight = 24.sp
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Pager Indicator
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            repeat(onboardingSlides.size) { activeIndex ->
                                val color = if (pagerState.currentPage == activeIndex) primaryLight else Color.LightGray
                                Box(
                                    modifier = Modifier
                                        .padding(4.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .size(10.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(elevation = 8.dp)
                .background(MaterialTheme.colorScheme.surface)
                .drawBehind {
                    drawLine(
                        color = primaryLight,
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        strokeWidth = 1.dp.toPx()
                    )
                }
                .windowInsetsPadding(WindowInsets(0, 0, 0, 20))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = { onNavigateToAuth() },
                modifier = Modifier.weight(0.5f),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.background),
                border = BorderStroke(1.dp, primaryLight)
            ) {
                Text("Skip", color = MaterialTheme.colorScheme.onBackground)
            }
            Spacer(modifier = Modifier.weight(0.05f))
            Button(
                onClick = {
                    scope.launch {
                        if (pagerState.currentPage < onboardingSlides.size - 1) {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        } else {
                            onNavigateToAuth()
                        }
                    }
                },
                modifier = Modifier.weight(0.5f),
                colors = ButtonDefaults.buttonColors(containerColor = primaryLight)
            ) {
                Text(
                    if (pagerState.currentPage < onboardingSlides.size - 1) "Continue" else "Get Started",
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
        }
    }
}
