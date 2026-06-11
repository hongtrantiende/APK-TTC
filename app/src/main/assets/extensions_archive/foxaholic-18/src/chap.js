load("config.js");

var PREMIUM_CHAPTER_URL_RE = /[?&]vbook_premium_chapter=(\d+)$/i;

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

function execute(url) {
    var premiumMatch = PREMIUM_CHAPTER_URL_RE.exec(url || "");
    if (premiumMatch) {
        return Response.error("Chương này đang bị khóa coin trên Foxaholic 18, không có URL đọc công khai");
    }

    var chapUrl = resolveUrl(url);
    var doc = loadDoc(chapUrl);
    if (!doc) return Response.error("Không tải được nội dung chương hoặc bị Cloudflare chặn");

    var content = selFirst(doc, ".reading-content .text-left, .reading-content, .text-left, .entry-content-single, .entry-content, .chapter-content");
    if (!content) return Response.error("Không tìm thấy nội dung chương");

    content.select("script, style, ins, noscript, iframe, button, a, .adsbygoogle, .sharedaddy, .google-auto-placed, .code-block, .foxah-entity-placement, [id^='foxah-'], [class*='bg-ssp']").remove();

    var html = content.html();
    if (!html || html.trim().length === 0) return Response.error("Nội dung chương trống");

    return Response.success(html);
}