load('config.js');
function execute(url,page) {
    if(!page) page = '1';
    url = url.replace(/^(?:https?:\/\/)?(?:[^@\n]+@)?(?:www\.)?([^:\/\n?]+)/img, BASE_URL);
    let response = fetch(BASE_URL + url + "/page/" + page + "/");
    if (response.ok) {
        let doc = response.html();
        const data = [];
        doc.select("#wrap .pictext a").forEach(e => {
            data.push({
                name: e.select(".info_title").last().text(),
                link: e.select("a").first().attr("href"),
                cover: e.select(".left_img img").first().attr("data-original"),
                description: e.select(".info_description").first().text(),
                host: BASE_URL
            })
        });
        let next = (parseInt(page) + 1).toString();
        return Response.success(data,next)
    }
    return null;
}