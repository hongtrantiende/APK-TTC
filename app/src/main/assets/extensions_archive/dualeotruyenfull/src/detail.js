load("config.js");

function execute(url) {
    var detailUrl = resolveUrl(url);
    var doc = fetchDoc(detailUrl);
    if (!doc) return Response.error("Không tải được thông tin truyện");

    // Title
    var nameEl = selFirst(doc, "#manga-title, h1");
    var name = nameEl ? nameEl.text().trim() : "";

    // Cover
    var cover = "";
    var coverEl = selFirst(doc, ".story-cover img, .story-cover-wrap img");
    if (coverEl) cover = coverEl.attr("data-src") || coverEl.attr("src") || "";

    // Author - not always present
    var author = "";
    var authorEl = selFirst(doc, "a[href*='/tac-gia/']");
    if (authorEl) author = authorEl.text().trim();

    // Description
    var descEl = selFirst(doc, "#manga-description");
    var description = descEl ? stripHtml(descEl.html()) : "";

    // Genres
    var genres = [];
    var tagLinks = doc.select("a[href*='/the-loai/']");
    for (var gi = 0; gi < tagLinks.size(); gi++) {
        var tag = tagLinks.get(gi);
        var tagName = tag.text().trim();
        var tagHref = tag.attr("href") || "";
        if (tagName && tagHref.indexOf("/the-loai/") !== -1) {
            var input = tagHref.replace(/.*\/the-loai\//, "").replace(/\/$/, "");
            if (input && input.indexOf("/") === -1) {
                genres.push({ name: tagName, input: input, script: "genrecontent.js" });
            }
        }
    }

    return Response.success({
        name: name,
        author: author,
        cover: cover,
        description: description,
        genres: genres
    });
}
