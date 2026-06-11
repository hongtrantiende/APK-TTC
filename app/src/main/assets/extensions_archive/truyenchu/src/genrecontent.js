load("config.js");

function execute(url, page) {
    var p = page ? parseInt(page) : 1;
    // url = slug thể loại, VD: "tien-hiep"
    // WordPress /page/N/ permalink format
    var fetchUrl = p <= 1
        ? BASE_URL + "/the-loai/" + url + "/"
        : BASE_URL + "/the-loai/" + url + "/page/" + p + "/";

    var res = fetchRetry(fetchUrl);
    if (!res || !res.ok) return Response.error("Không tải được thể loại: " + url);
    var doc = res.html();
    if (!doc) return Response.success([], null);

    var items = parseList(doc);
    if (!items || items.length === 0) return Response.success([], null);

    var next = getNextPage(doc, p);
    return Response.success(items, next);
}
