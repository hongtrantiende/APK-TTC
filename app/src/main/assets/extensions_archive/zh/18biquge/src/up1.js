load('config.js');
function execute(url) {
    url = url.replace(/^(?:https?:\/\/)?(?:[^@\n]+@)?(?:www\.)?([^:\/\n?]+)/img, BASE_URL);
    let response = fetch(BASE_URL + url);
    if (response.ok) {
        let doc = response.html();
        const data = [];
		    doc.select(".txt-list li").forEach(e => {
            data.push({
                name: e.select("a").first().text(),
                link: BASE_URL + e.select("a").first().attr("href"),
                description: e.select("a").last().text(),
                host: BASE_URL
            })
        });
        return Response.success(data)
    }
    return null;
}