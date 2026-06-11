load("config.js");
function execute(url) {
    var json = fetchApi("/novel/rank/fanqie");
    if (!json || json.code !== 200) return Response.error("Tải bảng xếp hạng thất bại");
    var ranks = json.data;
    if (!ranks) return Response.error("Không có dữ liệu");
    var result = [], seen = {};
    for (var i = 0; i < ranks.length; i++) {
        var books = ranks[i].books;
        if (!books) continue;
        for (var j = 0; j < books.length; j++) {
            var b = books[j], id = b.my_novel_id;
            if (!id || !b.title || seen[id]) continue;
            seen[id] = true;
            result.push({ name: b.title, link: BASE_URL + "/novel.html?articleid=" + id, host: BASE_URL, cover: coverUrl(id), description: b.author || "" });
        }
    }
    return result.length ? Response.success(result) : Response.error("Không có dữ liệu");
}
