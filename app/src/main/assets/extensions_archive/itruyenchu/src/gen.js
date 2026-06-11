load('config.js');

function execute(input, page) {
    let current = page ? parseInt(page) : 1;
    let params = { page: current, limit: 24 };

    if (input === "latest" || input === "trending_now") {
        params.sort = input === "latest" ? "createdAt" : "totalViews";
    } else if (input.indexOf("/the-loai/") !== -1) {
        params.categories = getSlug(input);
    } else {
        params.categories = getSlug(input);
    }

    let json = fetchJson(apiBooksUrl(params));
    if (json) return Response.success(mapApiBooks(json), nextPage(json, current));
    return null;
}
