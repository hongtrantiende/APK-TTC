load("config.js");

function execute(url) {
    var response = fetch(normalizeUrl(url));
    if (!response.ok) return null;

    var doc = response.html();
    doc.select(".truyen script").remove();

    var html = doc.select(".truyen").html();
    if (!html) return null;

    return Response.success(cleanHtml(html));
}

function cleanHtml(html) {
    return (html || "")
        .replace(/<!--[\s\S]*?-->/g, "")
        .replace(/<a[^>]*>(.*?)<\/a>/gi, "$1")
        .replace(/&nbsp;/gi, " ")
        .replace(/(<br\s*\/?>\s*){3,}/gi, "<br><br>")
        .trim();
}
