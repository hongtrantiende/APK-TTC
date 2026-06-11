var HOST = "https://dualeotruyenfull.net";
var BASE_URL = "https://dualeotruyenfull.net";

var FETCH_HEADERS = {
    "User-Agent": "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
    "Accept-Language": "vi-VN,vi;q=0.9,en;q=0.5",
    "Referer": "https://dualeotruyenfull.net/"
};
var FETCH_OPTIONS = { headers: FETCH_HEADERS };

function fetchDoc(url) {
    var res = fetch(url, FETCH_OPTIONS);
    if (!res || !res.ok) return null;
    return res.html();
}

function selFirst(el, css) {
    var r = el.select(css);
    return r.size() > 0 ? r.get(0) : null;
}

function resolveUrl(url) {
    if (!url) return "";
    if (url.indexOf("http") === 0) return url;
    return BASE_URL + url;
}

function parseList(doc) {
    var result = [];
    var seen = {};
    var cards = doc.select(".manga-item-grid");
    for (var i = 0; i < cards.size(); i++) {
        var card = cards.get(i);
        var a = selFirst(card, "a[href*='/doc-truyen/']");
        if (!a) continue;
        var href = a.attr("href") || "";
        if (!href || seen[href]) continue;
        seen[href] = true;

        var titleEl = selFirst(card, ".manga-title a, h3 a, h4 a");
        var name = "";
        if (titleEl) {
            name = titleEl.text().trim();
        }
        if (!name) {
            var img = selFirst(card, "img");
            if (img) {
                var alt = img.attr("alt") || "";
                name = alt.replace(/^Ảnh bìa của\s*/i, "").trim();
            }
        }
        if (!name || name.length < 2) continue;

        var cover = "";
        var imgEl = selFirst(card, "img");
        if (imgEl) cover = imgEl.attr("data-src") || imgEl.attr("src") || "";

        var link = href.replace(BASE_URL, "");
        result.push({
            name: name,
            link: link,
            host: HOST,
            cover: cover,
            description: ""
        });
        if (result.length >= 30) break;
    }
    return result;
}

function parseSearchList(doc) {
    var result = [];
    var seen = {};
    var articles = doc.select("article");
    for (var i = 0; i < articles.size(); i++) {
        var art = articles.get(i);
        var a = selFirst(art, "a[href*='/doc-truyen/']");
        if (!a) continue;
        var href = a.attr("href") || "";
        if (!href || seen[href]) continue;
        seen[href] = true;

        var titleEl = selFirst(art, "h2 a, h3 a");
        var name = titleEl ? titleEl.text().trim() : "";
        if (!name || name.length < 2) continue;

        var cover = "";
        var imgEl = selFirst(art, "img");
        if (imgEl) cover = imgEl.attr("data-src") || imgEl.attr("src") || "";

        var link = href.replace(BASE_URL, "");
        result.push({
            name: name,
            link: link,
            host: HOST,
            cover: cover,
            description: ""
        });
        if (result.length >= 30) break;
    }
    return result;
}

function stripHtml(html) {
    if (!html) return "";
    return html
        .replace(/(<br[^>]*>\s*(<\/br>)?\s*)+/gi, "\n\n")
        .replace(/<\/p>/gi, "\n\n")
        .replace(/<p[^>]*>/gi, "")
        .replace(/<[^>]*>/g, "")
        .replace(/&amp;/g, "&").replace(/&lt;/g, "<").replace(/&gt;/g, ">")
        .replace(/&quot;/g, "\"").replace(/&nbsp;/g, " ").replace(/&#160;/g, " ")
        .replace(/[ \t\r]+/g, " ")
        .replace(/\n[ \t]+/g, "\n")
        .replace(/[ \t]+\n/g, "\n")
        .replace(/\n{3,}/g, "\n\n")
        .trim();
}
