load("config.js");

var AUTHOR_RE = /\/manga-author\/([^\/]+)\/?/;
var GENRE_RE = /\/manga-genre\/([^\/]+)\/?/;

function textByHeading(doc, heading) {
    var items = doc.select(".post-content_item");
    for (var i = 0; i < items.size(); i++) {
        var item = items.get(i);
        var headEl = selFirst(item, ".summary-heading");
        if (!headEl) continue;
        var head = headEl.text().trim();
        if (head !== heading) continue;

        var contentEl = selFirst(item, ".summary-content");
        return contentEl ? contentEl.text().trim() : "";
    }
    return "";
}

function execute(url) {
    var storyUrl = resolveUrl(url);
    var res = fetchRetry(storyUrl);
    if (!res || !res.ok) return Response.error("Khong tai duoc trang truyen");
    var doc = res.html();
    if (!doc) return Response.error("Khong doc duoc noi dung trang");

    var titleEl = selFirst(doc, "h1");
    var coverEl = selFirst(doc, ".summary_image img, .summary_image a img");
    var authorEl = selFirst(doc, ".author-content a[href], .summary-content a[href*='/manga-author/']");
    var descEl = selFirst(doc, ".summary__content, .description-summary");

    var name = titleEl ? titleEl.text().trim() : "";
    var cover = "";
    if (coverEl) {
        cover = coverEl.attr("data-src") || coverEl.attr("data-lazy-src") || coverEl.attr("data-lazy") || coverEl.attr("src") || "";
        if (cover && cover.indexOf("http") !== 0) cover = BASE_URL + cover;
    }

    var author = authorEl ? authorEl.text().trim() : "";
    var authorHref = authorEl ? (authorEl.attr("href") || "") : "";
    var authorMatch = AUTHOR_RE.exec(authorHref);

    var genres = [];
    var genreEls = doc.select(".summary-content a[href*='/manga-genre/'], .genres-content a[href*='/manga-genre/']");
    for (var gi = 0; gi < genreEls.size(); gi++) {
        var genreEl = genreEls.get(gi);
        var genreName = genreEl.text().trim();
        if (!genreName) continue;
        var genreHref = genreEl.attr("href") || "";
        var genreMatch = GENRE_RE.exec(genreHref);
        if (!genreMatch) continue;
        genres.push({ title: genreName, input: genreMatch[1], script: "genrecontent.js" });
    }

    var tags = [];
    var tagEls = doc.select(".summary-content a[href*='/manga-tag/']");
    for (var ti = 0; ti < tagEls.size(); ti++) {
        var tagName = tagEls.get(ti).text().trim();
        if (tagName) tags.push(tagName);
    }

    var descParts = [];
    if (descEl) {
        var ps = descEl.select("p");
        for (var pi = 0; pi < ps.size(); pi++) {
            var part = ps.get(pi).text().trim();
            if (part) descParts.push(part);
        }
        if (descParts.length === 0) {
            var fallback = descEl.text().trim();
            if (fallback) descParts.push(fallback);
        }
    }

    var status = textByHeading(doc, "Status");
    var alternative = textByHeading(doc, "Alternative");
    var type = textByHeading(doc, "Type");
    var ongoing = true;
    if (status) {
        var lowerStatus = status.toLowerCase();
        ongoing = lowerStatus.indexOf("complete") === -1 && lowerStatus.indexOf("hoan") === -1;
    }

    var detailParts = [];
    if (status) detailParts.push("Tinh trang: " + status);
    if (type) detailParts.push("Loai: " + type);
    if (alternative) detailParts.push("Ten khac: " + alternative);
    if (tags.length > 0) detailParts.push("Tags: " + tags.join(", "));
    detailParts.push("Noi dung: 18+");

    var suggests = [];
    if (authorMatch) {
        suggests.push({ title: "Cung tac gia: " + author, input: "author:" + authorMatch[1], script: "suggest.js" });
    }

    return Response.success({
        name: name,
        cover: cover,
        host: HOST,
        author: author,
        description: descParts.join("\n"),
        detail: detailParts.join("\n"),
        ongoing: ongoing,
        genres: genres,
        suggests: suggests,
        comments: []
    });
}