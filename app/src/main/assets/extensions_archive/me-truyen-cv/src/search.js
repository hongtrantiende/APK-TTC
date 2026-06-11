load("config.js");

function execute(key, page) {
    if (!page) page = "1";
    //let response = fetch(BASE_URL + "/page/" + page + "/?s=" + encodeURIComponent(key) + "&post_type=wp-manga");
    let response = fetch(BASE_URL + "/page/" + page + "/", {
        queries: {
            s: key,
            post_type: "wp-manga"
        }
    });
    if (response.ok) {
        let doc = response.html();
        let placeholder = doc.select('#madara_goto_page').attr('placeholder');
        let next = '';
        if (placeholder && !isNaN(placeholder)) {
            next = (parseInt(placeholder, 10) + 1).toString();
        }

        let novelList = [];
        doc.select(".c-tabs-item").forEach(e => {
            let name = e.select(".post-title h3 a").text();
            let link = e.select(".post-title h3 a").attr("href");
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