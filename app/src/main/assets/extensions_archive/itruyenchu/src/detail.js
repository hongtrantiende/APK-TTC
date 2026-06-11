load('config.js');

function execute(url) {
    url = normalizeUrl(url);
    let slug = getSlug(url);
    let book = fetchJson(API_URL + "/books/" + encodeURIComponent(slug));
    let doc = fetchDocument(bookUrl(slug));
    if (!doc && !book) return null;

    let schema = doc ? parseBookSchema(doc) : null;
    let title = book && book.title ? book.title : (schema && schema.name ? schema.name : cleanText(doc.select("h1").first().text()));
    let author = book && book.tacGia ? book.tacGia : "";
    if (!author && schema && schema.author && schema.author.name) author = schema.author.name;
    if (!author && doc) author = cleanText(doc.select("h2:contains(Tác giả), h1 + h2, h1 ~ h2").first().text());

    let cover = book && book.bannerURL ? book.bannerURL : (schema && schema.image ? schema.image : "");
    if (!cover && doc) {
        let img = doc.select("picture img, img[alt*='book cover'], img[src*='/book-cover/']").first();
        cover = img ? absoluteAsset(img.attr("src")) : fullCoverUrl(slug);
    }

    let description = "";
    if (book && book.description) description = responseContent(book.description);
    if (!description && schema && schema.description) description = "<p>" + schema.description + "</p>";
    let intro = doc ? doc.select("article p, .space-y-2 p") : null;
    if (intro && intro.size() > 0) {
        let parts = [];
        intro.forEach(function (p) {
            let text = cleanText(p.text());
            if (text) parts.push("<p>" + text + "</p>");
        });
        if (parts.length) description = parts.join("");
    }

    let total = book && book.currentChapter ? parseInt(book.currentChapter) : (doc ? parseTotalChapters(doc, schema) : 0);
    let genres = [];
    if (book && book.categories) {
        book.categories.forEach(function (cat) {
            genres.push({ title: cat, input: "/the-loai/" + cat, script: "gen.js" });
        });
    } else if (doc) {
        doc.select("a[href^='/the-loai/']").forEach(function (e) {
            let title = cleanText(e.text());
            let href = e.attr("href");
            if (title && href) genres.push({ title: title, input: href, script: "gen.js" });
        });
    }

    let detail = [];
    if (author) detail.push("<b>Tác giả:</b> " + author);
    if (total) detail.push("<b>Số chương:</b> " + total);
    if (book && book.updatedAt) detail.push("<b>Cập nhật:</b> " + book.updatedAt.substring(0, 10));
    else if (schema && schema.dateModified) detail.push("<b>Cập nhật:</b> " + schema.dateModified.substring(0, 10));

    return Response.success({
        name: title,
        cover: cover,
        author: author,
        description: description,
        detail: detail.join("<br>"),
        host: BASE_URL,
        genres: genres,
        ongoing: true
    });
}
