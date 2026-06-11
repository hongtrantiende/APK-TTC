load("config.js");

function execute(url) {
    var requestUrl = normalizeUrl(url || BASE_URL + "/");
    var doc = fetchStableDocument(requestUrl, BASE_URL, BASE_URL);
    if (!doc) return Response.success([]);

    return Response.success(extractRecentItems(doc));
}
