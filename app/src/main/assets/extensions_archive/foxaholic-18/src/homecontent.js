load("config.js");

function isChallengeDoc(doc) {
    if (!doc) return true;
    var titleEl = selFirst(doc, "title");
    var title = titleEl ? titleEl.text().trim() : "";
    if (title.indexOf("Just a moment") >= 0) return true;
    var text = doc.text() || "";
    return text.indexOf("security verification") >= 0 || text.indexOf("Checking your Browser") >= 0 || doc.select("iframe[title*='Cloudflare']").size() > 0;
}

function execute(url, page) {
    var p = page ? parseInt(page) : 1;
    var fetchUrl = p <= 1
        ? BASE_URL + "/novel/"
        : BASE_URL + "/novel/page/" + p + "/";

    // Browser load — bot-detector JS set _awl, server SSR trả HTML đầy đủ với .page-item-detail items
    var doc = null;
    var browser = Engine.newBrowser();
    try { doc = browser.launch(fetchUrl, 20000); } catch (e) { doc = null; }
    try { browser.close(); } catch (e2) {}

    if (!doc || isChallengeDoc(doc)) return Response.success([], null);

    var items = parseList(doc);
    if (!items || items.length === 0) return Response.success([], null);
    return Response.success(items, getNextPage(doc, p));
}