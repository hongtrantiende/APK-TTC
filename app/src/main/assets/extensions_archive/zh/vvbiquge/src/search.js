load("config.js");

function execute(key, page) {
    let response = fetch(BASE_URL + "/searcha0.html", {
        method: "POST",
        queries: {
            "searchkey": key
        }
    });

    if (response.ok) {
        let doc = response.html();
        let books = [];
        doc.select(".bookbox").forEach(e => {
            books.push({
                name: e.select("h4 a").first().text(),
                link: e.select("h4 a").first().attr("href"),
                cover: e.select("img").attr("src"),
                description: e.select(".update").text(),
                host: BASE_URL
            })
        });

        return Response.success(books);
    }

    return null;
}