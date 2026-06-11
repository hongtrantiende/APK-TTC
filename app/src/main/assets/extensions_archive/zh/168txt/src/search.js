load("config.js");

function execute(key, page) {
    let response = fetch(BASE_URL + "/s/", {
        method: "POST",
        queries: {
            "s": key
        }
    });

    if (response.ok) {
        let doc = response.html();
        let books = [];
        doc.select(".mh_list li").forEach(e => {
            books.push({
                name: e.select("a label").first().text(),
                link: e.select("a").first().attr("href"),
                description: e.select("dd").text(),
                host: BASE_URL
            })
        });

        return Response.success(books);
    }

    return null;
}