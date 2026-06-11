load('config.js');

function storyLinkNode(e) {
    let links = e.select("a[href]");
    for (let i = 0; i < links.size(); i++) {
        let a = links.get(i);
        if (isStoryUrl(a.attr("href"))) return a;
    }
    return null;
}

function itemDescription(e) {
    let parts = [];
    e.select(".list-chapter a[href], .chapter a[href], .wp-manga-chapter a[href], a[href*='/chapter-']").forEach(function(a) {
        let href = a.attr("href");
        let text = cleanText(a.text());
        if (text && isChapterUrl(href) && parts.length < 3) parts.push(text);
    });
    let extra = firstText(e, [".post-on", ".latest-chap", ".chapter-release-date", ".manga-views", ".item-summary"]);
    if (extra && parts.length < 4) parts.push(extra);
    return parts.join(" - ");
}

function novelItem(e) {
    let linkNode = storyLinkNode(e);
    let imgNode = e.select(".item-thumb img, .summary_image img, .tab-thumb img, .post-thumbnail img, img").first();
    let name = firstText(e, [
        ".post-title a",
        ".manga-title a",
        ".item-title a",
        "h3 a",
        "h4 a",
        "h5 a"
    ]);
    if (!name && linkNode) name = cleanText(linkNode.attr("title") || linkNode.text());
    if (!name && imgNode) name = cleanText(imgNode.attr("alt") || imgNode.attr("title"));

    return {
        name: name,
        link: linkNode ? normalizeUrl(linkNode.attr("href")) : "",
        cover: imageFromNode(imgNode),
        description: itemDescription(e),
        host: BASE_URL
    };
}

function validName(name) {
    let folded = foldText(name);
    return name && folded.indexOf("chapter") < 0 && folded.indexOf("read first") < 0 && folded.indexOf("read last") < 0;
}

function collectCards(doc) {
    let data = [];
    let seen = {};
    let nodes = doc.select(".page-item-detail, .c-tabs-item__content, .row.c-tabs-item__content, .manga__item, .manga-item, .popular-item-wrap");
    nodes.forEach(function(e) {
        let item = novelItem(e);
        if (!validName(item.name) || !item.link || seen[item.link]) return;
        seen[item.link] = true;
        data.push(item);
    });
    return data;
}

function collectLinks(doc) {
    let data = [];
    let seen = {};
    doc.select("a[href*='/novel/']").forEach(function(a) {
        let href = a.attr("href");
        if (!isStoryUrl(href)) return;
        let link = normalizeUrl(href);
        if (seen[link]) return;
        let name = cleanText(a.attr("title") || a.text());
        if (!validName(name)) return;
        seen[link] = true;
        data.push({name: name, link: link, cover: "", description: "", host: BASE_URL});
    });
    return data;
}

function execute(url, page) {
    let target = listPageUrl(url || "/novel/", page);
    let doc = getDoc(target);
    if (!doc) return loadError();

    let data = collectCards(doc);
    if (data.length === 0) data = collectLinks(doc);
    return Response.success(data, nextPage(doc, target));
}
