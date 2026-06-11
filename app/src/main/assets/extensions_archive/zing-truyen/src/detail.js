load('config.js');
function execute(url) {
    url = url.replace(/^(?:https?:\/\/)?(?:[^@\n]+@)?(?:www\.)?([^:\/\n?]+)/img, BASE_URL);
    let response = fetch(url);
    if (response.ok) {
        let doc = response.html();

        let coverImg = doc.select(".thumb img").first().attr("src");
        let descriptionMeta = doc.select(".description").html();
        let novelTitle = doc.select('meta[property="og:title"]').attr("content");
        let newChap = doc.select(".card-box").first().select("li").get(0).text();
        let author = doc.select(".author span a").first().text();
        let novelCategory = doc.select(".story_categories span a").text();
        let status = doc.select(".infos").first().select("p").get(3).text();

        return Response.success({
            name: novelTitle,
            cover: coverImg,
            author: author,
            description: descriptionMeta,
            detail: "Tác giả: " + author + '<br>' + "Thể loại: " + novelCategory + '<br>' + "Tình trạng: " + status + '<br>' + "Mới nhất: " + newChap,
            host: BASE_URL
        });
    }
    return null;
}