load("config.js");

function execute(input, page) {
    let pageNum = parseInt(page || "1");
    if (!pageNum || pageNum < 1) pageNum = 1;

    let url = input || BASE_URL + "/novels/";
    let doc = fetchDoc(url);
    if (!doc) return Response.success([]);

    let list = parseNovelCards(doc);
    let next = list.length > 0 ? (pageNum + 1) + "" : "";

    return Response.success(list, next);
}
