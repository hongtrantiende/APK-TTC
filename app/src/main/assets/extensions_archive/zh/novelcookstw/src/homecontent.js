load("config.js");
function execute(url, page) {
    var json = fetchApi("/novel/hot");
    if (!json || json.code !== 200) return Response.error("Tải danh sách thất bại");
    if (!json.data) return Response.error("Không có dữ liệu");
    return Response.success(buildItems(json.data));
}
