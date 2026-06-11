load("config.js");

function abs(u) {
    if (!u) return "";
    if (u.startsWith("http")) return u;
    if (u.startsWith("//")) return "https:" + u;
    return BASE_URL + (u.startsWith("/") ? "" : "/") + u;
}

function execute(url) {
    const res = fetch(url);
    if (!res || !res.ok) return null;

    const doc = res.html();

    // Title
    let name = "";
    let h1 = doc.select("h1").first();
    if (h1) name = h1.text();
    if (!name) {
        // fallback to og:title
        let ogt = doc.select('meta[property="og:title"]').attr("content");
        if (ogt) name = ogt;
    }

    // Author
    let author = "";
    let authorLink = doc.select('p:matches(Tác giả) a, a[href*="tim-truyen?keyword="]').first();
    if (authorLink) author = authorLink.text();

    // Cover
    let cover = doc.select('meta[property="og:image"]').attr("content")
        || doc.select(".card img, img").first().attr("src");
    cover = abs(cover);

    // Status (Đang ra / Hoàn thành)
    let status = "";
    // look for rounded-full badges in the header card
    doc.select('.flex.flex-wrap.gap-2 span, .flex.flex-wrap.gap-2 a').forEach(e => {
        let t = (e.text() || "").trim();
        if (!status && (t === "Đang ra" || t === "Hoàn thành")) status = t;
    });

    // Genres (categories)
    let genres = [];
    doc.select('a[href^="/the-loai/"]').forEach(a => {
        let title = (a.text() || "").trim();
        let href = a.attr("href");
        if (title) {
            genres.push({ title: title, input: abs(href), script: "gen.js" });
        }
    });
    // De-dup genres by title
    let seen = {};
    genres = genres.filter(g => {
        if (seen[g.title]) return false;
        seen[g.title] = true;
        return true;
    });

    // Description: content in Giới thiệu tab
    let descEl = doc.select('#gioi-thieu .leading-relaxed, #gioi-thieu p').first();
    let description = descEl ? descEl.html() : "";
    if (!description) {
        // fallback to meta description
        description = doc.select('meta[name="description"]').attr('content') || "";
    }

    // Latest chapter (optional)
    let lastChEl = doc.select('#gioi-thieu a[href*="/chuong-"]').first();
    let lastChapter = lastChEl ? lastChEl.text() : "";

    // Build detail HTML
    let categoryText = genres.map(g => g.title).join(', ');
    let detail = '<div>'
        + (categoryText ? '<p><strong>Thể loại:</strong> ' + categoryText + '</p>' : '')
        + (status ? '<p><strong>Tình trạng:</strong> ' + status + '</p>' : '')
        + (lastChapter ? '<p><strong>Chương mới:</strong> ' + lastChapter + '</p>' : '')
        + '</div>';

    const ongoing = status ? (status.toLowerCase().indexOf('hoàn') === -1) : true;

    return Response.success({
        name: name,
        cover: cover,
        author: author,
        description: description,
        genres: genres,
        detail: detail,
        ongoing: ongoing,
        host: BASE_URL
    });
}