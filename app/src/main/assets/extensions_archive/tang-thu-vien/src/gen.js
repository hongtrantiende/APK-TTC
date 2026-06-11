load("config.js");

function toAbsolute(url) {
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

function withPage(url, page) {
    let p = parseInt(page || "1");
    if (p < 1) p = 1;
    if (/([?&])page=\d+/i.test(url)) {
        return url.replace(/([?&])page=\d+/i, "$1page=" + p);
    }
    return url + (url.indexOf("?") > -1 ? "&" : "?") + "page=" + p;
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

function parseCardList(doc) {
    let list = [];
    let items = doc.select(".book-img-text > ul > li");
    items.forEach(item => {
        let titleEl = item.select(".book-mid-info h4 a").first();
        if (!titleEl) titleEl = item.select("h4 a[href*='/doc-truyen/']").first();
        if (!titleEl) return;

        let name = cleanText(titleEl.text());
        let link = toAbsolute(titleEl.attr("href"));

        let coverEl = item.select(".book-img-box img").first();
        if (!coverEl) coverEl = item.select("img").first();
        let cover = "";
        if (coverEl) cover = coverEl.attr("data-src") || coverEl.attr("src") || "";

        let author = cleanText(item.select("p.author").text());
        let update = cleanText(item.select("p.update").text());
        let intro = cleanText(item.select("p.intro").text());
        if (intro.length > 180) intro = intro.substring(0, 177) + "...";

        let descParts = [];
        if (author) descParts.push(author);
        if (update) descParts.push(update);
        if (intro) descParts.push(intro);

        if (name && link) {
            list.push({
                name: name,
                link: link,
                cover: toAbsolute(cover),
                description: descParts.join(" | "),
                host: BASE_URL
            });
        }
    });
    return list;
}

function parseUpdateTable(doc) {
    let list = [];
    let rows = doc.select(".update-table tbody tr, .update-list table tbody tr");
    rows.forEach(row => {
        let titleEl = row.select("a.name[href*='/doc-truyen/']").first();
        if (!titleEl) return;

        let name = cleanText(titleEl.text());
        let link = toAbsolute(titleEl.attr("href"));

        let category = cleanText(row.select("a.classify").text());
        let chapter = cleanText(row.select("a.section").text());
        let author = cleanText(row.select("a.author").text());
        let timeEl = row.select("td.respon i").first();
        let time = timeEl ? cleanText(timeEl.text()) : "";

        let descParts = [];
        if (category) descParts.push(category);
        if (author) descParts.push(author);
        if (chapter) descParts.push(chapter);
        if (time) descParts.push(time);

        if (name && link) {
            list.push({
                name: name,
                link: link,
                cover: "",
                description: descParts.join(" | "),
                host: BASE_URL
            });
        }
    });
    return list;
}

function parseRankingRows(doc) {
    let list = [];
    let rows = doc.select(".list-page .row");
    rows.forEach(row => {
        let titleEl = row.select(".truyen-title a[href*='/doc-truyen/']").first();
        if (!titleEl) return;

        let name = cleanText(titleEl.text());
        let link = toAbsolute(titleEl.attr("href"));
        if (!name || !link) return;

        let coverEl = row.select(".item-image img").first();
        let cover = "";
        if (coverEl) cover = coverEl.attr("data-src") || coverEl.attr("src") || "";

        let descParts = [];
        row.select("p.item-author, p.item-update").forEach(p => {
            let t = cleanText(p.text());
            if (t) descParts.push(t);
        });

        list.push({
            name: name,
            link: link,
            cover: toAbsolute(cover),
            description: descParts.join(" | "),
            host: BASE_URL
        });
    });
    return list;
}

function parseList(doc) {
    let list = parseCardList(doc);
    if (list.length > 0) return list;

    list = parseUpdateTable(doc);
    if (list.length > 0) return list;

    return parseRankingRows(doc);
}

function parsePageFromHref(href) {
    let m = (href || "").match(/[?&]page=(\d+)/i);
    return m ? parseInt(m[1]) : 0;
}

function parseNext(doc, page) {
    let current = parseInt(page || "1");

    let nextRel = doc.select("ul.pagination a[rel=next]").first();
    if (nextRel) {
        let nextPage = parsePageFromHref(nextRel.attr("href"));
        if (nextPage > current) return String(nextPage);
        return String(current + 1);
    }

    let min = Number.POSITIVE_INFINITY;
    doc.select("ul.pagination a[href*='page=']").forEach(a => {
        let p = parsePageFromHref(a.attr("href"));
        if (p > current && p < min) min = p;
    });

    if (min !== Number.POSITIVE_INFINITY) return String(min);
    return "";
}

function execute(url, page) {
    if (!page) page = "1";

    let finalUrl = withPage(normalizeUrl(url), page);
    let doc = fetchDoc(finalUrl);
    if (!doc) return Response.success([]);

    let list = parseList(doc);
    let next = list.length > 0 ? parseNext(doc, page) : "";
    return Response.success(list, next);
}
