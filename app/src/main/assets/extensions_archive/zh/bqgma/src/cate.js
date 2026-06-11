load('config.js');
function execute(url, page) {
    if(!page) page = '1';
    if(url.slice(-1) !== "/")
        url = url + "/";
        url = url.replace(/^(?:https?:\/\/)?(?:[^@\n]+@)?(?:www\.)?([^:\/\n?]+)/img, BASE_URL);
    let response = fetch(BASE_URL + url +  page + ".html");
    if (response.ok) {
        let doc = response.html();
        const data = [];
        let next = ""
        next = doc.select(".page a").last().attr("href").replace(".html","").split(/[/ ]+/).pop();
        doc.select(".item").forEach(e => {
            data.push({
                name: e.select("a").last().text(),
                link: e.select("a").first().attr("href"),
                cover: e.select("img").attr("src"),
                description: e.select("dd").first().text(),
                host: BASE_URL
            })
        });


        return Response.success(data, next);
    }
    return null;
}