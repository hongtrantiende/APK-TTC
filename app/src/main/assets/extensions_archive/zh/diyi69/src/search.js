load('config.js');
function execute(key,page) {
    let response = fetch(BASE_URL + "/search/", {
        method: "POST",
        queries: {
            "searchkey": key,
            searhtype: "all"
        }
    });

    if (response.ok) {
        let doc = response.html();
        let books = [];
        doc.select(".searchresult").forEach(e => 
        {
            books.push({
                name: e.select("h3").last().text(),
                link: BASE_URL + e.select("a").first().attr("href"),
                cover: e.select("img").first().attr("data-original"),
                description: e.select("span").first().text(),
                host: BASE_URL
            })
            
        });

        return Response.success(books);
    }

    return null;
}