load('config.js');
function execute(url,page) {
    if(!page) page = '1';
    url = url.replace(/^(?:https?:\/\/)?(?:[^@\n]+@)?(?:www\.)?([^:\/\n?]+)/img, BASE_URL);
    let response = fetch(url + "/" + page + ".html");
    if (response.ok) {
        let doc = response.html();
        const data = [];
       doc.select(".grid-stories div").forEach(e => {
            data.push({
                name: e.select("h3 a").first().text(),
                link: e.select("a").first().attr("href"),
                cover: e.select(".thumb img").first().attr("src"),
                description: e.select(".info meta").first().attr("content"),
                host: BASE_URL
            });
        });
        let next = (parseInt(page) + 1).toString();
        return Response.success(data, next);
    }
    return null;
}