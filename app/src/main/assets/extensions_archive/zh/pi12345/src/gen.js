load('config.js');

function execute(url) {
    url = url.replace(/^(?:https?:\/\/)?(?:[^@\n]+@)?(?:www\.)?([^:\/\n?]+)/img, BASE_URL);
    let response = fetch(BASE_URL + url);
    
    if (response.ok) {
        let doc = response.html();
        const data = [];
        
        doc.select(".CGsectionTwo-right-content-unit").forEach(e => {
            data.push({
                name: e.select("a.r").text(),
                link: BASE_URL + e.select("a.r").attr("href"),
                description: e.select("p").last().text(),
                host: BASE_URL
            });
        });
        
        return Response.success(data);
    }
    
    return null;
}