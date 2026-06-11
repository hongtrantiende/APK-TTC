load('config.js');
function execute(url) {
    url = url.replace(/^(?:https?:\/\/)?(?:[^@\n]+@)?(?:www\.)?([^:\/\n?]+)/img, BASE_URL);
    let response = fetch(BASE_URL + url);
    if (response.ok) {
        let doc = response.html();
        const data = [];
		doc.select("#rows-newest table tbody tr").forEach(e => {
            data.push({
                name: e.select(".name a").first().text(),
                link: BASE_URL + e.select(".name a").first().attr("href"),
                description: e.select(".chap a").first().text(),
                host: BASE_URL
            })
        });
        return Response.success(data)
    }
    return null;
}