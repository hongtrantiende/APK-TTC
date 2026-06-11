load("config.js");

function toAbsolute(url) {
    if (!url) return "";
    url = String(url).trim();
    if (!url) return "";
    if (url.startsWith("http")) return url;
    if (url.startsWith("//")) return "https:" + url;
    return BASE_URL + (url.startsWith("/") ? "" : "/") + url;
}

function normalizeUrl(url) {
    if (!url) return BASE_URL;
    if (url.startsWith("http")) return url.replace(/^http:\/\//i, "https://");
    return BASE_URL + (url.startsWith("/") ? "" : "/") + url;
}

function normalizeStoryUrl(url) {
    let normalized = normalizeUrl(url);
    let m = normalized.match(/(https?:\/\/[^\/]+\/doc-truyen\/[^\/?#]+)/i);
    return m ? m[1] : normalized;
}

function fetchDoc(url) {
    let response = fetch(url);
    if (response && response.ok) {
        return response.html();
    }

    if (response && (response.status === 400 || response.status === 401 || response.status === 403 || response.status === 503)) {
        let browser = Engine.newBrowser();
        browser.launch(url, 7000);
        let doc = browser.html();
        browser.close();
        return doc;
    }

    return null;
}

function cleanText(text) {
    return (text || "").replace(/\s+/g, " ").trim();
}

function extractStoryId(doc) {
    let id = doc.select("#story_id_hidden").attr("value");
    if (id) return parseInt(id);

    let metaId = doc.select("meta[name='book_detail']").attr("content");
    if (metaId) return parseInt(metaId);

    return 0;
}

function extractMaxPageIndex(doc) {
    let maxIndex = 0;
    doc.select("[onclick*='Loading(']").forEach(el => {
        let onclick = el.attr("onclick") || "";
        let m = onclick.match(/Loading\((\d+)\)/i);
        if (m) {
            let idx = parseInt(m[1]);
            if (idx > maxIndex) maxIndex = idx;
        }
    });
    return maxIndex;
}

function chapterAnchors(doc) {
    let links = [];
    let selectors = [
        ".catalog-content-wrap .list-chapter a[href*='/doc-truyen/'][href*='chuong-']",
        ".list-chapter a[href*='/doc-truyen/'][href*='chuong-']",
        ".catalog-content-wrap a[href*='/doc-truyen/'][href*='chuong-']",
        ".volume-wrap a[href*='/doc-truyen/'][href*='chuong-']"
    ];

    selectors.forEach(selector => {
        doc.select(selector).forEach(a => links.push(a));
    });

    if (links.length === 0) {
        doc.select("a[href*='/doc-truyen/'][href*='chuong-']").forEach(a => links.push(a));
    }

    return links;
}

function chapterNameFromAnchor(a, link) {
    let title = cleanText(a.attr("title"));
    let text = cleanText(a.text());
    let name = title || text;
    if (name) return name;

    let no = chapterNumber(link);
    return no > 0 ? "Chuong " + no : "";
}

function collectChapters(doc, seen, list) {
    chapterAnchors(doc).forEach(a => {
        let id = cleanText(a.attr("id")).toLowerCase();
        if (id === "readbtn") return;

        let href = cleanText(a.attr("href"));
        if (!href || href.indexOf("javascript:") === 0) return;

        let link = toAbsolute(href);
        if (!link || seen[link]) return;

        let name = chapterNameFromAnchor(a, link);
        if (!name) return;

        seen[link] = true;
        list.push({
            name: name,
            url: link,
            host: BASE_URL
        });
    });
}

function fetchChapterPage(storyId, pageIndex) {
    let url = BASE_URL + "/doc-truyen/page/" + storyId + "?page=" + pageIndex + "&limit=75&web=1";
    return fetchDoc(url);
}

function chapterNumber(url) {
    let re = /chuong-(\d+)/gi;
    let m = null;
    let last = 0;
    while (true) {
        m = re.exec(url || "");
        if (!m) break;
        last = parseInt(m[1]);
    }
    return last;
}

function sortChapters(list) {
    list.sort((a, b) => {
        let na = chapterNumber(a.url);
        let nb = chapterNumber(b.url);
        if (na > 0 && nb > 0 && na !== nb) return na - nb;
        return 0;
    });
}

function execute(url) {
    let storyUrl = normalizeStoryUrl(url);
    let detailDoc = fetchDoc(storyUrl);
    if (!detailDoc) return Response.success([]);

    let storyId = extractStoryId(detailDoc);
    let maxPageIndex = extractMaxPageIndex(detailDoc);

    let list = [];
    let seen = {};
    collectChapters(detailDoc, seen, list);

    if (list.length === 0 && storyId) {
        let page0Doc = fetchChapterPage(storyId, 0);
        if (page0Doc) {
            collectChapters(page0Doc, seen, list);
            let endpointMax = extractMaxPageIndex(page0Doc);
            if (endpointMax > maxPageIndex) maxPageIndex = endpointMax;
        }
    }

    if (storyId && maxPageIndex > 0) {
        for (let i = 1; i <= maxPageIndex && i <= 300; i++) {
            let doc = fetchChapterPage(storyId, i);
            if (!doc) continue;
            collectChapters(doc, seen, list);
        }
    }

    sortChapters(list);
    return Response.success(list);
}
