load("config.js");

function execute(key, page) {
    let response = fetch(BASE_URL + "/search/", {
        method: "POST",
        queries: {
            "searchkey": key
        }
    });

    if (response.ok) {
        let doc = response.html();
        let books = [];
        doc.select(".border3.commend.flex.flex-between.category-commend .category-div").forEach(e => {
            let nameElement = e.select(".flex.flex-between.commend-title h3 a").first();
            let authorElement = e.select(".flex.flex-between.commend-title span").first();
            let coverElement = e.select("img.lazy");

            let description = `${e.select(".intro.indent").text()} - Author: ${authorElement.text()}`;

            books.push({
                name: nameElement.text(),
                link: nameElement.attr("href"),
                cover: coverElement.attr("data-original"),
                description: description,
                host: BASE_URL
            });
        });

        return Response.success(books);
    }

    return null;
}