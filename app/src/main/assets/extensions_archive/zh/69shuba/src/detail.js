load("config.js");

function buildDetail(doc) {
    let lines = [];
    let category = metaContent(doc, "meta[property=og:novel:category]") || textByPrefix(doc, ["类别", "類別"]);
    let status = metaContent(doc, "meta[property=og:novel:status]") || textByPrefix(doc, ["状态", "狀態"]);
    let words = textByPrefix(doc, ["字数", "字數"]);
    let update = metaContent(doc, "meta[property=og:novel:update_time]") || textByPrefix(doc, ["更新"]);
    let latest = metaContent(doc, "meta[property=og:novel:latest_chapter_name]") || textByPrefix(doc, ["最新"]);

    if (category) lines.push("<b>类别:</b> " + escapeHtml(category));
    if (status) lines.push("<b>状态:</b> " + escapeHtml(status));
    if (words) lines.push("<b>字数:</b> " + escapeHtml(words));
    if (update) lines.push("<b>更新:</b> " + escapeHtml(update));
    if (latest) lines.push("<b>最新章节:</b> " + escapeHtml(latest));
    return lines.join("<br>");
}

function isOngoing(status) {
    let folded = foldText(status);
    return folded.indexOf("全本") < 0 &&
        folded.indexOf("完结") < 0 &&
        folded.indexOf("完結") < 0 &&
        folded.indexOf("已完") < 0;
}

function execute(url) {
    let doc = getDoc(storyUrlFromAny(url));
    if (!doc) return loadError();

    let name = metaContent(doc, "meta[property=og:title], meta[property=og:novel:book_name]") || firstText(doc, ["h1"]);
    if (!name) return Response.error("Khong tim thay thong tin truyen.");

    let cover = metaImage(doc) || imageFromNode(doc.select("img").first());
    let author = metaContent(doc, "meta[property=og:novel:author]") || textByPrefix(doc, ["作者"]);
    let status = metaContent(doc, "meta[property=og:novel:status]") || textByPrefix(doc, ["状态", "狀態"]);
    let description = firstHtml(doc, [
        "#bookintro",
        ".bookintro",
        ".intro",
        ".jjtxt",
        "[itemprop=description]"
    ]);
    if (!description) {
        let metaDesc = metaContent(doc, "meta[name=description], meta[property=og:description]");
        if (metaDesc) description = "<p>" + escapeHtml(metaDesc) + "</p>";
    }

    return Response.success({
        name: name,
        cover: cover,
        host: BASE_URL,
        author: author,
        description: description,
        detail: buildDetail(doc),
        ongoing: isOngoing(status),
        genres: genresFromDoc(doc)
    });
}
