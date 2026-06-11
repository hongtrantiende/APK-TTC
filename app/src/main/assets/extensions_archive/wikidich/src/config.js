let BASE_URL = 'https://wikidich.com.vn';
try {
    if (CONFIG_URL) BASE_URL = CONFIG_URL;
} catch (error) {
}

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
    return url.replace(/^http:\/\/wikidich\.com\.vn/i, BASE_URL);
}

function normalizeImage(url) {
    if (!url) return "";
    url = ("" + url).replace(/&amp;/g, "&").trim();
    if (url.startsWith("//")) return "https:" + url;
    if (url.startsWith("/")) return stripTrailingSlash(BASE_URL) + url;
    if (!/^https?:\/\//i.test(url)) return stripTrailingSlash(BASE_URL) + "/" + url.replace(/^\/+/, "");
    return url;
}

function request(url, options) {
    options = options || {};
    let headers = options.headers || {};
    headers["Accept"] = headers["Accept"] || "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8";
    headers["Accept-Language"] = headers["Accept-Language"] || "vi-VN,vi;q=0.9,en-US;q=0.8,en;q=0.7";
    headers["Referer"] = headers["Referer"] || BASE_URL + "/";
    headers["User-Agent"] = headers["User-Agent"] || "Mozilla/5.0 (Linux; Android 12; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0 Mobile Safari/537.36";
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

function getText(url) {
    try {
        let response = request(url, {headers: {"X-Requested-With": "XMLHttpRequest"}});
        if (response.ok) return response.text();
    } catch (error) {
    }
    return "";
}

function pathOf(url) {
    url = normalizeUrl(url);
    let match = /^https?:\/\/[^/]+(\/[^?#]*)?/i.exec(url);
    return match && match[1] ? match[1] : "/";
}

function isReservedPath(path) {
    return /^(?:\/the-loai\/|\/danh-sach\/|\/tac-gia\/|\/tim-kiem|\/search|\/contact|\/tos|\/aff\/|\/cdn-cgi\/|\/images\/|\/theme\/|\/media\/|\/img\/|\/get\/|\/vote|\/comment|\/favicon\.ico)/i.test(path);
}

function isStoryUrl(url) {
    let path = pathOf(url).replace(/\/+$/, "");
    if (!path || path === "/" || isReservedPath(path)) return false;
    return /^\/[^/]+$/i.test(path);
}

function isChapterUrl(url) {
    let path = pathOf(url).replace(/\/+$/, "");
    return /^\/[^/]+\/chuong-[^/]+$/i.test(path);
}

function storyUrlFromAny(url) {
    url = normalizeUrl(url);
    let match = /^(https?:\/\/[^/]+\/[^/?#\/]+)/i.exec(url);
    return match ? match[1] : url;
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
    return normalizeImage(metaContent(doc, "meta[property=og:image], meta[name=twitter:image]"));
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

function lineValue(node) {
    let text = cleanText(node.text());
    return text.replace(/^Tac gia\s*:?\s*/i, "Tac gia: ")
        .replace(/^The loai\s*:?\s*/i, "The loai: ")
        .replace(/^So chuong\s*:?\s*/i, "So chuong: ")
        .replace(/^Luot xem\s*:?\s*/i, "Luot xem: ");
}

function labelValue(doc, label) {
    let target = foldText(label).replace(/\s+/g, " ");
    let nodes = doc.select(".book-info-text li, p.line, li");
    for (let i = 0; i < nodes.size(); i++) {
        let text = cleanText(nodes.get(i).text());
        let folded = foldText(text);
        if (folded.indexOf(target) === 0) {
            return cleanText(text.replace(/^[^:]+:\s*/, ""));
        }
    }
    return "";
}

function chapterNumber(text) {
    let m = /chuong[-\s]*(\d+(?:[-.]\d+)?)/i.exec(foldText(text || ""));
    if (!m) m = /(\d+(?:[-.]\d+)?)/.exec(text || "");
    return m ? parseFloat(m[1].replace("-", ".")) : 0;
}

function pageNumberFromUrl(url) {
    let m = /[?&]page=(\d+)/i.exec(url || "");
    return m ? parseInt(m[1]) : 1;
}

function addPageParam(url, page) {
    url = normalizeUrl(url);
    if (!page || page === "1") return url;
    if (("" + page).indexOf("http") === 0 || ("" + page).indexOf("/") === 0) return normalizeUrl(page);
    if (/[?&]page=\d+/i.test(url)) return url.replace(/([?&]page=)\d+/i, "$1" + page);
    return url + (url.indexOf("?") >= 0 ? "&" : "?") + "page=" + encodeURIComponent(page);
}

function listPageUrl(url, page) {
    if (!url) url = "/";
    return addPageParam(url, page);
}

function nextPage(doc, currentUrl) {
    let arrow = doc.select("a.btn-page[href]");
    for (let i = 0; i < arrow.size(); i++) {
        let a = arrow.get(i);
        let text = cleanText(a.text());
        if (text === "›" || text === "❭" || text.indexOf("Sau") >= 0) return normalizeUrl(a.attr("href"));
    }

    let current = pageNumberFromUrl(currentUrl || "");
    let links = doc.select("a[href*='page=']");
    let best = 0;
    let bestHref = "";
    for (let j = 0; j < links.size(); j++) {
        let href = links.get(j).attr("href");
        let p = pageNumberFromUrl(href);
        if (p > current && (!best || p < best)) {
            best = p;
            bestHref = href;
        }
    }
    return bestHref ? normalizeUrl(bestHref) : "";
}

function parseChapterPageInfo(html) {
    let text = html || "";
    let id = "";
    let total = 1;
    let re = /page\((\d+)\s*,\s*(\d+)\)/g;
    let m;
    while ((m = re.exec(text)) !== null) {
        id = id || m[1];
        let page = parseInt(m[2]);
        if (page > total) total = page;
    }
    return {id: id, total: total};
}

function execute() {
    return Response.success({baseUrl: BASE_URL, ok: true});
}
