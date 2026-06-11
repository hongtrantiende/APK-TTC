load('config.js');

function execute(url) {
    url = url.replace(/^(?:https?:\/\/)?(?:[^@\n]+@)?(?:www\.)?([^:\/\n?]+)/img, BASE_URL);

    let response = fetch(url);
    if (response.ok) {
        let doc = response.html();
        let chapters = [];
        let el = doc.select(".panel-chapterlist").get(1)
        el.select(".col-md-3 a").forEach(e => {
            chapters.push({
                name: e.text(),
                url: e.select("a").attr("href"),
                host: BASE_URL
            })
        });

        return Response.success(chapters);
    }

    return null;
}