load("config.js");

function execute(key, page) {
    let pageNum = parseInt(page || "1");
    if (!pageNum || pageNum < 1) pageNum = 1;

    let q = key ? ("" + key).trim() : "";
    let url = BASE_URL + "/?s=" + encodeURIComponent(q);
    let doc = fetchDoc(url);
    if (!doc) return Response.success([]);

    let list = [];
    let seen = {};
    doc.select("article.post h2.entry-title a").forEach(a => {
        let link = normalizeUrl(a.attr("href"));
        let name = cleanText(a.text());
        if (!name || !link || seen[link]) return;
        seen[link] = true;
        let article = a.parent();
        let imgEl = article ? article.select("img").first() : null;
        if (!imgEl) {
            let parent = a.parent();
            while (parent) { imgEl = parent.select("img").first(); if (imgEl) break; parent = parent.parent(); }
        }
        let cover = imgEl ? toAbsolute(imgEl.attr("src") || "") : "";
        list.push({ name: name, link: link, cover: cover, description: "", host: BASE_URL });
    });

    return Response.success(list, null);
}
