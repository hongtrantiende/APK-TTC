load("config.js");

function collectTocPages(doc, bookId) {
    let data = [];
    let seen = {};
    let items = doc.select("a[href], option[value]");

    for (let i = 0; i < items.size(); i++) {
        let node = items.get(i);
        let link = normalizeUrl(node.attr("href") || node.attr("value"));
        if (!link || seen[link]) continue;
        if (!isTocUrl(link) || storyIdFromUrl(link) !== bookId) continue;
        seen[link] = true;
        data.push(link);
    }

    data.sort(function(a, b) {
        return pageNumberFromUrl(a) - pageNumberFromUrl(b);
    });
    return data;
}

function estimateTocPages(bookId) {
    let doc = getDoc(storyUrl(bookId));
    if (!doc) return [];

    let latest = metaContent(doc, "meta[property=og:novel:latest_chapter_name]") || textByPrefix(doc, ["最新"]);
    let total = chapterNumber(latest);
    if (!total || total < 1) return [];

    let pages = [];
    let maxPage = Math.ceil(total / 100);
    for (let i = 1; i <= maxPage; i++) pages.push(tocUrl(bookId, i));
    return pages;
}

function execute(url) {
    let bookId = storyIdFromUrl(url);
    if (!bookId) return Response.error("Url truyen khong hop le.");

    let firstUrl = tocUrl(bookId, 1);
    let doc = getDoc(firstUrl);
    if (!doc) return Response.success([firstUrl]);

    let pages = collectTocPages(doc, bookId);
    if (pages.length <= 1) pages = estimateTocPages(bookId);
    if (pages.length === 0) pages = [firstUrl];
    return Response.success(pages);
}
