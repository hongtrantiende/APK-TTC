load('config.js');

function genresFromNovel(novel) {
    let genres = [];
    let seen = {};
    (novel.genres || []).forEach(function(g) {
        let genre = g.genre || g;
        if (!genre || !genre.name || !genre.slug || seen[genre.slug]) return;
        seen[genre.slug] = true;
        genres.push({title: genre.name, input: BASE_URL + "/the-loai/" + genre.slug, script: "gen.js"});
    });
    return genres;
}

function latestChapters(novel) {
    let parts = [];
    (novel.chapters || []).forEach(function(ch) {
        if (parts.length >= 5) return;
        if (ch && ch.title) parts.push(cleanText(ch.title));
    });
    return parts.join("<br>");
}

function detailFromApi(slug) {
    let novel = apiData("/novels/" + encodeURIComponent(slug));
    if (!novel) return null;
    let details = [];
    if (novel.translationGroup) details.push("<b>Nhom dich:</b> " + cleanText(novel.translationGroup));
    if (novel.novelType && novel.novelType.name) details.push("<b>Loai:</b> " + cleanText(novel.novelType.name));
    if (novel.status) details.push("<b>Trang thai:</b> " + statusText(novel.status));
    if (novel.releaseYear) details.push("<b>Nam:</b> " + novel.releaseYear);
    if (novel.views !== undefined) details.push("<b>Luot xem:</b> " + novel.views);
    if (novel.favorites !== undefined) details.push("<b>Luot thich:</b> " + novel.favorites);
    let latest = latestChapters(novel);
    if (latest) details.push("<b>Moi cap nhat:</b><br>" + latest);

    let foldedStatus = foldText(novel.status);
    return {
        name: cleanText(novel.title),
        cover: normalizeImage(novel.coverImageUrl || novel.coverImageLandscapeUrl),
        host: BASE_URL,
        author: cleanText(novel.author),
        description: cleanText(novel.description),
        detail: details.join("<br>"),
        ongoing: foldedStatus.indexOf("complete") < 0 && foldedStatus.indexOf("completed") < 0 && foldedStatus.indexOf("hoan") < 0,
        genres: genresFromNovel(novel)
    };
}

function detailFromHtml(url) {
    let doc = getDoc(url);
    if (!doc) return null;
    let name = firstText(doc, ["h1"]);
    if (!name) name = metaContent(doc, "meta[property=og:title], meta[name=twitter:title]").split("[")[0];
    let genres = [];
    let seen = {};
    doc.select("a[href^='/the-loai/']").forEach(function(a) {
        let title = cleanText(a.text());
        let href = normalizeUrl(a.attr("href"));
        if (!title || seen[href]) return;
        seen[href] = true;
        genres.push({title: title, input: href, script: "gen.js"});
    });
    return {
        name: name,
        cover: metaImage(doc) || imageFromNode(doc.select("img[alt]").first()),
        host: BASE_URL,
        author: "",
        description: metaContent(doc, "meta[name=description], meta[property=og:description]"),
        detail: "",
        ongoing: true,
        genres: genres
    };
}

function execute(url) {
    let slug = storySlugFromUrl(url);
    if (!slug) return Response.error("Url truyen khong hop le.");
    let detail = detailFromApi(slug) || detailFromHtml(storyUrlFromSlug(slug));
    if (!detail || !detail.name) return Response.error("Khong tim thay thong tin truyen.");
    return Response.success(detail);
}
