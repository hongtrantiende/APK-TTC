load("config.js");

function execute(input, page) {
    if (input.indexOf("author:") !== 0) return Response.success([], null);

    var p = page ? parseInt(page) : 1;
    var slug = input.substring(7);
    var fetchUrl = p <= 1
        ? BASE_URL + "/manga-author/" + slug + "/"
        : BASE_URL + "/manga-author/" + slug + "/page/" + p + "/";

    var res = fetchRetry(fetchUrl);
    if (!res || !res.ok) return Response.success([], null);
    var doc = res.html();
    if (!doc) return Response.success([], null);

    var items = parseList(doc);
    var next = getNextPage(doc, p);
    return Response.success(items, next);
}