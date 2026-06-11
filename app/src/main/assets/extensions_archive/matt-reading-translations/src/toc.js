load("config.js");

function execute(url) {
    let pageUrl = normalizeUrl(url);
    let doc = fetchDoc(pageUrl);
    if (!doc) return Response.error("Cannot load chapter list page.");

    let chapters = parseTocPage(doc);
    if (chapters.length === 0) return Response.error("Cannot find chapter list.");

    return Response.success(chapters);
}
