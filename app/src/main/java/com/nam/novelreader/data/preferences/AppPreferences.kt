package com.nam.novelreader.data.preferences

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AppPreferences — trung tâm quản lý cài đặt ứng dụng.
 *
 * Tất cả cài đặt lưu vào SharedPreferences "novel_reader_prefs".
 * Mỗi nhóm có prefix riêng để dễ quản lý:
 * - display_* : Hiển thị (theme, font, AMOLED...)
 * - conn_*    : Kết nối (protocol, DNS, threads, delay...)
 * - reader_*  : Đọc truyện (scroll mode, page turn, screen on...)
 * - download_*: Tải truyện
 * - notif_*   : Thông báo
 * - ext_*     : Extension-scoped (per extension)
 */
@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext val context: Context
) {
    val prefs: SharedPreferences =
        context.getSharedPreferences("novel_reader_prefs", Context.MODE_PRIVATE)

    // ========== DISPLAY ==========

    /** Chế độ tối: "system" | "light" | "dark" */
    var darkMode: String
        get() = prefs.getString("display_dark_mode", "system") ?: "system"
        set(value) = prefs.edit().putString("display_dark_mode", value).apply()

    /** AMOLED mode — nền đen tuyệt đối */
    var amoledMode: Boolean
        get() = prefs.getBoolean("display_amoled_mode", false)
        set(value) = prefs.edit().putBoolean("display_amoled_mode", value).apply()

    /** Dynamic Color (Material You) */
    var dynamicColor: Boolean
        get() = prefs.getBoolean("display_dynamic_color", false)
        set(value) = prefs.edit().putBoolean("display_dynamic_color", value).apply()

    /** Theme color hex (khi không dùng dynamic) */
    var themeColorHex: String
        get() = prefs.getString("display_theme_color", "#D4A574") ?: "#D4A574"
        set(value) = prefs.edit().putString("display_theme_color", value).apply()

    var settingsItemBgColorHex: String
        get() = prefs.getString("display_settings_bg_color", "") ?: ""
        set(value) = prefs.edit().putString("display_settings_bg_color", value).apply()

    var settingsItemTextColorHex: String
        get() = prefs.getString("display_settings_text_color", "") ?: ""
        set(value) = prefs.edit().putString("display_settings_text_color", value).apply()

    /** E-ink mode */
    var einkMode: Boolean
        get() = prefs.getBoolean("display_eink_mode", false)
        set(value) = prefs.edit().putBoolean("display_eink_mode", value).apply()

    /** Font scale (0.8 → 1.5) */
    var fontScale: Float
        get() = prefs.getFloat("display_font_scale", 1.0f)
        set(value) = prefs.edit().putFloat("display_font_scale", value).apply()

    /** Density scale (0.8 → 1.5) */
    var densityScale: Float
        get() = prefs.getFloat("display_density_scale", 1.0f)
        set(value) = prefs.edit().putFloat("display_density_scale", value).apply()

    /** Font family ("system", "inter", "nunito", "literata") */
    var fontFamily: String
        get() = prefs.getString("display_font_family", "system") ?: "system"
        set(value) = prefs.edit().putString("display_font_family", value).apply()

    /** UI background style ("default", "no_bg", etc.) */
    var displayBackground: String
        get() = prefs.getString("display_background", "default") ?: "default"
        set(value) = prefs.edit().putString("display_background", value).apply()

    /** Liquid Glass Effect */
    var liquidGlass: Boolean
        get() = prefs.getBoolean("display_liquid_glass", false)
        set(value) = prefs.edit().putBoolean("display_liquid_glass", value).apply()

    // BROWSE LAYOUT SETTINGS
    var browseGridColumns: Int
        get() = prefs.getInt("display_browse_grid_columns", 3)
        set(value) = prefs.edit().putInt("display_browse_grid_columns", value).apply()

    var browseTitleFontSize: Float
        get() = prefs.getFloat("display_browse_title_font_size", 12f)
        set(value) = prefs.edit().putFloat("display_browse_title_font_size", value).apply()

    var browseTitleMaxLines: Int
        get() = prefs.getInt("display_browse_title_max_lines", 2)
        set(value) = prefs.edit().putInt("display_browse_title_max_lines", value).apply()

    var browseTitleAlign: String
        get() = prefs.getString("display_browse_title_align", "start") ?: "start"
        set(value) = prefs.edit().putString("display_browse_title_align", value).apply()

    var browseCoverCornerRadius: Float
        get() = prefs.getFloat("display_browse_cover_corner_radius", 6f)
        set(value) = prefs.edit().putFloat("display_browse_cover_corner_radius", value).apply()

    // ========== CONNECTION ==========

    /** Giao thức kết nối: "http1" | "http2" | "cronet" */
    var connectionProtocol: String
        get() = prefs.getString("conn_protocol", "cronet") ?: "cronet"
        set(value) = prefs.edit().putString("conn_protocol", value).apply()

    /** Số luồng song song mặc định (global, không per-extension) */
    var connectionThreads: Int
        get() = prefs.getInt("conn_threads", 2)
        set(value) = prefs.edit().putInt("conn_threads", value).apply()

    /** Giãn cách kết nối mặc định (ms) */
    var connectionDelay: Int
        get() = prefs.getInt("conn_delay", 100)
        set(value) = prefs.edit().putInt("conn_delay", value).apply()

    /** Số lần retry khi lỗi */
    var connectionRetry: Int
        get() = prefs.getInt("conn_retry", 2)
        set(value) = prefs.edit().putInt("conn_retry", value).apply()

    /** DNS: "system" | "google" | "cloudflare" | custom IP */
    var connectionDns: String
        get() = prefs.getString("conn_dns", "cloudflare") ?: "cloudflare"
        set(value) = prefs.edit().putString("conn_dns", value).apply()

    /** Bỏ qua lỗi SSL */
    var connectionIgnoreSsl: Boolean
        get() = prefs.getBoolean("conn_ignore_ssl", true)
        set(value) = prefs.edit().putBoolean("conn_ignore_ssl", value).apply()

    /** Custom User Agent string */
    var connectionUserAgent: String
        get() = prefs.getString("conn_user_agent", "") ?: ""
        set(value) = prefs.edit().putString("conn_user_agent", value).apply()

    /** Thời gian chờ kết nối (giây) */
    var connectionTimeout: Int
        get() = prefs.getInt("conn_timeout", 30)
        set(value) = prefs.edit().putInt("conn_timeout", value).apply()

    /** URL override map (JSON string: {"old.com": "new.com"}) */
    var connectionUrlOverride: String
        get() = prefs.getString("conn_url_override", "") ?: ""
        set(value) = prefs.edit().putString("conn_url_override", value).apply()

    /** Hiệu suất kết nối: "balanced" | "performance" | "battery" */
    var connectionPerformance: String
        get() = prefs.getString("conn_performance", "balanced") ?: "balanced"
        set(value) = prefs.edit().putString("conn_performance", value).apply()

    // ========== PROXY & TẢI SIÊU TỐC ==========

    /** Bật/Tắt Proxy */
    var proxyEnabled: Boolean
        get() = prefs.getBoolean("proxy_enabled", false)
        set(value) = prefs.edit().putBoolean("proxy_enabled", value).apply()

    /** Proxy Host (IP) */
    var proxyHost: String
        get() = prefs.getString("proxy_host", "") ?: ""
        set(value) = prefs.edit().putString("proxy_host", value).apply()

    /** Proxy Port */
    var proxyPort: Int
        get() = prefs.getInt("proxy_port", 0)
        set(value) = prefs.edit().putInt("proxy_port", value).apply()

    /** Link API Xoay IP */
    var proxyRotateApi: String
        get() = prefs.getString("proxy_rotate_api", "") ?: ""
        set(value) = prefs.edit().putString("proxy_rotate_api", value).apply()

    /** Bỏ qua giới hạn an toàn (số luồng, thời gian chờ) để tải siêu tốc */
    var proxyBypassThreadLimit: Boolean
        get() = prefs.getBoolean("proxy_bypass_thread_limit", false)
        set(value) = prefs.edit().putBoolean("proxy_bypass_thread_limit", value).apply()


    // ========== READER ==========

    /** Luôn giữ màn hình sáng khi đọc */
    var readerScreenOn: Boolean
        get() = prefs.getBoolean("reader_screen_on", true)
        set(value) = prefs.edit().putBoolean("reader_screen_on", value).apply()

    /** Tự động mở chương đang đọc dở */
    var readerAutoOpenLastRead: Boolean
        get() = prefs.getBoolean("reader_auto_open_last_read", true)
        set(value) = prefs.edit().putBoolean("reader_auto_open_last_read", value).apply()

    /** Tự động tải mục lục */
    var readerAutoToc: Boolean
        get() = prefs.getBoolean("reader_auto_toc", true)
        set(value) = prefs.edit().putBoolean("reader_auto_toc", value).apply()

    /** Lưu lịch sử đọc */
    var readerSaveHistory: Boolean
        get() = prefs.getBoolean("reader_save_history", true)
        set(value) = prefs.edit().putBoolean("reader_save_history", value).apply()

    /** Chế độ cuộn: true = cuộn liên tục, false = lật trang */
    var readerScrollMode: Boolean
        get() = prefs.getBoolean("reader_scroll_mode", true)
        set(value) = prefs.edit().putBoolean("reader_scroll_mode", value).apply()

    /** Hiệu ứng lật trang: "none" | "slide" | "curl" */
    var readerTurnPageAnim: String
        get() = prefs.getString("reader_turn_page_anim", "slide") ?: "slide"
        set(value) = prefs.edit().putString("reader_turn_page_anim", value).apply()

    /** Lật trang bằng phím âm lượng */
    var readerVolumeTurnPage: Boolean
        get() = prefs.getBoolean("reader_volume_turn_page", false)
        set(value) = prefs.edit().putBoolean("reader_volume_turn_page", value).apply()

    /** Menu ngữ cảnh khi chọn text */
    var readerContextMenu: Boolean
        get() = prefs.getBoolean("reader_context_menu", true)
        set(value) = prefs.edit().putBoolean("reader_context_menu", value).apply()

    /** Tự động dịch QT */
    var readerAutoTranslateQt: Boolean
        get() = prefs.getBoolean("reader_auto_translate_qt", false)
        set(value) = prefs.edit().putBoolean("reader_auto_translate_qt", value).apply()

    /** Hướng màn hình: "auto" | "portrait" | "landscape" */
    var readerScreenOrientation: String
        get() = prefs.getString("reader_screen_orientation", "auto") ?: "auto"
        set(value) = prefs.edit().putString("reader_screen_orientation", value).apply()

    /** Hiển thị tiến trình đọc */
    var readerShowProgress: Boolean
        get() = prefs.getBoolean("reader_show_progress", true)
        set(value) = prefs.edit().putBoolean("reader_show_progress", value).apply()

    /** Cỡ chữ đọc truyện (sp) */
    var readerFontSize: Float
        get() = prefs.getFloat("reader_font_size", 18f)
        set(value) = prefs.edit().putFloat("reader_font_size", value).apply()

    /** Giãn cách dòng (multiplier) */
    var readerLineSpacing: Float
        get() = prefs.getFloat("reader_line_spacing", 1.6f)
        set(value) = prefs.edit().putFloat("reader_line_spacing", value).apply()

    /** Giãn cách chữ (sp) */
    var readerLetterSpacing: Float
        get() = prefs.getFloat("reader_letter_spacing", 0f)
        set(value) = prefs.edit().putFloat("reader_letter_spacing", value).apply()

    /** Giãn cách đoạn (dp) */
    var readerParagraphSpacing: Float
        get() = prefs.getFloat("reader_paragraph_spacing", 12f)
        set(value) = prefs.edit().putFloat("reader_paragraph_spacing", value).apply()

    /** Thụt đầu dòng (dp) */
    var readerTextIndent: Float
        get() = prefs.getFloat("reader_text_indent", 0f)
        set(value) = prefs.edit().putFloat("reader_text_indent", value).apply()

    /** Canh lề: "left" | "center" | "right" | "justify" */
    var readerTextAlign: String
        get() = prefs.getString("reader_text_align", "justify") ?: "justify"
        set(value) = prefs.edit().putString("reader_text_align", value).apply()

    /** Margin trái (dp) */
    var readerMarginLeft: Int
        get() = prefs.getInt("reader_margin_left", 16)
        set(value) = prefs.edit().putInt("reader_margin_left", value).apply()

    /** Margin phải (dp) */
    var readerMarginRight: Int
        get() = prefs.getInt("reader_margin_right", 16)
        set(value) = prefs.edit().putInt("reader_margin_right", value).apply()

    /** Margin trên (dp) */
    var readerMarginTop: Int
        get() = prefs.getInt("reader_margin_top", 24)
        set(value) = prefs.edit().putInt("reader_margin_top", value).apply()

    /** Margin dưới (dp) */
    var readerMarginBottom: Int
        get() = prefs.getInt("reader_margin_bottom", 24)
        set(value) = prefs.edit().putInt("reader_margin_bottom", value).apply()

    /** Màu nền đọc (hex) */
    var readerBackgroundColor: String
        get() = prefs.getString("reader_bg_color", "#12100E") ?: "#12100E"
        set(value) = prefs.edit().putString("reader_bg_color", value).apply()

    /** Màu chữ đọc (hex) */
    var readerTextColor: String
        get() = prefs.getString("reader_text_color", "#E0D5C8") ?: "#E0D5C8"
        set(value) = prefs.edit().putString("reader_text_color", value).apply()

    // ========== DOWNLOAD ==========

    /** Tự động thêm vào tủ sách khi tải */
    var downloadAddToShelf: Boolean
        get() = prefs.getBoolean("download_add_to_shelf", true)
        set(value) = prefs.edit().putBoolean("download_add_to_shelf", value).apply()

    // ========== NOTIFICATION ==========

    /** Thông báo chương mới */
    var notifNewChapter: Boolean
        get() = prefs.getBoolean("notif_new_chapter", true)
        set(value) = prefs.edit().putBoolean("notif_new_chapter", value).apply()

    /** Khoảng thời gian kiểm tra (phút) */
    var notifCheckInterval: Int
        get() = prefs.getInt("notif_check_interval", 60)
        set(value) = prefs.edit().putInt("notif_check_interval", value).apply()

    // ========== SUPABASE ==========

    /** Supabase Project URL */
    var supabaseUrl: String
        get() = prefs.getString("supabase_url", "https://arsnnqwcqzaqhndxemgz.supabase.co/") ?: "https://arsnnqwcqzaqhndxemgz.supabase.co/"
        set(value) = prefs.edit().putString("supabase_url", value).apply()

    /** Supabase Anon Key */
    var supabaseAnonKey: String
        get() = prefs.getString("supabase_anon_key", "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImFyc25ucXdjcXphcWhuZHhlbWd6Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzgzNDE3NTksImV4cCI6MjA5MzkxNzc1OX0.dvPw5jgvPGmDqqoHF5la_d7AAwxH5PhVLhVSP5ayWA8") ?: "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImFyc25ucXdjcXphcWhuZHhlbWd6Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzgzNDE3NTksImV4cCI6MjA5MzkxNzc1OX0.dvPw5jgvPGmDqqoHF5la_d7AAwxH5PhVLhVSP5ayWA8"
        set(value) = prefs.edit().putString("supabase_anon_key", value).apply()

    /** Supabase Service Role Key (Admin) */
    var supabaseServiceRoleKey: String
        get() = prefs.getString("supabase_service_role_key", "") ?: ""
        set(value) = prefs.edit().putString("supabase_service_role_key", value).apply()

    /** Access Token */
    var supabaseAccessToken: String
        get() = prefs.getString("supabase_access_token", "") ?: ""
        set(value) = prefs.edit().putString("supabase_access_token", value).apply()

    /** Refresh Token */
    var supabaseRefreshToken: String
        get() = prefs.getString("supabase_refresh_token", "") ?: ""
        set(value) = prefs.edit().putString("supabase_refresh_token", value).apply()

    /** User ID */
    var supabaseUserId: String
        get() = prefs.getString("supabase_user_id", "") ?: ""
        set(value) = prefs.edit().putString("supabase_user_id", value).apply()

    /** User Email */
    var supabaseUserEmail: String
        get() = prefs.getString("supabase_user_email", "") ?: ""
        set(value) = prefs.edit().putString("supabase_user_email", value).apply()

    /** Trạng thái đã đăng nhập Supabase */
    var supabaseIsLoggedIn: Boolean
        get() = prefs.getBoolean("supabase_is_logged_in", false)
        set(value) = prefs.edit().putBoolean("supabase_is_logged_in", value).apply()

    /** Đường dẫn ảnh đại diện cục bộ */
    var userAvatarPath: String
        get() = prefs.getString("user_avatar_path", "") ?: ""
        set(value) = prefs.edit().putString("user_avatar_path", value).apply()

    // ========== DEVELOPER ==========

    /** Chế độ nhà phát triển */
    var developerMode: Boolean
        get() = prefs.getBoolean("developer_mode", false)
        set(value) = prefs.edit().putBoolean("developer_mode", value).apply()

    // ========== EXTENSION-SCOPED HELPERS ==========

    private val extremelySafeExtensions = setOf(
        "%C4%90%E1%BB%99c%20gi%E1%BA%A3%20s%E1%BB%91%201",
        "18mh",
        "8ternal",
        "8xsk",
        "9y02",
        "aahhss",
        "acqq",
        "akaytruyen",
        "anime-hay",
        "animevsub",
        "baotangtruyen",
        "baozimh",
        "best-girl-sexy",
        "bilibili.tv",
        "blvietsub",
        "book18",
        "Buondua",
        "cartoon18",
        "cbunu-comic",
        "chickenmanga",
        "cmanga",
        "comicland",
        "conduongbachu",
        "cool18-novel",
        "cosplaytele",
        "crxs",
        "crxs.me",
        "cuutruyen",
        "czbooks-repo-duong-den",
        "dailymotion",
        "dam-co-nuong",
        "danbooru",
        "daomeoden",
        "destin-team",
        "doc-truyen-14",
        "doc-truyen-tranh-bl",
        "doujins",
        "e-hentai",
        "everia",
        "foamgirl",
        "foxaholic-18",
        "freereels",
        "goc-truyen-tranh",
        "h528",
        "hac-am-chi-cac",
        "haitang16",
        "hentai-image",
        "hentai-img",
        "hentai2read",
        "hentaic",
        "hentaicube",
        "hentaicube-18",
        "hentaifox-com",
        "hentaiporns",
        "hentairead",
        "hentairules",
        "hentaivietsub-com",
        "hentaizbot",
        "hentaizhot",
        "hhhtq",
        "hhkungfu",
        "hhtq-vietsub",
        "hhtqhay-vip",
        "hiperdex",
        "hong-tra-team",
        "ihentai",
        "iq.com",
        "iqiyi",
        "jelly-comics",
        "julycomic",
        "julycomics",
        "khomanhwa",
        "kissaway",
        "kisslove-raw",
        "leesin-comic",
        "loppytoon",
        "loppytoon-18",
        "luottruyen",
        "manga-raw",
        "manga-read",
        "mangadistrict",
        "mangadm",
        "mangafire",
        "mangapuma",
        "mangaread",
        "mangayeh",
        "manhua-gui",
        "manhuagui",
        "manhuarock",
        "manhuavn",
        "manhuavn-free-chap",
        "manhwabuddy",
        "manhwahentai",
        "me-truyen-chu-vn",
        "mgtv",
        "mimimoe",
        "misskon",
        "moetruyen",
        "motchill",
        "my-reading-manga",
        "nettruyenviet",
        "nguonc",
        "nhentai-one",
        "omegascans",
        "otakusic",
        "phim4k",
        "phimfun",
        "photos18",
        "po18",
        "po18gv",
        "po18sm",
        "qmanga",
        "rophim",
        "say-hentai",
        "sayhentai",
        "sayhentai-18",
        "shibashuwu-18",
        "shinigami-ln-team",
        "showbox",
        "sinodan",
        "skymanga",
        "thien-thai-truyen",
        "Ti%E1%BB%83u%20B%E1%BA%A1ch%20TV",
        "tieu-bach-tv",
        "tiktok",
        "tiktok-tts",
        "tranh18",
        "truyen-de-xuat",
        "truyen-qq",
        "truyen-tv",
        "truyencv-io",
        "truyendex",
        "truyengg",
        "truyenhay24h",
        "truyenqq",
        "truyenqq-com-vn",
        "truyenqqko",
        "truyentranhdammyy",
        "truyentv",
        "truyenvn",
        "tsumino",
        "tusachxinhxinh",
        "tuthienbao",
        "tvkh",
        "tvtruyen",
        "twitch",
        "twitch.tv",
        "Uaa",
        "umetruyen",
        "ung-ty-comics",
        "vi-hentai",
        "vihentai",
        "wanwansekai-18",
        "watchhentai-net",
        "webtoonscan",
        "wetv",
        "wnacg",
        "xinyushuwu",
        "xx-knix",
        "xyushu5",
        "yazhouse8",
        "yeu-anime",
        "youku",
        "youtube",
        "yurineko"
    )

    private val moderatelySafeExtensions = setOf(
        "18biquge",
        "200669-api-9x-btc",
        "20xs",
        "2shuquge",
        "520danmei",
        "52shuku",
        "69",
        "69shu",
        "69shu-repo-duong-den",
        "69shuba",
        "69shumi",
        "69yuedu",
        "80qishu",
        "abcbiquge",
        "app-zhihu-hoi-dap",
        "bach-ngoc-sach",
        "bachngocsach",
        "biquge",
        "biqugeabc",
        "biqugess",
        "biqugezz",
        "biququ",
        "biquxs",
        "czbooks",
        "damixs",
        "ddxs",
        "deqixs",
        "dmxs",
        "douyinxs",
        "ffxs8",
        "fuxsb",
        "hako",
        "hako-novel",
        "hetushu",
        "ijjxs",
        "ilwxs",
        "iqushuwang",
        "isach",
        "ixdzs",
        "ixdzs8",
        "ixunshu",
        "jpxs123",
        "kakuyomu",
        "kanshuwu",
        "lrxs",
        "lwxs",
        "mengyuanshucheng",
        "novelcookstw",
        "piaotian-app",
        "piaotian5",
        "qidian",
        "qidian-home",
        "qidian-vp",
        "qiuxiaoshuo",
        "quanben-repo-duong-den",
        "review-tuishujun",
        "shubl",
        "shuhaige",
        "shumilou",
        "shuqi-com",
        "sang-tac-viet-pro",
        "shushuwuxs",
        "soxscc",
        "ss-truyen",
        "sstruyen",
        "stv-app-vi",
        "stv-home",
        "syosetu",
        "tang-thu-vien-app",
        "tangthuvienAPP",
        "truyen-full",
        "truyenfull",
        "truyenyy",
        "trxs",
        "tushumi",
        "twfanti",
        "uukanshu",
        "uushuk",
        "uuxs",
        "uuxs-com-repo-duong-den",
        "vvbiquge",
        "weibo",
        "wfxs-tw",
        "wikidich",
        "wikidich-app",
        "wodeshucheng5",
        "x33xs4",
        "xbiquge",
        "xiaoshubao",
        "yushubo",
        "zhenhunxiaoshuo",
        "zhihu"
    )

    private fun isLikelyHeavyOrRateLimited(extId: String): Boolean {
        val id = extId.lowercase(java.util.Locale.ROOT)
        return id.contains("hentai") ||
               id.contains("manga") ||
               id.contains("comic") ||
               id.contains("anime") ||
               id.contains("phim") ||
               id.contains("movie") ||
               id.contains("video") ||
               id.contains("tv") ||
               id.contains("showbox") ||
               id.contains("18") ||
               id.contains("sex") ||
               id.contains("porn")
    }

    /** Lấy số luồng cho 1 extension cụ thể (fallback sang giá trị mặc định an toàn cho extension đó, hoặc global) */
    fun getExtParallelConnections(extId: String): Int {
        val key = "ext_parallel_connections_$extId"
        if (prefs.contains(key)) {
            return prefs.getInt(key, connectionThreads)
        }
        return when {
            extremelySafeExtensions.contains(extId) || isLikelyHeavyOrRateLimited(extId) -> 1
            moderatelySafeExtensions.contains(extId) -> 2
            else -> connectionThreads
        }
    }

    /** Lấy số luồng tải thực tế (bỏ qua giới hạn nếu bật chế độ proxy tải siêu tốc) */
    fun getEffectiveParallelConnections(extId: String): Int {
        if (proxyEnabled && proxyBypassThreadLimit) {
            return connectionThreads // Trả về tối đa theo mức kéo thanh trượt của người dùng (có thể tới 20)
        }
        return getExtParallelConnections(extId)
    }

    /** Lấy giãn cách kết nối cho 1 extension (fallback sang giá trị mặc định an toàn cho extension đó, hoặc global) */
    fun getExtConnectionInterval(extId: String): Int {
        val key = "ext_connection_interval_$extId"
        if (prefs.contains(key)) {
            return prefs.getInt(key, connectionDelay)
        }
        return when {
            extremelySafeExtensions.contains(extId) || isLikelyHeavyOrRateLimited(extId) -> 400 // Giảm xuống 400ms (vẫn đủ an toàn khi chạy tuần tự)
            moderatelySafeExtensions.contains(extId) -> 100 // Giảm xuống 100ms
            else -> connectionDelay // Trả về cài đặt trễ toàn cục của người dùng (mặc định 100ms)
        }
    }

    /** Lấy giãn cách kết nối thực tế (kể cả khi dùng proxy vẫn nên có 1 chút delay để tránh bị Cloudflare ban ngay lập tức) */
    fun getEffectiveConnectionInterval(extId: String): Int {
        if (proxyEnabled && proxyBypassThreadLimit) {
            // Vẫn dùng connectionDelay của người dùng (tối thiểu 50ms) để tránh bị rate-limit ngay lập tức
            return maxOf(50, connectionDelay)
        }
        return getExtConnectionInterval(extId)
    }

    /** Lưu số luồng tải cho 1 extension cụ thể */
    fun setExtParallelConnections(extId: String, value: Int) {
        prefs.edit().putInt("ext_parallel_connections_$extId", value).apply()
    }

    /** Lưu giãn cách kết nối cho 1 extension cụ thể */
    fun setExtConnectionInterval(extId: String, value: Int) {
        prefs.edit().putInt("ext_connection_interval_$extId", value).apply()
    }

    /** Lấy thời gian chờ kết nối cho 1 extension cụ thể (fallback sang global connectionTimeout) */
    fun getExtConnectionTimeout(extId: String): Int {
        val key = "ext_connection_timeout_$extId"
        return prefs.getInt(key, connectionTimeout)
    }

    /** Lưu thời gian chờ kết nối cho 1 extension cụ thể */
    fun setExtConnectionTimeout(extId: String, value: Int) {
        prefs.edit().putInt("ext_connection_timeout_$extId", value).apply()
    }


    /** Chế độ ẩn danh cho 1 extension */
    fun getExtIncognitoMode(extId: String): Boolean =
        prefs.getBoolean("ext_incognito_mode_$extId", false)

    /** Cookie cho 1 extension */
    fun getExtCookies(extId: String): String =
        prefs.getString("ext_cookies_$extId", "") ?: ""

    // ========== FOLLOWED NOVELS ==========

    /** Danh sách truyện theo dõi dưới dạng Set các chuỗi "extensionId|novelUrl|novelTitle" */
    var followedNovels: Set<String>
        get() = prefs.getStringSet("followed_novels", emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet("followed_novels", value).apply()

    fun isNovelFollowed(novelUrl: String): Boolean {
        return followedNovels.any { it.split("|").getOrNull(1) == novelUrl }
    }

    fun followNovel(extensionId: String, novelUrl: String, novelTitle: String) {
        val current = followedNovels.toMutableSet()
        current.removeAll { it.split("|").getOrNull(1) == novelUrl }
        current.add("$extensionId|$novelUrl|$novelTitle")
        followedNovels = current
    }

    fun unfollowNovel(novelUrl: String) {
        val current = followedNovels.toMutableSet()
        current.removeAll { it.split("|").getOrNull(1) == novelUrl }
        followedNovels = current
    }

    // ========== PUBLIC EXTENSIONS ==========

    /** Danh sách các tiện ích được Admin mở công khai (danh sách slug/id) */
    var publicExtensions: Set<String>
        get() = prefs.getStringSet("public_extensions", emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet("public_extensions", value).apply()

    // ========== TTS CONFIGURATION ==========

    var ttsSelectedEngine: String
        get() = prefs.getString("reader_tts_engine", "system") ?: "system"
        set(value) = prefs.edit().putString("reader_tts_engine", value).apply()

    var ttsSelectedVoice: String
        get() = prefs.getString("reader_tts_voice", "") ?: ""
        set(value) = prefs.edit().putString("reader_tts_voice", value).apply()

    var ttsAutoExpand: Boolean
        get() = prefs.getBoolean("reader_tts_auto_expand", true)
        set(value) = prefs.edit().putBoolean("reader_tts_auto_expand", value).apply()

    var ttsAudioFocus: Boolean
        get() = prefs.getBoolean("reader_tts_audio_focus", true)
        set(value) = prefs.edit().putBoolean("reader_tts_audio_focus", value).apply()

    var ttsStopOnExit: Boolean
        get() = prefs.getBoolean("reader_tts_stop_on_exit", false)
        set(value) = prefs.edit().putBoolean("reader_tts_stop_on_exit", value).apply()

    var ttsHeadphoneControl: Boolean
        get() = prefs.getBoolean("reader_tts_headphone_control", true)
        set(value) = prefs.edit().putBoolean("reader_tts_headphone_control", value).apply()

    var ttsWordReplacements: String
        get() = prefs.getString("reader_tts_word_replacements", "") ?: ""
        set(value) = prefs.edit().putString("reader_tts_word_replacements", value).apply()

    var ttsSplitType: String
        get() = prefs.getString("reader_tts_split_type", "Theo đoạn") ?: "Theo đoạn"
        set(value) = prefs.edit().putString("reader_tts_split_type", value).apply()

    var ttsMaxLength: Int
        get() = prefs.getInt("reader_tts_max_length", 4000)
        set(value) = prefs.edit().putInt("reader_tts_max_length", value).apply()

    var ttsSystemEngine: String
        get() = prefs.getString("reader_tts_system_engine", "com.google.android.tts") ?: "com.google.android.tts"
        set(value) = prefs.edit().putString("reader_tts_system_engine", value).apply()

    var ttsSystemLanguage: String
        get() = prefs.getString("reader_tts_system_language", "vi-VN") ?: "vi-VN"
        set(value) = prefs.edit().putString("reader_tts_system_language", value).apply()

    var ttsCustomLanguage: String
        get() = prefs.getString("reader_tts_custom_language", "Tiếng Việt") ?: "Tiếng Việt"
        set(value) = prefs.edit().putString("reader_tts_custom_language", value).apply()

    var ttsSpeed: Float
        get() = prefs.getFloat("reader_tts_speed", 1.0f)
        set(value) = prefs.edit().putFloat("reader_tts_speed", value).apply()

    var ttsPitch: Float
        get() = prefs.getFloat("reader_tts_pitch", 1.0f)
        set(value) = prefs.edit().putFloat("reader_tts_pitch", value).apply()

    // ========== VIP STATUS ==========

    /** Thời hạn VIP tài khoản dạng timestamp (ms) */
    var vipExpiryTimestamp: Long
        get() = prefs.getLong("vip_expiry_timestamp", 0L)
        set(value) = prefs.edit().putLong("vip_expiry_timestamp", value).apply()

    fun isVip(): Boolean = System.currentTimeMillis() < vipExpiryTimestamp
}
