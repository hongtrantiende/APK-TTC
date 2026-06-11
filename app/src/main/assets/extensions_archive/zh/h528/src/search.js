load("config.js");

function execute(url, key, page) {
    url = url.replace(/^(?:https?:\/\/)?(?:[^@\n]+@)?(?:www\.)?([^:\/\n?]+)/img, BASE_URL);
    if(!page) page = '1';
    let response = fetch(BASE_URL + "/page/" + page + "?s=" + key);

    if (response.ok) {
        let doc = response.html();
        let books = [];
        doc.select("#content .post").forEach(e => {
            books.push({
                name: e.select("h3 a").first().text(),
                link: e.select("h3 a").first().attr("href"),
                description: e.select("a").text(),
                host: BASE_URL
            })
        });

        let next = (parseInt(page) + 1).toString();
        return Response.success(books,next)
    }

    return null;
}