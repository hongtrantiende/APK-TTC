load('config.js');

function execute(url) {
    url = normalizeUrl(url);
    let response = fetch(url);
    if (response.ok) {
        let doc = response.html();
        let title = doc.select("h1").first();
        let cover = doc.select(".summary_image img, .tab-summary img, .post-thumb img").first();
        let author = doc.select(".author-content a").first();
        let intro = doc.select(".summary__content, .manga-excerpt, .description-summary").first();
        let status = doc.select(".post-status .summary-content").first();

        let genres = [];
        let seen = {};
        doc.select(".genres-content a").forEach(function (e) {
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
        let infoHtml = "";
        if (statusText) {
            infoHtml += "<p>Trạng thái: " + statusText + "</p>";
        }
        if (author) {
            infoHtml += "<p>Tác giả: " + cleanText(author.text()) + "</p>";
        }

        return Response.success({
            name: title ? cleanText(title.text()) : "",
            cover: cover ? normalizeUrl(cover.attr("data-src") || cover.attr("src")) : "",
            author: author ? cleanText(author.text()) : "",
            description: intro ? intro.html() : "",
            detail: infoHtml,
            host: BASE_URL,
            genres: genres,
            ongoing: statusText.toLowerCase().indexOf("hoàn") < 0 && statusText.toLowerCase().indexOf("full") < 0 && statusText.toLowerCase().indexOf("end") < 0
        });
    }

    return null;
}
