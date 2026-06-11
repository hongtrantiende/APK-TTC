load('config.js');
function execute(url) {
    url = url.replace(/^(?:https?:\/\/)?(?:[^@\n]+@)?(?:www\.)?([^:\/\n?]+)/img, BASE_URL);
    let response = fetch(BASE_URL + url);
    if (response.ok) {
        let doc = response.html();
        const data = [];
		doc.select(".flex li").forEach(e => {
            data.push({
                name: e.select("h2").last().text()||e.select("a").first().text()||e.select("p a").first().text(),
                link: BASE_URL + e.select("a").first().attr("href"),
                cover: e.select("a img").first().attr("src"),
                description: e.select(".ident").first().text(),
                host: BASE_URL
            })
        });
        return Response.success(data)
    }
    return null;
}