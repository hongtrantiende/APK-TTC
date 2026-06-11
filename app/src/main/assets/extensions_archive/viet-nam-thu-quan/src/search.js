load("config.js");

function execute(key, page) {
    if (!key || ("" + key).trim().length < 2) return Response.success([]);
    let q = ("" + key).trim().replace(/\s+/g, "-");
    let url = BASE_URL + "/Truyen/TimKiem/" + encodeURIComponent(q) + "/Trang-1";
    let doc = fetchDoc(url);
    if (!doc) return Response.success([]);

    let list = parseNovelList(doc);
    return Response.success(list, null);
}