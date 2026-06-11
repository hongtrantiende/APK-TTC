load("config.js");

function execute(url) {
    var sourceUrl = normalizeUrl(url);
    var chapters = [];

    if (sourceUrl.indexOf("/get/listchap/") >= 0) {
        var response = fetch(sourceUrl, {
            method: "GET",
            headers: {
                accept: "*/*",
                "x-requested-with": "XMLHttpRequest"
            },
            referrer: BASE_URL
        });
        if (!response.ok) return null;

        var payload = response.json();
        var html = Html.parse(payload.data || "");

        html.select("ul li a").forEach(function (item) {
            chapters.push({
                name: cleanText(item.text()),
                url: absoluteUrl(item.attr("href")),
                host: BASE_URL
            });
        });

        return Response.success(chapters);
    }

    var doc = fetch(sourceUrl).html();
    doc.select("#chapter-list ul li a").forEach(function (item) {
        chapters.push({
            name: cleanText(item.text()),
            url: absoluteUrl(item.attr("href")),
            host: BASE_URL
        });
    });

    return Response.success(chapters);
}
