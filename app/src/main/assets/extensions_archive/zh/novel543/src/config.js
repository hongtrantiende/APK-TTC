var BASE_URL = "https://www.novel543.com";
var HOST = "https://www.novel543.com";

// ============ Cloudflare Cookie System ============
// After first browser CF bypass, Android WebView stores cf_clearance cookie.
// We extract it and reuse for all fetch() calls — making them instant.
var _cfCookie = null;
var _cfUA = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";
var _cfReady = false;  // true after first successful bypass

var _BLOCK_ADS = [
    "holmesmind.com", "tamedia.com", "popin.cc",
    "pubfuture", "clickforceads", "googletagmanager",
    "google-analytics", "stat.gn01.top", "onead",
    "beacon.min.js", "cloudflareinsights"
];
var _BLOCK_HEAVY = [
    ".jpg", ".jpeg", ".png", ".webp", ".gif", ".svg",
    ".css", ".woff", ".woff2", ".ttf", ".eot"
];

// --- Cookie Storage (multiple backends for reliability) ---
function loadCookie() {
    if (_cfCookie) return _cfCookie;

    // 1. Try localCookie (shares with WebView cookie jar)
    try {
        var lc = localCookie.getCookie();
        if (lc && lc.length > 10) { _cfCookie = lc; return _cfCookie; }
    } catch(e) {}

    // 2. Try localStorage
    try {
        var ls = localStorage.getItem("n543_cf");
        if (ls && ls.length > 10) { _cfCookie = ls; return _cfCookie; }
    } catch(e) {}

    return null;
}

function storeCookie(cookie) {
    if (!cookie || cookie.length < 5) return;
    _cfCookie = cookie;
    _cfReady = true;
    try { localStorage.setItem("n543_cf", cookie); } catch(e) {}
    // Also store individual cookies in localCookie for WebView sharing
    try {
        var parts = cookie.split(";");
        for (var i = 0; i < parts.length; i++) {
            var p = parts[i].trim();
            if (p) localCookie.setCookie(p);
        }
    } catch(e) {}
}

function invalidateCookie() {
    _cfCookie = null;
    _cfReady = false;
    try { localStorage.removeItem("n543_cf"); } catch(e) {}
}

// --- Extract cookies from browser session ---
function extractCookiesFromBrowser(browser) {
    var cookie = "";

    // Method 1: callJs to get document.cookie
    try {
        browser.callJs("window._n543c = document.cookie;", 300);
        cookie = browser.getVariable("_n543c") || "";
    } catch(e) {}

    // Method 2: localCookie (has access to HttpOnly cookies)
    if (!cookie || cookie.length < 10) {
        try {
            cookie = localCookie.getCookie() || "";
        } catch(e) {}
    }

    // Method 3: try reading from response headers
    if (!cookie || cookie.length < 10) {
        try {
            // Some implementations expose cookies via request headers
            var res = browser.urls();
            if (res) cookie = String(res) || "";
        } catch(e) {}
    }

    if (cookie && cookie.length > 5) {
        storeCookie(cookie);
    }
}

// ============ WARMUP: Solve CF once, cache forever ============
function warmupCF() {
    if (_cfReady || loadCookie()) {
        _cfReady = true;
        return true;
    }

    Console.log("[novel543] Warming up Cloudflare bypass...");
    var browser = Engine.newBrowser();
    browser.setUserAgent(UserAgent.android());
    browser.block(_BLOCK_ADS.concat(_BLOCK_HEAVY));

    var doc = browser.launch(BASE_URL + "/", 15000);
    if (doc) {
        var t = doc.text();
        // If still on CF challenge, wait more
        if (t.length < 500 || t.indexOf("Just a moment") >= 0) {
            sleep(5000);
            doc = browser.html();
        }
        extractCookiesFromBrowser(browser);
    }
    browser.close();

    _cfReady = !!loadCookie();
    if (_cfReady) Console.log("[novel543] CF bypass OK, cookies cached!");
    return _cfReady;
}

// ============ FAST FETCH (with cached cookie) ~1-2s ============
function fetchFast(url) {
    var cookie = loadCookie();
    if (!cookie) return null;
    try {
        var res = fetch(url, {
            headers: {
                "User-Agent": _cfUA,
                "Accept": "text/html",
                "Accept-Language": "zh-TW,zh;q=0.9",
                "Cookie": cookie
            }
        });
        if (res && res.ok) {
            var doc = res.html();
            if (doc) {
                var t = doc.text();
                if (t.length > 500 && t.indexOf("Just a moment") < 0) return doc;
            }
        }
    } catch(e) {}
    // Cookie expired
    invalidateCookie();
    return null;
}

