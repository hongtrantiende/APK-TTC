load("config.js");

function execute(url) {
    let chapterUrl = normalizeUrl(url);
    let doc = fetchDoc(chapterUrl);
    if (!doc) return Response.error("Cannot load chapter.");

    let content = extractChapterContent(doc);
    if (!content) return Response.error("Cannot find chapter content.");

    return Response.success(content);
}
