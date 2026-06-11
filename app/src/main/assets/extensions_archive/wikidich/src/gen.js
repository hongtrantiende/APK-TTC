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
    e.select("p.line").forEach(function(p) {
        let text = lineValue(p);
        if (text && parts.length < 4) parts.push(text);
    });
    if (parts.length === 0) {
        let chapter = firstText(e, [".ichapter", ".chapter", ".caption .ichapter"]);
        if (chapter) parts.push(chapter);
    }
    return parts.join(" - ");
}

function novelItem(e) {
    let linkNode = storyLinkNode(e);
    let imgNode = e.select("a.cover img, .cover img, img").first();
    let name = firstText(e, ["h3 a", ".caption h3", ".caption a", "a.cover"]);
    if (!name && linkNode) name = cleanText(linkNode.attr("title") || linkNode.text()).replace(/\s*doc online$/i, "");
    if (!name && imgNode) name = cleanText(imgNode.attr("alt") || imgNode.attr("title")).replace(/\s*doc online$/i, "");

    return {
        name: name,
        link: linkNode ? normalizeUrl(linkNode.attr("href")) : "",
        cover: imageFromNode(imgNode),
        description: itemDescription(e),
        host: BASE_URL
    };
}

function collectCards(doc) {
    let data = [];
    let seen = {};
    let nodes = doc.select(".truyen-list .item, .full-book .item, .chapter-container .item");
    nodes.forEach(function(e) {
        let item = novelItem(e);
        if (!item.name || !item.link || seen[item.link]) return;
        seen[item.link] = true;
        data.push(item);
    });
    return data;
}

function collectLinks(doc) {
    let data = [];
    let seen = {};
    doc.select("a[href]").forEach(function(a) {
        let href = a.attr("href");
        if (!isStoryUrl(href)) return;
        let link = normalizeUrl(href);
        if (seen[link]) return;
        let name = cleanText(a.attr("title") || a.text()).replace(/^Doc truyen\s+/i, "").replace(/\s*doc online$/i, "");
        if (!name || foldText(name).indexOf("chuong") >= 0) return;
        seen[link] = true;
        data.push({name: name, link: link, cover: "", description: "", host: BASE_URL});
    });
    return data;
}

function execute(url, page) {
    let target = listPageUrl(url, page);
    let doc = getDoc(target);
    if (!doc) return null;

    let data = collectCards(doc);
    if (data.length === 0) data = collectLinks(doc);
    return Response.success(data, nextPage(doc, target));
}
