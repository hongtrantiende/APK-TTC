load("config.js");

function execute() {
    let sections = [
        {
            title: "All Novels",
            input: BASE_URL + "/novels/",
            script: "gen.js"
        }
    ];

    let doc = fetchDoc(BASE_URL + "/novels/");
    let categories = parseCategoryLinks(doc);
    categories.forEach(cat => {
        sections.push({
            title: cat.title,
            input: cat.url,
            script: "gen.js"
        });
    });

    return Response.success(sections);
}
