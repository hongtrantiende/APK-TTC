load("config.js");

function execute(key, page) {
    var pageNum = parseInt(page || "1", 10);
    if (pageNum > 1) return Response.success([]);

    var body = "keyword=" + encodeURIComponent(key || "");
    var res = fetchRetry(BASE_URL + "/search/", buildPostOptions(body));
    if (!res || !res.ok) {
        return Response.error("Khong tim duoc ket qua tim kiem");
    }

    var doc = res.html();
    return Response.success(parseSearchItems(doc));
}
