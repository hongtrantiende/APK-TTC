load("config.js");

function execute(url) {
    var chapUrl = resolveUrl(url);
    var res = fetchRetry(chapUrl);
    if (!res || !res.ok) return Response.error("Khong tai duoc chuong");
    var doc = res.html();
    if (!doc) return Response.error("Khong doc duoc noi dung chuong");

    var el = selFirst(doc, ".reading-content .text-left, .reading-content, .text-left, .entry-content");
    if (!el) return Response.error("Khong tim thay noi dung chuong");

    el.select("script, style, ins, noscript, iframe, button, .adsense, .adsbygoogle, .ad, [class*='ads']").remove();
    el.select("a").remove();

    var html = el.html();
    if (!html || html.trim().length === 0) return Response.error("Noi dung chuong trong");

    return Response.success(html);
}