load("config.js");

function execute(key, page) {
    var p = page ? parseInt(page) : 1;
    var q = encodeURIComponent(key || "");
    var searchUrl = BASE_URL + "/?s=" + q;
    if (p > 1) searchUrl = BASE_URL + "/page/" + p + "/?s=" + q;
    var doc = fetchDoc(searchUrl);
    if (!doc) return Response.error("Không tải được kết quả tìm kiếm");
    var items = parseSearchList(doc);
    if (!items || items.length === 0) return Response.success([], null);
    var nextLink = selFirst(doc, ".uk-pagination a[rel='next'], a.next");
    var next = nextLink ? String(p + 1) : null;
    return Response.success(items, next);
}
