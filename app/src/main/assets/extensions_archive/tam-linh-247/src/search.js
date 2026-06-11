load("config.js");

function execute(key, page) {
    let response = fetch(BASE_URL + "/tim-truyen/", {
        method: "UTF-8",
        queries: {
            "q": key
        }
    });

    if (response.ok) {
        let doc = response.html();
        let books = [];
        doc.select(".show-list-home-select .row-custom div.col-md-2").forEach(e => {
            books.push({
                name: e.select("a h3").last.text(),
                link: e.select("a").first().attr("href"),
                cover: e.select("a img").attr("src"),
                description: e.select(".rate").text(),
                host: BASE_URL
            })
        });

        return Response.success(books);
    }

    return null;
}