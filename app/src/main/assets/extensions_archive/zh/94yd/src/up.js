load('config.js');
function execute(url,page) {
    if(!page) page = '1';
    url = url.replace(/^(?:https?:\/\/)?(?:[^@\n]+@)?(?:www\.)?([^:\/\n?]+)/img, BASE_URL);
    let response = fetch(BASE_URL + url + page + ".html");
    console.log(BASE_URL + url + page + ".html")
    if (response.ok) {
        let doc = response.html();
        const data = [];
        doc.select(".list li").forEach(e => {
            let link = BASE_URL + e.select("a").first().attr("href");
            let cover = e.select("img").first().attr("src");
            data.push({
                name: e.select("p").first().text(),
                link: link,
                cover: cover,
                description: e.select("p").last().text(),
                host: BASE_URL
            })
        });
        let next = (parseInt(page) + 1).toString();
        return Response.success(data,next)
    }
    return null;
}