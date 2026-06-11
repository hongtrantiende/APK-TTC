load('config.js');
function execute(url) {
    url = url.replace(/^(?:https?:\/\/)?(?:[^@\n]+@)?(?:www\.)?([^:\/\n?]+)/img, BASE_URL);

    let response = fetch(url);
    if (response.ok) {
        let doc = response.html();
        let detail = "";
        doc.select("div > a").forEach(e => {
            detail += e.text() + "<br>"
        });
        return Response.success({
            name: doc.select("span").first().text(),
            cover: doc.select(".divbox").select("div").first().select("a img").attr("src"),
            author: doc.select("span").get(1).text().replace("作者：", ""),
            description: doc.select(".tabvalue div").html(),
            detail: detail,
            host: BASE_URL
        });
    }
    return null;
}