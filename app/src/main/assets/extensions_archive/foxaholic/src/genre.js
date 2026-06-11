load('config.js');

function fallbackGenres() {
    return [
        {title: "Chinese", input: "/genre/chinese/", script: "gen.js"},
        {title: "Korean", input: "/genre/korean/", script: "gen.js"},
        {title: "Japanese", input: "/genre/japanese/", script: "gen.js"},
        {title: "Original", input: "/genre/original/", script: "gen.js"},
        {title: "GL", input: "/genre/gl/", script: "gen.js"},
        {title: "Action", input: "/genre/action/", script: "gen.js"},
        {title: "Adventure", input: "/genre/adventure/", script: "gen.js"},
        {title: "Fantasy", input: "/genre/fantasy/", script: "gen.js"},
        {title: "Romance", input: "/genre/romance/", script: "gen.js"},
        {title: "Comedy", input: "/genre/comedy/", script: "gen.js"},
        {title: "Drama", input: "/genre/drama/", script: "gen.js"},
        {title: "Horror", input: "/genre/horror/", script: "gen.js"}
    ];
}

function execute() {
    let doc = getDoc(BASE_URL + "/novel/");
    if (!doc) return Response.success(fallbackGenres());

    let data = [];
    let seen = {};
    doc.select("a[href*='/genre/']").forEach(function(a) {
        let title = cleanText(a.text());
        let href = normalizeUrl(a.attr("href"));
        if (!title || !href || seen[href]) return;
        seen[href] = true;
        data.push({title: title, input: href, script: "gen.js"});
    });

    return Response.success(data.length > 0 ? data : fallbackGenres());
}
