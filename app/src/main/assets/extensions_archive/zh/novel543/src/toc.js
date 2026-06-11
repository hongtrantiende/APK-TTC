load("config.js");

function execute(url) {
    var storyUrl = resolveUrl(url);
    var bookIdMatch = storyUrl.match(/\/(\d{10,})\/?$/);
    if (!bookIdMatch) return Response.error("URL không hợp lệ");
    var bookId = bookIdMatch[1];
    var dirUrl = BASE_URL + "/" + bookId + "/dir";

    // Try dir page first (light fetch — no images needed)
    var doc = fetchCFLight(dirUrl);
    var chapters = [];
    var seen = {};

    if (doc) {
        var chapLinks = doc.select("a[href]");
        for (var i = 0; i < chapLinks.size(); i++) {
            var a = chapLinks.get(i);
            var href = a.attr("href") || "";
            if (href.indexOf("/" + bookId + "/") < 0 || href.indexOf(".html") < 0) continue;
            if (href.indexOf("/dir") >= 0) continue;
            if (href.indexOf("http") !== 0) href = BASE_URL + href;
            if (seen[href]) continue;

            var chapName = a.text().trim();
            if (!chapName || chapName.length < 2) continue;
            if (chapName === "\u4e0a\u4e00\u7ae0" || chapName === "\u4e0b\u4e00\u7ae0" ||
                chapName === "\u76ee\u9304") continue;

            seen[href] = true;
            chapters.push({ name: chapName, url: href, host: HOST });
        }
    }

    // Fallback: detail page (only if dir failed)
    if (chapters.length === 0) {
        var detailDoc = fetchCFLight(storyUrl);
        if (detailDoc) {
            var links2 = detailDoc.select(".chaplist a[href], a[href*='" + bookId + "'][href$='.html']");
            for (var j = 0; j < links2.size(); j++) {
                var a2 = links2.get(j);
                var h2 = a2.attr("href") || "";
                if (h2.indexOf("/" + bookId + "/") < 0 || h2.indexOf(".html") < 0) continue;
                if (h2.indexOf("/dir") >= 0) continue;
                if (h2.indexOf("http") !== 0) h2 = BASE_URL + h2;
                if (seen[h2]) continue;

                var cn2 = a2.attr("title") || a2.text().trim();
                if (!cn2 || cn2.length < 2) continue;
                cn2 = cn2.replace(/^[^\s]+ /, "");

                seen[h2] = true;
                chapters.push({ name: cn2, url: h2, host: HOST });
            }
            if (chapters.length > 0) chapters.reverse();
        }
    }

    if (chapters.length === 0) return Response.error("Không tìm thấy danh sách chương");
    return Response.success(chapters);
}
