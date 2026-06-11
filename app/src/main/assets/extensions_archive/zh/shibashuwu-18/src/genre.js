load("config.js");

function execute() {
    var genres = [];
    for (var i = 0; i < CATEGORY_TABS.length; i++) {
        genres.push({
            title: CATEGORY_TABS[i].title,
            input: CATEGORY_TABS[i].input,
            script: "genrecontent.js"
        });
    }
    return Response.success(genres);
}
