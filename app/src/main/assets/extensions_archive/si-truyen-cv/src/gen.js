function execute(url, page) {
    if (!page) page = "1";

    var api = "https://api.sitruyencv.com/api/stories/search?page=" + page + "&limit=24";

    var body = {
        keyword: ""
    };

    if (url.includes("completed")) {
        body.status = "Completed";
    } else if (url.includes("popular")) {
        body.sort_by = "Views";
    } else if (url.includes("updated")) {
        body.sort_by = "Updated";
    } else if (url.includes("recommended")) {
        body.sort_by = "Recommended";
    }


    var response = fetch(api, {
        method: "POST",
        headers: {
            "accept": "application/json, text/plain, */*",
            "content-type": "application/json",
            "origin": "https://sitruyencv.com",
            "referer": "https://sitruyencv.com/",
            "user-agent": "Mozilla/5.0"
        },
        body: JSON.stringify(body)
    });

    if (!response.ok) return null;

    // 🔥 FIX QUAN TRỌNG
    var json = JSON.parse(response.text());

    if (!json || !json.data || !json.data.items) {
        return Response.success([], null);
    }

    var list = json.data.items;
    var meta = json.data.metadata;

    var data = [];

    for (var i = 0; i < list.length; i++) {
        var e = list[i];

        var category = "";
        if (e.categories && e.categories.length > 0) {
            category = e.categories.map(function(c){
                return c.name;
            }).join(", ");
        }

        data.push({
            name: e.title,
            link: "https://sitruyencv.com/story/" + e.id + "-" + e.slug,
            cover: e.cover_image_url,
            description: category + " | " + e.total_chapters + " chương | " + e.total_views + " lượt xem",
            host: "https://sitruyencv.com"
        });
    }

    // pagination chuẩn theo metadata
    var next = null;
    if (meta && meta.current_page < meta.total_pages) {
        next = (meta.current_page + 1).toString();
    }

    return Response.success(data, next);
}