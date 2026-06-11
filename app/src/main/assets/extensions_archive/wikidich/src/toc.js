load('config.js');

function docFromChapterList(url) {
    if (normalizeUrl(url).indexOf("/get/listchap/") < 0) return getDoc(url);
    let text = getText(url);
    if (!text) return null;
    try {
        let json = JSON.parse(text);
        if (json && json.data) return Html.parse("<div>" + json.data + "</div>");
    } catch (error) {
    }
    return Html.parse("<div>" + text + "</div>");
}

function parseChapters(doc) {
    let data = [];
    let seen = {};
    let links = doc.select("#chapter-list a[href*='/chuong-'], a[href*='/chuong-']");
    links.forEach(function(a) {
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
    let target = isChapterUrl(url) ? storyUrlFromAny(url) : normalizeUrl(url);
    let doc = docFromChapterList(target);
    if (!doc) return null;

    let data = parseChapters(doc);
    if (data.length > 1 && chapterNumber(data[0].name + " " + data[0].url) > chapterNumber(data[data.length - 1].name + " " + data[data.length - 1].url)) {
        data.reverse();
    }
    return Response.success(data);
}
