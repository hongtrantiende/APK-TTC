load('config.js');

function fallbackGenres() {
    return [
        ["Action", "action"],
        ["Adventure", "adventure"],
        ["Comedy", "comedy"],
        ["Drama", "drama"],
        ["Fantasy", "fantasy"],
        ["Harem", "harem"],
        ["Isekai", "isekai"],
        ["Magic", "magic"],
        ["Martial Arts", "martial-arts"],
        ["Romance", "romance"],
        ["School Life", "school-life"],
        ["Slice of Life", "slice-of-life"],
        ["Supernatural", "supernatural"],
        ["Tragedy", "tragedy"],
        ["Yandere", "yandere"]
    ];
}

function execute() {
    let data = [];
    let genres = apiData("/genres") || [];
    if (genres.length > 0) {
        genres.forEach(function(e) {
            if (!e || !e.name || !e.slug) return;
            data.push({title: e.name, input: BASE_URL + "/the-loai/" + e.slug, script: "gen.js"});
        });
    }
    if (data.length === 0) {
        fallbackGenres().forEach(function(e) {
            data.push({title: e[0], input: BASE_URL + "/the-loai/" + e[1], script: "gen.js"});
        });
    }
    return Response.success(data);
}
