load("config.js");

function execute(key, page) {
    if(!page) page = 1
    let response = fetch(BASE_URL + "/q/" + key + "?page=" + page, {
        method: "GET",
    });

    if (response.ok) {
        let doc = response.html();
        let books = [];
        doc.select(".col-md-6 ul li").forEach(e => {
            books.push({
                name: e.select("a").first().text(),
                link: e.select("a").first().attr("href"),
                description: e.select("a").text(),
                host: BASE_URL
            })
        });

        let next = (parseInt(page) + 1).toString();
        return Response.success(books,next)
    }

    return null;
}