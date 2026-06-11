load('config.js');

function execute(url) {
    url = url.replace(/^(?:https?:\/\/)?(?:[^@\n]+@)?(?:www\.)?([^:\/\n?]+)/img, BASE_URL);

    let response = fetch(url);
    if (response.ok) {
        let doc = response.html();
        let detail = "";
        doc.select(".binfo div").forEach(e => {
            detail += e.text() + "<br>";
        });

        return Response.success({
            name: doc.select(".title").first().text(),
            cover: doc.select("dl .binfo img").attr("src"),
            author: doc.select(".binfo div").first().text(),
            description: "Xin Chào",
            detail: detail,
            host: BASE_URL
        });
    }

    return null;
}
