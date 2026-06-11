load('config.js');

function execute(key, page) {
    let current = page ? parseInt(page) : 1;
    let json = fetchJson(apiBooksUrl({ title: key || "", page: current, limit: 24 }));
    if (!json) return null;
    return Response.success(mapApiBooks(json), nextPage(json, current));
}
