load("config.js");

function execute(key, page) {
    let response = fetch(BASE_URL + "/dfhgiodrhgdior.php", {
        method: "GBK",
        queries: {
            "q": key
        }
    });

    if (response.ok) {
        let doc = response.html();
        let books = [];
        doc.select(".type_show > .bookbox").forEach(e => {
            books.push({
                name: e.select("h4 a").last().text(),
                link: e.select("h4 a").last().attr("href"),
                cover: e.select(".boximg a img").attr("src"),
                description: e.select("dd").text(),
                host: BASE_URL
            })
        });

        return Response.success(books);
    }

    return null;
}