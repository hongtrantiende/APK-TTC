load("config.js");

function execute(url) {
    var slug = ktcStorySlug(url);
    if (!slug) return Response.success([]);

    var storyResp = fetch(API_BASE + "/stories/" + encodeURIComponent(slug));
    if (!storyResp.ok) return Response.success([]);

    var storyJson = storyResp.json();
    if (!storyJson || !storyJson.data || !storyJson.data.id) return Response.success([]);

    var storyId = storyJson.data.id;
    var totalChapters = parseInt(storyJson.data.chapterCount, 10) || 0;
    var list = [];
    var page = 1;
    var totalPages = totalChapters > 0 ? Math.ceil(totalChapters / 50) : 1;
    if (totalPages < 1) totalPages = 1;

    while (page <= totalPages && page <= 100) {
        var chResp = fetch(API_BASE + "/stories/" + storyId + "/chapters?page=" + page);
        if (!chResp.ok) break;

        var chJson = chResp.json();
        if (!chJson || !chJson.data || !Array.isArray(chJson.data)) break;

        var chapters = chJson.data;
        if (chapters.length === 0) break;

        chapters.forEach(function(ch) {
            var name = ch.title || ("Ch\u01B0\u01A1ng " + ch.chapterNumber);
            var link = BASE_URL + "/truyen/" + slug + "/chuong-" + ch.chapterNumber;
            list.push({
                name: name,
                url: link,
                host: BASE_URL
            });
        });

        if (chapters.length < 50) break;
        page += 1;
    }

    return Response.success(list);
}
