load("config.js");

var SKIP_RE = /^(Đọc|Từ đầu|Mới nhất|Xem thêm|Trang trước|Trang sau|Tải thêm)$/;
var CHAPTERS_API_PER_PAGE = 50;

function normalizeChapterName(name, number) {
    var title = (name || "").replace(/\s+/g, " ").trim();
    if (title) return title;
    if (number || number === 0) return "Chương " + number;
    return "";
}

function getMangaId(doc) {
    if (!doc) return 0;

    var jsonLink = selFirst(doc, "link[rel='alternate'][type='application/json']");
    if (jsonLink) {
        var jsonHref = jsonLink.attr("href") || "";
        var jsonMatch = jsonHref.match(/\/wp-json\/wp\/v2\/manga\/(\d+)/);
        if (jsonMatch) return parseInt(jsonMatch[1], 10) || 0;
    }

    var html = doc.html ? doc.html() : "";
    if (html) {
        var htmlMatch = html.match(/\/wp-json\/wp\/v2\/manga\/(\d+)/);
        if (htmlMatch) return parseInt(htmlMatch[1], 10) || 0;
    }

    return 0;
}

function fetchChapterApiPage(mangaId, pageNum) {
    var apiUrl = BASE_URL + "/wp-json/initmanga/v1/chapters?manga_id=" + mangaId + "&per_page=" + CHAPTERS_API_PER_PAGE + "&paged=" + pageNum;
    var res = fetch(apiUrl, FETCH_OPTIONS);
    if (!res || !res.ok) return null;
    return res.json();
}

function collectApiChapters(detailUrl, mangaId) {
    if (!mangaId) return null;

    var firstPage = fetchChapterApiPage(mangaId, 1);
    if (!firstPage || !firstPage.items || firstPage.items.length === 0) return null;

    var all = [];
    var totalPages = parseInt(firstPage.total_pages, 10) || 1;
    var normalizedDetailUrl = detailUrl.replace(/\/+$/, "");

    function pushItems(items) {
        for (var i = 0; i < items.length; i++) {
            var item = items[i];
            if (!item) continue;
            var slug = item.slug || "";
            if (!slug) continue;

            var name = normalizeChapterName(item.title, item.number);
            if (!name || SKIP_RE.test(name)) continue;

            all.push({
                name: name,
                url: (normalizedDetailUrl + "/" + slug + "/").replace(BASE_URL, ""),
                host: HOST
            });
        }
    }

    pushItems(firstPage.items);
    for (var pageNum = 2; pageNum <= totalPages; pageNum++) {
        var pageData = fetchChapterApiPage(mangaId, pageNum);
        if (!pageData || !pageData.items || pageData.items.length === 0) continue;
        pushItems(pageData.items);
    }

    var ordered = [];
    for (var j = all.length - 1; j >= 0; j--) {
        ordered.push(all[j]);
    }
    return ordered;
}

function getChapterName(linkEl) {
    var titleEl = selFirst(linkEl, "h3.uk-link-heading, .uk-link-heading, h3");
    if (titleEl) {
        var title = titleEl.text().replace(/\s+/g, " ").trim();
        if (title) return title;
    }

    var clone = linkEl.clone();
    clone.select(".uk-article-meta, time, .init-plugin-suite-view-count-views, .uk-icon, hr").remove();
    return clone.text().replace(/\s+/g, " ").trim();
}

function collectChapters(doc, chapters, seen) {
    var chapLinks = doc.select("a[href*='/chuong-']");
    for (var i = 0; i < chapLinks.size(); i++) {
        var a = chapLinks.get(i);
        var href = a.attr("href") || "";
        if (!href || href.indexOf("/chuong-") === -1) continue;
        if (seen[href]) continue;

        var cname = getChapterName(a);
        if (!cname || cname.length < 2) continue;
        if (SKIP_RE.test(cname)) continue;

        seen[href] = true;
        chapters.push({
            name: cname,
            url: href.replace(BASE_URL, ""),
            host: HOST
        });
    }
}

function getMaxChapterPage(doc) {
    var pagerLinks = doc.select("a[href*='/chuong/page/']");
    var maxPage = 1;
    for (var i = 0; i < pagerLinks.size(); i++) {
        var href = pagerLinks.get(i).attr("href") || "";
        var match = href.match(/\/chuong\/page\/(\d+)\//);
        if (!match) continue;
        var pageNum = parseInt(match[1], 10);
        if (pageNum > maxPage) maxPage = pageNum;
    }
    return maxPage;
}

function execute(url) {
    var detailUrl = resolveUrl(url);
    var doc = fetchDoc(detailUrl);
    if (!doc) return Response.error("Không tải được danh sách chương");

    var mangaId = getMangaId(doc);
    var apiChapters = collectApiChapters(detailUrl, mangaId);
    if (apiChapters && apiChapters.length > 0) {
        return Response.success(apiChapters);
    }

    var chapters = [];
    var seen = {};
    collectChapters(doc, chapters, seen);

    var maxPage = getMaxChapterPage(doc);
    if (maxPage > 1) {
        var normalizedDetailUrl = detailUrl.replace(/\/+$/, "");
        for (var pageNum = 2; pageNum <= maxPage; pageNum++) {
            var pageUrl = normalizedDetailUrl + "/chuong/page/" + pageNum + "/";
            var pageDoc = fetchDoc(pageUrl);
            if (!pageDoc) continue;
            collectChapters(pageDoc, chapters, seen);
        }
    }

    // Reverse to get ascending order (chapter 1 first)
    var sorted = [];
    for (var j = chapters.length - 1; j >= 0; j--) {
        sorted.push(chapters[j]);
    }

    if (sorted.length === 0) return Response.error("Không tìm thấy chương nào");
    return Response.success(sorted);
}
