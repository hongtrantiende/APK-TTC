function execute(url) {
    const doc = Http.get(url).html();
    let list = [];
    let seen = {};
    let host = getHost(url);
    let chapters = doc.select("#list dt:contains(正文) ~ dd a[href]");

    if (chapters.size() > 0) {
        appendChapters(chapters, list, seen, host, false);
    } else {
        chapters = doc.select("#list dd a[href]");
        appendChapters(chapters, list, seen, host, true);
    }

    return Response.success(list);
}

function appendChapters(chapters, list, seen, host, reverseScan) {
    let start = reverseScan ? chapters.size() - 1 : 0;
    let end = reverseScan ? -1 : chapters.size();
    let step = reverseScan ? -1 : 1;

    for (let i = start; i !== end; i += step) {
        let a = chapters.get(i);
        let name = a.text().trim();
        let chapterUrl = absoluteUrl(a.attr("href"), host);
        if (!name || !chapterUrl || seen[name] || seen[chapterUrl]) continue;

        seen[name] = true;
        seen[chapterUrl] = true;
        let item = { name: name, url: chapterUrl, host: host };
        if (reverseScan) {
            list.unshift(item);
        } else {
            list.push(item);
        }
    }
}

function absoluteUrl(href, host) {
    if (!href) return "";
    if (/^https?:\/\//i.test(href)) return href;
    if (href.indexOf("//") === 0) return "https:" + href;
    if (href.charAt(0) === "/") return host + href;
    return host + "/" + href;
}

function getHost(url) {
    let match = String(url || "").match(/^(https?:\/\/[^\/]+)/i);
    return match ? match[1] : "https://www.tushumi.cc";
}
