load("config.js");

function execute(key, page) {
    let response = fetch(BASE_URL + "/search/", {
        method: "UTF-8",
        queries: {
            "searchkey": key
        }
    });

    if (response.ok) {
        let doc = response.html();
        let books = [];
        doc.select(".l .item").forEach(e => {
            books.push({
                name: e.select("dl dt a").first().text(),
                link: e.select("dl dt a").first().attr("href"),
                cover: e.select(".image a img").attr("src"),
                description: e.select("dl dd").text(),
                host: BASE_URL
            })
        });

        return Response.success(books);
    }

    return null;
}