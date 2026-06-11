load("config.js");
function execute() {
    var json = fetchApi("/novel/hot");
    if (!json || json.code !== 200) return Response.error("Tải gợi ý thất bại");
    if (!json.data) return Response.error("Không có dữ liệu");
    return Response.success(buildItems(json.data, 10));
}
