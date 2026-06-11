load('config.js');
function execute(url) {
    url = url.replace(/^(?:https?:\/\/)?(?:[^@\n]+@)?(?:www\.)?([^:\/\n?]+)/img, BASE_URL);

    let response = fetch(url);
    if (response.ok) {
        let doc = response.html();
        let detail = "";
        doc.select(".content1 > p").forEach(e => {
            detail += e.text() + "<br>"
        });
        return Response.success({
            name: doc.select(".title h1 a").text(),
            cover: doc.select("img").attr("data-src"),
            author: doc.select("#info > p").first().text().replace("Tác giả：", ""),
            description: doc.select(".description").html(),
            detail: detail,
            host: BASE_URL
        });
    }
    return null;
}