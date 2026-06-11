load("config.js");

function execute(key, page) {
    var keyword = ktcTrim(key);
    if (!keyword) return Response.success([]);

    var apiUrl = API_BASE + "/stories/search?q=" + encodeURIComponent(keyword);
    var response = fetch(apiUrl);
    if (!response.ok) return Response.success([]);

    var json = response.json();
    if (!json || !json.data || !Array.isArray(json.data)) return Response.success([]);

    var list = [];
    json.data.forEach(function(item) {
        var name = item.title || item.story || "";
        var slug = item.slug || "";
        if (!name || !slug) return;

        var desc = "";
        if (item.author) desc += "Tac giả: " + item.author;
        if (item.chapter) desc += (desc ? " | " : "") + "Chương: " + item.chapter;

        list.push({
            name: name,
            link: BASE_URL + "/truyen/" + slug,
            cover: ktcResolveCover(item.cover || item.coverUrl),
            description: desc,
            host: BASE_URL
        });
    });

    return Response.success(list);
}
