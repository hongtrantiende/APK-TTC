load("config.js");

function execute(url) {
    var response = fetch(normalizeUrl(url));
    if (!response.ok) return null;

    var doc = response.html();
    var content = doc.select(".truyen").first();

    if (!content) {
        content = doc.select("#story").first();
    }
    if (!content) {
        content = doc.select("#chapter-content, .chapter-content").first();
    }
    if (!content) return null;

    content.select("script").remove();
    content.select("style").remove();
    content.select("iframe").remove();
    content.select("ins").remove();
    content.select(".ads, .quangcao").remove();

    var html = content.html();
    if (!html) return null;

    return Response.success(cleanHtml(html));
}
