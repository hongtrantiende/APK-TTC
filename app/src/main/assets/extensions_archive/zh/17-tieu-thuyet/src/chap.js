load("config.js");

function execute(url) {
    var info = extractChapterInfo(url);
    if (!info.bookId || !info.chapterId) return null;

    var detailUrl = buildDetailUrl(info.bookId);
    var totalPages = 1;
    var paragraphs = [];

    fetchText(detailUrl, BASE_URL);

    for (var pageNo = 1; pageNo <= totalPages; pageNo++) {
        var currentUrl = buildChapterUrl(info.bookId, info.chapterId, pageNo);
        var referer = pageNo === 1
            ? detailUrl
            : buildChapterUrl(info.bookId, info.chapterId, pageNo - 1);
        var doc = fetchStableDocument(currentUrl, referer, detailUrl);
        if (!doc) break;

        var pageTotal = extractTotalPages(doc);
        if (pageTotal > totalPages) totalPages = pageTotal;

        var pageParagraphs = extractChapterParagraphs(doc);
        for (var i = 0; i < pageParagraphs.length; i++) {
            pushChapterParagraph(paragraphs, pageParagraphs[i]);
        }
    }

    return Response.success(renderChapterHtml(paragraphs));
}
