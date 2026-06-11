load('config.js');

function addChapter(data, seen, storySlug, ch) {
    let slug = chapterSlug(ch);
    if (!slug || seen[slug]) return;
    seen[slug] = true;
    data.push({
        name: cleanText(ch.title) || ("Chuong " + chapterNumber(slug)),
        url: chapterUrl(storySlug, slug),
        host: BASE_URL
    });
}

function tocFromGrouped(storySlug) {
    let grouped = apiData("/novels/" + encodeURIComponent(storySlug) + "/chapters/grouped?refresh=true");
    if (!grouped) return [];
    let data = [];
    let seen = {};
    (grouped.volumes || []).forEach(function(volume) {
        (volume.chapters || []).forEach(function(ch) {
            addChapter(data, seen, storySlug, ch);
        });
    });
    return data;
}

function tocFromNovel(storySlug) {
    let novel = apiData("/novels/" + encodeURIComponent(storySlug));
    let data = [];
    let seen = {};
    if (!novel) return data;
    (novel.chapters || []).forEach(function(ch) {
        addChapter(data, seen, storySlug, ch);
    });
    return data;
}

function tocFromHtml(storySlug) {
    let doc = getDoc(storyUrlFromSlug(storySlug));
    let data = [];
    let seen = {};
    if (!doc) return data;
    doc.select("a[href^='/" + storySlug + "/']").forEach(function(a) {
        let href = a.attr("href");
        if (!isChapterUrl(href)) return;
        let slug = chapterSlugFromUrl(href);
        if (!slug || seen[slug]) return;
        seen[slug] = true;
        data.push({name: cleanText(a.text()) || slug, url: normalizeUrl(href), host: BASE_URL});
    });

    let html = doc.html ? doc.html() : "";
    let re = /"title"\s*:\s*"([^"]+)"[\s\S]{0,300}?"slug"\s*:\s*(?:"([^"]+)"|\{\s*"current"\s*:\s*"([^"]+)"\s*\})/g;
    let match;
    while ((match = re.exec(html)) !== null) {
        let slug = match[2] || match[3];
        if (!slug || seen[slug]) continue;
        seen[slug] = true;
        data.push({name: htmlDecode(match[1]), url: chapterUrl(storySlug, slug), host: BASE_URL});
    }
    return data;
}

function execute(url) {
    let storySlug = storySlugFromUrl(url);
    if (!storySlug) return Response.error("Url truyen khong hop le.");
    let data = tocFromGrouped(storySlug);
    if (data.length === 0) data = tocFromNovel(storySlug);
    if (data.length === 0) data = tocFromHtml(storySlug);
    if (data.length > 1 && chapterNumber(data[0].name + " " + data[0].url) > chapterNumber(data[data.length - 1].name + " " + data[data.length - 1].url)) {
        data.reverse();
    }
    return Response.success(data);
}
