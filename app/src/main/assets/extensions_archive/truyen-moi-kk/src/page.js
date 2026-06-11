function execute(url) {
    let fetchUrl = normalizeUrl(url);
    let pageText = Http.get(fetchUrl).string();
    if (!pageText && fetchUrl !== url) pageText = Http.get(url).string();
    if (!pageText) return Response.success([url]);

    let canonicalUrl = getCanonicalUrl(pageText) || fetchUrl;
    let baseUrl = getBaseUrl(canonicalUrl);
    let novelPath = getNovelPath(canonicalUrl);
    let totalPages = getTotalPages(pageText);

    let list = [];
    for (let i = 1; i <= totalPages; i++) {
        list.push(i === 1 ? baseUrl + novelPath : baseUrl + novelPath + "/trang-" + i);
    }

    return Response.success(list);
}

function getTotalPages(html) {
    let totalPages = 1;
    let regex = /href=["'][^"']*(?:trang-|page=)(\d+)[^"']*["']/ig;
    let m;

    while ((m = regex.exec(html)) !== null) {
        let page = parseInt(m[1]);
        if (page > totalPages) totalPages = page;
    }

    if (totalPages === 1) {
        let totalChapterMatch = html.match(/id=["']totalChapter["'][^>]*>\s*([\d.,]+)/i);
        if (totalChapterMatch) {
            let totalChapter = parseInt(totalChapterMatch[1].replace(/[^\d]/g, ""));
            if (totalChapter > 50) totalPages = Math.ceil(totalChapter / 50);
        }
    }

    return totalPages;
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
    let path = String(url || "")
        .replace(/^https?:\/\/(?:www\.)?(?:truyenmoikk\.com|truyenmoiss\.com|truyenmoiii\.org|truyenmoiyy\.com)/i, "")
        .replace(/[?#].*$/, "")
        .replace(/\/$/, "");

    path = path.replace(/\/trang-\d+$/i, "");
    return path || "/";
}

function normalizeUrl(url) {
    return String(url || "").replace(/^https?:\/\/(?:www\.)?(?:truyenmoikk\.com|truyenmoiii\.org|truyenmoiyy\.com)/i, "https://truyenmoiss.com");
}
