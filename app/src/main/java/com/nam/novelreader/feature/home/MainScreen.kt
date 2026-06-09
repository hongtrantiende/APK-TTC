package com.nam.novelreader.feature.home

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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

    Scaffold(
        containerColor = VBookTheme.backgroundColor(),
        contentWindowInsets = WindowInsets(0.dp),
        bottomBar = {
            NavigationBar(
                containerColor = VBookTheme.backgroundColor(),
                contentColor = VBookTheme.subTextColor(),
                tonalElevation = 0.dp,
                modifier = Modifier.shadow(8.dp)
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = {
                        selectedTab = 0
                        coroutineScope.launch { pagerState.animateScrollToPage(0) }
                    },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == 0) Icons.Filled.Book else Icons.Outlined.Book,
                            contentDescription = "Trang chủ"
                        )
                    },
                    label = { Text("Trang chủ", fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = VBookTheme.primaryColor(),
                        selectedTextColor = VBookTheme.primaryColor(),
                        unselectedIconColor = VBookTheme.subTextColor(),
                        unselectedTextColor = VBookTheme.subTextColor(),
                        indicatorColor = Color.Transparent // Loại bỏ viền Pill của Material 3 giống VBook gốc
                    )
                )

                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = {
                        selectedTab = 1
                        coroutineScope.launch { pagerState.animateScrollToPage(1) }
                    },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == 1) Icons.Filled.Explore else Icons.Outlined.Explore,
                            contentDescription = "Khám phá"
                        )
                    },
                    label = { Text("Khám phá", fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = VBookTheme.primaryColor(),
                        selectedTextColor = VBookTheme.primaryColor(),
                        unselectedIconColor = VBookTheme.subTextColor(),
                        unselectedTextColor = VBookTheme.subTextColor(),
                        indicatorColor = Color.Transparent
                    )
                )

                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = {
                        selectedTab = 2
                        coroutineScope.launch { pagerState.animateScrollToPage(2) }
                    },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == 2) Icons.Filled.ChatBubble else Icons.Outlined.ChatBubbleOutline,
                            contentDescription = "Cộng đồng"
                        )
                    },
                    label = { Text("Cộng đồng", fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = VBookTheme.primaryColor(),
                        selectedTextColor = VBookTheme.primaryColor(),
                        unselectedIconColor = VBookTheme.subTextColor(),
                        unselectedTextColor = VBookTheme.subTextColor(),
                        indicatorColor = Color.Transparent
                    )
                )

                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = {
                        selectedTab = 3
                        coroutineScope.launch { pagerState.animateScrollToPage(3) }
                    },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == 3) Icons.Filled.Settings else Icons.Outlined.Settings,
                            contentDescription = "Thêm"
                        )
                    },
                    label = { Text("Thêm", fontWeight = if (selectedTab == 3) FontWeight.Bold else FontWeight.Normal) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = VBookTheme.primaryColor(),
                        selectedTextColor = VBookTheme.primaryColor(),
                        unselectedIconColor = VBookTheme.subTextColor(),
                        unselectedTextColor = VBookTheme.subTextColor(),
                        indicatorColor = Color.Transparent
                    )
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .consumeWindowInsets(paddingValues)
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
