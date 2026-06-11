package com.nam.novelreader.navigation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nam.novelreader.feature.browse.BrowseScreen
import com.nam.novelreader.feature.detail.NovelDetailScreen
import com.nam.novelreader.feature.extensions.ExtensionSettingsScreen
import com.nam.novelreader.feature.extensions.ExtensionStoreScreen
import com.nam.novelreader.feature.extensions.TranslationSettingsScreen
import com.nam.novelreader.feature.extensions.QuickTranslateSettingsScreen
import com.nam.novelreader.feature.home.MainScreen
import com.nam.novelreader.feature.localreader.LocalFileReaderScreen
import com.nam.novelreader.feature.reader.TextReaderScreen
import com.nam.novelreader.feature.reader.ComicReaderScreen
import com.nam.novelreader.feature.reader.VideoReaderScreen
import com.nam.novelreader.feature.search.SearchScreen
import com.nam.novelreader.feature.browse.WebViewScreen
import com.nam.novelreader.feature.settings.SettingsConnectionScreen
import com.nam.novelreader.feature.settings.SettingsDisplayScreen
import com.nam.novelreader.feature.settings.SettingsReaderScreen
import com.nam.novelreader.feature.settings.SettingsFontSizeScreen
import com.nam.novelreader.feature.download.DownloadScreen
import com.nam.novelreader.feature.history.HistoryScreen
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * App navigation routes.
 */
object Routes {
    const val MAIN = "main"
    const val BROWSE = "browse/{extensionId}"
    const val SEARCH = "search/{extensionId}?query={query}"
    const val DETAIL = "detail/{extensionId}?novelUrl={novelUrl}&isOffline={isOffline}"
    const val LIST_NOVEL = "list_novel/{extensionId}?title={title}&script={script}&input={input}"
    const val TEXT_READER = "reader/text/{extensionId}?novelUrl={novelUrl}&chapterUrl={chapterUrl}&isOffline={isOffline}"
    const val COMIC_READER = "reader/comic/{extensionId}?novelUrl={novelUrl}&chapterUrl={chapterUrl}&isOffline={isOffline}"
    const val VIDEO_READER = "reader/video/{extensionId}?novelUrl={novelUrl}&chapterUrl={chapterUrl}&isOffline={isOffline}"
    const val EXTENSION_STORE = "extensions"
    const val EXTENSION_SETTINGS = "extensions/settings/{extensionId}"
    const val WEBVIEW = "webview?url={url}&extensionId={extensionId}"

    // Settings sub-screens
    const val SETTINGS_DISPLAY = "settings/display"
    const val SETTINGS_FONT_SIZE = "settings/display/font_size"
    const val SETTINGS_CONNECTION = "settings/connection"
    const val SETTINGS_READER = "settings/reader"
    const val TRANSLATION_SETTINGS = "settings/translation"
    const val QUICK_TRANSLATE_SETTINGS = "settings/quick_translate"
    const val TTS_SETTINGS = "settings/tts"
    const val TTS_WORD_REPLACEMENT = "settings/tts/replacement"
    const val ADMIN_VIP_MANAGEMENT = "settings/admin_vip_management"
 
    // Download
    const val DOWNLOAD = "download"
    const val HISTORY = "history"

    // Forum + Local reader
    const val LOCAL_READER = "local_reader?uriStr={uriStr}&mimeType={mimeType}"
    fun localReader(uriStr: String, mimeType: String? = null) =
        "local_reader?uriStr=${URLEncoder.encode(uriStr, "UTF-8")}&mimeType=${URLEncoder.encode(mimeType ?: "", "UTF-8")}"

