load("config.js");
function execute(key, page) {
    var p = page || 1;
    var json = fetchApi("/novel/search?q=" + encodeURIComponent(key) + "&page=" + p + "&limit=20");
    if (!json || json.code !== 200) return Response.error("Tìm kiếm thất bại");
    var d = json.data;
    if (!d || !d.items) return Response.error("Không có kết quả");
    var next = p < Math.ceil(d.total / 20) ? p + 1 : null;
    return Response.success(buildItems(d.items), next);
}