// ============ BROWSER FETCH (CF bypass) ~10-15s ============
function fetchBrowserCF(url, timeout) {
    var t = timeout !== undefined ? timeout : 15000;
    var browser = Engine.newBrowser();
    browser.setUserAgent(UserAgent.android());
    browser.block(_BLOCK_ADS.concat(_BLOCK_HEAVY));

    var doc = browser.launch(url, t);
    if (doc) extractCookiesFromBrowser(browser);
    browser.close();
    return doc;
}

// ============ SMART FETCH: fast → warmup → browser ============
function fetchCF(url) {
    // 1. Instant: cached cookie fetch
    var doc = fetchFast(url);
    if (doc) return doc;

    // 2. Warmup if needed (solves CF once)
    if (!_cfReady) warmupCF();

    // 3. Retry fast fetch after warmup
    doc = fetchFast(url);
    if (doc) return doc;

    // 4. Last resort: browser
    return fetchBrowserCF(url);
}

// Light version for text-only pages (toc, chap, comments)
function fetchCFLight(url) {
    return fetchCF(url);  // Same logic, blocking already applied
}

// ============ Helpers ============
function selFirst(el, css) {
    var r = el.select(css);
    return r.size() > 0 ? r.get(0) : null;
}

function resolveUrl(url) {
    if (!url) return "";
    if (url.indexOf("http") === 0) return url.replace(/\/$/, "");
    if (url.charAt(0) !== "/") url = "/" + url;
    return BASE_URL + url.replace(/\/$/, "");
}

// ============ Parse novel list ============
function parseList(doc) {
    var result = [];
    var seen = {};

    var mediaItems = doc.select("li.media");
    for (var i = 0; i < mediaItems.size(); i++) {
        var item = mediaItems.get(i);
        var titleLink = selFirst(item, ".media-content h3 a");
        if (!titleLink) continue;
        var href = titleLink.attr("href") || "";
        if (!href || seen[href]) continue;
        var name = titleLink.text().trim();
        if (!name || name.length < 2) continue;
        seen[href] = true;

        var imgEl = selFirst(item, ".media-left img");
        var cover = imgEl ? (imgEl.attr("src") || imgEl.attr("data-src") || "") : "";
        var descEl = selFirst(item, "p.desc");
        var desc = descEl ? descEl.text().trim() : "";
        var authEl = selFirst(item, "span.author");
        var author = authEl ? authEl.text().trim() : "";

        result.push({
            name: name, link: resolveUrl(href), host: HOST, cover: cover,
            description: author ? author + " | " + desc : desc
        });
    }

    if (result.length === 0) {
        var gridItems = doc.select("ul.list li");
        for (var j = 0; j < gridItems.size(); j++) {
            var li = gridItems.get(j);
            if (li.attr("class") && li.attr("class").indexOf("media") >= 0) continue;
            var link = selFirst(li, "h3 a");
            if (!link) continue;
            var h2 = link.attr("href") || "";
            if (!h2 || seen[h2]) continue;
            var n2 = link.text().trim();
            if (!n2 || n2.length < 2) continue;
            seen[h2] = true;
            var im2 = selFirst(li, "img");
            var au2 = selFirst(li, ".author, p.author");
            result.push({
                name: n2, link: resolveUrl(h2), host: HOST,
                cover: im2 ? (im2.attr("src") || "") : "",
                description: au2 ? au2.text().trim() : ""
            });
        }
    }

    if (result.length === 0) {
        var newsItems = doc.select("ul.news li");
        for (var k = 0; k < newsItems.size(); k++) {
            var nli = newsItems.get(k);
            var nl = selFirst(nli, "h3 a");
            if (!nl) continue;
            var nh = nl.attr("href") || "";
            if (!nh || seen[nh]) continue;
            var nn = nl.text().trim();
            if (!nn || nn.length < 2) continue;
            seen[nh] = true;
            var ce = selFirst(nli, "span.cat");
            var na = selFirst(nli, "span.author");
            result.push({
                name: nn, link: resolveUrl(nh), host: HOST, cover: "",
                description: (ce ? ce.text().trim() + " " : "") + (na ? na.text().trim() : "")
            });
        }
    }

    return result;
}

function getNextPage(doc, current) {
    var n = current + 1;
    if (selFirst(doc, "a[href*='page=" + n + "']")) return String(n);
    if (selFirst(doc, "li.next a")) return String(n);
    return null;
}

function stripHtml(html) {
    if (!html) return "";
    return html
        .replace(/(<br[^>]*>\s*(<\/br>)?\s*)+/gi, "\n\n")
        .replace(/<\/p>/gi, "\n\n").replace(/<p[^>]*>/gi, "")
        .replace(/<\/(div|section|article|blockquote)>/gi, "\n")
        .replace(/<[^>]*>/g, "")
        .replace(/&nbsp;/gi, " ").replace(/&amp;/g, "&")
        .replace(/&lt;/g, "<").replace(/&gt;/g, ">")
        .replace(/[ \t\r]+/g, " ").replace(/\n{3,}/g, "\n\n").trim();
}
