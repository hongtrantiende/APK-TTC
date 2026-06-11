load('config.js');

function chaptersUrl(url) {
    return stripTrailingSlash(storyUrlFromAny(url)) + "/ajax/chapters/";
}

function parseChapters(doc) {
    let data = [];
    let seen = {};
    doc.select("li.wp-manga-chapter a[href], .wp-manga-chapter a[href], .chapter-list a[href], .listing-chapters_wrap a[href], a[href*='/chapter-']").forEach(function(a) {
        let href = a.attr("href");
        if (!isChapterUrl(href)) return;
        let link = normalizeUrl(href);
        if (seen[link]) return;
        seen[link] = true;
        data.push({
            name: cleanText(a.text()) || cleanText(a.attr("title")),
            url: link,
            host: BASE_URL
        });
    });
    return data;
}

function execute(url) {
    let target = storyUrlFromAny(url);
    let text = "";
    try {
        text = postText(chaptersUrl(target), "", target);
    } catch (error) {
    }

    let doc = text ? Html.parse(text) : null;
    let data = doc ? parseChapters(doc) : [];
    if (data.length === 0) {
        doc = getDoc(target);
        if (!doc) return loadError();
        data = parseChapters(doc);
    }

    return Response.success(sortChapters(data));
}
