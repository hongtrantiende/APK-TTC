load('config.js');
function execute(key,page) {
    let response = fetch(BASE_URL + "/search/", {
        method: "POST",
        queries: {
            "searchkey": key
        }
    });

    if (response.ok) {
        let doc = response.html();
        let books = [];
        doc.select(".category-div").forEach(e => 
        {
            books.push({
                name: e.select("h3").last().text(),
                link: BASE_URL + e.select("a").first().attr("href"),
                cover: e.select("a img").attr("data-original"),
                description: e.select(".intro").text(),
                host: BASE_URL
            })
            
        });

        return Response.success(books);
    }

    return null;
}