var BASE_URL = "https://m.bixiange.me";
var HOST = "https://m.bixiange.me";

var FETCH_HEADERS = {
    "User-Agent": "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
    "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.5",
    "Referer": "https://m.bixiange.me/"
};
var FETCH_OPTIONS = { headers: FETCH_HEADERS };
var LATEST_CHAPTER_CACHE = {};
var LATEST_FETCH_LIMIT = 10;

// Story URL regex: /{category-slug}/{id} — 12 known category slugs
var STORY_URL_RE = /^\/(dsyq|wxxz|xhqh|cyjk|khjj|ghxy|jsls|guanchang|xtfq|dmtr|trxs|jqxs)\/\d+/;
var LIST_SKIP_NAME_RE = /^在线阅读$|^目录$|^上一章$|^下一章$|^上一页$|^下一页$/;

// selectFirst helper — vBook chỉ hỗ trợ selectFirst trên Document, không hỗ trợ trên Element
function selFirst(el, css) {
    var r = el.select(css);
    return r.size() > 0 ? r.get(0) : null;
}

// Resolve URL to absolute
function resolveUrl(url) {
    if (!url) return "";
    if (url.indexOf("http") === 0) return url.replace(/\/$/, "");
    return BASE_URL + url.replace(/\/$/, "");
}

// Normalize href: extract path, remove .html suffix
function normalizeHref(href) {
    if (!href) return "";
    if (href.indexOf("http") === 0) {
        if (href.indexOf("bixiange.me") === -1) return "";
        href = href.replace(/https?:\/\/[^\/]+/, "");
    }
    return href.replace(/\/$/, "");
}

