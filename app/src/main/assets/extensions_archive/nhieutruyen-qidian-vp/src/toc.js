load('config.js')
function execute(url) {
    let response = fetch(url + "/muc-luc");
    if (response.ok) {
        let doc = response.html();
        let el = doc.select("#chapter-list a");
        const data = [];
        for (let i = 0; i < el.size(); i++) {
            let e = el.get(i);
            data.push({
                name: e.select("h3").text(),
                url: e.attr("href")
            });
        }
        return Response.success(data);
    }
    return null;
}