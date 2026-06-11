load("gen.js");

function searchBody(key) {
    return "searchkey=" + encodeURIComponent(key || "") + "&searchtype=all";
}

function execute(key, page) {
    if (page && ("" + page).indexOf("http") === 0) {
        let doc = getDoc(page);
        if (!doc) return loadError();
        let data = collectCards(doc);
        if (data.length === 0) data = collectByLinks(doc);
        return Response.success(data, nextPage(doc, page, "/search/"));
    }

    let doc = postDoc(BASE_URL + "/search/", searchBody(key));
    if (!doc) return loadError("Khong tim thay ket qua tim kiem.");

    let data = collectCards(doc);
    if (data.length === 0) data = collectByLinks(doc);
    return Response.success(data, nextPage(doc, BASE_URL + "/search/", "/search/"));
}
