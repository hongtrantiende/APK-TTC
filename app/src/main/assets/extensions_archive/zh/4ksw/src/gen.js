load('config.js');
function execute(url, page) {
    if(!page) page = '1';
    url = url.replace(/^(?:https?:\/\/)?(?:[^@\n]+@)?(?:www\.)?([^:\/\n?]+)/img, BASE_URL);
    let response = fetch(BASE_URL + url + page + '.html');
    if (response.ok) {
        let doc = response.html('gbk');
        const data = [];
		doc.select(".panel-body .col-lg-6").forEach(e => {
            data.push({
                name: e.select("h4 a").first().text(),
                link: e.select("a").first().attr("href"),
                cover: e.select(".media-left a img").first().attr("src"),
                description: e.select(".media-body p").first().text(),
                host: BASE_URL
            })
        });
        let next = (parseInt(page) + 1).toString();
        return Response.success(data, next)
    }
    return null;
}