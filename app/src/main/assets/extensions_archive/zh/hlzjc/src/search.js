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
        doc.select(".related-content ul li").forEach(e => {
            books.push({
                name: e.select(".list0 .title a").last().text(),
                link: e.select(".list0 .title a").last().attr("href"),
                cover: e.select("img").attr("src"),
                description: e.select(".intro").text(),
                host: BASE_URL
            })
        });

        return Response.success(books);
    }

    return null;
}