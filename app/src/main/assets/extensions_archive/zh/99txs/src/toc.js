load('config.js');
function execute(url,page) {
    if(!page) page = '1';
    url = url.replace(/^(?:https?:\/\/)?(?:[^@\n]+@)?(?:www\.)?([^:\/\n?]+)/img, BASE_URL);
    url1 = url.replace(".html","");
    let response = fetch(url1 + "/asc/index_" + page + ".html");
    if (response.ok) {
        let doc = response.html();
        let el1 = doc.select(".mainbody").first()
        let el = el1.select("dl dd a")
        const data = [];
        for (let i = 0; i < el.size(); i++) {
            var e = el.get(i);
            data.push({
                name: e.select("a").text(),
                url: e.attr("href"),
                host: BASE_URL
            })
        }
        return Response.success(data);
    }
    return null;
}