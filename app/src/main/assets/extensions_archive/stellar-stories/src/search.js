load('config.js');

function execute(key, page) {
    let data = apiData(apiPath("/novels", {
        search: key || "",
        page: pageNum(page),
        limit: 30,
        sort: "updated"
    }));
    if (!data) return null;

    let items = [];
    (data.novels || []).forEach(function(novel) {
        let item = novelItem(novel);
        if (item.name && item.link) items.push(item);
    });
    return Response.success(items, nextPageFromPagination(data.pagination));
}
