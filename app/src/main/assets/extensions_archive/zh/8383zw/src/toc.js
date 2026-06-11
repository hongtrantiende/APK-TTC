load('config.js');
function execute(url) {
    url = url.replace("/book/", "/allbook/");
    let response = fetch(url);
    if (response.ok) {
        let doc = response.html();
        let el1 = doc.select("#content_1 a[rel='chapter']");
        const data = [];
        for (let i = 0; i < el1.size(); i++) {
            var e = el1.get(i);
            data.push({
                name: e.select("dd").text(),
                url: BASE_URL + e.attr("href"),
                host: BASE_URL
            })
        }
        return Response.success(data);
    }
    return null;
}
