load("config.js");

function execute(key, page) {
    var p = page ? parseInt(page) : 1;
    // fetchBrowser tu xu ly GBK encoding (fetch POST bi mojibake)
    var searchUrl = BASE_URL + "/search/?keyword=" + encodeURIComponent(key);
    if (p > 1) searchUrl += "&page=" + p;
    var doc = fetchBrowserFast(searchUrl);
    if (!doc) return Response.error("Lỗi tìm kiếm");
    var items = parseList(doc);
    if (!items || items.length === 0) return Response.success([], null);
    // Search dùng ?page=N — không dùng getNextPage (vốn chỉ hiểu index_N.html)
    var nextLink = selFirst(doc, "a[href*='page=" + (p + 1) + "']");
    var next = nextLink ? String(p + 1) : null;
    return Response.success(items, next);
}
