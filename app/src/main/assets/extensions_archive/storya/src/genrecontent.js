load("config.js");

function execute(url, page) {
    var p = page ? parseInt(page) : 1;
    var json = apiJson("/genres/" + url + "/stories?page=" + p + "&limit=20");
    if (!json || !json.data) return Response.error("Không tải được danh sách truyện");
    var next = (json.meta && p < json.meta.totalPages) ? (p + 1) + "" : null;
    return Response.success(parseStories(json.data), next);
}
