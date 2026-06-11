load('config.js');
function execute(url) {
    url = url.replace(/^(?:https?:\/\/)?(?:[^@\n]+@)?(?:www\.)?([^:\/\n?]+)/img, BASE_URL);

    let response = fetch(url);
    if (response.ok) {
        let doc = response.html();
        let detail = "";
        doc.select(".detail > p").forEach(e => {
            detail += e.text() + "<br>"
        });
        return Response.success({
            name: doc.select("p").first().text(),
            cover: doc.select("img").attr("src"),
            author: doc.select("p").get(1).text().replace("作者：", ""),
            description: doc.select(".intro").html(),
            detail: detail,
            host: BASE_URL
        });
    }
    return null;
}