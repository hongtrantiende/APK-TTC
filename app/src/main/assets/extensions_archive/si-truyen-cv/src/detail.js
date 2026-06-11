function execute(url) {

    // lấy id từ url
    // ví dụ: /story/12790-abc → lấy 12790
    var match = url.match(/story\/(\d+)/);
    if (!match) return null;

    var id = match[1];

    var api = "https://api.sitruyencv.com/api/stories/" + id;

    var response = fetch(api, {
        headers: {
            "accept": "application/json, text/plain, */*",
            "origin": "https://sitruyencv.com",
            "referer": "https://sitruyencv.com/",
            "user-agent": "Mozilla/5.0"
        }
    });

    if (!response.ok) return null;

    // 🔥 luôn parse kiểu này cho chắc
    var json = JSON.parse(response.text());

    if (!json || !json.data) return null;

    var e = json.data;

    // ===== xử lý data =====

    var name = e.title || "";
    var cover = e.cover_image_url || "";

    var author = "";
    if (e.author) {
        author = e.author.name;
    }

    var description = e.description || "";

    // thể loại
    var category = "";
    if (e.categories && e.categories.length > 0) {
        category = e.categories.map(function(c){
            return c.name;
        }).join(", ");
    }

    return Response.success({
        name: name,
        cover: cover,
        author: author,
        description: description,
        detail: category + " | " + (e.total_chapters || 0) + " chương",
        host: "https://sitruyencv.com"
    });
}