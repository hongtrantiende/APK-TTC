load('config.js');
function execute(url) {
    url = url.replace(/^(?:https?:\/\/)?(?:[^@\n]+@)?(?:www\.)?([^:\/\n?]+)/img, BASE_URL);
    let response = fetch(BASE_URL + url);
    if (response.ok) {
        let doc = response.html();
        const data = [];
        doc.select(".row .col-md-2").forEach(e => {
            data.push({
                name: e.select(".name-book").text(),
                link: BASE_URL + e.select("a").first().attr("href"),
                cover: e.select("img").first().attr("data-src"),
                description: e.select(".rate").text(),
                host: BASE_URL
            });
        });
        return Response.success(data);
    }
    return null;
}