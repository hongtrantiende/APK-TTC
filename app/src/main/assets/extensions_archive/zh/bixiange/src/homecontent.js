load("config.js");

function execute(url, page) {
    var p = page ? parseInt(page) : 1;
    var fetchUrl;
    if (url === "home") {
        fetchUrl = BASE_URL + "/";
    } else {
        // newest → /newest/  or  /newest/index_N.html
        // sort   → /sort/    or  /sort/index_N.html
        fetchUrl = paginateUrl(url, p);
    }
    var doc = fetchBrowserFast(fetchUrl);
    if (!doc) return Response.error("Lỗi tải trang: " + fetchUrl);
    var items = parseList(doc);
    if (!items || items.length === 0) return Response.success([], null);
    var next = (url === "home") ? null : getNextPage(doc, p);
    return Response.success(items, next);
}
