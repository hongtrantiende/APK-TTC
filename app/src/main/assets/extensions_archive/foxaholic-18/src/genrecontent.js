load("config.js");

function isChallengeDoc(doc) {
    if (!doc) return true;
    var titleEl = selFirst(doc, "title");
    var title = titleEl ? titleEl.text().trim() : "";
    if (title.indexOf("Just a moment") >= 0) return true;
    var text = doc.text() || "";
    return text.indexOf("security verification") >= 0 || text.indexOf("Checking your Browser") >= 0 || doc.select("iframe[title*='Cloudflare']").size() > 0;
}

function loadDoc(url) {
    var fullUrl = resolveUrl(url);
    var doc = null;
    var browser = Engine.newBrowser();
    try { doc = browser.launch(fullUrl, 15000); } catch (e) { doc = null; }
    try { browser.close(); } catch (e2) {}
    if (doc && !isChallengeDoc(doc)) return doc;

    var res = fetchRetry(fullUrl);
    if (!res || !res.ok) return null;
    try { doc = res.html(); } catch (e3) { doc = null; }
    return doc && !isChallengeDoc(doc) ? doc : null;
}

function execute(url, page) {
    var p = page ? parseInt(page) : 1;
    var basePath = url || "/genre/smut/";
    if (basePath.indexOf("http") === 0) basePath = stripHost(basePath);
    if (basePath.indexOf("/genre/") !== 0) basePath = "/genre/" + basePath.replace(/^\/+|\/+$/g, "") + "/";
    if (basePath.charAt(basePath.length - 1) !== "/") basePath += "/";

    var fetchUrl = p <= 1
        ? BASE_URL + basePath
        : BASE_URL + basePath.replace(/\/?$/, "/") + "page/" + p + "/";

    var doc = loadDoc(fetchUrl);
    if (!doc) return Response.error("Không tải được trang thể loại");

    var items = parseList(doc);
    if (!items || items.length === 0) return Response.success([], null);

    return Response.success(items, getNextPage(doc, p));
}