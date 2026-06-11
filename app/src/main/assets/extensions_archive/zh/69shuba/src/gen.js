load("config.js");

function bestStoryLink(node) {
    let links = node.select("a[href*='/book/']");
    let fallback = null;
    for (let i = 0; i < links.size(); i++) {
        let a = links.get(i);
        let href = normalizeUrl(a.attr("href"));
        if (!isStoryUrl(href)) continue;
        let text = cleanText(a.text() || a.attr("title"));
        if (!fallback) fallback = a;
        if (text && text.length < 80) return a;
    }
    return fallback;
}

function metaLines(node, name) {
    let parts = [];
    let meta = firstText(node, ["p.fs12", "p.gray", ".gray"]);
    if (meta) parts.push(meta);

    let links = node.select("a[href*='/book/']");
    for (let i = 0; i < links.size(); i++) {
        let text = cleanText(links.get(i).text());
        if (!text || text === name || text.length > 180) continue;
        if (text.indexOf("作者:") === 0 && meta.indexOf(text) >= 0) continue;
        parts.push(text);
        break;
    }
    return parts.join(" - ");
}

function novelItem(node) {
    let linkNode = bestStoryLink(node);
    let imgNode = node.select("img").first();
    let name = firstText(node, [
        ".s_title",
        "h2 a",
        "h3 a",
        "a[title]"
    ]);
    if (!name && linkNode) name = cleanText(linkNode.text() || linkNode.attr("title"));
    if (!name && imgNode) name = cleanText(imgNode.attr("alt") || imgNode.attr("title"));
    return {
        name: name,
        link: linkNode ? normalizeUrl(linkNode.attr("href")) : "",
        cover: imageFromNode(imgNode),
        description: metaLines(node, name),
        host: BASE_URL
    };
}

function collectCards(doc) {
    let selectors = [".article", ".s_div"];
    for (let s = 0; s < selectors.length; s++) {
        let nodes = doc.select(selectors[s]);
        let data = [];
        let seen = {};
        for (let i = 0; i < nodes.size(); i++) {
            let item = novelItem(nodes.get(i));
            if (!item.name || !item.link || seen[item.link]) continue;
            seen[item.link] = true;
            data.push(item);
        }
        if (data.length > 0) return data;
    }
    return [];
}

function collectByLinks(doc) {
    let data = [];
    let seen = {};
    let links = doc.select("a[href*='/book/']");
    for (let i = 0; i < links.size(); i++) {
        let a = links.get(i);
        let link = normalizeUrl(a.attr("href"));
        let name = cleanText(a.attr("title") || a.text());
        if (!isStoryUrl(link) || !name || name.length > 80 || seen[link]) continue;
        seen[link] = true;
        data.push({
            name: name,
            link: link,
            cover: "",
            description: "",
            host: BASE_URL
        });
    }
    return data;
}

function execute(url, page) {
    let target = listPageUrl(url, page);
    let doc = getDoc(target);
    if (!doc) return loadError();

    let data = collectCards(doc);
    if (data.length === 0) data = collectByLinks(doc);
    let marker = isCategoryUrl(target) ? "/fenlei/" : "";
    return Response.success(data, nextPage(doc, target, marker));
}
