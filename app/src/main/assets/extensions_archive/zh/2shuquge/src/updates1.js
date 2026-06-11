load('config.js');
function execute(url) {
    url = url.replace(/^(?:https?:\/\/)?(?:[^@\n]+@)?(?:www\.)?([^:\/\n?]+)/img, BASE_URL);
    let response = fetch(BASE_URL + url);
    if (response.ok) {
        let doc = response.html();
        const data = [];
		doc.select(".popular .list-out").forEach(e => {
            data.push({
                name: e.select("a").first().text(),
                link: BASE_URL + e.select("a").first().attr("href"),
                cover: BASE_URL + '/static/shuquge/nocover.jpg',
                description: e.select("span").last().text(),
                host: BASE_URL
            })
        });
        return Response.success(data)
    }
    return null;
}