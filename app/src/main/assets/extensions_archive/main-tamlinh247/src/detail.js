load('config.js');

function execute(url) {
    url = url.replace(/^(?:https?:\/\/)?(?:[^@\n]+@)?(?:www\.)?([^:\/\n?]+)/img, BASE_URL);
    if (url.slice(-1) !== "/")
        url = url + "/";
    
    let response = fetch(url);
    if (response.ok) {
        let doc = response.html();

        let title = doc.select('.title h1 a').text();
        let coverImg = doc.select('img[itemprop="image"]').attr("data-src");
        let author = doc.select('span[itemprop="author"]').text();
        let genres = doc.select('span[itemprop="genre"]').text();
        let source = doc.select('.rv-sr-s-a').text();
        let status = doc.select('.status').text();

        let description = doc.select('.description').html();
        let detail = `<h2 style="font-size:15px;line-height: 1.6; font-weight: 700; margin: 0;">Thông tin: ${title} - Tác giả: ${author}</h2>`;
        detail += `<p>${description}</p>`;
        detail += `<p>Tác giả: ${author}</p>`;
        detail += `<p>Thể loại: ${genres}</p>`;
        detail += `<p>Nguồn: ${source}</p>`;
        detail += `<p>Trạng thái: ${status}</p>`;

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
