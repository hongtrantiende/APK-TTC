function execute(url) {
    let fetchUrl = normalizeUrl(url);
    let pageText = Http.get(fetchUrl).string();
    if (!pageText && fetchUrl !== url) pageText = Http.get(url).string();
    if (!pageText) return null;

    let canonicalUrl = getCanonicalUrl(pageText) || fetchUrl;
    let baseUrl = getBaseUrl(canonicalUrl);
    let novelPath = getNovelPath(canonicalUrl);
    let htmlInner = getChapterListHtml(pageText);

    if (!htmlInner) return null;

    let list = [];
    let aRegex = /<a[^>]*href=["']([^"']+)["'][^>]*>([\s\S]*?)<\/a>/gi;
    let m;

    while ((m = aRegex.exec(htmlInner)) !== null) {
        let link = m[1];
        let name = cleanText(m[2]);
        if (!link || !name || isPaginationName(name)) continue;

        let linkPath = getUrlPath(link);
        if (isPaginationPath(linkPath) || !isChapterPath(linkPath, novelPath)) continue;

        list.push({
            name: name,
            url: baseUrl + linkPath,
            host: baseUrl
        });
    }

    let dedupArray = [];
    let seenUrls = {};
    for (let i = 0; i < list.length; i++) {
        let item = list[i];
        if (!seenUrls[item.url]) {
            seenUrls[item.url] = true;
            dedupArray.push(item);
        }
    }

    return Response.success(dedupArray);
}

function getChapterListHtml(html) {
    html = html.replace(/<ul[^>]*id=["']chuong-moi-nhat["'][^>]*>[\s\S]*?<\/ul>/gi, "");

    let htmlInner = "";
    let listBlockRegex = /<ul[^>]*id=["']list-chapter["'][^>]*>([\s\S]*?)<\/ul>|<ul[^>]*class=["'][^"']*list-chapter[^"']*["'][^>]*>([\s\S]*?)<\/ul>/gi;
    let blockMatch;

    while ((blockMatch = listBlockRegex.exec(html)) !== null) {
        htmlInner += (blockMatch[1] || blockMatch[2] || "");
    }

    return htmlInner;
}

function cleanText(html) {
    return String(html || "")
        .replace(/<\/?[^>]+(>|$)/g, "")
        .replace(/&nbsp;/gi, " ")
        .replace(/&amp;/gi, "&")
        .replace(/&quot;/gi, "\"")
        .replace(/&#039;/gi, "'")
        .replace(/\s+/g, " ")
        .trim();
}

function isPaginationName(name) {
    let lower = name.toLowerCase();
    let badSymbols = ["\u00bb", "\u00ab", "<", ">", "\u203a", "\u2039", "...", ".."];
    return /^\d+$/.test(name) || badSymbols.indexOf(name) !== -1 || lower.indexOf("trang") === 0;
}

function isPaginationPath(path) {
    return /\/trang-\d+$/i.test(path) || /\?page=\d+/i.test(path);
}

function isChapterPath(linkPath, novelPath) {
    return linkPath.indexOf(novelPath + "/chuong-") === 0
        || linkPath.indexOf(novelPath + "/chuong/") === 0
        || linkPath.indexOf(novelPath + "/chapter-") === 0;
}

function getBaseUrl(url) {
    let match = String(url || "").match(/^https?:\/\/[^\/]+/i);
    return match ? match[0] : "https://truyenmoiss.com";
}

function getCanonicalUrl(html) {
    let match = html.match(/<link[^>]+rel=["']canonical["'][^>]+href=["']([^"']+)["']/i);
    if (!match) match = html.match(/<link[^>]+href=["']([^"']+)["'][^>]+rel=["']canonical["']/i);
    return match ? match[1] : "";
}

function getNovelPath(url) {
    let path = getUrlPath(url)
        .replace(/\/trang-\d+$/i, "")
        .replace(/\/$/, "");
    return path || "/";
}

function getUrlPath(url) {
    let value = String(url || "").trim();
    if (value.indexOf("http") !== 0) {
        if (value.charAt(0) !== "/") value = "/" + value;
        return value.replace(/[?#].*$/, "").replace(/\/$/, "");
    }

    return value
        .replace(/^https?:\/\/(?:www\.)?(?:truyenmoikk\.com|truyenmoiss\.com|truyenmoiii\.org|truyenmoiyy\.com)/i, "")
        .replace(/[?#].*$/, "")
        .replace(/\/$/, "");
}

function normalizeUrl(url) {
    return String(url || "").replace(/^https?:\/\/(?:www\.)?(?:truyenmoikk\.com|truyenmoiii\.org|truyenmoiyy\.com)/i, "https://truyenmoiss.com");
}
