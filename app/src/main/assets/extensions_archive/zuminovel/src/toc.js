load("config.js");

function execute(url) {
    url = normalizeUrl(url);
    var res = fetch(url);
    var loginError = loginErrorIfNeeded(res);
    if (loginError) return loginError;
    if (!res.ok) return Response.error("Cannot load: " + res.status);
    var chapters = extractChapterLinks(res.text(), url);
    if (chapters.length === 0) return Response.error("No chapters found");
    return Response.success(chapters);
}
