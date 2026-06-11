load("config.js");

function execute(url) {
    var chapUrl = resolveUrl(url);
    // Use light fetch — blocks all images/CSS/fonts for max speed
    var doc = fetchCFLight(chapUrl);
    if (!doc) return Response.error("Lỗi tải nội dung chương");

    var el = selFirst(doc, ".chapter-content .content, .content, #BookText, #content");
    if (!el) return Response.error("Không tìm thấy nội dung chương");

    // Remove junk
    el.select("script, style, ins, iframe, .gadBlock, .adBlock, [data-ad], a").remove();

    var html = el.html();
    if (!html || html.trim().length < 50) return Response.error("Nội dung chương trống");

    // Clean ad text
    html = html.replace(/溫馨提示[\s\S]*?等功能/g, "");
    html = html.replace(/&nbsp;/g, " ");

    return Response.success(html);
}
