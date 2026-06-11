load("config.js");

function isBookPage(d) {
    if (!d) return false;
    var h = selFirst(d, "a[href*='/writer/'], a[href*='/category/']");
    return h != null;
}

function loadDoc(pageUrl) {
    var doc = null;
    var browser = Engine.newBrowser();
    try {
        doc = browser.launch(pageUrl, 15000);
    } catch (e) {
        doc = null;
    }
    try { browser.close(); } catch (e2) {}
    return doc;
}

function execute(url) {
    var fullUrl = resolveUrl(url);

    var doc = loadDoc(fullUrl);

    if (!isBookPage(doc)) {
        doc = null;
    }

    if (!doc) {
        var res = fetchRetry(fullUrl);
        if (res && res.ok) {
            var tmp = res.html();
            if (isBookPage(tmp)) doc = tmp;
        }
    }

    if (doc) {
        var titleEl = selFirst(doc, "h1 a[href*='/book/']");
        if (!titleEl) titleEl = selFirst(doc, "h1");
        var title = titleEl ? cleanText(titleEl.text()) : "";
        if (title && title.indexOf("404") < 0 && title.indexOf("Not Found") < 0) {
            var authorA = selFirst(doc, "a[href*='/writer/']");
            var author = authorA ? cleanText(authorA.text()) : "Unknown";

            var categoryA = null;
            var links = doc.select("a[href]");
            for (var i = 0; i < links.size(); i++) {
                var link = links.get(i);
                var href = resolveUrl(link.attr("href") || "");
                if (CATEGORY_LINK_RE.test(href)) {
                    categoryA = link;
                    break;
                }
            }

            var descEl = selFirst(doc, "h3:matchesOwn(内容简介) + p");
            var description = descEl ? cleanText(descEl.text()) : "";

            var updateText = "";
            var paragraphs = doc.select("p");
            for (var pi = 0; pi < paragraphs.size(); pi++) {
                var text = cleanText(paragraphs.get(pi).text());
                if (text.indexOf("更新时间：") === 0) {
                    updateText = text;
                    break;
                }
            }

            var detailParts = [];
            if (categoryA) detailParts.push("类别: " + cleanText(categoryA.text()));
            if (updateText) detailParts.push(updateText);
            detailParts.push("内容分级: 18+");

            var genres = [];
            if (categoryA) {
                var genre = buildGenre(categoryA.text(), categoryA.attr("href"));
                if (genre) genres.push(genre);
            }

            return Response.success({
                name: title,
                cover: extractCover(doc),
                host: HOST,
                author: author,
                description: description,
                detail: detailParts.join("\n"),
                ongoing: true,
                genres: genres,
                suggests: collectSuggests(doc, fullUrl),
                comments: []
            });
        }
    }

    var catalogUrl = fullUrl;
    if (catalogUrl.charAt(catalogUrl.length - 1) !== "/") catalogUrl += "/";
    catalogUrl += "catalog/";

    var catDoc = loadDoc(catalogUrl);
    if (!catDoc) {
        var catRes = fetchRetry(catalogUrl);
        if (catRes && catRes.ok) catDoc = catRes.html();
    }

    if (catDoc) {
        var catTitleEl = selFirst(catDoc, "h1 a[href*='/book/']");
        if (!catTitleEl) catTitleEl = selFirst(catDoc, "h1");
        var catTitle = catTitleEl ? cleanText(catTitleEl.text()) : "";
        if (catTitle && catTitle.indexOf("404") < 0) {
            return Response.success({
                name: catTitle,
                cover: DEFAULT_COVER,
                host: HOST,
                author: "Unknown",
                description: "",
                detail: "内容分级: 18+",
                ongoing: true,
                genres: [],
                suggests: collectSuggests(catDoc, fullUrl),
                comments: []
            });
        }
    }

    return Response.error("Khong tai duoc thong tin truyen");
}
