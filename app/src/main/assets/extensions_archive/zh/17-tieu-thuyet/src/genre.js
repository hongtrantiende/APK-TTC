load("config.js");

function execute(url) {
    var requestUrl = normalizeUrl(url || BASE_URL + "/type");
    var doc = fetchStableDocument(requestUrl, BASE_URL, BASE_URL);
    if (!doc) return Response.success([]);

    var items = [];
    var sections = doc.select(".category");

    for (var i = 0; i < sections.size(); i++) {
        var section = sections.get(i);
        var prefix = cleanText(section.select(".title").text());
        var links = section.select("a[href^='/type/']");

        for (var j = 0; j < links.size(); j++) {
            var link = links.get(j);
            var name = cleanText(link.select(".name").text());
            var href = normalizeUrl(link.attr("href"));

            if (!name || !href) continue;

            items.push({
                title: prefix ? prefix + " · " + name : name,
                input: href,
                script: "book.js"
            });
        }
    }

    return Response.success(items);
}
