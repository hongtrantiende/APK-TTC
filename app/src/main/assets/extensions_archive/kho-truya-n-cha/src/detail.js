load("config.js");

function execute(url) {
    var slug = ktcStorySlug(url);
    if (!slug) return Response.error("Kh\u00F4ng l\u1EA5y \u0111\u01B0\u1EE3c slug truy\u1EC7n.");

    var apiUrl = API_BASE + "/stories/" + encodeURIComponent(slug);
    var response = fetch(apiUrl);
    if (!response.ok) return Response.error("Kh\u00F4ng t\u1EA3i \u0111\u01B0\u1EE3c th\u00F4ng tin truy\u1EC7n.");

    var json = response.json();
    if (!json || !json.data) return Response.error("D\u1EEF li\u1EC7u kh\u00F4ng h\u1EE3p l\u1EC7.");

    var story = json.data;
    var name = story.title || story.story || "";
    var cover = ktcResolveCover(story.cover || story.coverUrl);
    var author = story.author || "";

    var detailParts = [];
    if (author) detailParts.push("T\u00E1c gi\u1EA3: " + author);

    if (story.categories && Array.isArray(story.categories)) {
        var catNames = story.categories.map(function(c) { return c.name; });
        if (catNames.length > 0) detailParts.push("Th\u1EC3 lo\u1EA1i: " + catNames.join(", "));
    }

    var statusText = "";
    var ongoing = true;
    if (story.status === "completed" || story.status === "hoan-thanh") {
        statusText = "Ho\u00E0n th\u00E0nh";
        ongoing = false;
    } else if (story.status === "ongoing" || story.status === "dang-tien-hanh") {
        statusText = "\u0110ang ti\u1EBFn h\u00E0nh";
        ongoing = true;
    }
    if (statusText) detailParts.push("T\u00ECnh tr\u1EA1ng: " + statusText);

    if (story.chapterCount) detailParts.push("S\u1ED1 ch\u01B0\u01A1ng: " + story.chapterCount);
    if (story.views) detailParts.push("L\u01B0\u1EE3t xem: " + story.views);

    var description = story.description || "";
    if (story.updatedAt) {
        if (description) description += "<br><br>";
        description += "<b>C\u1EADp nh\u1EADt:</b> " + story.updatedAt;
    }

    return Response.success({
        name: name,
        cover: cover,
        author: author,
        description: description,
        detail: detailParts.join("<br>"),
        host: BASE_URL,
        genres: [],
        ongoing: ongoing
    });
}
