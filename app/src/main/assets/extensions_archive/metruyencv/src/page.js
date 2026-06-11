load("config.js");

function execute(url) {
    var response = fetch(normalizeUrl(url));
    if (!response.ok) return null;

    var doc = response.html();
    var bookId = doc.select("input[name=bid]").attr("value");
    var pageCount = getPageCount(doc);
    var pages = [];

    if (!bookId || pageCount <= 1) {
        return Response.success([normalizeUrl(url)]);
    }

    for (var i = 1; i <= pageCount; i++) {
        pages.push(BASE_URL + "/get/listchap/" + bookId + "?page=" + i);
    }

    return Response.success(pages);
}

function getPageCount(doc) {
    var totalChapters = 0;
    var pageCount = 1;
    var infoItems = doc.select(".book-info-text li");

    if (infoItems.size() > 2) {
        var chapterText = cleanText(infoItems.get(2).text());
        var totalMatch = chapterText.replace(/\./g, "").match(/(\d+)/);
        if (totalMatch) totalChapters = parseInt(totalMatch[1], 10);
    }

    if (totalChapters > 0) {
        pageCount = Math.ceil(totalChapters / 100);
    }

    doc.select(".paging a").forEach(function (item) {
        var text = cleanText(item.text());
        var onclick = item.attr("onclick");
        var match = onclick ? onclick.match(/page\(\d+,(\d+)\)/) : null;

        if (/^\d+$/.test(text)) {
            pageCount = Math.max(pageCount, parseInt(text, 10));
        }
        if (match) {
            pageCount = Math.max(pageCount, parseInt(match[1], 10));
        }
    });

    return pageCount;
}
