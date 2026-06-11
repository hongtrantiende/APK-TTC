load("config.js");

function parseListFromDoc(doc) {
    let list = [];
    let items = doc.select("#story-list-container .story-item");
    if (items.size() === 0) items = doc.select(".story-item");

    items.forEach(function(item) {
        let titleEl = item.select("a.story-title").first();
        if (!titleEl) titleEl = item.select("a[href*='/truyen/']").first();
        if (!titleEl) titleEl = item.select("a").first();
        if (!titleEl) return;

        let link = ttcToAbsolute(titleEl.attr("href"));
        let name = ttcTrim(titleEl.text().replace(/\s+/g, " "));
        let coverEl = item.select("img.story-poster").first();
        if (!coverEl) coverEl = item.select("img").first();
        let cover = coverEl ? (coverEl.attr("data-src") || coverEl.attr("src") || "") : "";
        let desc = ttcTrim(item.select(".story-desc").text());

        if (!name || !link) return;
        list.push({
            name: name,
            link: link,
            cover: ttcToAbsolute(cover),
            description: desc,
            host: BASE_URL
        });
    });
    return list;
}

function parseNext(doc, page) {
    let current = parseInt(page || "1", 10);
    let min = Number.POSITIVE_INFINITY;
    doc.select("a[href*='page=']").forEach(function(item) {
        let href = item.attr("href") || "";
        let matched = href.match(/[?&]page=(\d+)/);
        if (!matched) return;
        let value = parseInt(matched[1], 10);
        if (value > current && value < min) {
            min = value;
        }
    });
    return min !== Number.POSITIVE_INFINITY ? String(min) : "";
}

function execute(key, page) {
    if (!page) page = "1";

    // Try browser-based search (user needs to be logged in)
    let searchUrl = BASE_URL + "/danh-sach?keyword=" + encodeURIComponent(key) + "&page=" + page;
    let pageData = ttcFetchPage(searchUrl, null, 15000);

    if (pageData && pageData.doc && !pageData.loginRequired) {
        let list = parseListFromDoc(pageData.doc);
        if (list.length > 0) {
            let next = parseNext(pageData.doc, page);
            return Response.success(list, next);
        }
    }

    if (ttcHasCookie()) {
        return Response.error("Khong tim thay ket qua hoac website dang bao tri. Hay thu lai sau.");
    }
    return Response.error("Tim kiem yeu cau dang nhap. Hien tai website dang bao tri.");
}