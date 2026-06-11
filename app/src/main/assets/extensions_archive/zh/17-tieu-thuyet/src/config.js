var BASE_URL = "https://m.1qxs.com";
var USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36";

try {
    if (CONFIG_URL) {
        BASE_URL = CONFIG_URL.replace(/\/+$/, "");
    }
} catch (error) {
}

function normalizeUrl(url) {
    if (!url) return BASE_URL + "/";

    var value = String(url).trim();
    if (!value) return BASE_URL + "/";
    if (value.indexOf("//") === 0) return "https:" + value;

    if (/^https?:\/\//i.test(value)) {
        return value.replace(/^https?:\/\/(?:m\.)?1qxs\.com/i, BASE_URL);
    }

    if (value.charAt(0) === "/") return BASE_URL + value;
    return BASE_URL + "/" + value.replace(/^\/+/, "");
}

function cleanText(text) {
    var value = text === null || typeof text === "undefined" ? "" : String(text);

    value = value.replace(/\u00a0/g, " ");
    value = value.replace(/&nbsp;/gi, " ");
    value = value.replace(/\r/g, "\n");
    value = value.replace(/[ \t\f\v]+/g, " ");
    value = value.replace(/\n\s+/g, "\n");
    value = value.replace(/\s+\n/g, "\n");
    value = value.replace(/\n{3,}/g, "\n\n");

    return value.replace(/^\s+|\s+$/g, "");
}

function escapeHtml(text) {
    var value = text === null || typeof text === "undefined" ? "" : String(text);
    return value
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#39;");
}

function browserHeaders(referer) {
    var headers = {
        "User-Agent": USER_AGENT,
        "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
        "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8",
        "Upgrade-Insecure-Requests": "1",
        "Sec-Fetch-Dest": "document",
        "Sec-Fetch-Mode": "navigate",
        "Sec-Fetch-Site": "same-origin",
        "Sec-Fetch-User": "?1",
        "Cache-Control": "no-cache",
        "Pragma": "no-cache"
    };

    if (referer) headers["Referer"] = normalizeUrl(referer);
    return headers;
}

function fetchText(url, referer) {
    var response = fetch(normalizeUrl(url), {
        headers: browserHeaders(referer)
    });

    if (!response || !response.ok) return "";
    return response.text();
}

function fetchDocument(url, referer) {
    var text = fetchText(url, referer);
    if (!text) return null;
    return Html.parse(text);
}

function normalizeSampleText(text) {
    return cleanText(text).replace(/[|｜]/g, "").replace(/\s+/g, "");
}

function isBlockedText(text) {
    var sample = normalizeSampleText(text);
    if (!sample) return true;

    return sample.indexOf("网络错误,请点击刷新按钮重试") !== -1
        || sample.indexOf("访问太频繁了") !== -1
        || sample.indexOf("请30秒过后刷新重试") !== -1;
}

function fetchStableText(url, referer, warmUrl) {
    var text = fetchText(url, referer);
    if (!isBlockedText(text)) return text;

    if (warmUrl) {
        if (typeof sleep === "function") sleep(1500);
        fetchText(warmUrl, referer);
        text = fetchText(url, warmUrl);
    }

    if (isBlockedText(text)) {
        throw new Error("Trang web từ chối kết nối (chặn) vì tải bộ mục lục quá nhanh/nhiều. Vui lòng dừng tải và thử làm mới lại sau 30-60 giây.");
    }

    return text;
}

function fetchStableDocument(url, referer, warmUrl) {
    var text = fetchStableText(url, referer, warmUrl);
    if (!text) return null;
    return Html.parse(text);
}

function buildListingDescription(author, meta, desc) {
    var parts = [];
    if (author) parts.push("作者: " + author);
    if (meta) parts.push(meta);
    if (desc) parts.push(desc);
    return parts.join(" | ");
}

function extractListingMeta(link) {
    var parts = [];
    var spans = link.select(".msg span");

    for (var i = 0; i < spans.size(); i++) {
        var text = cleanText(spans.get(i).text());
        if (text) parts.push(text);
    }

    return parts.join(" | ");
}

function extractBookItems(doc) {
    var items = [];
    var seen = {};
    var selector = ".template-02 .show > ul > a[href*='/xs_1/'], "
        + ".template-02 .show > ul > li > a[href*='/xs_1/'], "
        + ".template-04 > ul > li > a[href*='/xs_1/'], "
        + ".template-06 > ul > a[href*='/xs_1/'], "
        + ".template-06 > ul > li > a[href*='/xs_1/']";
    var links = doc.select(selector);

    for (var i = 0; i < links.size(); i++) {
        var link = links.get(i);
        var href = normalizeUrl(link.attr("href"));
        if (!href || !/\/xs_1\/\d+/i.test(href) || seen[href]) continue;
        seen[href] = true;

        var titleEl = link.select(".name").first();
        var imgEl = link.select("img").first();
        var author = cleanText(link.select(".author").text());
        var meta = extractListingMeta(link);
        var desc = cleanText(link.select(".desc").text());
        var cover = "";

        if (imgEl) {
            cover = imgEl.attr("data-original");
            if (!cover) cover = imgEl.attr("data-src");
            if (!cover) cover = imgEl.attr("src");
        }

        var name = titleEl ? cleanText(titleEl.text()) : "";
        if (!name && imgEl) name = cleanText(imgEl.attr("alt"));
        if (!name) continue;

        items.push({
            name: name,
            link: href,
            cover: normalizeUrl(cover),
            description: buildListingDescription(author, meta, desc),
            host: BASE_URL
        });
    }

    return items;
}

