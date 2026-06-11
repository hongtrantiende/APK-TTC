let BASE_URL = "http://vnthuquan.org";

try { if (CONFIG_URL) { BASE_URL = ("" + CONFIG_URL).trim().replace(/\/+$/, ""); } } catch (e) {}

function cleanText(text) {
    return (text || "").replace(/\s+/g, " ").trim();
}

function toAbsolute(url) {
    if (!url) return "";
    let raw = ("" + url).trim();
    if (!raw) return "";
    if (raw.startsWith("http://") || raw.startsWith("https://")) return raw;
    if (raw.startsWith("//")) return "http:" + raw;
    if (raw.startsWith("/")) return BASE_URL + raw;
    return BASE_URL + "/" + raw;
}

function buildRequestOptions(extraHeaders) {
    let headers = {
        Accept: "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Language": "vi-VN,en-US;q=0.9,en;q=0.8",
        "Cache-Control": "no-cache",
        Pragma: "no-cache",
        Referer: BASE_URL + "/",
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36"
    };
    if (extraHeaders) {
        Object.keys(extraHeaders).forEach(k => { headers[k] = extraHeaders[k]; });
    }
    let options = {};
    if (Object.keys(headers).length > 0) options.headers = headers;
    return options;
}

function fetchDoc(url) {
    let response = null;
    try { response = fetch(url, buildRequestOptions()); } catch (e) {}
    if (response && response.ok) return response.html();
    try { response = fetch(url); } catch (e) {}
    if (response && response.ok) return response.html();
    return null;
}

function parseNovelList(doc) {
    let list = [];
    if (!doc) return list;

    doc.select("div.danhsach").forEach(box => {
        let nameEl = box.select("div.truyen-title span.viethoachu a").first();
        if (!nameEl) nameEl = box.select("div.truyen-title a").first();
        if (!nameEl) return;
        let href = toAbsolute(nameEl.attr("href"));
        let name = cleanText(nameEl.text());
        if (!name || !href) return;

        let coverEl = box.select("a img.img-rounded").first();
        if (!coverEl) coverEl = box.select("a img[src*='hinhbiasach']").first();
        let cover = coverEl ? toAbsolute(coverEl.attr("src") || "") : "";

        list.push({ name: name, link: href, cover: cover, description: "", host: BASE_URL });
    });

    return list;
}