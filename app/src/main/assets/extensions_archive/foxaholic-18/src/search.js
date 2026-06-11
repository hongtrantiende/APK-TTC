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

function execute(keyword, page) {
    var p = page ? parseInt(page) : 1;
    var q = encodeURIComponent(keyword);
    var fetchUrl = p <= 1
        ? BASE_URL + "/?s=" + q + "&post_type=wp-manga"
        : BASE_URL + "/page/" + p + "/?s=" + q + "&post_type=wp-manga";

    var doc = loadDoc(fetchUrl);
    if (!doc) return Response.success([], null);

    var items = parseList(doc);
    if (!items || items.length === 0) return Response.success([], null);

    return Response.success(items, getNextPage(doc, p));
}