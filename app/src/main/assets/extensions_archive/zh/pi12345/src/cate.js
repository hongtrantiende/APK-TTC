load('config.js');

function execute(url, page) {
    if (!page) page = '1';
    url = url.replace(/^(?:https?:\/\/)?(?:[^@\n]+@)?(?:www\.)?([^:\/\n?]+)/img, BASE_URL);
    let response = fetch(BASE_URL + url + page + ".html");
    console.log(BASE_URL + url + page + ".html");

    if (response.ok) {
        let doc = response.html();
        const data = [];

        doc.select(".CGsectionTwo-right-content-unit").forEach(e => {
            const title = e.select("a.title").first();
            const author = e.select("p span a.b").first();
            const description = e.select("p").last().text();
            const link = BASE_URL + title.attr("href");

            data.push({
                name: title.text(),
                link: link,
                description: `${author.text()} - ${description}`,
                host: BASE_URL
            });
        });

        let next = (parseInt(page) + 1).toString();
        return Response.success(data, next);
    }

    return null;
}
