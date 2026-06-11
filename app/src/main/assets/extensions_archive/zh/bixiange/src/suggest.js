load("config.js");

// Precompile regex — tránh tạo mới mỗi lần gọi execute
var CATEGORY_RE = /\/(dsyq|wxxz|xhqh|cyjk|khjj|ghxy|jsls|guanchang|xtfq|dmtr|trxs|jqxs)\//;

function execute(url) {
    // Truyện cùng tác giả — search theo tên tác giả
    if (url.indexOf("author:") === 0) {
        var authorName = url.substring(7);
        var searchUrl = BASE_URL + "/search/?keyword=" + encodeURIComponent(authorName);
        var doc = fetchBrowserFast(searchUrl);
        if (!doc) return Response.success([]);
        var items = parseList(doc);
        return Response.success(items || []);
    }

    // Lấy truyện đề xuất từ cùng thể loại với bộ truyện đang xem
    var m = CATEGORY_RE.exec(url);
    if (!m) return Response.success([]);
    var category = m[1];
    var fetchUrl = paginateUrl(category, 1);
    var doc = fetchBrowserFast(fetchUrl);
    if (!doc) return Response.success([]);
    var items = parseList(doc);
    return Response.success(items || []);
}
