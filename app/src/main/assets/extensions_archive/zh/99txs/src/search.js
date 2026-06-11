load("config.js");
function execute(key, page) {
    if(!page) page = 1
    let response = fetch(BASE_URL + "/modules/article/search.php?searchkey=" + key + "&searchtype=all&page=" + page, {
        method: "POST",
    });
    console.log(BASE_URL + "/modules/article/search.php?searchkey=" + key + "&searchtype=all&page=" + page)

    if (response.ok) {
        let doc = response.html();
        let books = [];
        doc.select(".c_row").forEach(e => {
            books.push({
                name: e.select("h4").last().text(),
                link: e.select("a").first().attr("href"),
                cover: e.select(".fl a img").attr("src"),
                description: e.select(".c_description").text(),
                host: BASE_URL
            })
        });
        let next = (parseInt(page) + 1).toString();
        return Response.success(books,next);
    }

    return null;
}