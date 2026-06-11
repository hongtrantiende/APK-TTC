load('config.js');
function execute(url,page) {
    if(!page) page = '1';
    url = url.replace(/^(?:https?:\/\/)?(?:[^@\n]+@)?(?:www\.)?([^:\/\n?]+)/img, BASE_URL);
    let response = fetch(BASE_URL + url + page + "/");
    if (response.ok) {
        let doc = response.html();
        const data = [];
        doc.select(".flex li").forEach(e => {
            data.push({
                name: e.select("h2").last().text(),
                link: BASE_URL + e.select("a").first().attr("href"),
                cover: e.select("img").first().attr("data-original"),
                description: e.select(".indent").first().text(),
                host: BASE_URL
            })
        });
        let next = (parseInt(page) + 1).toString();
        return Response.success(data,next)
    }
    return null;
}