var RECENT_COVER_CACHE = {};

function extractImageUrl(imgEl) {
    if (!imgEl) return "";

    var cover = imgEl.attr("data-original");
    if (!cover) cover = imgEl.attr("data-src");
    if (!cover) cover = imgEl.attr("src");
    if (!cover || cover.indexOf("/img/default.png") !== -1) return "";

    return normalizeUrl(cover);
}

function collectCoverMapFromDocument(doc) {
    var map = {};
    var links = doc.select("a[href*='/xs_1/']");

    for (var i = 0; i < links.size(); i++) {
        var link = links.get(i);
        var href = normalizeUrl(link.attr("href"));
        if (!href) continue;

        var cover = extractImageUrl(link.select("img").first());
        if (!cover) continue;

        var bookId = extractBookId(href);
        if (!map[href]) map[href] = cover;
        if (bookId && !map[bookId]) map[bookId] = cover;
    }

    return map;
}

function extractDetailCover(doc) {
    var coverEl = doc.select(".book-intro .cover img").first();
    return extractImageUrl(coverEl);
}

function guessCoverUrl(bookId) {
    if (!bookId) return "";
    return "https://img.1qxs.com/cover/" + bookId + ".jpg";
}

function resolveRecentCover(bookId, detailUrl, coverMap) {
    var cacheKey = bookId || detailUrl;
    if (cacheKey && RECENT_COVER_CACHE[cacheKey]) return RECENT_COVER_CACHE[cacheKey];

    var cover = "";
    if (coverMap) {
        if (detailUrl && coverMap[detailUrl]) cover = coverMap[detailUrl];
        if (!cover && bookId && coverMap[bookId]) cover = coverMap[bookId];
    }

    if (!cover) cover = guessCoverUrl(bookId);

    if (cacheKey && cover) RECENT_COVER_CACHE[cacheKey] = cover;
    return cover;
}

function extractRecentItems(doc) {
    var items = [];
    var seen = {};
    var coverMap = collectCoverMapFromDocument(doc);
    var links = doc.select(".template-03 .show > ul > li > a[href*='/xs_1/']");

    for (var i = 0; i < links.size(); i++) {
        var link = links.get(i);
        var href = normalizeUrl(link.attr("href"));
        if (!href || seen[href]) continue;
        seen[href] = true;

        var bookId = extractBookId(href);
        var title = cleanText(link.select(".name").text());
        var author = cleanText(link.select(".author").text());
        var time = cleanText(link.select(".time").text());
        var chapter = cleanText(link.select(".lastchapter .line_1").text());
        if (!title) continue;

        var descriptionParts = [];
        if (author) descriptionParts.push("作者: " + author);
        if (time) descriptionParts.push("更新: " + time);
        if (chapter) descriptionParts.push(chapter);

        items.push({
            name: title,
            link: href,
            cover: resolveRecentCover(bookId, href, coverMap),
            description: descriptionParts.join(" | "),
            host: BASE_URL
        });
    }

    return items;
}

function extractNextPageUrl(doc) {
    var links = doc.select(".page a");

    for (var i = 0; i < links.size(); i++) {
        var link = links.get(i);
        var text = cleanText(link.text());
        var href = link.attr("href");

        if (!href || href.indexOf("javascript:") === 0) continue;
        if (text.indexOf("下一页") !== -1) return normalizeUrl(href);
    }

    return null;
}

function resolvePagedUrl(url, page) {
    if (page && (String(page).indexOf("http://") === 0 || String(page).indexOf("https://") === 0 || String(page).charAt(0) === "/")) {
        return normalizeUrl(page);
    }

    return normalizeUrl(url);
}

function buildDetailUrl(bookId) {
    return BASE_URL + "/xs_1/" + bookId;
}

function buildCatalogUrl(bookId, pageNo) {
    var page = pageNo ? String(pageNo) : "1";
    if (page !== "1") return BASE_URL + "/catalog_1/" + bookId + "/" + page;
    return BASE_URL + "/catalog_1/" + bookId;
}

function buildChapterUrl(bookId, chapterId, pageNo) {
    var url = BASE_URL + "/xs_1/" + bookId + "/" + chapterId;
    var page = pageNo ? String(pageNo) : "1";
    if (page !== "1") url += "/" + page;
    return url;
}

function extractBookId(url) {
    var match = normalizeUrl(url).match(/\/(?:xs_\d+|catalog_\d+)\/(\d+)/i);
    return match ? match[1] : "";
}

