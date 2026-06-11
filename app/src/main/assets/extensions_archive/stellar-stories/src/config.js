let BASE_URL = 'https://stellarstory.net';
let API_URL = 'https://api.stellarstory.net/api';
try {
    if (CONFIG_URL) BASE_URL = CONFIG_URL;
} catch (error) {
}

let MOBILE_UA = "Mozilla/5.0 (Linux; Android 12; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0 Mobile Safari/537.36";
let NO_COVER = "/no_cover.jpg";

function cleanText(text) {
    if (!text) return "";
    return ("" + text).replace(/\s+/g, " ").trim();
}

function foldText(text) {
    text = cleanText(text).toLowerCase();
    try {
        text = text.normalize("NFD").replace(/[\u0300-\u036f]/g, "");
    } catch (error) {
    }
    return text.replace(/\u0111/g, "d").replace(/\u0110/g, "d");
}

function stripTrailingSlash(url) {
    if (!url) return url;
    return ("" + url).replace(/\/+$/, "");
}

function normalizeUrl(url) {
    if (!url) return "";
    url = ("" + url).replace(/&amp;/g, "&").trim();
    if (url.startsWith("//")) return "https:" + url;
    if (url.startsWith("/")) return stripTrailingSlash(BASE_URL) + url;
    if (!/^https?:\/\//i.test(url)) return stripTrailingSlash(BASE_URL) + "/" + url.replace(/^\/+/, "");
    return url.replace(/^http:\/\/(?:www\.)?stellarstory\.net/i, BASE_URL).replace(/^https:\/\/www\.stellarstory\.net/i, BASE_URL);
}

function decodeURIComponentSafe(text) {
    try {
        return decodeURIComponent(text);
    } catch (error) {
        return text;
    }
}

function queryParam(url, name) {
    let re = new RegExp("[?&]" + name + "=([^&#]+)", "i");
    let match = re.exec(url || "");
    return match ? decodeURIComponentSafe(match[1].replace(/\+/g, " ")) : "";
}

function normalizeImage(url) {
    if (!url) return "";
    url = ("" + url).replace(/&amp;/g, "&").trim();
    if (!url || url === NO_COVER || url.indexOf("/no_cover") >= 0 || url.indexOf("ln.hako.vn/img/nocover") >= 0) return "";
    if (url.indexOf("/_next/image") === 0 || url.indexOf(BASE_URL + "/_next/image") === 0) {
        let nested = queryParam(url, "url");
        if (nested) url = nested;
    }
    if (url.indexOf("/api/proxy-image") === 0 || url.indexOf(BASE_URL + "/api/proxy-image") === 0) {
        let proxied = queryParam(url, "url");
        if (proxied) url = proxied;
    }
    if (url.startsWith("//")) return "https:" + url;
    if (url.startsWith("/")) return stripTrailingSlash(BASE_URL) + url;
    return url;
}

function request(url, options) {
    options = options || {};
    let headers = options.headers || {};
    headers["Accept"] = headers["Accept"] || "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8";
    headers["Accept-Language"] = headers["Accept-Language"] || "vi-VN,vi;q=0.9,en-US;q=0.8,en;q=0.7";
    headers["Referer"] = headers["Referer"] || BASE_URL + "/";
    headers["User-Agent"] = headers["User-Agent"] || MOBILE_UA;
    options.headers = headers;
    return fetch(normalizeUrl(url), options);
}

function getDoc(url) {
    try {
        let response = request(url);
        if (response.ok) return response.html();
    } catch (error) {
    }
    return null;
}

function apiRequest(path) {
    let url = path;
    if (!/^https?:\/\//i.test(url)) url = stripTrailingSlash(API_URL) + (path.indexOf("/") === 0 ? path : "/" + path);
    try {
        let response = fetch(url, {
            headers: {
                "Accept": "application/json, text/plain, */*",
                "Accept-Language": "vi-VN,vi;q=0.9,en-US;q=0.8,en;q=0.7",
                "Origin": BASE_URL,
                "Referer": BASE_URL + "/",
                "User-Agent": MOBILE_UA
            }
        });
        if (!response.ok) return null;
        let text = response.text();
        if (!text) return null;
        return JSON.parse(text);
    } catch (error) {
        return null;
    }
}

function apiData(path) {
    let json = apiRequest(path);
    if (!json || !json.success) return null;
    return json.data;
}

function apiPath(path, params) {
    let query = [];
    params = params || {};
    for (let key in params) {
        if (params[key] === undefined || params[key] === null || params[key] === "") continue;
        query.push(encodeURIComponent(key) + "=" + encodeURIComponent(params[key]));
    }
    return path + (query.length ? "?" + query.join("&") : "");
}

function pathOf(url) {
    url = normalizeUrl(url);
    let match = /^https?:\/\/[^/]+(\/[^?#]*)?/i.exec(url);
    return match && match[1] ? match[1] : "/";
}

function isStoryUrl(url) {
    let path = pathOf(url).replace(/\/+$/, "");
    return /^\/novel\/[^/]+$/i.test(path);
}

function isChapterUrl(url) {
    let path = pathOf(url).replace(/\/+$/, "");
    if (!/^\/[^/]+\/[^/]+$/i.test(path)) return false;
    return !/^\/(novel|hot|search|the-loai|login|random|gioi-thieu|lien-he)(?:\/|$)/i.test(path);
}

function storySlugFromUrl(url) {
    let path = pathOf(url).replace(/\/+$/, "");
    let match = /^\/novel\/([^/]+)$/i.exec(path);
    if (match) return match[1];
    match = /^\/([^/]+)\/[^/]+$/i.exec(path);
    return match ? match[1] : "";
}

function chapterSlugFromUrl(url) {
    let path = pathOf(url).replace(/\/+$/, "");
    let match = /^\/[^/]+\/([^/]+)$/i.exec(path);
    return match ? match[1] : "";
}

function storyUrlFromSlug(slug) {
    return BASE_URL + "/novel/" + slug;
}

function storyUrlFromAny(url) {
    let slug = storySlugFromUrl(url);
    return slug ? storyUrlFromSlug(slug) : normalizeUrl(url);
}

function chapterUrl(storySlug, chSlug) {
    return BASE_URL + "/" + storySlug + "/" + chSlug;
}

function imageAttr(e) {
    if (!e) return "";
    let srcset = e.attr("data-srcset") || e.attr("srcset");
    if (srcset) return cleanText(srcset.split(",")[0].split(" ")[0]);
    return e.attr("data-original") || e.attr("data-src") || e.attr("data-lazy-src") || e.attr("src");
}

function imageFromNode(e) {
    return normalizeImage(imageAttr(e));
}

function metaContent(doc, selector) {
    let node = doc.select(selector).first();
    return node ? cleanText(node.attr("content")) : "";
}

function metaImage(doc) {
    return normalizeImage(metaContent(doc, "meta[property=og:image], meta[property='og:image:secure_url'], meta[name=twitter:image], meta[itemprop=image]"));
}

function firstText(node, selectors) {
    for (let i = 0; i < selectors.length; i++) {
        let e = node.select(selectors[i]).first();
        let text = e ? cleanText(e.text()) : "";
        if (text) return text;
    }
    return "";
}

function firstHtml(node, selectors) {
    for (let i = 0; i < selectors.length; i++) {
        let e = node.select(selectors[i]).first();
        if (e && cleanText(e.text())) return e.html();
    }
    return "";
}

function statusText(status) {
    status = cleanText(status);
    if (status === "completed" || status === "complete") return "Hoan thanh";
    if (status === "hiatus") return "Tam ngung";
    if (status === "ongoing") return "Dang tien hanh";
    return status;
}

function novelSlug(novel) {
    if (!novel) return "";
    if (typeof novel.slug === "string") return novel.slug;
    if (novel.slug && novel.slug.current) return novel.slug.current;
    return "";
}

function chapterSlug(chapter) {
    if (!chapter) return "";
    if (typeof chapter.slug === "string") return chapter.slug;
    if (chapter.slug && chapter.slug.current) return chapter.slug.current;
    return "";
}

function novelItem(novel) {
    let slug = novelSlug(novel);
    let parts = [];
    if (novel.author) parts.push(cleanText(novel.author));
    if (novel.latestChapter && novel.latestChapter.title) parts.push(cleanText(novel.latestChapter.title));
    if (novel.chapterCount) parts.push(novel.chapterCount + " chuong");
    return {
        name: cleanText(novel.title),
        link: slug ? storyUrlFromSlug(slug) : "",
        cover: normalizeImage(novel.coverImageUrl || novel.coverImageLandscapeUrl),
        description: parts.join(" - "),
        host: BASE_URL
    };
}

function pageNum(page) {
    page = parseInt(page || "1");
    return page > 0 ? page : 1;
}

function nextPageFromPagination(pagination) {
    if (!pagination) return "";
    let current = parseInt(pagination.page || 1);
    let total = parseInt(pagination.totalPages || 1);
    return current < total ? "" + (current + 1) : "";
}

function chapterNumber(text) {
    let folded = foldText(text || "").replace(/_/g, "-");
    let match = /(?:chuong|chapter|chap|c)[-\s]*(\d+(?:[.-]\d+)?)/i.exec(folded);
    if (!match) match = /(\d+(?:[.-]\d+)?)/.exec(folded);
    return match ? parseFloat(match[1].replace("-", ".")) : 0;
}

function htmlDecode(text) {
    if (!text) return "";
    return ("" + text)
        .replace(/&amp;/g, "&")
        .replace(/&quot;/g, "\"")
        .replace(/&#039;/g, "'")
        .replace(/&apos;/g, "'")
        .replace(/&lt;/g, "<")
        .replace(/&gt;/g, ">");
}

function stripBadChapterBlocks(html) {
    if (!html) return "";
    html = html.replace(/<p[^>]*style=["'][^"']*display\s*:\s*none[^"']*["'][^>]*>[\s\S]*?<\/p>/ig, "");
    html = html.replace(/<div\b[^>]*class=["'][^"']*note-reg[^"']*["'][^>]*>[\s\S]*?<\/div>\s*$/ig, "");
    html = html.replace(/<a\b[^>]*>\s*<img\b[^>]*chapter-banners[\s\S]*?<\/a>/ig, "");
    html = html.replace(/<img\b[^>]*chapter-banners[^>]*>/ig, "");
    html = html.replace(/\[note\d+\]/g, "");
    return html.trim();
}

function imageHeaders(url) {
    return {
        "Referer": normalizeUrl(url),
        "User-Agent": MOBILE_UA
    };
}

function execute() {
    return Response.success({baseUrl: BASE_URL, apiUrl: API_URL, ok: true});
}
