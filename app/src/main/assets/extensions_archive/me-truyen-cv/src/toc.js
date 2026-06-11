load("config.js");

function execute(url, id) {
    apiUrl = url.replace(/^(?:https?:\/\/)?(?:[^@\n]+@)?(?:www\.)?([^:\/\n?]+)/img, BASE_URL) + "/ajax/chapters/";

    let postBody = {
        action: "manga_get_chapters",
        manga: id
    };

    let chapterRes = fetch(apiUrl, {
        method: "POST",
        headers: {
            "content-type": "application/x-www-form-urlencoded; charset=UTF-8",
            "x-requested-with": "XMLHttpRequest"
        },
        body: postBody
    });
    if (!chapterRes.ok) return error("Không thể kết nối đến " + apiUrl);
    let doc = chapterRes.html();
    let chapters = [];
    doc.select("li.wp-manga-chapter a").forEach(e => {
        chapters.push({
            name: e.text(),
            url: e.attr("href"),
            host: BASE_URL
        });
    });
    chapters.reverse();
    return Response.success(chapters);
}
