load("config.js");

function execute(url, page) {
    if (!page) page = "1";
    var input = ktcTrim(url);

    var apiUrl = "";
    if (input.indexOf("/stories/") === 0) {
        apiUrl = API_BASE + input;
    } else if (input.indexOf("http") === 0) {
        apiUrl = input;
    } else {
        apiUrl = API_BASE + "/" + input.replace(/^\/+/, "");
    }

    var response = fetch(apiUrl);
    if (!response.ok) return Response.success([]);

    var json = response.json();
    if (!json || !json.data || !Array.isArray(json.data)) {
        return Response.success([]);
    }

    var isLatest = apiUrl.indexOf("/latest") > -1;
    var list = [];

    json.data.forEach(function(item) {
        var name, slug, link, desc, cover;
        if (isLatest) {
            name = item.story || item.title || "";
            slug = item.slug || "";
        } else {
            name = item.title || item.story || "";
            slug = item.slug || "";
        }
        if (!name || !slug) return;

        link = BASE_URL + "/truyen/" + slug;
        cover = ktcResolveCover(item.cover || item.coverUrl);

        desc = "";
        if (item.author) desc += "Tac gi?: " + item.author;
        if (item.chapter) desc += (desc ? " | " : "") + "Chuong: " + item.chapter;
        if (item.views) desc += (desc ? " | " : "") + "Lu?t xem: " + item.views;
        if (item.time) desc += (desc ? " | " : "") + item.time;

        list.push({
            name: name,
            link: link,
            cover: cover,
            description: desc,
            host: BASE_URL
        });
    });

    return Response.success(list);
}
