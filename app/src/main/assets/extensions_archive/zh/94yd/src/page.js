load('config.js');
function execute(url) {
    url = url.replace(/^(?:https?:\/\/)?(?:[^@\n]+@)?(?:www\.)?([^:\/\n?]+)/img, BASE_URL);
    let url1 = url.replace(".html","")
    let response = fetch(url1);
    if (response.ok) {
        let doc = response.html();
        let pages = [];
        doc.select(".pagenum").last().select("option").forEach(e => {
            console.log(e)
            pages.push(BASE_URL + e.attr("value"));
        });
        if (pages.length === 0) {
            pages.push(url1);
        }
        return Response.success(pages);
    }
    return null;
}