function execute(key, page) {
    if (!page) page = "1";

    var api = "https://api.sitruyencv.com/api/stories/search?page=" + page + "&limit=24";

    var response = fetch(api, {
        method: "POST",
        headers: {
            "accept": "application/json, text/plain, */*",
            "content-type": "application/json",
            "origin": "https://sitruyencv.com",
            "referer": "https://sitruyencv.com/",
            "user-agent": "Mozilla/5.0",
            "accept-language": "vi-VN,vi;q=0.9,en-US;q=0.8"
        },
        body: JSON.stringify({
            keyword: key
        })
    });

    if (!response.ok) return null;

    var json = response.json();

    // 🔥 DEBUG (rất quan trọng)
    // console.log(JSON.stringify(json));

    var list = [];

    // fix nhiều dạng response
    if (json.data && Array.isArray(json.data)) {
        list = json.data;
    } else if (json.data && json.data.items) {
        list = json.data.items;
    } else if (json.items) {
        list = json.items;
    }

    var data = [];

    for (var i = 0; i < list.length; i++) {
        var e = list[i];

        var name = e.name || e.title || "";
        var slug = e.slug || e._id || e.id || "";

        var link = "https://sitruyencv.com/story/" + e.id + "-"+ slug;

        var cover = e.cover_image_url;

        var category = "";
        if (e.categories && e.categories.length > 0) {
            category = e.categories.map(function(c){ return c.name; }).join(", ");
        }
        let ongoing = false;
        if (e.status == "Complete") ongoing = true;

        var chapters = e.total_chapters;
        var views = e.total_view;

        var description = category + " | " + chapters + " chương | " + views + " lượt xem";

        data.push({
            name: name,
            link: link,
            cover: cover,
            ongoing: ongoing,
            description: description,
            host: "https://sitruyencv.com"
        });
    }

    var next = (parseInt(page) + 1).toString();

    return Response.success(data, next);
}