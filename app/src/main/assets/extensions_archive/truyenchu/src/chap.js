load("config.js");

function execute(url) {
    var chapUrl = resolveUrl(url);
    var res = fetchRetry(chapUrl);
    if (!res || !res.ok) return Response.error("Không tải được chương");
    var doc = res.html();
    if (!doc) return Response.error("Không đọc được nội dung chương");

    // Lấy vùng nội dung chương — Madara theme
    var el = selFirst(doc, ".reading-content .text-left, .reading-content, .entry-content-single, .text-left");
    if (!el) return Response.error("Không tìm thấy nội dung chương");

    // Loại bỏ script, style, ads, link quảng cáo
    el.select("script, style, ins, noscript, iframe, button, .adsense, .adsbygoogle, .ad, [class*='ads']").remove();
    // Loại bỏ các link inline (thường là quảng cáo nhúng trong text)
    el.select("a").remove();

    var html = el.html();
    if (!html || html.trim().length === 0) return Response.error("Nội dung chương trống");

    return Response.success(html);
}
