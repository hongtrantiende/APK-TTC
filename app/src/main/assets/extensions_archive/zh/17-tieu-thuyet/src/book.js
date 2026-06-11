load("config.js");

function execute(url, page) {
    var requestUrl = resolvePagedUrl(url, page);
    var doc = fetchStableDocument(requestUrl, url, BASE_URL);
    if (!doc) return Response.success([], null);

    if (requestUrl.replace(/\/+$/, "") === BASE_URL.replace(/\/+$/, "")) {
        return Response.success(extractRecentItems(doc), null);
    }

    return Response.success(extractBookItems(doc), extractNextPageUrl(doc));
}
