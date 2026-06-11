load("config.js");

function parsePage(doc, chapters, seen) {
    var links = doc.select("a[href*='/book/']");
    for (var i = 0; i < links.size(); i++) {
        var link = links.get(i);
        var href = resolveUrl(link.attr("href") || "");
        var cleanHref = stripUrlDecoration(href);
        if (!CHAPTER_RE.test(cleanHref) || seen[cleanHref]) continue;

        var name = cleanText(link.text());
        if (!name) continue;

        seen[cleanHref] = true;
        chapters.push({
            name: name,
            url: addChapterCacheBust(cleanHref),
            host: HOST
        });
    }
}

function execute(url) {
    var storyUrl = resolveUrl(url);
    if (storyUrl.charAt(storyUrl.length - 1) !== "/") storyUrl += "/";

    var catalogUrl = storyUrl + "catalog/";
    var firstDoc = null;

    var browser = Engine.newBrowser();
    try {
        firstDoc = browser.launch(catalogUrl, 12000);
    } catch (e) {
        firstDoc = null;
    }
    try { browser.close(); } catch (e2) {}

    if (!firstDoc) {
        var firstRes = fetchRetry(catalogUrl);
        if (firstRes && firstRes.ok) {
            firstDoc = firstRes.html();
        }
    }

    if (!firstDoc) {
        return Response.error("Khong tai duoc muc luc");
    }
    var chapters = [];
    var seen = {};
    parsePage(firstDoc, chapters, seen);

    var lastPage = 1;
    var tailA = selFirst(firstDoc, "a:matchesOwn(尾页)");
    if (tailA) {
        var tailHref = resolveUrl(tailA.attr("href") || "");
        var match = /\/catalog\/(\d+)\.html$/.exec(tailHref);
        if (match) lastPage = parseInt(match[1], 10);
    }

    for (var page = 2; page <= lastPage; page++) {
        var pageUrl = storyUrl + "catalog/" + page + ".html";
        var pageDoc = null;
        var br = Engine.newBrowser();
        try {
            pageDoc = br.launch(pageUrl, 10000);
        } catch (ep) {
            pageDoc = null;
        }
        try { br.close(); } catch (ep2) {}
        if (!pageDoc) {
            var res = fetchRetry(pageUrl);
            if (res && res.ok) pageDoc = res.html();
        }
        if (!pageDoc) break;
        parsePage(pageDoc, chapters, seen);
    }

    return Response.success(chapters);
}
