let BASE_URL = "https://zuminovel.com";
try { if (CONFIG_URL) BASE_URL = CONFIG_URL; } catch (e) {}
function cleanText(s) {
    return ((s || "") + "").replace(/\s+/g, " ").trim();
}

function normalizeUrl(url) {
    url = (url || "") + "";
    if (!url) return "";
    if (url.indexOf("//") === 0) return "https:" + url;
    if (url.indexOf("/") === 0) return BASE_URL + url;
    if (url.indexOf("http") !== 0) return BASE_URL + "/" + url;
    return url;
}

function appendPageParam(url, page) {
    url = normalizeUrl(url);
    page = page || "1";
    if (url.indexOf("page=") >= 0) return url.replace(/([?&]page=)[^&#]*/, "$1" + page);
    return url + (url.indexOf("?") >= 0 ? "&" : "?") + "page=" + page;
}

function getAuthToken() {
    try {
        return (localStorage.getItem("auth_token") || "") + "";
    } catch (e) {
        return "";
    }
}

function buildHeaders() {
    var token = getAuthToken();
    var headers = {
        "User-Agent": "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36"
    };
    if (token) {
        headers["Cookie"] = "auth_token=" + token;
        headers["Authorization"] = "Bearer " + token;
    }
    return headers;
}

function getHtml(url) {
    return fetch(normalizeUrl(url), { headers: buildHeaders() });
}

function getJson(url) {
    var headers = buildHeaders();
    headers["Accept"] = "application/json";
    return fetch(normalizeUrl(url), { headers: headers });
}

function loginErrorIfNeeded(res) {
    if (!res.ok) return Response.error("Vui lòng đăng nhập");
    return null;
}

function decodeFlightText(text) {
    text = (text || "") + "";
    text = text.replace(/\\u003c/g, "<");
    text = text.replace(/\\u003e/g, ">");
    text = text.replace(/\\u0026/g, "&");
    text = text.replace(/\\n/g, "\n");
    text = text.replace(/\\"/g, '"');
    text = text.replace(/\\\\/g, "\\");
    return text;
}

function absoluteNovelUrl(slug) {
    return BASE_URL + "/novel/" + slug;
}

function parseJsonObject(raw) {
    try { return JSON.parse(raw); } catch (e) { return null; }
}

function extractJsonAt(text, start) {
    var depth = 0;
    var inString = false;
    var escape = false;
    for (var i = start; i < text.length; i++) {
        var ch = text.charAt(i);
        if (escape) {
            escape = false;
            continue;
        }
        if (ch === "\\") {
            escape = true;
            continue;
        }
        if (ch === '"') {
            inString = !inString;
            continue;
        }
        if (inString) continue;
        if (ch === "{") depth++;
        if (ch === "}") {
            depth--;
            if (depth === 0) return text.substring(start, i + 1);
        }
    }
    return "";
}

function extractBookJson(html) {
    var book = extractBookJsonFromText(html);
    if (book) return book;
    html = decodeFlightText(html);
    book = extractBookJsonFromText(html);
    if (book) return book;
    return extractInitialNovelJson(html);
}

function extractBookJsonFromText(text) {
    var marker = '{"@context":"https://schema.org","@type":"Book"';
    var start = text.indexOf(marker);
    while (start >= 0) {
        var raw = extractJsonAt(text, start);
        var book = parseJsonObject(raw);
        if (book && book["@type"] === "Book") return book;
        start = text.indexOf(marker, start + marker.length);
    }
    return null;
}

function extractInitialNovelJson(html) {
    var marker = '"initialNovel":';
    var start = html.indexOf(marker);
    if (start < 0) return null;
    start = html.indexOf("{", start + marker.length);
    if (start < 0) return null;
    var novel = parseJsonObject(extractJsonAt(html, start));
    if (!novel) return null;
    return {
        name: novel.title,
        image: novel.coverUrl,
        author: { name: novel.author },
        description: novel.description,
        genre: novel.genres || [],
        numberOfPages: novel.chapters ? novel.chapters.length : "",
        status: novel.status
    };
}

function extractSlug(url) {
    var m = ((url || "") + "").match(/\/novel\/([^\/?#]+)/);
    return m && m[1] ? m[1] + "" : "";
}

function extractChapterLinks(html, novelUrl) {
    html = decodeFlightText(html);
    var data = [];
    var seen = {};
    var re = /\{"id":"([^"]+)","title":"([^"]*)","slug":"([^"]*)","order":(\d+),"volume":"([^"]*)","isVIP":(true|false)[\s\S]*?\}/g;
    var m;
    while ((m = re.exec(html)) !== null) {
        var id = m[1] + "";
        var title = cleanText(m[2]);
        var slug = cleanText(m[3]);
        var volume = cleanText(m[5]);
        var key = id;
        if (seen[key]) continue;
        seen[key] = true;
        var volSlug = toAsciiSlug(volume || "chuong");
        var chapSlug = encodeURIComponent(slug + "-" + id);
        data.push({
            name: (volume ? volume + " - " : "") + (title || slug || id),
            url: normalizeUrl(extractSlug(novelUrl) ? ("/novel/" + extractSlug(novelUrl) + "/read/" + volSlug + "/" + chapSlug) : ""),
            host: BASE_URL,
            pay: m[6] === "true" ? true : undefined
        });
    }
    return data;
}

function toAsciiSlug(text) {
    var s = cleanText(text).toLowerCase();
    var from = "àáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđ";
    var to =   "aaaaaaaaaaaaaaaaaeeeeeeeeeeeiiiiiooooooooooooooooouuuuuuuuuuuyyyyyd";
    for (var i = 0; i < from.length; i++) s = s.replace(new RegExp(from.charAt(i), "g"), to.charAt(i));
    s = s.replace(/[^a-z0-9\s-]/g, "").replace(/\s+/g, "-").replace(/-+/g, "-").replace(/^-|-$/g, "");
    return s || "chuong";
}

function mapBookJson(book) {
    if (!book) return null;
    var url = normalizeUrl(book.url || "");
    return {
        name: cleanText(book.name),
        link: url,
        cover: normalizeUrl(book.image || ""),
        description: cleanText(book.author && book.author.name ? book.author.name : ""),
        host: BASE_URL
    };
}

function mapNovelItem(item) {
    item = item || {};
    var slug = cleanText(item.slug || "");
    return {
        name: cleanText(item.title || slug),
        link: slug ? absoluteNovelUrl(slug) : "",
        cover: normalizeUrl(item.coverUrl || ""),
        description: cleanText(item.author || ""),
        host: BASE_URL
    };
}

function apiListUrl(page) {
    return BASE_URL + "/api/novels?page=" + encodeURIComponent((page || "1") + "");
}

function apiSearchUrl(key, page) {
    return BASE_URL + "/api/novels?search=" + encodeURIComponent((key || "") + "") + "&page=" + encodeURIComponent((page || "1") + "");
}

function checkLoginFromRes(res) {
    var authCookie = res.request.headers.cookie;
    var hasAuthToken = /(?:^|;\s*)auth_token=([^;]+)/.test(authCookie || "");
    if (!hasAuthToken) return Response.error("Vui lòng đăng nhập");
    // do nothing, just return null to continue
    return null;
}