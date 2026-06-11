load('config.js');

function execute(url) {
    url = url.replace(/^(?:https?:\/\/)?(?:[^@\n]+@)?(?:www\.)?([^:\/\n?]+)/img, BASE_URL);
    let response = fetch(url);
    if (response.ok) {
        let doc = response.html();
        const data = [];
        doc.select("#tab-day .top-item").forEach(e => {
            let bookLink = e.select("a").first().attr("href");
            
            let bookPageResponse = fetch(bookLink);
            if (bookPageResponse.ok) {
                let bookPageDoc = bookPageResponse.html();
                data.push({
                    name: bookPageDoc.select("meta[property='og:title']").attr("content"),
                    link: bookLink,
                    cover: bookPageDoc.select("meta[property='og:image']").attr("content"),
                    description: e.select(".category span.view").text(),
                    host: BASE_URL
                });
            }
        });
        return Response.success(data);
    }
    return null;
}