function escapeRegExp(text) {
    return (text || "").replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function storyPathFromUrl(url) {
    var v = url || "";
    if (v.indexOf("http") === 0) v = v.replace(/https?:\/\/[^\/]+/, "");
    return v.replace(/\.html$/, "").replace(/\/$/, "");
}

function ensureStoryUrl(url) {
    var storyUrl = resolveUrl(url);
    var storyPath = storyPathFromUrl(storyUrl);
    if (!/\.html$/i.test(storyUrl) && STORY_URL_RE.test(storyPath)) {
        storyUrl += ".html";
    }
    return storyUrl;
}

function getChapterHrefInfo(href, storyPath) {
    if (!href) return null;
    if (href.indexOf("http") === 0) {
        if (href.indexOf("bixiange.me") === -1) return null;
        href = href.replace(/https?:\/\/[^\/]+/, "");
    }
    href = href.replace(/\/$/, "");

    var storyBase = storyPathFromUrl(storyPath);
    var chapRe = new RegExp("^" + escapeRegExp(storyBase) + "\/(?:(?:index\/)?)(\\d+)(?:_(\\d+))?\\.html$");
    var match = chapRe.exec(href);
    if (!match) return null;

    return {
        path: href,
        number: parseInt(match[1], 10),
        part: match[2] ? parseInt(match[2], 10) : 1
    };
}

function extractLatestChapterLabel(doc, storyPath) {
    var links = doc.select("a[href*='" + storyPath + "/']");
    var latestNumber = -1;

    for (var i = 0; i < links.size(); i++) {
        var a = links.get(i);
        var info = getChapterHrefInfo(a.attr("href") || "", storyPath);
        if (!info || info.part !== 1) continue;

        if (info.number > latestNumber) {
            latestNumber = info.number;
        }
    }

    return latestNumber > 0 ? "Chương " + latestNumber : "";
}

function extractLatestChapterLabelFromText(rawHtml, storyPath) {
    if (!rawHtml) return "";

    var storyBase = storyPathFromUrl(storyPath);
    var chapterRe = new RegExp(escapeRegExp(storyBase) + "\\/(?:(?:index\\/)?)?(\\d+)(?:_(\\d+))?\\.html", "g");
    var latestNumber = -1;
    var match;

    while ((match = chapterRe.exec(rawHtml)) !== null) {
        var chapterNumber = parseInt(match[1], 10);
        var chapterPart = match[2] ? parseInt(match[2], 10) : 1;
        if (chapterPart !== 1) continue;
        if (chapterNumber > latestNumber) latestNumber = chapterNumber;
    }

    return latestNumber > 0 ? "Chương " + latestNumber : "";
}

function getLatestChapter(href) {
    var storyUrl = ensureStoryUrl(href);
    if (LATEST_CHAPTER_CACHE.hasOwnProperty(storyUrl)) return LATEST_CHAPTER_CACHE[storyUrl];

    var res = fetch(storyUrl, FETCH_OPTIONS);
    if (!res || !res.ok) {
        LATEST_CHAPTER_CACHE[storyUrl] = "";
        return "";
    }

    var latest = "";
    try {
        latest = extractLatestChapterLabelFromText(res.text(), storyPathFromUrl(storyUrl));
    } catch (e) {}

    if (!latest) {
        try {
            var doc = res.html();
            if (doc) latest = extractLatestChapterLabel(doc, storyPathFromUrl(storyUrl));
        } catch (e2) {}
    }

    LATEST_CHAPTER_CACHE[storyUrl] = latest;
    return latest;
}

// Build paginated URL — Bixiange pattern:
//   page 1: BASE_URL/{section}/
//   page N: BASE_URL/{section}/index_{N}.html
function paginateUrl(section, page) {
    var p = page ? parseInt(page) : 1;
    var base = BASE_URL + "/" + section;
    if (p <= 1) return base + "/";
    return base + "/index_" + p + ".html";
}

// Fetch với retry — bỏ qua 4xx
function fetchRetry(url) {
    var res = fetch(url, FETCH_OPTIONS);
    if (!res) return res;
    if (!res.ok && !(res.status >= 400 && res.status < 500)) res = fetch(url, FETCH_OPTIONS);
    return res;
}

// Fetch bằng browser (cho trang cần JS render) — GBK encoding
function fetchBrowser(url, timeout) {
    var t = timeout || 7000;
    var browser = Engine.newBrowser();
    try {
        return browser.launch(url, t);
    } finally {
        browser.close();
    }
}

// Fetch browser nhanh — dùng cho trang list (ít JS, timeout ngắn hơn)
function fetchBrowserFast(url) {
    var browser = Engine.newBrowser();
    try {
        return browser.launch(url, 5000);
    } finally {
        browser.close();
    }
}

// Parse danh sách truyện từ doc (list/genre/search pages)
function parseList(doc) {
    var result = [];
    var seen = {};
    var coverMap = {};
    var canFetch = -1;
    var latestFetchCount = 0;

    // Pass 1: build cover map từ a:has(img) có story URL
    var imgLinks = doc.select("a[href]:has(img)");
    for (var ci = 0; ci < imgLinks.size(); ci++) {
        var ael = imgLinks.get(ci);
        var ah = normalizeHref(ael.attr("href") || "");
        if (!ah || !STORY_URL_RE.test(ah)) continue;
        var img = selFirst(ael, "img");
        if (!img) continue;
        var src = img.attr("data-src") || img.attr("data-original") || img.attr("src") || "";
        if (src && src.charAt(0) === 47) src = BASE_URL + src;
        if (src && !coverMap[ah]) coverMap[ah] = src;
    }

    // Pass 2: tìm title links (text 2-100 ký tự)
    var allLinks = doc.select("a[href]");
    for (var i = 0; i < allLinks.size(); i++) {
        var a = allLinks.get(i);
        var href = normalizeHref(a.attr("href") || "");
        if (!href || !STORY_URL_RE.test(href)) continue;
        if (seen[href]) continue;
        var name = a.text().trim();
        if (!name || name.length < 2 || name.length > 100) continue;
        seen[href] = true;

        var description = "";
        if (canFetch !== 0 && latestFetchCount < LATEST_FETCH_LIMIT) {
            latestFetchCount++;
            var latestChapter = getLatestChapter(href);
            if (latestChapter) {
                description = latestChapter;
                canFetch = 1;
            } else if (canFetch === -1) {
                canFetch = 0;
            }
        }

        result.push({
            name: name,
            link: href,
            host: HOST,
            cover: coverMap[href] || "",
            description: description
        });
        if (result.length >= 30) break;
    }
    return result;
}

// Trang tiếp theo — Bixiange dùng /index_{N}.html
function getNextPage(doc, current) {
    // Tìm link tới trang N+1
    var nextNum = current + 1;
    var nextLink = selFirst(doc, "a[href*='index_" + nextNum + ".html']");
    if (nextLink) return String(nextNum);
    // Fallback: tìm nút "下一页"
    var pageLinks = doc.select("a[href*='index_']");
    for (var i = 0; i < pageLinks.size(); i++) {
        var txt = pageLinks.get(i).text().trim();
        if (txt === "\u4e0b\u4e00\u9875" || txt.indexOf("\u4e0b\u4e00") === 0) return String(nextNum);
    }
    return null;
}

// Strip HTML → plain text
var ENTITY_MAP = {
    "&amp;": "&", "&lt;": "<", "&gt;": ">",
    "&quot;": '"', "&#039;": "'", "&nbsp;": " ", "&#160;": " "
};
function stripHtml(html) {
    if (!html) return "";
    return html
        .replace(/(<br[^>]*>\s*(<\/br>)?\s*)+/gi, "\n\n")  // <br>, <br/>, <br></br> (1+) → paragraph break
        .replace(/<\/p>/gi, "\n\n")                      // đóng <\/p> → xuống đoạn (dòng trắng)
        .replace(/<p[^>]*>/gi, "")                        // mở <p> → bỏ (close tag đã tạo khoảng)
        .replace(/<\/(div|section|article|blockquote)>/gi, "\n")  // đóng block → xuống dòng
        .replace(/<[^>]*>/g, "")
        .replace(/&[a-z#0-9]+;/gi, function(e) { return ENTITY_MAP[e] || e; })
        .replace(/[ \t\r]+/g, " ")      // chuẩn hóa khoảng trắng ngang
        .replace(/\n[ \t]+/g, "\n")     // bỏ space đầu dòng (giữ 　 full-width)
        .replace(/[ \t]+\n/g, "\n")     // bỏ space cuối dòng
        .replace(/\n{3,}/g, "\n\n")     // tối đa 2 dòng trắng liên tiếp
        .trim();
}
