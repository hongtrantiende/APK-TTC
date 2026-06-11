load("config.js");

function execute(url) {
    var response = fetch(normalizeUrl(url || "/"));
    if (!response.ok) return null;

    var doc = response.html();
    var items = doc.select(".home-content .itemupdate");
    var data = [];
    var limit = Math.min(items.size(), 24);

    for (var i = 0; i < limit; i++) {
        var item = items.get(i);
        var titleNode = item.select("h3 a").first();
        if (!titleNode) continue;

        var bookUrl = absoluteUrl(titleNode.attr("href"));
        var chapterText = cleanText(item.select(".ichapter a").text());
        var updatedText = cleanText(item.select(".iupdated").text());
        var cover = "";

        if (bookUrl) {
            var detailResponse = fetch(bookUrl);
            if (detailResponse.ok) {
                cover = absoluteUrl(detailResponse.html().select(".book-info-pic img").attr("src"));
            }
        }

        data.push({
            name: cleanText(titleNode.text()),
            link: bookUrl,
            description: cleanText(chapterText + (updatedText ? " - " + updatedText : "")),
            cover: cover,
            host: BASE_URL
        });
    }

    return Response.success(data);
}
