load('config.js');
function execute(key) {
    let response = fetch(BASE_URL + "/h5/search?word=" + key, {
      method: "GET"
    });
    if (response.ok) {
        let doc = response.html();
        let books = [];
        doc.select(".books-list li").forEach(e => 
        {
            books.push({
                name: e.select("h3").last().text(),
                link: BASE_URL + e.select("a").first().attr("href"),
                cover: e.select("a img").attr("src"),
                description: e.select(".author").text(),
                host: BASE_URL
            })
        });

        return Response.success(books);
    }

    return null;
}