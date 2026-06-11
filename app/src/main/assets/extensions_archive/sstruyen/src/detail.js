load('config.js');

function execute(url) {
    url = normalizeUrl(url);
    let response = fetch(url);
    if (response.ok) {
        let doc = response.html();
        let title = doc.select("h1[itemprop='name'], .book-info h1, h1").first();
        let cover = doc.select(".book-info-pic img, img[itemprop='image'], img[src*='/media/book/']").first();
        let author = doc.select("a[itemprop='author'], a[href*='/tac-gia/']").first();
        let intro = doc.select("#gioithieu .scrolltext, .intro").first();
        let status = doc.select(".label-status").first();
        let info = doc.select(".book-info-text ul").first();

        let genres = [];
        let seen = {};
        doc.select(".li--genres a[href*='/the-loai/'], a[href*='/the-loai/']").forEach(function (e) {
            let name = cleanText(e.text());
            if (name && !seen[name]) {
                seen[name] = true;
                genres.push({
                    title: name,
                    input: normalizeUrl(e.attr("href")),
                    script: "gen.js"
                });
            }
        });

        let statusText = status ? cleanText(status.text()) : "";
        let infoHtml = info ? info.html() : "";

        return Response.success({
            name: title ? cleanText(title.text()) : "",
            cover: cover ? normalizeUrl(cover.attr("data-src") || cover.attr("src")) : "",
            author: author ? cleanText(author.text()) : "",
            description: intro ? intro.html() : "",
            detail: infoHtml,
            host: BASE_URL,
            genres: genres,
            ongoing: statusText.toLowerCase().indexOf("full") < 0 && statusText.toLowerCase().indexOf("hoan") < 0
        });
    }

    return null;
}
