load('config.js');
function execute(url) {
    url = url.replace(/^(?:https?:\/\/)?(?:[^@\n]+@)?(?:www\.)?([^:\/\n?]+)/img, BASE_URL);
    let response = fetch(BASE_URL + url);
    if (response.ok) {
        let doc = response.html();
        const data = [];
        doc.select(".books-list li").forEach(e => {
          data.push({
            name: e.select("h3").last().text(),
            link: BASE_URL + e.select("a").first().attr("href"),
            cover: e.select("a img").attr("src"),
            description: e.select(".author").text(),
            host: BASE_URL
          })
        });
        return Response.success(data)
    }
    return null;
}