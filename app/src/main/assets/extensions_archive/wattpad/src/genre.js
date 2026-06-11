load("config.js");

function execute() {
    var response = fetch(BASE_URL);
    if (!response.ok) return Response.success([]);

    var doc = response.html();
    var genres = [];
    var seen = {};

    doc.select(".menu-subs.menu-column-3 a[rel=tag], .widget.tag a[rel=tag], .tag.tag-name.tagfull a[rel=tag]").forEach(function (item) {
        var title = cleanText(item.text());
        var href = item.attr("href");

        if (!title || !href || seen[href]) return;
        seen[href] = true;

        genres.push({
            title: title,
            input: href,
            script: "gen.js"
        });
    });

    return Response.success(genres);
}
