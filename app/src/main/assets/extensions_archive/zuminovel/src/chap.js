load("config.js");

function execute(url) {
    url = normalizeUrl(url);
    var res = getHtml(url);
    var loginError = loginErrorIfNeeded(res);
    if (loginError) return loginError;
    if (!res.ok) return Response.error("Cannot load: " + res.status);
    var doc = res.html();
    doc.select("script,style,button,nav,header,footer").remove();
    var content = doc.select("#chapter-content").first() || doc.select("article.chapter-content").first() || doc.select("article.prose").first();
    if (!content) return Response.error("No content found");
    return Response.success((content.html() + "").replace(/&nbsp;/g, " "));
}
