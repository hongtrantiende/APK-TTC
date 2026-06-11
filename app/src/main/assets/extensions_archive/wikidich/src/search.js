load('config.js');

function execute(key, page) {
    let url = BASE_URL + "/tim-kiem?s=" + encodeURIComponent(key || "");
    if (page && page !== "1") url = addPageParam(url, page);
    let doc = getDoc(url);
    if (!doc) return null;

    let data = [];
    let seen = {};
    doc.select(".item a[href], a[href]").forEach(function(a) {
        let href = a.attr("href");
        if (!isStoryUrl(href)) return;
        let link = normalizeUrl(href);
        if (seen[link]) return;
        let name = cleanText(a.attr("title") || a.text()).replace(/^Doc truyen\s+/i, "").replace(/\s*doc online$/i, "");
        let i = a.select("i").first();
        let desc = i ? cleanText(i.text()) : "";
        if (i) name = cleanText(name.replace(desc, ""));
        if (!name || foldText(name).indexOf("chuong") >= 0) return;
        seen[link] = true;
        data.push({name: name, link: link, cover: "", description: desc, host: BASE_URL});
    });
    return Response.success(data, nextPage(doc, url));
}
