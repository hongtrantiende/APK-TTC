load("config.js");

function execute(url) {
    let storyUrl = normalizeUrl(url);
    let doc = fetchDoc(storyUrl);
    if (!doc) return Response.success([storyUrl]);

    let maxPage = parseMaxPage(doc);
    if (!maxPage || maxPage <= 1) return Response.success([storyUrl]);

    let pages = [];
    for (let p = 1; p <= maxPage; p++) {
        pages.push(withPage(storyUrl, p));
    }
    return Response.success(pages);
}