function extractChapterInfo(url) {
    var match = normalizeUrl(url).match(/\/xs_\d+\/(\d+)\/(\d+)(?:\/(\d+))?/i);
    return {
        bookId: match ? match[1] : "",
        chapterId: match ? match[2] : "",
        pageNo: match && match[3] ? match[3] : "1"
    };
}

function extractTotalPages(doc) {
    var titleEl = doc.select("#main h1").first();
    if (!titleEl) titleEl = doc.select("h1").first();
    var title = titleEl ? cleanText(titleEl.text()) : "";
    var match = title.match(/\((\d+)\/(\d+)\)\s*$/);

    if (match) return parseInt(match[2], 10) || 1;

    var nextEl = doc.select(".page .right a[href*='/xs_1/'], .footer .next").first();
    return nextEl ? 2 : 1;
}

function extractPKey(doc) {
    var scripts = doc.select("script");

    for (var i = 0; i < scripts.size(); i++) {
        var content = scripts.get(i).html();
        if (!content) continue;

        var match = content.match(/p_key\s*=\s*'([^']+)'/);
        if (!match) match = content.match(/p_key\s*=\s*"([^"]+)"/);
        if (match && match[1]) return match[1];
    }

    return "";
}

function getGlobalObject() {
    if (typeof globalThis !== "undefined") return globalThis;

    try {
        return Function("return this")();
    } catch (error) {
        return {};
    }
}

function atobFallback(input) {
    var chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=";
    var value = cleanText(input).replace(/[^A-Za-z0-9\+\/\=]/g, "");
    var output = "";
    var index = 0;

    while (index < value.length) {
        var e1 = chars.indexOf(value.charAt(index++));
        var e2 = chars.indexOf(value.charAt(index++));
        var e3 = chars.indexOf(value.charAt(index++));
        var e4 = chars.indexOf(value.charAt(index++));

        if (e1 < 0 || e2 < 0) continue;

        var c1 = (e1 << 2) | (e2 >> 4);
        var c2 = ((e2 & 15) << 4) | (e3 >> 2);
        var c3 = ((e3 & 3) << 6) | e4;

        output += String.fromCharCode(c1);
        if (e3 !== 64 && e3 >= 0) output += String.fromCharCode(c2);
        if (e4 !== 64 && e4 >= 0) output += String.fromCharCode(c3);
    }

    return output;
}

function decodeBase64Utf8(base64Text) {
    var input = cleanText(base64Text).replace(/\s+/g, "");
    if (!input) return "";

    var g = getGlobalObject();
    var binary = "";

    try {
        binary = g && typeof g.atob === "function" ? g.atob(input) : atobFallback(input);
    } catch (error) {
        return "";
    }

    try {
        return decodeURIComponent(escape(binary));
    } catch (error) {
        return binary;
    }
}

function isNoiseChapterLine(text) {
    var sample = normalizeSampleText(text);
    if (!sample) return true;

    return sample.indexOf("小说免费阅读，请收藏") !== -1
        || sample.indexOf("阅读模式或畅读模式下，无法显示本章节全部内容，请返回原网页阅读。") !== -1
        || sample.indexOf("加载更多") !== -1
        || sample.indexOf("本章未完，点击[下一页]继续阅读-->") !== -1;
}

function pushChapterParagraph(paragraphs, text) {
    var value = cleanText(text);
    if (!value || isNoiseChapterLine(value)) return;

    if (paragraphs.length > 0 && paragraphs[paragraphs.length - 1] === value) return;
    paragraphs.push(value);
}

function appendParagraphsFromElement(root, paragraphs) {
    var paragraphEls = root.select("p");

    if (paragraphEls.size() > 0) {
        for (var i = 0; i < paragraphEls.size(); i++) {
            pushChapterParagraph(paragraphs, paragraphEls.get(i).text());
        }
        return;
    }

    var html = root.html();
    html = html.replace(/<br\s*\/?>/gi, "\n");
    html = html.replace(/<\/p>/gi, "\n");
    html = html.replace(/<\/div>/gi, "\n");
    html = html.replace(/<[^>]+>/g, "");

    var lines = html.split(/\n+/);
    for (var j = 0; j < lines.length; j++) {
        pushChapterParagraph(paragraphs, lines[j]);
    }
}

function extractChapterParagraphs(doc) {
    var paragraphs = [];
    var contentEl = doc.select(".content").first();

    if (contentEl) {
        appendParagraphsFromElement(contentEl, paragraphs);
    }

    var pKey = extractPKey(doc);
    if (pKey) {
        var decoded = decodeBase64Utf8(pKey);
        if (decoded) {
            var wrapper = Html.parse("<div class='decoded-chapter'>" + decoded + "</div>");
            var decodedEl = wrapper.select(".decoded-chapter").first();
            if (decodedEl) appendParagraphsFromElement(decodedEl, paragraphs);
        }
    }

    return paragraphs;
}

function renderChapterHtml(paragraphs) {
    var parts = [];

    for (var i = 0; i < paragraphs.length; i++) {
        parts.push("<p>" + escapeHtml(paragraphs[i]) + "</p>");
    }

    return parts.join("");
}
