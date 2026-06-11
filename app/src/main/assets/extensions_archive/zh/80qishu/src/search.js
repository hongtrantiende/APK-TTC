load('config.js');

function execute(key, page) {
    if (page && page !== "1") {
        let doc = getDoc(page);
        if (!doc) return Response.success([], "");
        return Response.success(parseListItems(doc), nextPage(doc, page));
    }

    let doc = postSearchDoc(key);
    if (!doc) return loadError();
    return Response.success(parseListItems(doc), nextPage(doc, BASE_URL + "/e/search/result/"));
}
