function execute(url) {

    // ===== parse URL =====
    var match = url.match(/read\/(\d+)\/(\d+)\?v=(\d+)/);

    if (!match) return null;

    var storyId = match[1];
    var chapterNumber = match[2];
    var versionId = match[3];

    // ===== gọi API =====
    var api = "https://api.sitruyencv.com/api/chapters/" 
        + versionId + "/read/" + chapterNumber 
        + "?story_id=" + storyId;

    var res = fetch(api, {
        headers: {
            "accept": "application/json",
            "origin": "https://sitruyencv.com",
            "referer": "https://sitruyencv.com/",
            "user-agent": "Mozilla/5.0"
        }
    });

    if (!res.ok) return null;

    var text = res.text();
    if (!text) return null;

    var json;

    try {
        json = JSON.parse(text);
    } catch (e) {
        return null;
    }

    if (!json || !json.data) return null;

    var content = json.data.content || "";

    // ===== format nội dung =====
    content = content
        .replace(/\r\n/g, "<br>")
        .replace(/\n/g, "<br>")
        .replace(/\t/g, "&nbsp;&nbsp;&nbsp;&nbsp;");

    return Response.success(content);
}