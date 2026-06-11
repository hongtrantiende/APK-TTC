load("config.js");
function execute(url) {
    var articleId = parseArticleId(url);
    var chapterId = parseChapterId(url);
    if (!articleId || !chapterId) return Response.error("Không đọc được ID từ URL");
    var json = fetchApi("/chapter/content/" + articleId + "/" + chapterId);
    if (!json || json.code !== 200) return Response.error("Tải nội dung chương thất bại");
    var data = json.data;
    if (!data || !data.content) return Response.error("Nội dung chương trống");
    return Response.success(textToHtml(data.content));
}
