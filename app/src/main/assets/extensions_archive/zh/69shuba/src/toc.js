load("config.js");

function parseChapters(doc, bookId, seen) {
    let data = [];
    let items = doc.select("a[href*='/read/" + bookId + "/'], .protected-chapter-link[data-cid-url]");
    for (let i = 0; i < items.size(); i++) {
        let node = items.get(i);
        let link = normalizeUrl(node.attr("href") || node.attr("data-cid-url"));
        if (!isChapterUrl(link) || seen[link]) continue;

        let name = cleanText(node.text() || node.attr("title") || node.attr("data-title"));
        if (!name || name === "上一页" || name === "下一页" || name === "返回目录" || name === "返回目錄") continue;

        seen[link] = true;
        data.push({
            name: name,
            url: link,
            host: BASE_URL
        });
    }
    return data;
}

function execute(url) {
    let bookId = storyIdFromUrl(url);
    if (!bookId) return Response.error("Url truyen khong hop le.");

    let target = isTocUrl(url) ? normalizeUrl(url) : tocUrl(bookId, 1);
    let doc = getDoc(target);
    if (!doc) return loadError();

    let data = parseChapters(doc, bookId, {});
    if (data.length === 0) return loadError("Khong tim thay danh sach chuong.");
    if (data.length > 1 && chapterNumber(data[0].name) > chapterNumber(data[data.length - 1].name)) data.reverse();
    return Response.success(data);
}
