load("config.js");

function execute(url) {
    var response = fetch(normalizeUrl(url));
    if (!response.ok) return null;

    var doc = response.html();
    var infoItems = doc.select(".book-info-text li");
    var detailLines = [];
    var genres = [];

    infoItems.forEach(function (item) {
        var text = cleanText(item.text());
        if (!text) return;

        var lower = text.toLowerCase();
        if (lower.indexOf("tr\u1ea1ng th\u00e1i") === 0 || lower.indexOf("t\u00ecnh tr\u1ea1ng") === 0) {
            text = "Tr\u1ea1ng th\u00e1i: \u0110ang ra";
        }

        detailLines.push(text);
    });
    if (!detailLines.some(function (line) {
        var lower = String(line || "").toLowerCase();
        return lower.indexOf("tr\u1ea1ng th\u00e1i") === 0 || lower.indexOf("t\u00ecnh tr\u1ea1ng") === 0;
    })) {
        detailLines.push("Tr\u1ea1ng th\u00e1i: \u0110ang ra");
    }

    doc.select(".li--genres a").forEach(function (item) {
        var title = cleanText(item.text());
        var href = item.attr("href");
        if (!title || !href) return;

        genres.push({
            title: title,
            input: href,
            script: "gen.js"
        });
    });

    var author = "";
    if (infoItems.size() > 0) {
        author = extractValue(infoItems.get(0).text());
    }

    var descriptionNode = doc.select("#gioithieu [itemprop=description]").first();
    var description = descriptionNode ? cleanDescription(descriptionNode.text()) : "";

    return Response.success({
        name: cleanText(doc.select(".mRightCol h1").text()),
        cover: absoluteUrl(doc.select(".book-info-pic img").attr("src")),
        author: author,
        description: description,
        detail: detailLines.join("<br>"),
        host: BASE_URL,
        ongoing: true,
        genres: genres
    });
}

function cleanDescription(text) {
    return cleanText(text);
}

function extractValue(text) {
    var parts = String(text || "").split(":");
    if (parts.length <= 1) return cleanText(text);
    parts.shift();
    return cleanText(parts.join(":"));
}
