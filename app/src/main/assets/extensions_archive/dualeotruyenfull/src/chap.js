load("config.js");

function execute(url) {
    var chapUrl = resolveUrl(url);
    var doc = fetchDoc(chapUrl);
    if (!doc) return Response.error("Không tải được nội dung chương");

    var el = selFirst(doc, "#chapter-content");
    if (!el) return Response.error("Không tìm thấy nội dung chương");

    el.select("script, style, a, ins, noscript, iframe, .ads, .ad, .chapter-nav").remove();
    var html = el.html();
    if (!html) return Response.error("Nội dung chương trống");
    return Response.success(html);
}
