load("config.js");

function execute(key, page) {
    let response = fetch(BASE_URL + "/modules/article/search.php", {
        method: "GBK",
        queries: {
            "searchkey": key
        }
    });

    if (response.ok) {
        let doc = response.html();
        let books = [];
        doc.select(".main li").forEach(e => {
            books.push({
                name: e.select(".s2 a").first().text(),
                link: e.select(".s2 a").first().attr("href"),
                cover: e.select("img").attr("src"),
                host: BASE_URL
            })
        });

        return Response.success(books);
    }

    return null;
}