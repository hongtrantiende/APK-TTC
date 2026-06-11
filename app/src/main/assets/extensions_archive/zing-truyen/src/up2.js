load('config.js');
function execute(url) {
    url = url.replace(/^(?:https?:\/\/)?(?:[^@\n]+@)?(?:www\.)?([^:\/\n?]+)/img, BASE_URL);
    let response = fetch(BASE_URL + url);
    if (response.ok) {
        let doc = response.html();
        const data = [];
        doc.select(".grid-stories div").forEach(e => {
            data.push({
                name: e.select("h3 a").first().text(),
                link: e.select("a").first().attr("href"),
                cover: e.select(".thumb img").first().attr("src"),
                description: e.select(".info meta").first().attr("content"),
                host: BASE_URL
            });
        });
        return Response.success(data);
    }
    return null;
}