load("config.js");

var AUTHOR_SLUG_RE = /\/tac-gia\/([^\/]+)\/?/;
var GENRE_SLUG_RE = /\/the-loai\/([^\/]+)\/?/;

function execute(url) {
    var storyUrl = resolveUrl(url);
    var res = fetchRetry(storyUrl);
    if (!res || !res.ok) return Response.error("Không tải được trang truyện");
    var doc = res.html();
    if (!doc) return Response.error("Không đọc được nội dung trang");

    // Tiêu đề — truyenchu.net dùng <h1> nằm ngoài .post-title
    var titleEl = selFirst(doc, "h1.entry-title, .post-title h1, h1.post-title, h1");
    var title = titleEl ? titleEl.text().trim() : "";

    // Cover
    var cover = "";
    var coverEl = selFirst(doc, ".summary_image img, .tab-summary img");
    if (coverEl) {
        cover = coverEl.attr("data-src") || coverEl.attr("data-lazy") || coverEl.attr("src") || "";
    }
    if (cover && cover.indexOf("http") !== 0) cover = BASE_URL + cover;

    // Tác giả
    var authorName = "";
    var authorLink = "";
    var authorEl = selFirst(doc, ".author-content a, .manga-authors a");
    if (authorEl) {
        authorName = authorEl.text().trim();
        var authorHref = authorEl.attr("href") || "";
        var aMatch = AUTHOR_SLUG_RE.exec(authorHref);
        if (aMatch) authorLink = "author:" + aMatch[1];
    }

    // Thể loại — lấy cả text lẫn slug từ href
    var genres = [];
    var genresList = [];
    var genreEls = doc.select(".genres-content a[href*='/the-loai/']");
    for (var gi = 0; gi < genreEls.size(); gi++) {
        var ga = genreEls.get(gi);
        var gText = ga.text().trim();
        if (!gText) continue;
        genres.push(gText);
        var gHref = ga.attr("href") || "";
        var gm = GENRE_SLUG_RE.exec(gHref);
        if (gm) genresList.push({ title: gText, input: gm[1], script: "genrecontent.js" });
    }

    // Tình trạng — dùng selector trực tiếp
    var statusEl = selFirst(doc, ".post-status .summary-content");
    var status = statusEl ? statusEl.text().trim() : "";

    // Mô tả
    var description = "";
    var descEl = selFirst(doc, ".manga-excerpt, .summary__content, .description-summary");
    if (descEl) {
        var descPs = descEl.select("p");
        if (descPs.size() > 0) {
            var parts = [];
            for (var di = 0; di < descPs.size(); di++) {
                var t = descPs.get(di).text().trim();
                if (t) parts.push(t);
            }
            description = parts.join("\n");
        }
        if (!description) description = descEl.text().trim();
    }

    // ongoing: Madara dùng "Completed" / "OnGoing"
    var ongoing = (status.indexOf("Completed") === -1 && status.indexOf("Hoàn") === -1);

    // detail string
    var detail = "";
    if (status) detail += "Tình trạng: " + status;
    if (genres.length > 0) detail += (detail ? "\n" : "") + "Thể loại: " + genres.join(", ");

    var suggests = [];
    if (authorLink) {
        suggests.push({ title: "Cùng tác giả: " + authorName, input: authorLink, script: "suggest.js" });
    }

    return Response.success({
        name: title,
        cover: cover,
        host: HOST,
        author: authorName,
        description: description,
        detail: detail,
        ongoing: ongoing,
        genres: genresList,
        suggests: suggests,
        comments: []
    });
}
