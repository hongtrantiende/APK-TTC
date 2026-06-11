load("config.js");

function execute(url, page) {
    var p = page ? parseInt(page) : 1;
    var fetchUrl = p <= 1
        ? BASE_URL + "/novel/"
        : BASE_URL + "/novel/page/" + p + "/";

    var res = fetchRetry(fetchUrl);
    if (!res || !res.ok) return Response.success([], null);
    var doc = res.html();
    if (!doc) return Response.success([], null);

    var items = parseList(doc);
    var next = getNextPage(doc, p);
    return Response.success(items, next);
}