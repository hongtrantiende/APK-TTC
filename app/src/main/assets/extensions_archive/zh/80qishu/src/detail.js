load('config.js');

function execute(url) {
    let target = storyUrlFromAny(url);
    let doc = getDoc(target);
    if (!doc) return loadError();

    let name = stripBookMarks(firstText(doc, [".bookcover h1.title", "h1.title", "h1"]) || metaContent(doc, "meta[property=og:title]"));
    if (!name) return Response.error("未找到小说信息。");

    let cover = imageFromNode(doc.select(".bookcover .pic img, .bookcover img").first()) || metaImage(doc);
    let author = textByPrefix(doc.select(".bookcover").first() || doc, "\u4f5c\u8005");
    let status = textByPrefix(doc.select(".bookcover").first() || doc, "\u8fdb\u5ea6");
    let size = textByPrefix(doc.select(".bookcover").first() || doc, "\u5927\u5c0f");
    let update = textByPrefix(doc.select(".bookcover").first() || doc, "\u66f4\u65b0");
    let description = firstHtml(doc, [".bookintro .con", ".bookintro"]);

    let detail = [];
    let category = textByPrefix(doc.select(".bookcover").first() || doc, "\u5206\u7c7b");
    if (category) detail.push("<b>分类：</b> " + category);
    if (size) detail.push("<b>大小：</b> " + size);
    if (status) detail.push("<b>进度：</b> " + status);
    if (update) detail.push("<b>更新：</b> " + update);

    return Response.success({
        name: name,
        cover: cover,
        host: BASE_URL,
        author: author,
        description: description,
        detail: detail.join("<br>"),
        ongoing: status.indexOf("\u5df2\u5b8c\u7ed3") < 0,
        genres: genresFromDoc(doc)
    });
}
