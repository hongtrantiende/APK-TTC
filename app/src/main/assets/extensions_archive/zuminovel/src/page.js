load("config.js");

function execute(url) {
    url = normalizeUrl(url);
    if (url.indexOf("?tab=chapters") < 0) url = url.replace(/\?.*$/, "") + "?tab=chapters";
    return Response.success([url]);
}
