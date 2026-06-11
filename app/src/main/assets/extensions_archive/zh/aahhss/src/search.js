load('config.js');
function execute(key,page) {
    let response = fetch(BASE_URL + "/e/search/", {
        method: "POST",
        queries: {
            "keyboard": key
        }
    });

    if (response.ok) {
        let doc = response.html();
        let books = [];
        doc.select(".book-like a").forEach(e => 
        {
            books.push({
                name: e.select("h4").first().text(),
                link: BASE_URL + e.attr("href"),
                description: e.select("span").text(),
                host: BASE_URL
            })
            
        });

        return Response.success(books);
    }

    return null;
}