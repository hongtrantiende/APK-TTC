load("config.js");
function execute(sortid, page) {
    var p = page || 1;
    var json = fetchApi("/novel/list?sortid=" + sortid + "&page=" + p + "&limit=20");
    if (!json || json.code !== 200) return Response.error("Tải danh sách thất bại");
    var d = json.data;
    if (!d || !d.data) return Response.error("Không có dữ liệu");
    return Response.success(buildItems(d.data), d.next_page || null);
}
