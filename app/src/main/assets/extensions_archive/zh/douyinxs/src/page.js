load('config.js');
function execute(url) {
    url = url.replace(/^(?:https?:\/\/)?(?:[^@\n]+@)?(?:www\.)?([^:\/\n?]+)/img, BASE_URL);
    let response = fetch(url);
    if (response.ok) {
        let doc = response.html();
        let pages = [];
        doc.select("#indexselect option").forEach(e => {
            console.log(e)
            pages.push(BASE_URL + e.attr("value"));
        });
        if (pages.length === 0) {
            pages.push(url);
        }
        return Response.success(pages);
    }
    return null;
}