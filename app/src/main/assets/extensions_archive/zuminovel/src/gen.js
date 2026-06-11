load("config.js");

function execute(url, page) {
    page = String(parseInt(page || "1") || 1);
    var res = getJson(apiListUrl(page));
    var loginError = loginErrorIfNeeded(res);
    if (loginError) return loginError;
    if (!res.ok) return Response.error("Cannot load: " + res.status);

    var payload = res.json() || {};
    var items = payload.data || [];
    var data = [];

    items.forEach(function(item) {
        var mapped = mapNovelItem(item);
        if (mapped.link) data.push(mapped);
    });

    return Response.success(data, data.length > 0 ? String((parseInt(page) || 1) + 1) : null);
}
