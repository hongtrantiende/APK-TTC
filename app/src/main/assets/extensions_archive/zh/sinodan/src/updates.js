load('config.js');

function execute(url) {
    url = url.replace(/^(?:https?:\/\/)?(?:[^@\n]+@)?(?:www\.)?([^:\/\n?]+)/img, BASE_URL);
    let response = fetch(BASE_URL + url);
    if (response.ok) {
        let doc = response.html();
        const data = [];
        doc.select(".bd .column-2").forEach(e => {
            let nameElement = e.select(".name");
            let updateElement = e.select(".update a");
            let link = BASE_URL + nameElement.attr("href");

            let bookPageResponse = fetch(link);
            if (bookPageResponse.ok) {
                let bookPageDoc = bookPageResponse.html();
                let coverElement = bookPageDoc.select(".bd.column-2 .left img");
                let cover = coverElement.attr("src");

                data.push({
                    name: nameElement.text(),
                    link: link,
                    description: `最新章节：${updateElement.text()}`,
                    cover: cover ? BASE_URL + cover : null,
                    host: BASE_URL
                });
            }
        });
        return Response.success(data);
    }
    return null;
}
