load('config.js');
function execute(url,page) {
    if(!page) page = '1';
    url = url.replace(/^(?:https?:\/\/)?(?:[^@\n]+@)?(?:www\.)?([^:\/\n?]+)/img, BASE_URL);
    let response = fetch(BASE_URL + url + page + "/");
    console.log(BASE_URL + url + page + "/")
    if (response.ok) {
        let doc = response.html();
        const data = [];
        doc.select(".l div.item").forEach(e => {
          data.push({
            name: e.select("dl dt a").first().text(),
            link: BASE_URL + e.select("dl dt a").first().attr("href"),
            cover: e.select(".image a img").first().attr("href"),
            description: e.select("dl dd").first().text(),
            host: BASE_URL
          })
        });
        let next = (parseInt(page) + 1).toString();
        return Response.success(data,next)
    }
    return null;
}