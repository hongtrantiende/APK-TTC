load("config.js");

function execute(url, page) {
    var requestUrl = resolvePagedUrl(url, page);
    var doc = fetchStableDocument(requestUrl, url, BASE_URL);
    if (!doc) return Response.success([], null);

    return Response.success(extractBookItems(doc), extractNextPageUrl(doc));
}
