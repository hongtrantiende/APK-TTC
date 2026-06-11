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
        doc.select(".tab-bd .list li").forEach(e => 
        {
            books.push({
                name: e.select(".name").first().text(),
                link: BASE_URL + e.select("a").first().attr("href"),
                description: e.select(".time").text(),
                host: BASE_URL
            })
            
        });

        return Response.success(books);
    }

    return null;
}