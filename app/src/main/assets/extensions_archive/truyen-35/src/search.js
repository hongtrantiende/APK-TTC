load('config.js');
function execute(key,page) {
    if(!page) page = '1';
    let response = fetch(BASE_URL + "/search?q=" + key + "&page=" + page);

    if (response.ok) {
        let doc = response.html();
        const data = [];
        doc.select(".index-intro .item").forEach(e => {
            data.push({
                name: e.select("h3").last().text(),
                link: e.select("a").first().attr("href"),
                cover: e.select(".img-hover img").first().attr("src"),
                description: "Chương" + e.select(".show-chapters").first().text(),
                host: BASE_URL
            })
        });
        let next = (parseInt(page) + 1).toString();
        return Response.success(data,next)
    }
    return null;
}