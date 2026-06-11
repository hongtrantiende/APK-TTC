load("config.js");

function execute(url) {
    // Same author search — input is "author:AuthorName"
    if (url.indexOf("author:") === 0) {
        var authorName = url.substring(7);
        var items = null;
        var doc = null;

        // Fast: try direct URL with cached cookies
        var urls = [
            BASE_URL + "/search/" + encodeURIComponent(authorName) + "/",
            BASE_URL + "/search/?keyword=" + encodeURIComponent(authorName)
        ];
        for (var i = 0; i < urls.length; i++) {
            doc = fetchFast(urls[i]);
            if (doc) {
                items = parseList(doc);
                if (items && items.length > 0) break;
            }
        }
        // Fallback: browser fetch
        if (!items || items.length === 0) {
            for (var j = 0; j < urls.length; j++) {
                doc = fetchBrowserCF(urls[j], 12000);
                if (doc) {
                    items = parseList(doc);
                    if (items && items.length > 0) break;
                }
            }
        }
        return Response.success(items || []);
    }

    // 編輯推薦 — editor recommendations from the detail page
    var storyUrl = resolveUrl(url);
    var doc = fetchCF(storyUrl);
    if (!doc) return Response.success([]);

    var result = [];
    var seen = {};

    // Get current book ID to exclude it
    var currentMatch = storyUrl.match(/\/(\d{10,})/);
    if (currentMatch) seen[currentMatch[1]] = true;

    // Look specifically for 編輯推薦 section
    var sections = doc.select("section");
    var recSection = null;
    for (var s = 0; s < sections.size(); s++) {
        var sec = sections.get(s);
        var header = selFirst(sec, "h3 span, .header h3");
        if (header && header.text().indexOf("\u7de8\u8f2f\u63a8\u85a6") >= 0) {
            recSection = sec;
            break;
        }
    }

    // Parse recommendations from 編輯推薦 section
    if (recSection) {
        var recItems = recSection.select("li");
        for (var ri = 0; ri < recItems.size(); ri++) {
            var li = recItems.get(ri);
            var link = selFirst(li, "h3 a, a[href]");
            if (!link) continue;
            var href = link.attr("href") || "";
            var bookMatch = href.match(/\/(\d{10,})\//);
            if (!bookMatch || seen[bookMatch[1]]) continue;
            seen[bookMatch[1]] = true;

            var name = "";
            var titleEl = selFirst(li, "h3 a");
            if (titleEl) name = titleEl.text().trim();
            if (!name) {
                var imgElAlt = selFirst(li, "img");
                if (imgElAlt) name = imgElAlt.attr("alt") || "";
            }
            if (!name || name.length < 2) continue;

            var imgEl = selFirst(li, "img");
            var cover = imgEl ? (imgEl.attr("src") || "") : "";
            var authEl = selFirst(li, ".author, p.author");
            var author = authEl ? authEl.text().trim() : "";

            result.push({
                name: name,
                link: resolveUrl(href),
                host: HOST,
                cover: cover,
                description: author
            });
        }
    }

    // Fallback: 網友都在看 sidebar
    if (result.length < 5) {
        var sidebarLinks = doc.select("aside a[href], .sidebar a[href]");
        for (var si = 0; si < sidebarLinks.size(); si++) {
            var a = sidebarLinks.get(si);
            var href2 = a.attr("href") || "";
            var bm = href2.match(/\/(\d{10,})\//);
            if (!bm || seen[bm[1]]) continue;
            seen[bm[1]] = true;

            var name2 = a.text().trim();
            if (!name2 || name2.length < 2 || name2.length > 100) continue;
            if (name2.indexOf("\u67e5\u770b") === 0) continue;

            result.push({
                name: name2,
                link: resolveUrl(href2),
                host: HOST,
                cover: "",
                description: ""
            });
            if (result.length >= 20) break;
        }
    }

    return Response.success(result);
}
