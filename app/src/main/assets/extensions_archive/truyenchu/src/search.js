load("config.js");

function execute(keyword, page) {
    var p = page ? parseInt(page) : 1;
    var q = encodeURIComponent(keyword);
    // Madara WP-Manga: tìm kiếm với post_type=wp-manga
    // WordPress /page/N/ cho kết quả search pagination
    var fetchUrl = p <= 1
        ? BASE_URL + "/?s=" + q + "&post_type=wp-manga"
        : BASE_URL + "/page/" + p + "/?s=" + q + "&post_type=wp-manga";

    var res = fetchRetry(fetchUrl);
    if (!res || !res.ok) return Response.success([], null);
    var doc = res.html();
    if (!doc) return Response.success([], null);

    var items = parseList(doc);
    if (!items || items.length === 0) return Response.success([], null);

    var next = getNextPage(doc, p);
    return Response.success(items, next);
}
