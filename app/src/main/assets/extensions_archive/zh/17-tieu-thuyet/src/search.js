load("config.js");

function execute(key, page) {
    var baseUrl = BASE_URL + "/search?kw=" + encodeURIComponent(key);
    var requestUrl = resolvePagedUrl(baseUrl, page);
    var doc = fetchStableDocument(requestUrl, BASE_URL + "/search", BASE_URL);
    if (!doc) return Response.success([], null);

    return Response.success(extractBookItems(doc), extractNextPageUrl(doc));
}
