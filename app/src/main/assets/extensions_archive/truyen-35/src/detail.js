load('config.js');

function execute(url) {
    url = url.replace(/^(?:https?:\/\/)?(?:[^@\n]+@)?(?:www\.)?([^:\/\n?]+)/img, BASE_URL);
    if (url.slice(-1) !== "/")
        url = url + "/";
    let response = fetch(url);
    if (response.ok) {
        let doc = response.html();

        let title = doc.select('h3').first().text();
        let coverImg = doc.select('.info-holder .book img').attr("src");
        let author = doc.select('.info-holder [itemprop="author"]').text();
        let genre = doc.select('.info-holder [itemprop="genre"]').text();
        let views = doc.select('.info-holder h3:contains("Lượt xem")').last().text();
        let status = doc.select('.info-holder h3:contains("Trạng thái")').last().text();
        let rating = doc.select('.rate-holder [name="score"]').attr("value");
        let description = doc.select('.desc-text-full').text();

        let detail = `Tác giả: ${author}<br>Thể loại: ${genre}<br>Lượt xem: ${views}<br>Trạng thái: ${status}<br>Đánh giá: ${rating}/10`;

        return Response.success({
            name: title,
            cover: coverImg,
            author: author,
            description: description,
            detail: detail,
            host: BASE_URL
        });
    }
    return null;
}
