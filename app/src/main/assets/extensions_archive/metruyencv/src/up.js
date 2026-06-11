load("config.js");

function execute(url) {
    var response = fetch(normalizeUrl(url || "/"));
    if (!response.ok) return null;

    var doc = response.html();
    var data = [];

    doc.select(".home-content .itemupdate").forEach(function (item) {
        var titleNode = item.select("h3 a").first();
        var chapterText = cleanText(item.select(".ichapter a").text());
        var updatedText = cleanText(item.select(".iupdated").text());
        var bookUrl = absoluteUrl(titleNode.attr("href"));
        var cover = "";

        var detailResponse = fetch(bookUrl);
        if (detailResponse.ok) {
            cover = absoluteUrl(detailResponse.html().select(".book-info-pic img").attr("src"));
        }

        data.push({
            name: cleanText(titleNode.text()),
            link: bookUrl,
            description: cleanText(chapterText + (updatedText ? " - " + updatedText : "")),
            cover: cover,
            host: BASE_URL
        });
    });

    return Response.success(data);
}
