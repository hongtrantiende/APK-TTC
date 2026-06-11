load("config.js");

function loadDetailDocument(url) {
    var requestUrl = normalizeUrl(url);
    var doc = fetchStableDocument(requestUrl, BASE_URL, BASE_URL);
    if (doc && doc.select(".book-intro").size() > 0) return doc;

    fetchText(BASE_URL, requestUrl);
    doc = fetchStableDocument(requestUrl, requestUrl, BASE_URL);
    if (doc && doc.select(".book-intro").size() > 0) return doc;

    return null;
}

function execute(url) {
    var requestUrl = normalizeUrl(url);
    var doc = loadDetailDocument(requestUrl);
    if (!doc) return null;

    var titleEl = doc.select(".book-intro h1").first();
    var coverEl = doc.select(".book-intro .cover img").first();
    var author = cleanText(doc.select(".book-intro .author").text());
    var desc = cleanText(doc.select(".book-intro .desc p").text());
    var typeEl = doc.select(".book-intro .type a").first();
    var awardText = cleanText(doc.select(".book-intro .award").text());
    var typeText = cleanText(doc.select(".book-intro .type").text());
    var cover = "";
    var genres = [];

    if (coverEl) {
        cover = coverEl.attr("data-original");
        if (!cover) cover = coverEl.attr("data-src");
        if (!cover) cover = coverEl.attr("src");
    }

    if (typeEl) {
        genres.push({
            title: cleanText(typeEl.text()),
            input: normalizeUrl(typeEl.attr("href")),
            script: "book.js"
        });
    }

    var detail = [];
    if (author) detail.push("<b>作者:</b> " + escapeHtml(author));
    if (typeText) detail.push("<b>分类:</b> " + escapeHtml(typeText));
    if (awardText) detail.push("<b>信息:</b> " + escapeHtml(awardText.replace(/\|/g, " | ")));

    return Response.success({
        name: titleEl ? cleanText(titleEl.text()) : "",
        cover: normalizeUrl(cover),
        author: author,
        description: desc ? "<p>" + escapeHtml(desc) + "</p>" : "",
        detail: detail.join("<br>"),
        host: BASE_URL,
        genres: genres,
        ongoing: awardText.indexOf("完结") === -1
    });
}
