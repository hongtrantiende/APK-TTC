load('config.js');

function latestChapters(doc) {
    let data = [];
    let seen = {};
    doc.select(".new-chap a[href*='/chuong-'], a[href*='/chuong-']").forEach(function(a) {
        let href = a.attr("href");
        if (!isChapterUrl(href) || seen[href]) return;
        seen[href] = true;
        let text = cleanText(a.text());
        if (text && data.length < 5) data.push(text);
    });
    return data.join("<br>");
}

function genreList(doc) {
    let data = [];
    let seen = {};
    doc.select(".li--genres a[href^='/the-loai/'], .book-info-text a[href^='/the-loai/']").forEach(function(a) {
        let title = cleanText(a.text());
        let href = a.attr("href");
        if (!title || !href || seen[href]) return;
        seen[href] = true;
        data.push({title: title, input: normalizeUrl(href), script: "gen.js"});
    });
    return data;
}

function execute(url) {
    let target = storyUrlFromAny(url);
    let doc = getDoc(target);
    if (!doc) return null;

    let name = firstText(doc, ["h1[itemprop=name]", ".mRightCol h1", "h1"]);
    let cover = imageFromNode(doc.select(".book-info-pic img, .book-3d img, img[itemprop=image]").first()) || metaImage(doc);
    let author = labelValue(doc, "Tac gia") || firstText(doc, ["a[itemprop=author]"]);
    let status = labelValue(doc, "Trang thai");
    let total = labelValue(doc, "So chuong");
    let views = labelValue(doc, "Luot xem");
    let categories = labelValue(doc, "The loai");
    let description = firstHtml(doc, ["div[itemprop=description]", "#gioithieu .scrolltext", ".intro"]);
    if (!description) description = metaContent(doc, "meta[name=description], meta[property=og:description]");

    let detail = [];
    if (categories) detail.push("<b>The loai:</b> " + categories);
    if (status) detail.push("<b>Trang thai:</b> " + status);
    if (total) detail.push("<b>So chuong:</b> " + total);
    if (views) detail.push("<b>Luot xem:</b> " + views);
    let latest = latestChapters(doc);
    if (latest) detail.push("<b>Moi cap nhat:</b><br>" + latest);

    return Response.success({
        name: name,
        cover: cover,
        host: BASE_URL,
        author: author,
        description: description,
        detail: detail.join("<br>"),
        ongoing: foldText(status).indexOf("hoan") < 0,
        genres: genreList(doc)
    });
}
