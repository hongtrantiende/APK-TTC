load('config.js');

function execute(url) {
    url = url.replace(/^(?:https?:\/\/)?(?:[^@\n]+@)?(?:www\.)?([^:\/\n?]+)/img, BASE_URL);
    !1===url.endsWith("/")&&(url+="/");
    let response = fetch(url);
    if (response.ok) {
        let doc = response.html();
        let chapters = [];
        let el = doc.select("#list dl dd a")
        for (let i = 12; i < el.size(); i++) {
            let e = el.get(i);
            chapters.push({
                name: e.select("a").text(),
                url: url + e.attr("href"),
                host: BASE_URL
            });
        };

        return Response.success(chapters);
    }

    return null;
}