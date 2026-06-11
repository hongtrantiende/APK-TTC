load('config.js');

function execute(url) {
    url = url.replace(/^(?:https?:\/\/)?(?:[^@\n]+@)?(?:www\.)?([^:\/\n?]+)/img, BASE_URL);
    let response = fetch(url);
    if (response.ok) {
        let doc = response.html();
        const data = [];
        let el = doc.select(".directoryArea p a");
        for (let i = 5; i < el.size(); i++) {
            let e = el.get(i);
            data.push({
                name: e.text(),
                url: BASE_URL + e.attr("href"),
                host: BASE_URL
            });
        }
        return Response.success(data);
    }
    return null;
}
