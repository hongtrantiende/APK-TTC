load('config.js');

function execute(url, page) {
    if (!page) page = '1';
    url = url.replace(/^(?:https?:\/\/)?(?:[^@\n]+@)?(?:www\.)?([^:\/\n?]+)/img, BASE_URL);
    let response = fetch(BASE_URL + url + page + ".html");
    
    if (response.ok) {
        let doc = response.html();
        const data = [];
        
        doc.select(".CGsectionTwo-right-content-unit").forEach(e => {
            data.push({
                name: e.select("a.r").text().trim(),
                link: BASE_URL + e.select("a.r").attr("href"),
                description: e.select("p").last().text().trim(),
                host: BASE_URL
            });
        });
        
        let next = (parseInt(page) + 1).toString();
        return Response.success(data, next);
    }
    
    return null;
}
