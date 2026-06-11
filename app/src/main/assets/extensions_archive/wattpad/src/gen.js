load("config.js");

function execute(url, page) {
    var pageUrl = page ? normalizeUrl(page) : normalizeUrl(url);
    if (!/[?&]page=/.test(pageUrl)) {
        pageUrl += (pageUrl.indexOf("?") >= 0 ? "&" : "?") + "page=1";
    }

    var response = fetch(pageUrl);
    if (!response.ok) return null;

    var doc = response.html();
    var data = [];

    doc.select(".truyen-list .item").forEach(function (item) {
        var titleNode = item.select("h3 a").first();
        if (!titleNode) return;

        var authorNode = item.select("p.line").first();
        var coverNode = item.select("a.cover img").first();

        data.push({
            name: cleanText(titleNode.text()),
            link: absoluteUrl(titleNode.attr("href")),
            cover: absoluteUrl(coverNode ? coverNode.attr("src") : ""),
            description: cleanText(authorNode ? authorNode.text() : ""),
            host: BASE_URL
        });
    });

    return Response.success(data, findNextPage(doc));
}

function findNextPage(doc) {
    var next = null;
    var currentPage = 1;
    var maxPage = 1;
    var arrowLinks = [];

    doc.select(".phan-trang a.btn-page").forEach(function (item) {
        var text = cleanText(item.text());
        var href = item.attr("href");
        var cls = item.attr("class") || "";

        if (/^\d+$/.test(text)) {
            maxPage = Math.max(maxPage, parseInt(text, 10));
            if (cls.indexOf("active") >= 0) currentPage = parseInt(text, 10);
        } else if (href && href.indexOf("javascript:") !== 0) {
            arrowLinks.push(absoluteUrl(href));
        }
    });

    if (currentPage < maxPage && arrowLinks.length > 0) {
        next = arrowLinks[arrowLinks.length - 1];
    }

    return next;
}
