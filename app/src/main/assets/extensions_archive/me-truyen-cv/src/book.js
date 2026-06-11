load("config.js");
function execute(url, page) {
    if (!page) page = "1";
    url = url.replace(/^(?:https?:\/\/)?(?:[^@\n]+@)?(?:www\.)?([^:\/\n?]+)/img, BASE_URL) + "/page/" + page;
    let response = fetch(url);
    if (response.ok) {

        let doc = response.html();
        let placeholder = doc.select('#madara_goto_page').attr('placeholder');
        let next = '';
        if (placeholder && !isNaN(placeholder)) {
            next = (parseInt(placeholder, 10) + 1).toString();
        }

        let novelList = [];
        doc.select(".page-listing-item").forEach(e => {
            let name = e.select(".post-title a, h3 a").text();
            let link = e.select(".post-title a, h3 a").attr("href");
            let cover = e.select("img").attr("src");
            let description = e.select(".except-summary").text();
            if (name && link) {
                novelList.push({
                    name: name,
                    link: link,
                    cover: cover,
                    author: "N/A",
                    description: description,
                    host: BASE_URL
                });
            }
        });

        return Response.success(novelList, next);
    }
    return null;
}