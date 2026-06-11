load('config.js');

function paramsFromListUrl(url, page) {
    let path = pathOf(url);
    let params = {page: pageNum(page), limit: 30};
    let sort = queryParam(url, "sort");
    let status = queryParam(url, "status");
    if (sort) params.sort = sort;
    if (status) params.status = status;

    let genreMatch = /^\/the-loai\/([^/]+)\/?$/i.exec(path);
    if (genreMatch) params.genre = genreMatch[1];

    if (path === "/hot") params.sort = "views";
    if (!params.sort) params.sort = "updated";
    return params;
}

function collectFromApi(url, page) {
    let path = pathOf(url);
    let data = null;
    data = apiData(apiPath("/novels", paramsFromListUrl(url || "/novel", page)));
    if (!data) return {items: [], next: ""};
    let novels = data.novels || [];
    let items = [];
    novels.forEach(function(novel) {
        let item = novelItem(novel);
        if (item.name && item.link) items.push(item);
    });
    return {items: items, next: nextPageFromPagination(data.pagination)};
}

function collectFromHtml(url) {
    let doc = getDoc(url || BASE_URL + "/novel");
    if (!doc) return {items: [], next: ""};
    let data = [];
    let seen = {};
    doc.select("a[href^='/novel/']").forEach(function(a) {
        let href = a.attr("href");
        if (!isStoryUrl(href)) return;
        let link = normalizeUrl(href);
        if (seen[link]) return;
        let img = a.select("img").first();
        let name = cleanText((img && img.attr("alt")) || a.select("h2, h3, h4").text() || a.attr("title") || a.text());
        if (!name || foldText(name).indexOf("stellar stories") >= 0) return;
        seen[link] = true;
        data.push({name: name, link: link, cover: imageFromNode(img), description: "", host: BASE_URL});
    });
    return {items: data, next: ""};
}

function execute(url, page) {
    let result = collectFromApi(url || BASE_URL + "/novel", page);
    if (result.items.length === 0) result = collectFromHtml(url || BASE_URL + "/novel");
    return Response.success(result.items, result.next);
}
