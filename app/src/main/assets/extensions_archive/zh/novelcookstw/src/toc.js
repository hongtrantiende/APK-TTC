load("config.js");
function execute(url) {
    var id = parseArticleId(url);
    if (!id) return Response.error("Không đọc được articleid từ URL");
    var json = fetchApi("/chapter/list/" + id);
    if (!json || json.code !== 200) return Response.error("Tải danh sách chương thất bại");
    var list = json.data;
    if (!list) return Response.error("Không có dữ liệu chương");
    var result = [];
    for (var i = 0; i < list.length; i++) {
        var c = list[i];
        result.push({
            name: c.chaptername || ("Chương " + (i + 1)),
            url: makeChapUrl(id, c.chapterid),
            host: BASE_URL
        });
    }
    return Response.success(result);
}
