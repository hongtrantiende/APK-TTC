load("config.js");

function execute() {
    let doc = fetchDoc(BASE_URL + "/novels/");
    if (!doc) return Response.success([]);

    let categories = parseCategoryLinks(doc);
    let out = [];
    categories.forEach(cat => {
        out.push({
            title: cat.title,
            input: cat.url,
            script: "gen.js"
        });
    });
    return Response.success(out);
}
