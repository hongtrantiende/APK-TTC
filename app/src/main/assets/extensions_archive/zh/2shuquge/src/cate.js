load('config.js');
function execute(url,page) {
    if(!page) page = '1';
    url = url.replace(/^(?:https?:\/\/)?(?:[^@\n]+@)?(?:www\.)?([^:\/\n?]+)/img, BASE_URL);
    let response = fetch(BASE_URL + url + page + ".html");
    if (response.ok) {
        let doc = response.html();
        const data = [];
        doc.select(".mb20 div .list-out").forEach(e => {
            data.push({
                name: e.select("a").first().text(),
                link: BASE_URL + e.select("a").first().attr("href"),
                description: e.select("a").last().text()||e.select("a").first().text(),
                host: BASE_URL
            })
        });
        let next = (parseInt(page) + 1).toString();
        return Response.success(data,next)
    }
    return null;
}