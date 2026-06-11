load('config.js');
function execute(url) {
    url = url.replace(/^(?:https?:\/\/)?(?:[^@\n]+@)?(?:www\.)?([^:\/\n?]+)/img, BASE_URL);
    let response = fetch(BASE_URL + url);
    if (response.ok) {
        let doc = response.html();
        const data = [];
		doc.select(".mytable tbody tr").forEach(e => {
            data.push({
                name: e.select(".col-md-4 a").first().text(),
                link: e.select(".col-md-4 a").first().attr("href"),
                description: e.select(".col-md-2").first().text(),
                host: BASE_URL
            })
        });
        return Response.success(data)
    }
    return null;
}