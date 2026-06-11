load('config.js');
function execute(url) {
    url = url.replace(/^(?:https?:\/\/)?(?:[^@\n]+@)?(?:www\.)?([^:\/\n?]+)/img, BASE_URL);
    let response = fetch(BASE_URL + url);
    if (response.ok) {
        let doc = response.html();
        const data = [];
		doc.select(".g-plate ul li").forEach(e => {
            data.push({
                name: e.select("h3").last().text()||e.select(".name").first().text()||e.select("a").first().text(),
                link: BASE_URL + e.select("a").first().attr("href"),
                cover: e.select("a img").first().attr("data-original"),
                description: e.select("desc").first().text(),
                host: BASE_URL
            })
        });
        return Response.success(data)
    }
    return null;
}