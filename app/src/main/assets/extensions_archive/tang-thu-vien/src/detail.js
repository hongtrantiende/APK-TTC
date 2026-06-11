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

function parseChapterCount(doc) {
    let tab = cleanText(doc.select("#j-bookCatalogPage").text());
    let m = tab.match(/\((\d+)\s*chuong/i);
    return m ? m[1] : "";
}

function parseDetail(doc) {
    let name = cleanText(doc.select(".book-info h1").first() ? doc.select(".book-info h1").first().text() : "");
    if (!name) {
        let ogTitle = doc.select("meta[property='og:title']").attr("content");
        name = cleanText(ogTitle);
    }

    let cover = "";
    let coverEl = doc.select(".book-img img").first();
    if (coverEl) cover = coverEl.attr("data-src") || coverEl.attr("src") || "";
    if (!cover) {
        let ogImage = doc.select("meta[property='og:image']").attr("content");
        cover = ogImage || "";
    }

    let author = "";
    let authorEl = doc.select(".book-info p.tag a[href*='tac-gia?author=']").first();
    if (authorEl) author = cleanText(authorEl.text());

    let status = "";
    let statusEl = doc.select(".book-info p.tag span.blue").first();
    if (statusEl) status = cleanText(statusEl.text());

    let categories = [];
    doc.select(".book-info p.tag a[href*='/the-loai/']").forEach(a => {
        let t = cleanText(a.text());
        if (t) categories.push(t);
    });

    let description = "";
    let introBox = doc.select(".book-intro").first();
    if (introBox) description = introBox.html();
    if (!description) {
        let introLine = doc.select(".book-info p.intro").first();
        if (introLine) description = cleanText(introLine.text());
    }
    if (!description) {
        let metaDesc = doc.select("meta[name='description']").attr("content");
        description = cleanText(metaDesc);
    }

    let chapterCount = parseChapterCount(doc);
    let detailParts = [];
    if (author) detailParts.push("Tac gia: " + author);
    if (categories.length > 0) detailParts.push("The loai: " + categories.join(", "));
    if (status) detailParts.push("Trang thai: " + status);
    if (chapterCount) detailParts.push("So chuong: " + chapterCount);

    return {
        name: name,
        cover: toAbsolute(cover),
        author: author,
        description: description,
        detail: detailParts.join("<br>"),
        host: BASE_URL
    };
}

function execute(url) {
    let doc = fetchDoc(normalizeStoryUrl(url));
    if (!doc) {
        return Response.error("Khong the tai chi tiet truyen.");
    }

    let result = parseDetail(doc);
    if (!result.name) {
        return Response.error("Khong tim thay thong tin truyen.");
    }

    return Response.success(result);
}
