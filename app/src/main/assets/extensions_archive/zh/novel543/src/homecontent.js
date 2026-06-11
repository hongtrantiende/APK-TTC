load("config.js");

function execute(url, page) {
    var p = page ? parseInt(page) : 1;
    var fetchUrl;

    if (url === "home") {
        fetchUrl = BASE_URL + "/";
    } else if (url === "bookstack") {
        fetchUrl = BASE_URL + "/bookstack/?page=" + p;
    } else if (url === "bookstack_boy") {
        fetchUrl = BASE_URL + "/bookstack/?gender=boy&page=" + p;
    } else if (url === "bookstack_girl") {
        fetchUrl = BASE_URL + "/bookstack/?gender=girl&page=" + p;
    } else if (url === "bookstack_end") {
        fetchUrl = BASE_URL + "/bookstack/?end=2&page=" + p;
    } else {
        fetchUrl = BASE_URL + "/bookstack/?page=" + p;
    }

    // List pages don't need images loaded in browser
    var doc = fetchCFLight(fetchUrl);
    if (!doc) return Response.error("Lỗi tải trang: " + fetchUrl);

    var items = parseList(doc);
    if (!items || items.length === 0) return Response.success([], null);

    var next = (url === "home") ? null : getNextPage(doc, p);
    return Response.success(items, next);
}
