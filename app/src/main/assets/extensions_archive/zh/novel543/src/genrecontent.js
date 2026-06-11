load("config.js");

function execute(url, page) {
    var p = page ? parseInt(page) : 1;
    var fetchUrl = BASE_URL + "/bookstack/" + url + "/?page=" + p;

    // Genre pages are list-only, no images needed
    var doc = fetchCFLight(fetchUrl);
    if (!doc) return Response.error("Lỗi tải thể loại: " + url);

    var items = parseList(doc);
    if (!items || items.length === 0) return Response.success([], null);

    var next = getNextPage(doc, p);
    return Response.success(items, next);
}
