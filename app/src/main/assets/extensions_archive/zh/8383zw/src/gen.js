load('config.js');

function execute(url, page) {
    if (!page) page = '1';
    url = url.replace(/^(?:https?:\/\/)?(?:[^@\n]+@)?(?:www\.)?([^:\/\n?]+)/img, BASE_URL);
    let response = fetch(BASE_URL + url + page + '/');
    if (response.ok) {
        let doc = response.html();
        const data = [];
        
        // Process items from .rank .item
        doc.select(".rank .item").forEach(e => {
            data.push({
                name: e.select("a").first().attr("title"),
                link: BASE_URL + e.select("a").first().attr("href"),
                cover: e.select(".image img").first().attr("data-original"),
                description: e.select(".btm a").first().text(),
                host: BASE_URL
            });
        });

        // Process items from .r ul > li
        doc.select(".r ul > li").forEach(e => {
            data.push({
                name: e.select(".s2 a").text(),
                link: BASE_URL + e.select(".s2 a").attr("href"),
                cover: '',  // No cover information in this part
                description: e.select(".s5").text(),
                host: BASE_URL
            });
        });
        
        // Process items from .l ul > lin
        doc.select(".l ul > li").forEach(e => {
            data.push({
                name: e.select(".s2 a").text(),
                link: BASE_URL + e.select(".s2 a").attr("href"),
                cover: '',  // No cover information in this part
                description: e.select(".s5").text(),
                host: BASE_URL
            });
        });

        let next = (parseInt(page) + 1).toString();
        return Response.success(data, next);
    }
    return null;
}
