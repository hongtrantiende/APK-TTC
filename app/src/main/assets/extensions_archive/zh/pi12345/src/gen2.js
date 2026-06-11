load('config.js');

function execute(url) {
    url = url.replace(/^(?:https?:\/\/)?(?:[^@\n]+@)?(?:www\.)?([^:\/\n?]+)/img, BASE_URL);
    let response = fetch(BASE_URL + url);

    if (response.ok) {
        let doc = response.html();
        const data = [];

        doc.select(".sectionTwo-content p").forEach(e => {
            const genre = e.select("span").first().text();
            const name = e.select("a.b").text();
            const author = e.select("a.g").text();

            data.push({
                genre,
                name,
                author,
                link: BASE_URL + e.select("a.b").attr("href"),
            });
        });

        return Response.success(data);
    }

    return null;
}