    fun browse(extensionId: String) = "browse/$extensionId"
    fun search(extensionId: String, query: String? = null) =
        if (query != null) "search/$extensionId?query=${URLEncoder.encode(query, "UTF-8")}" else "search/$extensionId"
    fun extensionSettings(extensionId: String) = "extensions/settings/$extensionId"
    fun detail(extensionId: String, novelUrl: String, isOffline: Boolean = false) =
        "detail/$extensionId?novelUrl=${URLEncoder.encode(novelUrl, "UTF-8")}&isOffline=$isOffline"
    fun listNovel(extensionId: String, title: String, script: String, input: String) =
        "list_novel/$extensionId?title=${URLEncoder.encode(title, "UTF-8")}&script=${URLEncoder.encode(script, "UTF-8")}&input=${URLEncoder.encode(input, "UTF-8")}"
    fun textReader(extensionId: String, novelUrl: String, chapterUrl: String, isOffline: Boolean = false) =
        "reader/text/$extensionId?novelUrl=${URLEncoder.encode(novelUrl, "UTF-8")}&chapterUrl=${URLEncoder.encode(chapterUrl, "UTF-8")}&isOffline=$isOffline"
    fun comicReader(extensionId: String, novelUrl: String, chapterUrl: String, isOffline: Boolean = false) =
        "reader/comic/$extensionId?novelUrl=${URLEncoder.encode(novelUrl, "UTF-8")}&chapterUrl=${URLEncoder.encode(chapterUrl, "UTF-8")}&isOffline=$isOffline"
    fun videoReader(extensionId: String, novelUrl: String, chapterUrl: String, isOffline: Boolean = false) =
        "reader/video/$extensionId?novelUrl=${URLEncoder.encode(novelUrl, "UTF-8")}&chapterUrl=${URLEncoder.encode(chapterUrl, "UTF-8")}&isOffline=$isOffline"
    fun webview(url: String, extensionId: String? = null): String {
        val base = "webview?url=${URLEncoder.encode(url, "UTF-8")}"
        return if (extensionId != null) "$base&extensionId=${extensionId}" else base
    }
}

