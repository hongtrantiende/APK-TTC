let BASE_URL = "https://mattreadingtranslations.com";

try { if (CONFIG_URL) { BASE_URL = ("" + CONFIG_URL).trim().replace(/\/+$/, ""); } } catch (e) {}

function cleanText(text) {
    return (text || "").replace(/\s+/g, " ").trim();
}

function toAbsolute(url) {
    if (!url) return "";
    let raw = ("" + url).trim();
    if (!raw) return "";
    if (raw.startsWith("http://")) return "https://" + raw.substring(7);
    if (raw.startsWith("https://")) return raw;
    if (raw.startsWith("//")) return "https:" + raw;
    return BASE_URL + (raw.startsWith("/") ? "" : "/") + raw;
}

function normalizeUrl(url) {
    if (!url) return BASE_URL;
    return toAbsolute(url);
}

function buildRequestOptions(extraHeaders) {
    let headers = {
        Accept: "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Language": "en-US,en;q=0.9",
        "Cache-Control": "no-cache",
        Pragma: "no-cache",
        Referer: BASE_URL + "/",
    };
    if (extraHeaders) {
        Object.keys(extraHeaders).forEach(k => { headers[k] = extraHeaders[k]; });
    }
    let options = {};
    if (Object.keys(headers).length > 0) options.headers = headers;
    return options;
}

function fetchDoc(url) {
    let finalUrl = normalizeUrl(url);
    let response = null;
    try { response = fetch(finalUrl, buildRequestOptions()); } catch (e) {}
    if (response && response.ok) return response.html();
    try { response = fetch(finalUrl); } catch (e) {}
    if (response && response.ok) return response.html();
    return null;
}

function fetchText(url) {
    let finalUrl = normalizeUrl(url);
    let response = null;
    try { response = fetch(finalUrl, buildRequestOptions()); } catch (e) {}
    if (response && response.ok) { try { return response.text(); } catch (e) {} }
    try { response = fetch(finalUrl); } catch (e) {}
    if (response && response.ok) { try { return response.text(); } catch (e) {} }
    return "";
}

function parseNovelCards(doc) {
    let list = [];
    if (!doc) return list;
    let seen = {};
    let content = doc.select(".entry-content").first();
    if (!content) return list;
    content.select("p").forEach(p => {
        let linkEl = p.select("a[href]").first();
        if (!linkEl) return;
        let href = normalizeUrl(linkEl.attr("href"));
        let text = cleanText(linkEl.text());
        if (!text || !href) return;
        if (text.toLowerCase().indexOf("chapter") > -1) return;
        if (href.indexOf(BASE_URL) !== 0) return;
        if (seen[href]) return;
        seen[href] = true;
        let imgEl = p.select("img").first();
        let cover = imgEl ? toAbsolute(imgEl.attr("src") || "") : "";
        list.push({ name: text, link: href, cover: cover, description: "", host: BASE_URL });
    });
    return list;
}

function parseCategoryLinks(doc) {
    let list = [];
    if (!doc) return list;
    doc.select("ul.wp-block-categories-list li.cat-item a").forEach(a => {
        let href = normalizeUrl(a.attr("href") || "");
        let title = cleanText(a.text());
        if (title && href) list.push({ title: title, url: href });
    });
    return list;
}

function parseTocPage(doc) {
    let chapters = [];
    if (!doc) return chapters;
    doc.select("table.chapter-table tbody tr").forEach(tr => {
        let linkEl = tr.select("td a").first();
        if (!linkEl) return;
        let url = normalizeUrl(linkEl.attr("href"));
        let name = cleanText(linkEl.text());
        if (name && url) chapters.push({ name: name, url: url, host: BASE_URL });
    });
    return chapters;
}

function parseMaxPage(doc) {
    if (!doc) return 1;
    let max = 1;
    doc.select("div.pagination a.page-numbers").forEach(a => {
        let num = parseInt(cleanText(a.text()), 10);
        if (num > max) max = num;
    });
    return max;
}

function withPage(url, page) {
    let u = normalizeUrl(url).replace(/\/+$/, "");
    if (page <= 1) return u + "/";
    return u + "/page/" + page + "/";
}

function extractChapterContent(doc) {
    if (!doc) return "";
    let el = doc.select("div.entry-content").first();
    if (!el) return "";
    el.select("script, style, ins.adsbygoogle, .code-block, .adsbygoogle").remove();
    let html = el.html();
    html = html.replace(/<nav[\s\S]*?<\/nav>/gi, "");
    return html;
}
