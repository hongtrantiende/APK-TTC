load("config.js");

function buildCatalogPagePattern(bookId) {
    return new RegExp("/catalog_\\d+/" + bookId + "(?:/(\\d+))?(?:\\.html)?/?$", "i");
}

function extractCatalogPageOrder(url, bookId) {
    var match = normalizeUrl(url).match(buildCatalogPagePattern(bookId));
    if (!match) return 1;
    return match[1] ? parseInt(match[1], 10) || 1 : 1;
}

function isCatalogPageUrl(url, bookId) {
    return buildCatalogPagePattern(bookId).test(normalizeUrl(url));
}

function collectCatalogPageUrls(doc, bookId, currentUrl) {
    var urls = [];
    var seen = {};
    var selectors = [
        "select.pagelist option",
        "select option[value*='/catalog_']",
        ".chapter_page a[href*='/catalog_']",
        "a[href*='/catalog_']",
        ".page a",
        ".pagelist a"
    ];

    for (var s = 0; s < selectors.length; s++) {
        var nodes = doc.select(selectors[s]);

        for (var i = 0; i < nodes.size(); i++) {
            var node = nodes.get(i);
            var raw = node.attr("value");
            if (!raw) raw = node.attr("href");
            if (!raw) raw = node.attr("data-url");
            if (!raw) raw = node.attr("data-href");
            if (!raw) continue;

            var value = cleanText(raw);
            if (!value) continue;
            if (/^\d+$/.test(value)) {
                var prefixMatch = currentUrl ? String(currentUrl).match(/(catalog_\d+)/i) : null;
                var prefix = prefixMatch ? prefixMatch[1] : "catalog_1";
                value = BASE_URL + "/" + prefix + "/" + bookId + "/" + value;
            }

            var href = normalizeUrl(value);
            if (!isCatalogPageUrl(href, bookId) || seen[href]) continue;

            seen[href] = true;
            urls.push(href);
        }
    }

    urls.sort(function(a, b) {
        return extractCatalogPageOrder(a, bookId) - extractCatalogPageOrder(b, bookId);
    });

    return urls;
}

function isNoiseChapterName(name) {
    var value = cleanText(name);
    if (!value) return true;

    return value === "免费阅读"
        || value === "开始阅读"
        || value === "上一页"
        || value === "下一页"
        || value === "刷新";
}

function normalizeChapterName(name) {
    var value = cleanText(name);
    if (!value) return "";

    var patterns = [
        /^\s*0*\d+\s*[\u3001,:;\uFF0C\uFF1A.\uFF0E\-]\s*(.+)$/,
        /^\s*第\s*[0-9\u96f6\u3007\u4e00\u4e8c\u4e09\u56db\u4e94\u516d\u4e03\u516b\u4e5d\u5341\u767e\u5343\u4e07\u4e24]+\s*[\u7ae0\u8282\u5377\u56de\u96c6\u90e8\u7bc7\u5e55\u8bdd\u9875]\s*[\u3001,:;\uFF0C\uFF1A.\uFF0E\-]?\s*(.+)$/,
        /^\s*[0-9\u96f6\u3007\u4e00\u4e8c\u4e09\u56db\u4e94\u516d\u4e03\u516b\u4e5d\u5341\u767e\u5343\u4e07\u4e24]+\s*[\u7ae0\u8282\u5377\u56de\u96c6\u90e8\u7bc7\u5e55\u8bdd\u9875]\s*[\u3001,:;\uFF0C\uFF1A.\uFF0E\-]?\s*(.+)$/
    ];

    for (var i = 0; i < patterns.length; i++) {
        var match = value.match(patterns[i]);
        if (match && match[1]) {
            var normalized = cleanText(match[1]);
            if (normalized) return normalized;
        }
    }

    return value;
}

function collectChapterEntries(doc, bookId, seen) {
    var entries = [];
    var links = doc.select("a[href*='/xs_']");
    var pattern = new RegExp("/xs_\\d+/" + bookId + "/(\\d+)/?$", "i");

    for (var i = 0; i < links.size(); i++) {
        var link = links.get(i);
        var href = normalizeUrl(link.attr("href"));
        var match = href.match(pattern);
        var order = match ? parseInt(match[1], 10) || 0 : 0;
        var name = normalizeChapterName(link.text());
        if (!match || seen[href] || isNoiseChapterName(name)) continue;

        seen[href] = true;
        entries.push({
            name: name,
            url: href,
            host: BASE_URL,
            order: order
        });
    }

    return entries;
}

function execute(url) {
    var bookId = extractBookId(url);
    if (!bookId) return Response.success([]);

    var detailUrl = buildDetailUrl(bookId);
    var chapters = [];
    var seenChapters = {};
    var seenPages = {};
    var queuedPages = {};
    var pendingPages = [buildCatalogUrl(bookId, 1)];
    var guard = 0;

    queuedPages[pendingPages[0]] = true;
    fetchText(detailUrl, BASE_URL);

    while (pendingPages.length > 0 && guard < 4000) {
        var currentUrl = pendingPages.shift();
        if (!currentUrl || seenPages[currentUrl]) continue;

        seenPages[currentUrl] = true;
        if (guard > 0 && typeof sleep === "function") {
            sleep(600);
        }
        guard += 1;

        var doc = fetchStableDocument(currentUrl, detailUrl, detailUrl);
        if (!doc) continue;

        var entries = collectChapterEntries(doc, bookId, seenChapters);
        if (entries.length > 0) {
            for (var i = 0; i < entries.length; i++) {
                if (entries[i].name) chapters.push(entries[i]);
            }

            var catMatch = String(currentUrl).match(/(catalog_\d+)/i);
            var catPrefix = catMatch ? catMatch[1] : "catalog_1";
            var currentOrder = extractCatalogPageOrder(currentUrl, bookId);
            var nextPageUrl = BASE_URL + "/" + catPrefix + "/" + bookId + "/" + (currentOrder + 1);
            if (!seenPages[nextPageUrl] && !queuedPages[nextPageUrl]) {
                queuedPages[nextPageUrl] = true;
                pendingPages.push(nextPageUrl);
            }
        }

        var pageUrls = collectCatalogPageUrls(doc, bookId, currentUrl);
        for (var j = 0; j < pageUrls.length; j++) {
            var pageUrl = pageUrls[j];
            if (seenPages[pageUrl] || queuedPages[pageUrl]) continue;

            queuedPages[pageUrl] = true;
            pendingPages.push(pageUrl);
        }
    }

    chapters.sort(function(a, b) {
        return a.order - b.order;
    });

    for (var k = 0; k < chapters.length; k++) {
        delete chapters[k].order;
    }

    return Response.success(chapters);
}
