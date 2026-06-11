load('config.js');

function execute(url) {
    url = url.replace(/^(?:https?:\/\/)?(?:[^@\n]+@)?(?:www\.)?([^:\/\n?]+)/img, BASE_URL);
    let response = fetch(BASE_URL + url);
    if (response.ok) {
        let doc = response.html();
        const data = [];
        doc.select("#recent_parent_story_widget-2 ul li").forEach(e => {
            let name = e.select("a").first().text();
            data.push({
                name: name,
                link: e.select("a").first().attr("href"),
                description: e.select("a").first().text(),
                host: BASE_URL
            });
        });
        return Response.success(data);
    }
    return null;
}