@Composable
fun NovelReaderNavHost(
    navController: NavHostController = rememberNavController(),
    initialUriStr: String = "",
    initialMimeType: String? = null,
) {
    // Navigate to local reader if opened from file manager
    androidx.compose.runtime.LaunchedEffect(initialUriStr) {
        if (initialUriStr.isNotBlank()) {
            navController.navigate(Routes.localReader(initialUriStr, initialMimeType))
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.MAIN,
        enterTransition = {
            slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(200))
        },
        exitTransition = {
            // Slide trái nhẹ + fade — KHÔNG scale để tránh tụt FPS
            slideOutHorizontally(
                targetOffsetX = { -it / 4 },
                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
            ) + fadeOut(animationSpec = tween(200))
        },
        popEnterTransition = {
            // Màn hình quay lại: slide từ trái nhẹ
            slideInHorizontally(
                initialOffsetX = { -it / 4 },
                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(200))
        },
        popExitTransition = {
            // Vuốt quay lại: spring physics = mượt tự nhiên, không giật
            slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) + fadeOut(animationSpec = tween(180))
        }
    ) {
        composable(Routes.MAIN) {
            MainScreen(navController = navController)
        }

        composable(Routes.EXTENSION_STORE) {
            ExtensionStoreScreen(navController = navController)
        }

        composable(
            Routes.EXTENSION_SETTINGS,
            arguments = listOf(navArgument("extensionId") { type = NavType.StringType })
        ) { backStackEntry ->
            val extensionId = backStackEntry.arguments?.getString("extensionId") ?: return@composable
            ExtensionSettingsScreen(extensionId = extensionId, navController = navController)
        }

        composable(
            Routes.BROWSE,
            arguments = listOf(navArgument("extensionId") { type = NavType.StringType })
        ) { backStackEntry ->
            val extensionId = backStackEntry.arguments?.getString("extensionId") ?: return@composable
            BrowseScreen(extensionId = extensionId, navController = navController)
        }

        composable(
            Routes.SEARCH,
            arguments = listOf(
                navArgument("extensionId") { type = NavType.StringType },
                navArgument("query") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val extensionId = backStackEntry.arguments?.getString("extensionId") ?: return@composable
            val query = backStackEntry.arguments?.getString("query")
            SearchScreen(extensionId = extensionId, initialQuery = query, navController = navController)
        }

        composable(
            Routes.DETAIL,
            arguments = listOf(
                navArgument("extensionId") { type = NavType.StringType },
                navArgument("novelUrl") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("isOffline") {
                    type = NavType.BoolType
                    defaultValue = false
                }
            ),
            deepLinks = listOf(
                androidx.navigation.navDeepLink {
                    uriPattern = "novelreader://detail/{extensionId}?novelUrl={novelUrl}"
                }
            ),
            enterTransition = {
                slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(300))
            },
            exitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { -it / 3 },
                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                ) + fadeOut(animationSpec = tween(300))
            },
            popEnterTransition = {
                slideInHorizontally(
                    initialOffsetX = { -it / 3 },
                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(300))
            },
            popExitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                ) + fadeOut(animationSpec = tween(300))
            }
        ) { backStackEntry ->
            val extensionId = backStackEntry.arguments?.getString("extensionId") ?: return@composable
            val novelUrl = backStackEntry.arguments?.getString("novelUrl") ?: ""
            val isOffline = backStackEntry.arguments?.getBoolean("isOffline") ?: false
            NovelDetailScreen(extensionId = extensionId, novelUrl = novelUrl, isOffline = isOffline, navController = navController)
        }

        composable(
            Routes.LIST_NOVEL,
            arguments = listOf(
                navArgument("extensionId") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType; defaultValue = "" },
                navArgument("script") { type = NavType.StringType; defaultValue = "" },
                navArgument("input") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            val extensionId = backStackEntry.arguments?.getString("extensionId") ?: return@composable
            val title = backStackEntry.arguments?.getString("title") ?: ""
            val script = backStackEntry.arguments?.getString("script") ?: ""
            val input = backStackEntry.arguments?.getString("input") ?: ""
            com.nam.novelreader.feature.listnovel.ListNovelScreen(
                extensionId = extensionId, 
                title = title, 
                script = script, 
                input = input, 
                navController = navController
            )
        }

        composable(
            Routes.TEXT_READER,
            arguments = listOf(
                navArgument("extensionId") { type = NavType.StringType },
                navArgument("novelUrl") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("chapterUrl") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("isOffline") {
                    type = NavType.BoolType
                    defaultValue = false
                }
            ),
            enterTransition = {
                slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(300))
            },
            exitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { -it / 3 },
                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                ) + fadeOut(animationSpec = tween(300))
            },
            popEnterTransition = {
                slideInHorizontally(
                    initialOffsetX = { -it / 3 },
                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(300))
            },
            popExitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                ) + fadeOut(animationSpec = tween(300))
            }
        ) { backStackEntry ->
            val extensionId = backStackEntry.arguments?.getString("extensionId") ?: return@composable
            val novelUrl = backStackEntry.arguments?.getString("novelUrl") ?: ""
            val chapterUrl = backStackEntry.arguments?.getString("chapterUrl") ?: ""
            val isOffline = backStackEntry.arguments?.getBoolean("isOffline") ?: false
            TextReaderScreen(
                extensionId = extensionId,
                novelUrl = novelUrl,
                chapterUrl = chapterUrl,
                isOffline = isOffline,
                navController = navController,
            )
        }

        composable(
            Routes.COMIC_READER,
            arguments = listOf(
                navArgument("extensionId") { type = NavType.StringType },
                navArgument("novelUrl") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("chapterUrl") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("isOffline") {
                    type = NavType.BoolType
                    defaultValue = false
                }
            )
        ) { backStackEntry ->
            val extensionId = backStackEntry.arguments?.getString("extensionId") ?: return@composable
            val novelUrl = backStackEntry.arguments?.getString("novelUrl") ?: ""
            val chapterUrl = backStackEntry.arguments?.getString("chapterUrl") ?: ""
            val isOffline = backStackEntry.arguments?.getBoolean("isOffline") ?: false
            ComicReaderScreen(
                extensionId = extensionId,
                novelUrl = novelUrl,
                chapterUrl = chapterUrl,
                isOffline = isOffline,
                navController = navController,
            )
        }

        composable(
            Routes.VIDEO_READER,
            arguments = listOf(
                navArgument("extensionId") { type = NavType.StringType },
                navArgument("novelUrl") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("chapterUrl") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("isOffline") {
                    type = NavType.BoolType
                    defaultValue = false
                }
            )
        ) { backStackEntry ->
            val extensionId = backStackEntry.arguments?.getString("extensionId") ?: return@composable
            val novelUrl = backStackEntry.arguments?.getString("novelUrl") ?: ""
            val chapterUrl = backStackEntry.arguments?.getString("chapterUrl") ?: ""
            val isOffline = backStackEntry.arguments?.getBoolean("isOffline") ?: false
            VideoReaderScreen(
                extensionId = extensionId,
                novelUrl = novelUrl,
                chapterUrl = chapterUrl,
                isOffline = isOffline,
                navController = navController,
            )
        }

        composable(
            Routes.WEBVIEW,
            arguments = listOf(
                navArgument("url") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("extensionId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val url = backStackEntry.arguments?.getString("url") ?: ""
            val extensionId = backStackEntry.arguments?.getString("extensionId")
            WebViewScreen(url = url, extensionId = extensionId, navController = navController)
        }

        // Settings sub-screens
        composable(Routes.SETTINGS_DISPLAY) {
            SettingsDisplayScreen(navController = navController)
        }
        composable(Routes.SETTINGS_FONT_SIZE) {
            SettingsFontSizeScreen(navController = navController)
        }
        composable(Routes.SETTINGS_CONNECTION) {
            SettingsConnectionScreen(navController = navController)
        }
        composable(Routes.SETTINGS_READER) {
            SettingsReaderScreen(navController = navController)
        }

        // Download
        composable(Routes.DOWNLOAD) {
            DownloadScreen(navController = navController)
        }

        // History
        composable(Routes.HISTORY) {
            HistoryScreen(navController = navController)
        }

        // Local file reader — mở EPUB/TXT/CBZ từ file system
        composable(
            Routes.LOCAL_READER,
            arguments = listOf(
                navArgument("uriStr") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("mimeType") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val uriStr = backStackEntry.arguments?.getString("uriStr") ?: ""
            val mimeType = backStackEntry.arguments?.getString("mimeType")?.takeIf { it.isNotBlank() }
            if (uriStr.isNotBlank()) {
                val uri = android.net.Uri.parse(java.net.URLDecoder.decode(uriStr, "UTF-8"))
                LocalFileReaderScreen(
                    uri = uri,
                    mimeType = mimeType,
                    onBack = { navController.popBackStack() }
                )
            }
        }

        // Translation settings
        composable(Routes.TRANSLATION_SETTINGS) {
            TranslationSettingsScreen(navController = navController)
        }
        composable(Routes.QUICK_TRANSLATE_SETTINGS) {
            QuickTranslateSettingsScreen(navController = navController)
        }
        composable(Routes.TTS_SETTINGS) {
            com.nam.novelreader.feature.settings.TtsSettingsScreen(navController = navController)
        }
        composable(Routes.TTS_WORD_REPLACEMENT) {
            com.nam.novelreader.feature.settings.TtsWordReplacementScreen(navController = navController)
        }
        composable(Routes.ADMIN_VIP_MANAGEMENT) {
            com.nam.novelreader.feature.settings.AdminVipManagementScreen(navController = navController)
        }
    }
}
