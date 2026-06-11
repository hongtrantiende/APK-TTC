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

function normalizeStoryUrl(url) {
    var storyUrl = resolveUrl(url);
    storyUrl = storyUrl.replace(/[?&]vbook_premium_chapter=\d+/i, "");
    storyUrl = storyUrl.replace(/[?&]$/, "");
    if (/\/chapter-[^\/]+\/?$/i.test(storyUrl)) {
        return storyUrl.replace(/\/chapter-[^\/]+\/?$/i, "/");
    }
    if (/\/(?:\d+|n-a)\/?$/i.test(storyUrl)) {
        return storyUrl.replace(/\/(?:\d+|n-a)\/?$/i, "/");
    }
    return storyUrl;
}

function getMetaContent(doc, selector) {
    var meta = selFirst(doc, selector);
    return meta ? (meta.attr("content") || "").trim() : "";
}

function findSummaryValue(doc, label) {
    var items = doc.select(".post-content_item");
    for (var i = 0; i < items.size(); i++) {
        var item = items.get(i);
        var heading = selFirst(item, ".summary-heading h5");
        if (!heading) continue;
        var headingText = heading.text().trim();
        if (headingText.indexOf(label) === -1) continue;
        var content = selFirst(item, ".summary-content");
        if (!content) return "";
        return cleanInlineCss(content.text().trim());
    }
    return "";
}

function findSummaryItem(doc, label) {
    var items = doc.select(".post-content_item");
    for (var i = 0; i < items.size(); i++) {
        var item = items.get(i);
        var heading = selFirst(item, ".summary-heading h5");
        if (!heading) continue;
        if (heading.text().trim().indexOf(label) !== -1) return item;
    }
    return null;
}

function pickFirstText(doc, selector) {
    var el = selFirst(doc, selector);
    return el ? el.text().trim() : "";
}

function buildDescription(doc) {
    var descEl = selFirst(doc, ".manga-excerpt, .summary__content, .description-summary, .entry-content");
    if (descEl) {
        descEl.select("script, style, ins, iframe, img, .adsbygoogle, .sharedaddy, .post-tags, .post-rating, .foxah-entity-placement, [id^='foxah-'], [class*='bg-ssp']").remove();
        var ps = descEl.select("p");
        if (ps.size() > 0) {
            var parts = [];
            for (var i = 0; i < ps.size(); i++) {
                var text = cleanInlineCss(ps.get(i).text().trim());
                if (text && /ko-?fi|author['’]s other work|discounted and ad-free pdf version|just click down on the appropriate novel category/i.test(text)) break;
                if (text) parts.push(text);
            }
            if (parts.length > 0) return parts.join("\n");
        }
        var raw = cleanInlineCss(descEl.text().trim());
        if (raw) return raw;
    }
    return getMetaContent(doc, "meta[property='og:description'], meta[name='description']");
}

function collectGenreLinks(doc) {
    var item = findSummaryItem(doc, "Genre");
    if (!item) return [];
    return collectLinks(item, ".summary-content a[href], .genres-content a[href]", "genrecontent.js");
}

function collectLinks(doc, selector, scriptName) {
    var out = [];
    var seen = {};
    var links = doc.select(selector);
    for (var i = 0; i < links.size(); i++) {
        var link = links.get(i);
        var text = link.text().trim();
        if (!text || seen[text]) continue;
        seen[text] = true;
        out.push({ title: text, input: stripHost(link.attr("href") || ""), script: scriptName });
    }
    return out;
}

function execute(url) {
    var storyUrl = normalizeStoryUrl(url);
    var doc = loadDoc(storyUrl);
    if (!doc) return Response.error("Không tải được trang truyện hoặc bị Cloudflare chặn");

    var title = pickFirstText(doc, "h1.entry-title, .post-title h1, .post-title h3, h1") || getMetaContent(doc, "meta[property='og:title']");
    if (!title) return Response.error("Không đọc được tên truyện");

    var coverRoot = selFirst(doc, ".summary_image, .tab-summary .summary_image, .profile-manga.summary-layout-1 .summary_image, .profile-manga.summary-layout-1");
    var cover = coverRoot ? extractCover(coverRoot) : "";
    if (!cover) cover = getMetaContent(doc, "meta[property='og:image']");
    var author = pickFirstText(doc, ".author-content a, .manga-authors a, a[href*='/novel-author/'], a[href*='/author/'], a[href*='/writer/']");
    var team = pickFirstText(doc, ".artist-content a, a[href*='/team/']");
    var translationStatus = findSummaryValue(doc, "Translation");
    var novelStatus = findSummaryValue(doc, "Novel");
    var typeText = findSummaryValue(doc, "Type");
    var coinStatus = findSummaryValue(doc, "Coin Status");
    var titleAlt = findSummaryValue(doc, "Title");
    var genreEntries = collectGenreLinks(doc);
    var description = buildDescription(doc);

    var detail = [];
    if (titleAlt) detail.push("Alternative: " + titleAlt);
    if (typeText) detail.push("Type: " + typeText);
    if (team) detail.push("Team: " + team);
    if (novelStatus) detail.push("Novel: " + novelStatus);
    if (translationStatus) detail.push("Translation: " + translationStatus);
    if (coinStatus) detail.push("Coin Status: " + coinStatus);

    return Response.success({
        name: title,
        cover: cover,
        host: HOST,
        author: author,
        description: description,
        detail: detail.join("\n"),
        ongoing: translationStatus ? (translationStatus.indexOf("Finished") < 0 && translationStatus.indexOf("Completed") < 0) : (novelStatus ? (novelStatus.indexOf("Complete") < 0 && novelStatus.indexOf("Completed") < 0) : true),
        genres: genreEntries,
        suggests: [],
        comments: []
    });
}