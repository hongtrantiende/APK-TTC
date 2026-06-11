var BASE_URL = "https://18.foxaholic.com";
var HOST = "https://18.foxaholic.com";
var BASE_URL_ALT = "https://www.foxaholic.com";

var FETCH_HEADERS = {
    "User-Agent": "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
    "Accept-Language": "en-US,en;q=0.9,vi;q=0.5",
    "Referer": BASE_URL + "/"
};
var FETCH_OPTIONS = { headers: FETCH_HEADERS };

var BG_IMAGE_RE = /url\(['"]?([^'")\s]+)['"]?\)/;
var PAGE_NUM_RE = /\/page\/(\d+)\/?/;
var HREF_SKIP_RE = /^\s*$|^javascript:|^#|\/wp-login|\/wp-admin|\/cart|\/checkout|\/account|\/tag\/|\/feed\/|\/privacy|\/terms|\/contact|\/discord|\/resources\//;
var CHAPTER_RE = /\/novel\/[^\/]+\/chapter-[^\/]+\/?$/;

function selFirst(el, css) {
    var items = el.select(css);
    return items.size() > 0 ? items.get(0) : null;
}

function normalizeHost(url) {
    if (!url) return url;
    if (url.indexOf(BASE_URL_ALT) === 0) return BASE_URL + url.substring(BASE_URL_ALT.length);
    return url;
}

function resolveUrl(url) {
    if (!url) return BASE_URL;
    url = normalizeHost(url);
    if (url.indexOf("http") === 0) return url;
    return BASE_URL + (url.charAt(0) === "/" ? url : "/" + url);
}

function stripHost(href) {
    href = normalizeHost(href || "");
    if (href.indexOf(BASE_URL) === 0) return href.substring(BASE_URL.length);
    return href;
}

function fetchRetry(url, options) {
    var opt = options || FETCH_OPTIONS;
    var res = fetch(url, opt);
    if (!res) return res;
    if (!res.ok && !(res.status >= 400 && res.status < 500)) {
        res = fetch(url, opt);
    }
    return res;
}

function extractCover(el) {
    var imgEl = selFirst(el, ".summary_image img, .summary_image a img, .tab-summary img, .profile-manga.summary-layout-1 img, img[data-src], img[data-lazy-src], img[data-lazy], img[data-cfsrc], img[src]");
    if (imgEl) {
        var src = imgEl.attr("data-src") || imgEl.attr("data-lazy-src") || imgEl.attr("data-lazy") || imgEl.attr("data-cfsrc") || imgEl.attr("src") || "";
        if (src && src.indexOf("data:") !== 0) return resolveUrl(src);
    }

    var bgEl = selFirst(el, "[data-bg], [style*='background-image']");
    if (!bgEl) return "";

    var bg = bgEl.attr("data-bg") || "";
    if (!bg) {
        var match = BG_IMAGE_RE.exec(bgEl.attr("style") || "");
        if (match) bg = match[1];
    }
    return bg ? resolveUrl(bg) : "";
}

function isStoryHref(href) {
    href = stripHost(href || "");
    if (!href || href.charAt(0) !== "/") return false;
    if (HREF_SKIP_RE.test(href)) return false;
    if (href === "/" || href === "/novel/" || href === "/browse/" || href === "/about-foxaholic/") return false;
    if (href.indexOf("/page/") >= 0) return false;
    if (href.indexOf("/translation-status/") === 0) return false;
    if (href.indexOf("/genre/") === 0 || href.indexOf("/manga-genre/") === 0 || href.indexOf("/category/") === 0) return false;
    if (CHAPTER_RE.test(href)) return false;
    if (href.indexOf("?s=") >= 0 || href.indexOf("post_type=wp-manga") >= 0) return false;
    return href.indexOf("/novel/") === 0;
}

function cleanInlineCss(text) {
    if (!text) return "";
    return text.replace(/\.bg-ssp-[^\{]+\{[^\}]*\}/g, "").replace(/\s+/g, " ").trim();
}

function parseCard(card) {
    var titleA = selFirst(card, ".post-title a[href], h3 a[href], h4 a[href], h5 a[href], .item-summary a[href]");
    if (!titleA) return null;

    var href = titleA.attr("href") || "";
    if (!isStoryHref(href)) return null;

    var name = titleA.text().trim();
    if (!name) return null;

    var latestA = selFirst(card, ".list-chapter .chapter-item a[href], .chapter-item a[href], .post-on a[href], a[href='#']");
    var latestText = latestA ? latestA.text().trim() : "";
    // Loại bỏ nếu là số thuần (chapter count badge, không phải chapter title)
    if (latestText && /^\d+(\.\d+)?$/.test(latestText)) latestText = "";
    var latestDate = "";
    var dateEl = selFirst(card, ".chapter-release-date, .post-on, .font-meta");
    if (dateEl) latestDate = dateEl.text().trim();

    var description = latestText;
    if (latestDate) {
        var sameText = latestText && (latestText === latestDate || latestText.indexOf(latestDate) >= 0 || latestDate.indexOf(latestText) >= 0);
        if (!sameText) description = description ? (description + " | " + latestDate) : latestDate;
    }

    return {
        name: name,
        link: stripHost(href),
        host: HOST,
        cover: extractCover(card),
        description: description
    };
}

function parseList(doc) {
    var result = [];
    var seen = {};
    var cards = doc.select(".page-item-detail, .c-tabs-item__content, .content-genres-item, .postbody");

    for (var i = 0; i < cards.size(); i++) {
        var parsed = parseCard(cards.get(i));
        if (!parsed) continue;

        var link = parsed.link;
        if (seen[link]) continue;
        seen[link] = true;

        result.push(parsed);
    }

    return result;
}

function getNextPage(doc, page) {
    var nextPage = page + 1;
    var direct = selFirst(doc, "a[href*='/page/" + nextPage + "/']");
    if (direct) return String(nextPage);

    var nextLink = selFirst(doc, ".nav-links a.next, .nav-previous a, a.nextpostslink, .pagination .next");
    if (!nextLink) return null;

    var href = nextLink.attr("href") || "";
    var match = PAGE_NUM_RE.exec(href);
    if (match) return match[1];
    return href ? String(nextPage) : null;
}