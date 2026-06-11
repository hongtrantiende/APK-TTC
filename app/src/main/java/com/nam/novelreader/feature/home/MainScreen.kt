package com.nam.novelreader.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.nam.novelreader.feature.browse.BrowseScreen
import com.nam.novelreader.feature.library.LibraryScreen
import com.nam.novelreader.feature.settings.SettingsScreen
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.launch
import com.nam.novelreader.feature.components.VBookTheme

@Composable
fun MainScreen(navController: NavHostController) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 4 })
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(pagerState.currentPage) {
        selectedTab = pagerState.currentPage
    }

    val bgColor = VBookTheme.backgroundColor()
    val accentColor = VBookTheme.accentColor()
    val subTextColor = VBookTheme.subTextColor()
    val dividerColor = VBookTheme.cardColor()

    Scaffold(
        containerColor = bgColor,
        contentWindowInsets = WindowInsets(0.dp),
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Transparent)
            ) {
                // Floating pill-shaped bottom nav
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .height(64.dp)
                        .shadow(elevation = 8.dp, shape = RoundedCornerShape(32.dp))
                        .background(color = dividerColor, shape = RoundedCornerShape(32.dp))
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CompactNavItem(
                        selected = selectedTab == 0,
                        selectedIcon = Icons.Filled.Book,
                        unselectedIcon = Icons.Outlined.Book,
                        label = "Trang chủ",
                        primaryColor = accentColor,
                        subTextColor = subTextColor,
                        onClick = {
                            selectedTab = 0
                            coroutineScope.launch { pagerState.animateScrollToPage(0) }
                        }
                    )
                    CompactNavItem(
                        selected = selectedTab == 1,
                        selectedIcon = Icons.Filled.Explore,
                        unselectedIcon = Icons.Outlined.Explore,
                        label = "Khám phá",
                        primaryColor = accentColor,
                        subTextColor = subTextColor,
                        onClick = {
                            selectedTab = 1
                            coroutineScope.launch { pagerState.animateScrollToPage(1) }
                        }
                    )
                    CompactNavItem(
                        selected = selectedTab == 2,
                        selectedIcon = Icons.Filled.ChatBubble,
                        unselectedIcon = Icons.Outlined.ChatBubbleOutline,
                        label = "Cộng đồng",
                        primaryColor = accentColor,
                        subTextColor = subTextColor,
                        onClick = {
                            selectedTab = 2
                            coroutineScope.launch { pagerState.animateScrollToPage(2) }
                        }
                    )
                    CompactNavItem(
                        selected = selectedTab == 3,
                        selectedIcon = Icons.Filled.MoreHoriz,
                        unselectedIcon = Icons.Outlined.MoreHoriz,
                        label = "Thêm",
                        primaryColor = accentColor,
                        subTextColor = subTextColor,
                        onClick = {
                            selectedTab = 3
                            coroutineScope.launch { pagerState.animateScrollToPage(3) }
                        }
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(bottom = paddingValues.calculateBottomPadding())
                .consumeWindowInsets(PaddingValues(bottom = paddingValues.calculateBottomPadding()))
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = false,
                beyondViewportPageCount = 3
            ) { page ->
                when (page) {
                    0 -> HomeScreen(
                        navController = navController,
                        onNavigateToTab = { targetPage ->
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(targetPage)
                            }
                        }
                    )
                    1 -> BrowseScreen(navController = navController)
                    2 -> CommunityScreen(navController = navController)
                    3 -> SettingsScreen(navController = navController)
                }
            }
        }
    }
}

/**
 * Modern floating nav item — larger touch target, 24dp icon, animated color.
 */
@Composable
private fun CompactNavItem(
    selected: Boolean,
    selectedIcon: ImageVector,
    unselectedIcon: ImageVector,
    label: String,
    primaryColor: Color,
    subTextColor: Color,
    onClick: () -> Unit
) {
    val iconColor = if (selected) primaryColor else subTextColor.copy(alpha = 0.6f)
    val textColor = if (selected) primaryColor else subTextColor.copy(alpha = 0.5f)

    Column(
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (selected) selectedIcon else unselectedIcon,
            contentDescription = label,
            tint = iconColor,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

