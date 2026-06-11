function execute(url, page) {
    load("config.js");

    url = normalizeUrl(url);

    // Handle pagination: append ?page=N for page > 1
    if (page && parseInt(page) > 1) {
        // Remove existing page param
        url = url.replace(/[?&]page=\d+/, "");
        var separator = url.indexOf("?") >= 0 ? "&" : "?";
        url = url + separator + "page=" + page;
    }

    var doc = fetchDoc(url);
    if (!doc) return Response.error("Failed to load page");

    var data = parseNovelList(doc);

    if (data.length === 0) {
        return Response.success([]);
    }

    // Pagination: assume there's a next page if we got results
    // storya uses ?page=N pagination
    var currentPage = page ? parseInt(page) : 1;
    var nextPage = null;
    if (data.length >= 10) {
        nextPage = String(currentPage + 1);
    }

    return nextPage ? Response.success(data, nextPage) : Response.success(data);
}
