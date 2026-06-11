load("config.js");

function execute() {
    var response = fetch(API_BASE + "/categories");
    if (!response.ok) return Response.success([]);

    var json = response.json();
    if (!json || !json.data || !Array.isArray(json.data)) return Response.success([]);

    var list = [];
    json.data.forEach(function(item) {
        if (!item.slug || !item.name) return;
        list.push({
            title: item.name,
            input: "/stories?category=" + encodeURIComponent(item.slug) + "&page=1&limit=24",
            script: "gen.js"
        });
    });

    return Response.success(list);
}
