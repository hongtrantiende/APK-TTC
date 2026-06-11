function execute(url) {

    // ===== lấy story id =====
    var match = url.match(/story\/(\d+)/);
    if (!match) return null;

    var storyId = match[1];

    // ===== lấy versionId =====
    var vApi = "https://api.sitruyencv.com/api/stories/" + storyId + "/translate-versions";

    var vRes = fetch(vApi, {
        headers: {
            "accept": "application/json",
            "origin": "https://sitruyencv.com",
            "referer": "https://sitruyencv.com/",
            "user-agent": "Mozilla/5.0"
        }
    });

    if (!vRes.ok) return null;

    var vJson = JSON.parse(vRes.text());

    if (!vJson.data || vJson.data.length === 0) {
        return Response.success([]);
    }

    // ép int tránh 12391.0
    var versionId = parseInt(vJson.data[0].id);

    // ===== load chapters =====
    var chapters = [];
    var limit = 20;
    var page = 1;
    var totalLoaded = 0;

    while (true) {

        var api = "https://api.sitruyencv.com/api/chapters/" + versionId
            + "?page=" + page
            + "&limit=" + limit
            + "&story_id=" + storyId
            + "&sort_order=asc";

        var res = fetch(api, {
            headers: {
                "accept": "application/json",
                "origin": "https://sitruyencv.com",
                "referer": "https://sitruyencv.com/",
                "user-agent": "Mozilla/5.0"
            }
        });

        if (!res.ok) break;

        var text = res.text();
        if (!text) break;

        var json;
        try {
            json = JSON.parse(text);
        } catch (e) {
            break;
        }

        if (!json.data || !json.data.items) break;

        var list = json.data.items;
        let chap = 1;

        for (var i = 0; i < list.length; i++) {
            var c = list[i];
            let title = "";
            let chap = chapters.length + 1;
            if (c.title.includes("Chương")) title = c.title;
            else title = "Chương " + chap + ": " + c.title;

            // 🔥 dùng chapter_number
            chapters.push({
                name: title,
                url: "https://sitruyencv.com/read/" 
                    + storyId + "/" + c.chapter_number 
                    + "?v=" + versionId
            });
        }

        totalLoaded += list.length;

        var total = json.data.metadata ? json.data.metadata.total_items : 0;

        if (totalLoaded >= total || list.length === 0) break;

        page++;
    }

    return Response.success(chapters);
}