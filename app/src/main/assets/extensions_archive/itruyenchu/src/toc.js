load('config.js');

function execute(url) {
    let slug = getSlug(url);
    let book = fetchJson(API_URL + "/books/" + encodeURIComponent(slug));
    let doc = fetchDocument(bookUrl(slug));
    if (!doc && !book) return null;

    let schema = doc ? parseBookSchema(doc) : null;
    let total = book && book.currentChapter ? parseInt(book.currentChapter) : (doc ? parseTotalChapters(doc, schema) : 0);
    let data = [];

    if (!total) {
        let links = doc ? doc.select("a[href*='/chuong-']") : [];
        links.forEach(function (e) {
            let href = e.attr("href");
            let name = cleanText(e.text());
            if (href && name) data.push({ name: name, url: normalizeUrl(href), host: BASE_URL });
        });
        return Response.success(data);
    }

    for (let i = 1; i <= total; i++) {
        data.push({
            name: "Chương " + i,
            url: chapterUrl(slug, i),
            host: BASE_URL
        });
    }

    return Response.success(data);
}
