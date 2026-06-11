load("config.js");

var AUTHOR_SLUG_RE = /\/tac-gia\/([^\/]+)\/?/;

function execute(input, page) {
    var p = page ? parseInt(page) : 1;

    // Chỉ hỗ trợ "author:slug" — truyện cùng tác giả
    if (input.indexOf("author:") !== 0) return Response.success([], null);

    var slug = input.substring(7);
    var fetchUrl = p <= 1
        ? BASE_URL + "/tac-gia/" + slug + "/"
        : BASE_URL + "/tac-gia/" + slug + "/page/" + p + "/";

    var res = fetchRetry(fetchUrl);
    if (!res || !res.ok) return Response.success([], null);
    var doc = res.html();
    if (!doc) return Response.success([], null);

    var items = parseList(doc);
    if (!items || items.length === 0) return Response.success([], null);

    var next = getNextPage(doc, p);
    return Response.success(items, next);
}
