load('config.js');

function execute(url) {
    url = url.replace(/^(?:https?:\/\/)?(?:[^@\n]+@)?(?:www\.)?([^:\/\n?]+)/img, BASE_URL);
    let response = fetch(BASE_URL + url);
    if (response.ok) {
        let doc = response.html();
        const data = [];
        doc.select(".list-story-show tr").forEach(row => {
            let bookInfo = row.select(".info");
            let description = `Tác giả: ${bookInfo.select(".rv-sr-a").text()}<br>`;
            description += `Thể loại: ${bookInfo.select(".rv-sr-a[href^='/the-loai/']").text()}<br>`;
            description += `Trạng thái: ${bookInfo.select("p:contains('Trạng thái')").text()}<br>`;
            description += `Mới nhất: ${row.select(".chap.mobile a").text()}<br>`;
            description += `Lượt xem: ${bookInfo.select(".view").text()}`;

            data.push({
                name: bookInfo.select("h3 a").text(),
                link: BASE_URL + bookInfo.select("h3 a").attr("href"),
                cover: row.select(".image-book").attr("data-src"),
                description: description,
                host: BASE_URL
            });
        });
        return Response.success(data);
    }
    return null;
}
