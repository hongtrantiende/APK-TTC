load('config.js');

function execute(url) {
    url = url.replace(/^(?:https?:\/\/)?(?:[^@\n]+@)?(?:www\.)?([^:\/\n?]+)/img, BASE_URL);
    url = url.replace("/book/", "/list/").replace(".html", "/");
    let response = fetch(url, {
        headers: {
            'user-agent': UserAgent.android()
        }
    });
    if (response.ok) {
        let doc = response.html();
        let chapters = [];
        doc.select("ul.list li a").forEach(e => {
            chapters.push({
                name: e.select("a span").text(),
                url: e.attr("href"),
                host: BASE_URL
            })
        });

        return Response.success(chapters);
    }

    return null;
}