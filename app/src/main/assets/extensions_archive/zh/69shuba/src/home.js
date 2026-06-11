load("config.js");

function execute() {
    let data = [];
    for (let i = 0; i < CATEGORY_ITEMS.length; i++) {
        let item = CATEGORY_ITEMS[i];
        data.push({
            title: item.title,
            input: categoryUrl(item.slug, 1),
            script: "gen.js"
        });
    }
    return Response.success(data);
}
