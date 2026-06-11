load('config.js');
function execute(url) {
    url = url.replace(/^(?:https?:\/\/)?(?:[^@\n]+@)?(?:www\.)?([^:\/\n?]+)/img, BASE_URL);
    if (url.slice(-1) !== "/")
        url = url + "/";
    let response = fetch(url);
    if (response.ok) {
        let doc = response.html();

        let coverImg = doc.select(".cover img").attr("src");
        let descriptionMeta = doc.select(".desc").text();
        let novelTitle = doc.select(".info h3").first().text();
        let author = doc.select(".info .upload").text();
        let novelCategory = doc.select(".info .tag p").text();
        let status = doc.select('meta[property="og:novel:status"]').attr("content");
        let updateTime = doc.select(".chapter-info .update").text();

        return Response.success({
            name: novelTitle,
            cover: coverImg,
            author: author,
            description: descriptionMeta,
            detail: "Tác giả: " + author + '<br>' + "Thể loại: " + novelCategory + '<br>' + "Tình trạng: " + status + '<br>' + "Thời gian cập nhật: " + updateTime,
            host: BASE_URL
        });
    }
    return null;
}