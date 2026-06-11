var BASE_URL = "https://wanwansekai.com";
var HOST = "https://wanwansekai.com";

var FETCH_HEADERS = {
    "User-Agent": "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
    "Accept-Language": "vi-VN,vi;q=0.9,en;q=0.5",
    "Referer": BASE_URL + "/"
};
var FETCH_OPTIONS = { headers: FETCH_HEADERS };

var DETAIL_RE = /\/novel\/[^\/]+\/?$/;
var PAGE_NUM_RE = /\/page\/(\d+)\/?/;
var POSTS_PER_PAGE_RE = /"posts_per_page":(\d+)/;
var HREF_SKIP_RE = /^\s*$|^javascript:|^#|\/wp-login|\/wp-admin|\/cart|\/checkout|\/account/;

function selFirst(el, css) {
    var items = el.select(css);
    return items.size() > 0 ? items.get(0) : null;
}

function resolveUrl(url) {
    if (!url) return BASE_URL;
    if (url.indexOf("http") === 0) return url;
    return BASE_URL + (url.charAt(0) === "/" ? url : "/" + url);
}

function stripHost(href) {
    if (href.indexOf(BASE_URL) === 0) return href.substring(BASE_URL.length);
    return href;
}

function fetchRetry(url) {
    var res = fetch(url, FETCH_OPTIONS);
    if (!res) return res;
    if (!res.ok && !(res.status >= 400 && res.status < 500)) {
        res = fetch(url, FETCH_OPTIONS);
    }
    return res;
}

function extractCover(el) {
    var imgEl = selFirst(el, "img[data-src], img[data-lazy-src], img[data-lazy], img[src]");
    if (!imgEl) return "";

    var src = imgEl.attr("data-src") || imgEl.attr("data-lazy-src") || imgEl.attr("data-lazy") || imgEl.attr("src") || "";
    if (!src) return "";
    if (src.indexOf("http") === 0) return src;
    return BASE_URL + src;
}

function parseList(doc) {
    var result = [];
    var seen = {};
    var cards = doc.select(".page-item-detail, .c-tabs-item__content");

    for (var i = 0; i < cards.size(); i++) {
        var card = cards.get(i);
        var titleA = selFirst(card, ".post-title a[href], h3 a[href], h4 a[href]");
        if (!titleA) continue;

        var href = titleA.attr("href") || "";
        if (!href || HREF_SKIP_RE.test(href) || !DETAIL_RE.test(href)) continue;

        var link = stripHost(href);
        if (seen[link]) continue;
        seen[link] = true;

        var name = titleA.text().trim();
        if (!name) continue;

        var description = "";
        var latestA = selFirst(card, ".chapter-item a[href], .post-on a[href], .latest-chap a[href]");
        if (latestA) description = latestA.text().trim();

        result.push({
            name: name,
            link: link,
            host: HOST,
            cover: extractCover(card),
            description: description
        });
    }

    return result;
}

function getNextPage(doc, page) {
    var nextPage = page + 1;
    var direct = selFirst(doc, "a[href*='/page/" + nextPage + "/']");
    if (direct) return String(nextPage);

    var nextLink = selFirst(doc, ".nav-previous a, a.nextpostslink, .pagination .next");
    if (!nextLink) return null;

    var href = nextLink.attr("href") || "";
    var match = PAGE_NUM_RE.exec(href);
    if (match) return match[1];
    if (href) return String(nextPage);

    var loadMore = selFirst(doc, ".navigation-ajax .load-ajax, a.load-ajax");
    if (loadMore) {
        var perPage = 12;
        var scripts = doc.select("script");
        for (var si = 0; si < scripts.size(); si++) {
            var scriptHtml = scripts.get(si).html() || "";
            var scriptMatch = POSTS_PER_PAGE_RE.exec(scriptHtml);
            if (scriptMatch) {
                perPage = parseInt(scriptMatch[1]);
                break;
            }
        }

        var items = doc.select(".page-item-detail, .c-tabs-item__content").size();
        if (items >= perPage) return String(nextPage);
    }

    return null;
}