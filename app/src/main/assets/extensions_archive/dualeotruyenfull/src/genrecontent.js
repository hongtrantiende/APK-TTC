load("config.js");

function execute(url, page) {
    var p = page ? parseInt(page) : 1;
    var fetchUrl;
    if (p <= 1) {
        fetchUrl = BASE_URL + "/the-loai/" + url + "/";
    } else {
        fetchUrl = BASE_URL + "/the-loai/" + url + "/page/" + p + "/";
    }
    var doc = fetchDoc(fetchUrl);
    if (!doc) return Response.error("Không tải được danh sách thể loại");
    var items = parseList(doc);
    if (!items || items.length === 0) return Response.success([], null);
    var nextLink = selFirst(doc, ".uk-pagination a[rel='next'], a.next");
    var next = nextLink ? String(p + 1) : null;
    return Response.success(items, next);
}
